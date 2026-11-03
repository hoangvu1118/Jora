# 6. What's left, what to improve, and what to do next

Everything here was checked against the code at commit `2c2be3a` (3 Nov 2026). The product goal shapes the priorities: Jora starts as a learning playground, but should grow into a product with **paying users**, so the top priority after launch is that **it stays available when traffic suddenly jumps**.

## 6.1 Must do before real users (launch, 8 Nov)

| # | Task | Why it blocks launch |
|---|---|---|
| 1 | **Frontend**: the 6 MVP screens (login/signup, search profile, recommendations, job detail + "Tailor my CV", my resume upload + BASE, tailored resume) in `jora-web` | Users can't use Postman |
| 2 | **Domain + HTTPS** (Elastic IP, then a reverse proxy such as Caddy or Nginx with Let's Encrypt in front of 8080, or an AWS load balancer with an ACM certificate) | JWTs and passwords currently travel over plain HTTP |
| 3 | **CORS** for the frontend's domain | The browser will block the frontend's calls otherwise |
| 4 | **GitHub Actions**: `./mvnw test` on every push | A broken push should never reach the server |
| 5 | **Public health check** (`/health` is currently behind auth; better: Spring Boot Actuator `/actuator/health` with DB, Redis and Kafka checks, permitted in `SecurityConfig`) | Uptime monitors and load balancers need it |
| 6 | **Remove test endpoints** `/test/axon` (`GreenhouseTestController`) and `/test/searchProfile` (`HealthController`) | Any logged-in user can call them |
| 7 | **Lock down the network**: RDS public access off, delete the home-IP rule on the DB; close 8080 once HTTPS proxy is on 443 | Smaller attack surface |
| 8 | **Add real crawl targets**: more Greenhouse boards that hire in Vietnam/Singapore | One board (`axon`) = very few jobs |
| 9 | **Privacy**: Gemini paid tier or a provider with a no-training policy, plus a short privacy notice | CVs are personal data |
| 10 | Set `spring.jpa.show-sql=false` in production | Every SQL statement is logged, slow and noisy |

## 6.2 Known bugs (verified in code)

| # | Where | Problem | Fix idea |
|---|---|---|---|
| B1 | `V14__crawl_target.sql` vs `GreenhouseJobSource.platformName()` | V14 seeds the platform as `greenhouse`; the source and listener use `Greenhouse`. `findByName` is case-sensitive, so a second platform row `Greenhouse` is probably created, and postings and crawl targets point at different platform rows. **Check:** `SELECT * FROM platforms;` | Use one constant name (lower case), migrate the data in a new migration, and/or compare names case-insensitively |
| B2 | `CrawlRequestListener` | Ignores `event.platform()` and always uses the Greenhouse source | Inject all `JobSource` beans as a `Map<String, JobSource>` and pick by platform (Open/Closed: a new source = a new class only) |
| B3 | `JobPostingPersistenceService.save` | `raw.location().toLowerCase()` throws `NullPointerException` if the job **detail** has no location (the list filter only checked the summary) | Null guard; the message then retries 4 times and lands in the DLT for nothing |
| B4 | `ResumeVersionService.createTailorVersion` | On a race (two tailor calls at once) the loser gets 409 instead of the version that won | Catch, re-read by (resume, job, TAILORED), return it with 200, like BASE |
| B5 | Gemini output | Bad JSON from Gemini becomes a generic 500. Generation config is `null` (no JSON mode, no temperature 0). Header contact fields often come back null; `education` is missing from the `null` guard in `ResumeContent` | Set `responseMimeType=application/json` + `temperature=0`; map `JacksonException` to 503/502; null guards |
| B6 | Security | A request with no token returns 403, not 401 | Add an `AuthenticationEntryPoint` that returns 401 |
| B7 | `docker-compose.yml` | `CRAWL_CRON` / `CRAWL_TIMEZONE` aren't passed to the app container; the server still runs an unused `postgres` container | Add them under `jora.environment`; a `docker-compose.prod.yml` override without `postgres` |

## 6.3 Tech debt and small clean-ups

- `CrawlService`: unused `startupTime` field and imports.
- `KafkaConfig`: comment says "total of 4 retries"; it is 3 retries = 4 attempts.
- `AuthLimitLock.release(email)`: rename the parameter to `uniqueId` in `RateLimiterService`; unused imports in `AuthService`; old login TODO in `AuthController`; `RateLimitException("")` with an empty message.
- `LLMLock` lives in `resume.extractor` and is named "Lock" but is a rate limit: rename (`GeminiRateLimit`) and move to `common.ratelimit` with `AuthLimitLock`.
- `JobIngestionController`: large commented-out block. `DatabaseJobCandidateService`: commented-out filters (employment/workplace type are never set by ingestion, so they would exclude everything).
- `JobIngestionService` both crawls and finds-or-creates companies/platforms (two jobs); `persistDiscoveredJob` lives in the persist service but the lookups don't.
- Company display name: the board token is shown as the company name. Add `display_name`.
- The location list in `JobLocationFilter` is hard-coded. Move to config or a table.
- `jsoup` is in the pom but unused.
- No `README.md` and no `.env.example` in the repo: add both (this handover is the source).
- The test DB is still called `sefujo_test`. Rename to `jora_test` (and the comment) for consistency.
- Admin users can only be made by SQL.
- Resume module, auth, search profile, S3 storage and Redis classes have **no tests**.

## 6.4 Availability under a traffic spike (the priority after launch)

Think of a spike as "100x the normal users in 10 minutes" (a post goes viral, a university shares the link). Here is what breaks first, in order, and what to do.

| Rank | Weak point today | What happens in a spike | Improvement |
|---|---|---|---|
| 1 | **Tailoring and CV parsing are synchronous** and take seconds each | Each call holds a Tomcat thread (default pool 200). A few hundred tailor clicks and every other endpoint, even login, waits | Short term: enable **virtual threads** (`spring.threads.virtual.enabled=true`, Java 21 needed) and set **timeouts** on the Gemini call. Medium term: move tailoring to Kafka (`resume.tailoring.requested` -> `resume.tailored`, return 202 + a status endpoint). Add a **global** Gemini limit (not only per user) so you never exceed the provider quota |
| 2 | **One EC2 runs everything** (app, Kafka, Redis) | CPU/RAM on one box shared by all; if it dies, all is down. No auto-restart of the machine | Put the app behind an **Application Load Balancer** + **Auto Scaling Group** (2+ instances, needs the stateless JWT you already have); move Redis and Kafka off the app box (ElastiCache / MSK Serverless, or Upstash / Aiven when moving to free tiers) |
| 3 | **No timeouts** on the Greenhouse `WebClient` or Gemini | A slow upstream holds threads forever | Connect/read timeouts + a circuit breaker (**Resilience4j**) so a dead dependency fails fast |
| 4 | **JWT filter loads the user from Postgres on every request** | DB connections (HikariCP default 10) become the bottleneck | Put the role in the token claims and trust the signature; or cache the user in Redis for a short time. Tune the Hikari pool size to the RDS instance |
| 5 | **Read endpoints hit Postgres every time** | `GET /job`, `GET /job/{id}`, recommendations under load | Redis cache (`@Cacheable` with a short TTL) for job detail and job lists, only after measuring. Index `(created_at DESC, id DESC)` on jobs and check every paging query with `EXPLAIN ANALYZE` |
| 6 | **No rate limit on most endpoints** | One script can hammer the API | A per-IP / per-user limit for all routes (the existing `RateLimiterService` can do it in a filter), or at the edge (AWS WAF, Cloudflare) |
| 7 | **`recommendForNewJob` scores every profile** for each new job; **`generateRecommendations` loads every candidate job** | Grows with users x jobs; slow consumers, Kafka lag | Pre-filter profiles in SQL (location, title keywords), process in batches, increase `spring.kafka.listener.concurrency` to the partition count (3; today only 1 consumer thread is used) |
| 8 | **Crawl listener does many HTTP calls in one message** | A big board can exceed Kafka's `max.poll.interval.ms` (5 min), causing a rebalance and a re-crawl | Publish one message per job detail to fetch, or raise the poll interval and lower `max.poll.records` |
| 9 | **Single Kafka broker, replication 1; Redis without persistence** | Broker disk loss = lost in-flight events | 3 brokers with RF 3 and `min.insync.replicas=2` (a managed service makes this easy); the DB remains the source of truth, so a lost crawl just reruns |
| 10 | **No monitoring** | You learn about downtime from users | Actuator + Micrometer + Prometheus/Grafana (or CloudWatch), alerts on 5xx rate, latency, consumer lag, DLT size |

**How to prove it works (testing for availability):**
1. Write a **load test** with k6 or Gatling: login, list recommendations, open a job, tailor (with a stubbed Gemini). Run it against a staging copy, never against Gemini or production.
2. Find the breaking point: ramp users until p95 latency or error rate goes bad. Write the number down; that is your capacity.
3. Fix the top weak point from the table, run again, compare. One change at a time.
4. Add **chaos checks**: stop Redis, stop Kafka, make Gemini time out. The API should degrade (clear 503s) and recover by itself, not hang.
5. Keep a small load test in CI (smoke level) so a slow query is caught before deploy.

## 6.5 Proposed roadmap after launch

### Phase 1: launch hardening (week after 8 Nov)
Everything in 6.1 not finished, bugs B1-B7, tests for the resume module, structured logging, an uptime monitor.

### Phase 2: measure and test (2-3 weeks)
Testcontainers integration tests (Postgres, Kafka, Redis), the load test and its baseline, Actuator + metrics + dashboards, CI that builds and pushes a Docker image and deploys to EC2.

### Phase 3: scale for spikes
Virtual threads + timeouts + circuit breakers, async tailoring on Kafka, load balancer + 2 app instances, Redis cache for measured slow reads, global rate limits, Kafka consumer concurrency.

### Phase 4: product features
1. **Application tracking (M8)**: `application` table with `UNIQUE(user_id, job_id)`, status history, a dashboard. Submitting a FINAL version records exactly which CV was sent.
2. **PDF export** of any version (one renderer for preview and download; HTML-to-PDF is lighter than LaTeX).
3. **Edit a version** in the UI (DRAFT save, Submit -> FINAL).
4. **More sources**: Lever and Ashby (public JSON APIs, same `JobSource` interface), then JSON-LD.
5. **Email alerts** for new high-score recommendations: a consumer on `job.created`/`job.matched`, Redis "already sent" marker so no duplicate emails, daily digest vs instant (open question from the 11 Oct plan).
6. **Better matching**: embeddings of CV and job description (pgvector in Postgres, or Spring AI vector store) instead of keyword rules; explain the score to the user.
7. **Regenerate** a tailored CV (today the first one is kept forever).
8. **Paid plan**: e.g. free = N tailorings/month, paid = more. The Redis rate limiter is already the metering tool; add billing (Stripe) later.

### Phase 5: cost
After the first month on AWS, decide between staying (with savings: Graviton instances already, reserved/savings plans) and the free-tier mix (Neon or Supabase, Upstash, Aiven, Render/Koyeb, Vercel). Because every address is an env var, the move is: provision, `pg_dump`/`pg_restore`, copy S3 files, change `.env`.
