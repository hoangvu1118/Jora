# 3. Decisions and trade-offs

Each decision says: the problem, the options we looked at, what we chose, **why**, what it costs us, and when to revisit it. Dates are 2026. If you want to change one of these, first understand the "why"; most of them came from a real bug or a real race.

Contents
- [A. Overall shape](#a-overall-shape)
- [B. Crawling (how jobs get into Jora)](#b-crawling)
- [C. Persistence (how we store data)](#c-persistence)
- [D. Resumes and the LLM](#d-resumes-and-the-llm)
- [E. API, errors and security](#e-api-errors-and-security)
- [F. Hosting and delivery](#f-hosting-and-delivery)

---

## A. Overall shape

### D1. Modular monolith, not microservices
- **Options:** one Spring Boot app with feature packages / split into crawler, recommender, resume services.
- **Chosen:** one app. Kafka is used *inside* it to split background work into steps.
- **Why:** one developer, a hard launch date, one database. Microservices would add network calls, separate deploys and distributed debugging with no real gain at this size. Because the crawl steps already talk only through Kafka topics, a step can later move into its own service without rewriting it.
- **Revisit when:** one part needs to scale very differently from the rest (for example, many crawler workers but one API), or a second team joins.

### D2. Feature packages, interfaces at the edges
- **Chosen:** `auth/`, `job/`, `resume/`, ... each with its own controller/service/repo/dto, and interfaces (`JobSource`, `FileStorage`, `ResumeContentParser`, `ResumeTailor`, `JobMatchingStrategy`) wherever the implementation is likely to change.
- **Why:** you can find everything about a feature in one folder. The interfaces paid off already: S3 replaced local disk with one new class and a property; Gemini code is isolated behind two interfaces.

### D3. Vertical slices with a "done when" check
- Each piece of work is a complete user-visible path ("a user can upload a CV and get a BASE version, tested with Postman"), not a layer ("write the repositories"). This kept every day's work shippable.

### D4. "Use Kafka because a real async problem exists"
- The original roadmap put Kafka at milestone 10. On 11 Oct we mapped where it genuinely helps: crawling (slow, fails in pieces, others react to it) was the clear winner. Emails, analytics and tailoring were noted as later candidates. Redis was mapped to locks and rate limits, with a read cache only when a slow read is measured.

---

## B. Crawling

### B1. Where jobs come from: public ATS APIs, Greenhouse first
- **Options:** scrape career pages (HTML), scrape LinkedIn/Indeed, use public applicant-tracking-system (ATS) APIs (Greenhouse, Lever, Ashby), read JSON-LD `JobPosting` markup on pages.
- **Chosen:** Greenhouse's public boards API (`/v1/boards/{token}/jobs` then `/jobs/{id}`). Lever, Ashby and JSON-LD have empty stub classes ready behind the `JobSource` interface.
- **Why:** structured JSON, stable ids, no login, legal to read, no HTML that breaks when a site redesigns. LinkedIn/Indeed scraping breaks terms of service and gets blocked.
- **Cost:** only companies that use Greenhouse, and only those we list in `crawl_targets`. Few jobs at launch (risk noted in the plan: pick boards early).

### B2. Filter by location before fetching details
- `JobLocationFilter` keeps only Vietnam (several spellings, HCMC, Hanoi, Da Nang) and Singapore, using the location in the cheap job **list** call.
- **Why:** each detail is one more HTTP call. Filtering first cuts most calls. **Cost:** the list is hard-coded; a new market means a code change (move to config or DB later). Jobs with no location are dropped.

### B3. Two levels of de-duplication: posting and job
- **JobPosting** = one sighting on one platform, unique on `(platform_id, external_job_id)` (DB index). Seen again? Only `last_seen_at` is updated (`markSeen`), and no detail call is made.
- **Job** = the real job, matched on `(company, normalized title, location)`. The same job on two boards becomes one Job with two postings, so a user gets **one** recommendation, not two.
- **Trade-off:** matching on exact lower-cased title + location is simple but rough. "Backend Engineer" in two teams at the same company and city would merge into one Job. Fuzzy matching was left for later.

### B4. Company = board token
- The Company row is looked up by the **board token** (`sourceKey`), not by the display name Greenhouse returns. **Why:** a bug on 28 Oct created duplicate company rows when the display name was used in one place and the token in another. **Cost:** the company "name" shown to users is the token (`axon`). Add a display-name column later.

### B5. From a synchronous endpoint to a 3-step Kafka pipeline
- **Before:** `POST /admin/ingestion/greenhouse/{token}` crawled inside the HTTP request, one blocking call per job, and nothing ran on a timer.
- **Options:** keep it synchronous + `@Async`; one topic doing everything; a pipeline of small steps.
- **Chosen:** `crawl.requested` -> `job.discovered` -> `job.created`, plus the endpoint now just publishes and returns **202**.
- **Why each step is separate:** fetching (network, can be slow, can be blocked), saving (DB rules, de-dup) and recommending (CPU over all profiles) fail for different reasons and should retry separately. If saving one job fails, only that one message retries, not the whole board. New consumers (emails, analytics) can subscribe to `job.created` without touching the crawler.
- **Naming:** `*.requested` for commands, past tense (`discovered`, `created`) for facts that happened.

### B6. Message keys and partitions
| Topic | Key | Why this key |
|---|---|---|
| `crawl.requested` | board token | All requests for one board land on the same partition, so they are handled one at a time, in order |
| `job.discovered` | `platform:externalId` | A retry or duplicate of the same posting goes to the same partition and same consumer thread, so two copies can't race each other into the DB |
| `job.created` | job id as **String** | Same job always in order. It is a String because the default key serializer is `StringSerializer`; a `KafkaTemplate<Long, ...>` compiled fine but failed at runtime with `ClassCastException` (fixed 28 Oct) |

All topics have **3 partitions** = up to 3 consumers in the group can work in parallel. **Replication 1** because there is one broker (no copy survives if it dies; acceptable for launch, see [06](06-whats-left.md)).

### B7. At-least-once delivery, so every consumer is idempotent
- Kafka may deliver a message twice (a crash after the work but before the offset commit, or our own retries). The rule: **running a consumer twice must give the same result.**
- `JobDiscoveredPersistService.handleDiscoveredJob`: if the posting already exists, this is a retry; it **re-publishes `job.created` with the existing job id** instead of saving again. Why re-publish: the first attempt may have saved the row and then failed to publish, and we must not lose that event.
- `recommendForNewJob` upserts by (profile, job), and the DB has `UNIQUE(search_profile_id, job_id)`, so running it twice updates, never duplicates.
- Only a **brand new Job** fires `job.created`. A new posting of an existing job does not (that job was already scored).

### B8. Retries and dead-letter topics
- **Chosen:** `DefaultErrorHandler` + `FixedBackOff(1000, 3)` (1 try + 3 retries = 4 attempts, 1 s apart), then `DeadLetterPublishingRecoverer` to `<topic>.DLT` (3 partitions each, same partition as the source).
- Every publisher calls `.join()` on the send, so "Kafka didn't accept my message" becomes an exception inside the listener and gets the same retry. Without `.join()` the send fails silently in the background.
- **Trade-off:** these retries are **blocking**: while one bad message retries, the rest of its partition waits about 3 s. Fine at our volume. The non-blocking alternative is Spring's `@RetryableTopic` (retry topics with growing delays). Nothing reads the DLTs yet; you inspect them in Kafka UI and replay by hand.

### B9. Redis crawl lock
- **Problem:** the scheduler, an admin call and a Kafka redelivery (or two app copies) could crawl the same board at the same time, doubling calls to Greenhouse and racing on inserts.
- **Options:** Postgres row lock or `last_crawled_at` column; ShedLock on the scheduler; Redis `SET NX EX`.
- **Chosen:** Redis `SET crawl:{platform}:{token} NX EX 1800` in `CrawlLock`. It is both a lock **and** a 30-minute cooldown: if it is set, the crawl is skipped and logged. On failure the key is deleted and the error rethrown, so Kafka's retry can try again right away.
- **Why Redis:** atomic, has expiry built in (a crashed app can never leave the lock stuck forever), shared by every app copy, and cheap.
- **Trade-off:** if Redis is down, the listener fails and retries, then goes to the DLT. Crawling depends on Redis.

### B10. The scheduler and the `crawl_targets` table
- **Chosen:** `CrawlService` with `@Scheduled(cron = "${crawl.schedule.cron}", zone = "${crawl.time.zone}")`, default 06:00 and 18:00 Vietnam time. It reads enabled rows from `crawl_targets` (one query with `JOIN FETCH` of the platform) and publishes one `crawl.requested` each. Each target is wrapped in its own try/catch, so one bad target doesn't stop the rest.
- **Why a table, not a list in code or config:** you can add or disable a board with one SQL insert/update, no deploy. `UNIQUE(platform_id, company_board_token)` stops duplicates.
- **Why it only publishes:** SRP. The scheduler decides **when**; the slow work happens in the listener, so the single scheduler thread is never blocked.
- **Two app copies?** Both schedulers fire, but the Redis lock makes the second crawl a no-op.

### B11. Recommendations: push for new jobs, pull on demand
- `job.created` -> score **that one job** against **every** search profile (`recommendForNewJob`), save those with score >= 20 (`job.matchScore.threshold`).
- The older `POST /api/v1/jobRecommendation` (score all candidate jobs for one profile) stays for a user who just created or changed a profile.
- **Scoring** (`RuleBasedJobMatchingStrategy`): title match +40, location +20, employment type +20, skill found in description +20. Reasons saved as an enum set. Simple and explainable, behind an interface so a smarter strategy (embeddings) can replace it.
- **Cost at scale:** "every profile for every new job" grows with users x jobs. See [06](06-whats-left.md).

---

## C. Persistence

### C1. PostgreSQL + Flyway, `ddl-auto=validate`
- Flyway owns the schema. Hibernate only validates. **Why:** `ddl-auto=create` was once on (Milestone 5) and silently wiped data and hid schema mistakes. Migrations named with a single underscore (`V8_`) were also silently skipped. Rule now: never edit an old migration, always `V{n}__name.sql`.

### C2. Job domain normalized into four tables
- `company`, `platforms`, `jobs`, `job_postings`. A job knows its company; each posting knows its job and platform. **Why:** de-dup (B3), and a closed posting doesn't have to close the job if another board still lists it.

### C3. Paging: keyset (cursor), not offset
- **Options:** `OFFSET/LIMIT` with page numbers / keyset `WHERE (score, id) < (lastScore, lastId)`.
- **Chosen:** keyset. Recommendations sort by `(match_score DESC, id DESC)`, jobs by `(created date, id)`. Default size 20, max 50, more is a 400. Fetch `size + 1` rows to know `hasNext` without a `COUNT(*)`.
- **Why:** offset gets slower the deeper you go (the DB reads and throws away all skipped rows), and rows shift between pages when new jobs arrive (duplicates/missing items). Keyset is constant speed and stable. **Cost:** no "jump to page 7", only "next". Fine for an infinite-scroll list.
- The `id` tie-breaker matters: many recommendations share the same score.

### C4. Resume storage: three different things in three places
| Thing | Where | Why |
|---|---|---|
| Original PDF | File storage (local folder / S3). DB stores only the **key** `resumes/{userId}/{UUID}.pdf` in `resume.file_path` | Big binary files don't belong in Postgres. UUID, not the user's filename: no overwrite when the same name is uploaded twice, and no path-traversal tricks from a filename |
| Raw extracted text | `resume.raw_text` (TEXT) | Messy, never edited, belongs to the upload. Re-parsing never needs the PDF again |
| Structured CV | `resume_version.content` (JSONB, Java records `ResumeContent`) | Read and written as a whole, never queried by SQL, shape will evolve. One JSONB column beat 6 child tables |

- `ResumeContent` follows the sections of "Jake's Resume" (a well-known one-page template): header, education, experiences, projects, skill groups, extra sections. Dates are `YearMonth`; `null` end date = Present. A compact constructor turns `null` lists into empty lists.
- A PDF of a version is **never stored**; it will be rendered from `content` on download (same renderer for preview and download). Only the user's original upload is a stored file.
- Upload order: save the file **first**, then the row; if the row fails, delete the file. (Row first would leave rows pointing at missing files, which is worse.)

### C5. Version model: BASE / TAILORED and DRAFT / FINAL
- Every version has `resume_id` (makes ownership checks and listing one join), optional `job_id` (TAILORED only) and `parent_version_id` (TAILORED -> its BASE; later, edited FINAL -> new row).
- **DRAFT**: saving overwrites the same row. **FINAL**: locked (`updateContent()` throws); editing a FINAL creates a new DRAFT row whose parent is that FINAL. **Why:** you must always be able to show exactly which CV was sent to which company, without creating a new row on every keystroke.

### C6. Business rules enforced by partial unique indexes
- `V12`: `UNIQUE (resume_id) WHERE type='BASE'`. `V13`: `UNIQUE (resume_id, job_id) WHERE type='TAILORED'`.
- **Why the DB and not just a Java check:** two parallel requests both pass a "does it exist?" check and both insert. Only the database can stop the second one. The service still checks first (to skip the expensive Gemini call), and catches `DataIntegrityViolationException` and returns the row that won.
- **Idempotent HTTP:** asking for a BASE/TAILORED that exists returns it with **200** and makes no Gemini call; a new one is **201**. (Chosen over 409 because the client wanted the thing, and now has it.)
- The "one application per (user, job)" rule belongs to the future `application` table (`UNIQUE(user_id, job_id)`), not to versions.

### C7. Archive is a soft delete
- `resume.archived_at` instead of `DELETE`. Versions and history survive (an application may point at them). An archived resume is read-only: every write returns **409**; archiving twice is also 409 (owner's choice). Reads use `getOwnedResume`, writes use `getActiveResume`.

### C8. Ownership and 404
- Always load with the user in the query (`findByIdAndUserId`, `findByIdAndResumeIdAndResumeUserId`). Someone else's id gives **404**, not 403, so an attacker can't even learn it exists. This closed a real IDOR bug found in review (20 Oct).

### C9. `FileStorage` abstraction and S3
- `FileStorage { save(userId, file) -> key; load(key); delete(key) }`. Validation (PDF only, not empty, size) lives in the service, not the storage. Storage errors are wrapped in `FileStorageException`, and the internal key never appears in an error message.
- S3 is chosen by `STORAGE_TYPE=s3`. On EC2 the SDK picks up credentials from the **instance role**, so no AWS keys live on the server.

---

## D. Resumes and the LLM

### D5. Use an LLM for CV parsing now
- **Options (15 Oct):** empty draft the user fills in / regex parser / LLM.
- **Chosen:** LLM, behind `ResumeContentParser`, output as DRAFT for the user to review. **Costs accepted:** money, seconds of latency, an API key, possible hallucination (handled by "never invent" prompt rules + user review).

### D6. Gemini free tier
- Chosen for cost. **Known risk:** free-tier prompts may be used by Google to improve products, and CVs are personal data. Before paying users, move to a paid tier (or another provider) with a no-training data policy, and say so in a privacy notice.

### D7. Three separate calls: upload, extract, parse
- **Why:** each can fail on its own (bad file, unreadable PDF, LLM down) and be retried alone. The client calls them in order. `raw_text = null` means "not extracted yet" (400 if you try to parse).

### D8. Tailoring rules (agreed 23 Oct)
- Experience bullets: rephrase toward the job, keep every number and the purpose. Projects: may reorder and emphasize, never add a technology. Skills: remove or reorder only, never add. Header and education: untouched (copied in code, not sent to Gemini). Same number of entries in, same out. The user decides when to tailor (no "is my BASE good enough?" AI check).
- Only the three changeable sections are sent to Gemini (less data, less to break, less personal data shared).

### D9. Tailoring stays **synchronous** (decided 27 Oct)
- **Options:** sync request (user waits ~5-15 s) / Kafka `resume.tailoring.requested` -> `resume.tailored` with **202** + polling.
- **Chosen:** sync for launch. **Why:** one user waits for one result they asked for; async would need a status table, a polling or push endpoint, and frontend work, with a deadline 12 days away. Kafka was spent where it clearly pays (the crawl).
- **Cost:** each tailoring holds a web server thread for seconds. Under a traffic spike that is the first thing to run out (see [06](06-whats-left.md), "availability").

### D10. Interface split (ISP) and one Gemini client (DRY)
- `ResumeContentParser.parse(rawText)` and `ResumeTailor.tailorSections(jobDescription, sections)` are separate interfaces (different jobs). Both Gemini classes use one `GeminiClient.generate(prompt)`, the only place with the model name and the try/catch. The service never builds JSON for the LLM; the Gemini classes do.

### D11. Rate limit Gemini per user, at the last moment
- 10 calls/min/user via Redis. Checked **after** ownership, archive, "already exists" checks, right before the Gemini call, so cheap answers never use up the limit. Keyed by **user id** (a bug once keyed it by resume id, letting one user bypass it with many resumes).
- Gemini down = **503**, our limit hit = **429**.

---

## E. API, errors and security

### D12. One error format: `ProblemDetail`
- `GlobalExceptionHandler` extends the **servlet** `ResponseEntityExceptionHandler`, so Spring's own errors (bad JSON, `@Valid` failures, 404 route, 413 file too big) and ours all come back as RFC 9457 `ProblemDetail` JSON. 4xx show the real message; 5xx log the stack trace and return a generic message.

### D13. Stateless JWT
- No server session, so any app copy can serve any request (needed to scale out). 24 h tokens. **Trade-off:** no logout/revoke before expiry; the filter loads the user from the DB on every request.

### D14. Login rate limit keyed by email
- 5 tries per 15 min per email; success resets. **Trade-off:** someone can lock a victim out by guessing their email wrong 5 times. A common fix is to key by email **and** IP, or add a CAPTCHA after N failures.

---

## F. Hosting and delivery

### D15. Hosting: "AWS lean" for month one, then free tiers (decided 31 Oct)
| Option | What | Verdict |
|---|---|---|
| **A. AWS lean** | One EC2 runs the same compose file (app + Kafka + Redis); Postgres on RDS; files on S3 | **Chosen.** Almost identical to the laptop setup, real AWS learning, about $55-60/month covered by new-account credits |
| B. AWS managed | MSK (Kafka), ElastiCache (Redis), RDS, S3, ECS | Rejected for now: MSK and ElastiCache bill every hour even with zero users; much more networking setup |
| C. Free mix | Neon/Supabase (Postgres), Upstash (Redis), Aiven (Kafka), Render/Koyeb (app), Vercel (frontend) | Planned **after** month one to save money |
- **Why the database is managed even in option A:** data is the one thing you can't lose. RDS gives backups and restarts; Kafka and Redis on the EC2 only hold data we can rebuild.
- **Weakness accepted:** the EC2 is a single point of failure for the app, Kafka and Redis.
- **Portability rule:** every address, port and password comes from an env var, storage is S3-compatible, data moves with `pg_dump`. Moving hosts must be a config change, not a code change.
- **Region:** everything in Sydney (`ap-southeast-2`) because the account was created there; keeping app, DB and bucket in one region avoids cross-region latency and transfer fees.
- **Kafka UI is not public**; reach it with an SSH tunnel.

### D16. Frontend: React + Vite, separate repo
- Next.js's extras (server rendering, API routes) duplicate what Spring does, and learning it would cost days. A Vite build is static files that can be hosted free (S3 + CloudFront, Vercel, Netlify). Design is agreed visually first (three style directions were proposed: calm cards, bold poster, dark dashboard), then coded in one consistent, plain pattern.

### D17. CI/CD: GitHub Actions
- Chosen over GitLab CI and AWS CodePipeline: the code is already on GitHub, it's free for this size, and the YAML skills transfer everywhere. Plan: build + tests on every push, then build the image, push to a registry (GHCR or ECR) and deploy to EC2.
