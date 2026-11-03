# 1. Architecture

## 1.1 The big picture

```
                         ┌──────────────────────── EC2 (Docker Compose) ────────────────────────┐
                         │                                                                       │
 Browser / Postman ──────┼──► jora-app (Spring Boot, :8080)                                     │
   (JWT in header)       │     │   │   │   │                                                    │
                         │     │   │   │   └──► Redis (:6379)  crawl lock, rate limits          │
                         │     │   │   └──────► Kafka (:9092 / :29092)  crawl pipeline          │
                         │     │   │              ▲                                             │
                         │     │   │              └── Kafka UI (:8081, SSH tunnel only)         │
                         └─────┼───┼───────────────────────────────────────────────────────────┘
                               │   │
                               │   └──► Amazon S3 bucket (resume PDFs)
                               └──────► Amazon RDS PostgreSQL 17 (all data)

  jora-app also calls out to:  Greenhouse public boards API (jobs)  and  Google Gemini API (CV parse / tailor)
```

One Spring Boot application (a **modular monolith**) does everything: the REST API, the Kafka producers and consumers, and the scheduler. Kafka is used *inside* the monolith to split slow background work into steps that can retry on their own. There are no microservices, and that is on purpose (see [03-decisions.md](03-decisions.md#d1-modular-monolith-not-microservices)).

## 1.2 Package map (`src/main/java/com/sefujo`)

Packages are by **feature**, not by layer (no global `controller/`, `service/` folders). Inside a feature you find its controller, service, repository, entities and a `dto/` folder.

| Package | What lives there | Key classes |
|---|---|---|
| `auth` | Register, login, login rate limit | `AuthController`, `AuthService`, `AuthLimitLock` |
| `user` | User entity and "who is calling" helper | `User`, `UserService.getCurrentUserId()` |
| `searchprofile` | One search profile per user (CRUD) | `SearchProfile`, `SearchProfileService`, enums `EmploymentType`, `EmploymentLevel`, `WorkplaceType` |
| `job` | Job domain: entities, read API, job sources, saving postings | `Job`, `JobPosting`, `Company`, `Platform`, `CrawlTarget`, `JobService`, `JobPostingPersistenceService`, `JobLocationFilter` |
| `job.source` | One class per job board. Only Greenhouse works | `JobSource` (interface), `greenhouse/GreenhouseJobSource`, empty stubs `LeverJobSource`, `AshbyJobSource`, `JsonLdJobSource` |
| `ingestion` | The crawl flow: scheduler, fetch, persist decision, Redis crawl lock | `CrawlService` (scheduler), `JobIngestionService`, `JobDiscoveredPersistService`, `CrawlLock` |
| `ingestion.kafka` | All Kafka publishers and listeners | `CrawlRequestPublisher/Listener`, `JobDiscoveredPublisher/Listener`, `JobCreatedPublisher/Listener` |
| `ingestion.dto` | Kafka event records and internal results | `CrawlRequestEvent`, `JobDiscoveredEvent`, `JobCreatedEvent`, `RawJobPosting`, `DiscoveredJobResult` |
| `recommendation` | Scoring and the recommendations API | `JobMatchingStrategy` + `RuleBasedJobMatchingStrategy`, `JobCandidateService` + `DatabaseJobCandidateService`, `JobRecommendationService` |
| `resume` | Upload, extract, BASE + TAILORED versions, archive | `ResumeController`, `ResumeService`, `ResumeVersionService` |
| `resume.content` | The structured CV shape (Java records stored as JSONB) | `ResumeContent`, `Header`, `Education`, `Experience`, `Project`, `SkillGroup`, `ExtraSection`, `TailorSection` |
| `resume.extractor` | PDF to text, and the LLM interfaces | `ResumeExtractor` + `PdfBoxResumeExtractor`, `ResumeContentParser`, `ResumeTailor`, `LLMLock` (Gemini rate limit) |
| `resume.extractor.gemini` | Gemini implementations | `GeminiClient` (one place that calls Gemini), `GeminiResumeContentParser`, `GeminiTailoring` |
| `storage` | Where uploaded files live | `FileStorage` (interface), `LocalFileStorage`, `S3FileStorage` |
| `common.config` | Beans: Kafka topics + error handler, Redis Lua script, S3 client, WebClient, Gemini client | `KafkaConfig`, `RedisConfig`, `AWSConfig`, `WebClientConfig`, `APIConfig` |
| `common.security` | JWT filter, security rules, generic rate limiter | `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `RateLimiterService` |
| `common.exception` | Custom exceptions + one global handler | `GlobalExceptionHandler`, `ConflictException` (parent of all 409s) |
| `common.health` | `/health` and two test endpoints (remove before real users) | `HealthController`, `GreenhouseTestController` |
| `application` | Empty placeholder for Milestone 8 (application tracking) | `AppController` |

Resources: `db/migration/V1..V14` (Flyway), `prompts/prompt.txt` (CV parse prompt), `prompts/resumeTailor.txt` (tailor prompt), `application.properties` (every secret and address comes from an env var).

## 1.3 The main flows

### Flow A: crawl pipeline (background, Kafka)

```
CrawlService  @Scheduled 06:00 + 18:00 (Asia/Ho_Chi_Minh)
   │  for each enabled crawl_targets row (one bad target is logged, the loop continues)
   ▼
topic crawl.requested     key = boardToken      value = CrawlRequestEvent(platform, boardToken)
   │
   ▼  CrawlRequestListener
   │   1. Redis CrawlLock.tryAcquire("crawl:{platform}:{token}", 30 min)  -> already locked? log + skip
   │   2. JobIngestionService.ingest(): fetch job list from Greenhouse
   │        -> keep only Vietnam / Singapore locations (JobLocationFilter)
   │        -> postings already in DB: just update last_seen_at
   │        -> new ones: fetch the job detail
   │   3. publish one event per new posting. On any error: release the lock and rethrow (so Kafka retries)
   ▼
topic job.discovered      key = "{platform}:{externalId}"   value = JobDiscoveredEvent(platform, RawJobPosting)
   │
   ▼  JobDiscoveredListener -> JobDiscoveredPersistService.handleDiscoveredJob()
   │   posting already saved?  -> this is a retry -> re-publish its job id (idempotent)
   │   else save: find Job by (company, normalized title, location) or create it; always create the JobPosting
   │   publish job.created ONLY if a brand new Job row was created
   ▼
topic job.created         key = jobId (String)   value = JobCreatedEvent(jobId)
   │
   ▼  JobCreatedListener -> JobRecommendationService.recommendForNewJob(jobId)
       score this ONE job against ALL search profiles, upsert recommendations with score >= 20
```

Every listener has the same safety net (`KafkaConfig.kafkaErrorHandler`): on an exception, retry 3 more times 1 second apart (4 attempts), then publish the message to `<topic>.DLT` with the same partition. Publishers call `.join()` on `kafkaTemplate.send(...)`, so a failed send throws inside the listener and triggers that retry too.

Manual trigger for admins: `POST /api/v1/admin/ingestion/greenhouse/{boardToken}` publishes a `crawl.requested` and returns **202 Accepted**.

### Flow B: CV upload to BASE version (synchronous)

```
POST /api/v1/resume  (multipart "file", PDF only, max 2 MB)          -> 201 {id}
   validate -> FileStorage.save() -> key resumes/{userId}/{UUID}.pdf -> resume row (raw_text null)
   if the DB save fails, the stored file is deleted (no orphan files)

POST /api/v1/resume/{id}/extract                                      -> 200
   load file -> PDFBox text -> resume.raw_text            (corrupt PDF -> 422)

POST /api/v1/resume/{id}/versions                                     -> 201 new / 200 existing
   not extracted yet -> 400;  archived -> 409;  not yours -> 404
   Redis rate limit 10 Gemini calls / minute / user  -> 429
   Gemini parse raw_text -> ResumeContent JSON -> resume_version (type BASE, DRAFT)
   two parallel calls? the V12 unique index rejects the second insert; we catch it and return the existing BASE
```

### Flow C: tailor a CV for a job (synchronous)

```
POST /api/v1/resume/{resumeId}/tailor/{jobId}                         -> 201 new / 200 existing
   active resume (404 / 409) -> BASE must exist (409) -> TAILORED for this job exists? return it (200, no Gemini call)
   -> job exists (404) -> rate limit (429)
   -> send ONLY experiences, projects, skill groups + the job description to Gemini
   -> merge in code: header, education, extra sections copied from BASE unchanged
   -> save TAILORED, DRAFT, parent = BASE, job_id set           (V13 index: one per resume + job)
```

### Flow D: everything else (plain request/response)

| Endpoint | Notes |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Public. Login limited to 5 tries per 15 min per email (Redis); a correct password resets the counter. JWT valid 24 h |
| `POST/GET/PATCH/DELETE /api/v1/searchProfiles` | The caller's single profile |
| `GET /api/v1/job?title&employmentType&workplaceType&location&lastCreated&lastId&size` | Keyset paging on (created date, id), size 1..50, default 20 |
| `GET /api/v1/job/{id}` | Job detail |
| `POST /api/v1/jobRecommendation` | Re-score all candidate jobs for the caller's profile (the older "pull" way) |
| `GET /api/v1/jobRecommendation?lastScore&lastId&size` | Keyset paging on (score DESC, id DESC), DISMISSED hidden |
| `PATCH /api/v1/jobRecommendation/{id}` | Set status VIEWED / SAVED / DISMISSED (only your own) |
| `GET /api/v1/resume/{resumeId}/versions/{versionId}` | Any version you own |
| `POST /api/v1/resume/{resumeId}/archive` | 204; archiving twice = 409 |
| `GET /health` | Returns `{"status":"UP"}`. Note: currently behind auth (see 06) |

## 1.4 Data model

```
users 1───1 search_profiles ──┬── search_profile job_titles / skills / locations / employment_types (element collections)
  │                           │
  │                           └──* job_recommendation *──1 jobs *──1 company
  │                                 (score, reasons,          │
  │                                  status) UNIQUE(profile,  └──* job_postings *──1 platforms 1──* crawl_targets
  │                                  job)                          UNIQUE(platform_id, external_job_id)   UNIQUE(platform_id, board_token)
  │
  └──* resume 1──* resume_version ──(parent_version_id)──► resume_version
                     │  type BASE | TAILORED, edit_status DRAFT | FINAL, content JSONB
                     └──(job_id, nullable)──► jobs
                     partial UNIQUE (resume_id)         WHERE type='BASE'       (V12)
                     partial UNIQUE (resume_id, job_id) WHERE type='TAILORED'   (V13)
```

Migration history in one line each:

| Version | What it did |
|---|---|
| V1 | `users`, first `jobs` table |
| V2, V7, V8 | Job domain: `company`, `platforms`, `job_postings` (Job vs Posting split), unique (platform, external id), description to TEXT |
| V3 | User role |
| V4 - V6 | `search_profiles` and its collections, id type fix |
| V9, V10 | `job_recommendation` with unique (profile, job); match reasons; paging fixes |
| V11 | `resume` and `resume_version` (JSONB content) |
| V12 | One BASE per resume (partial unique index) |
| V13 | One TAILORED per (resume, job) (partial unique index) |
| V14 | `crawl_targets`, seeds platform `greenhouse` and target `axon` |

Rules: **never edit an old migration**, always add a new `V{n}__name.sql` (two underscores, or Flyway silently skips it). `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate only checks that entities match the schema and never changes it.

## 1.5 Code conventions a new developer must follow

1. **Interfaces at every boundary that might change**: `JobSource`, `FileStorage`, `ResumeExtractor`, `ResumeContentParser`, `ResumeTailor`, `JobMatchingStrategy`, `JobCandidateService`. Swapping Greenhouse for Lever, disk for S3, or Gemini for Claude/OpenAI must be a new class, not an edit to services.
2. **One job per class (SRP)**. Example: `CrawlService` only decides *when*; the publisher decides *how to send*; the listener decides *what to do*. `ResumeService` handles the file; `ResumeVersionService` handles versions; `GeminiClient` is the only class that talks to Gemini.
3. **Ownership in the query**: always `findByIdAndUserId(...)`, never `findById` then compare. Someone else's resource returns **404** (hides that it exists), not 403.
4. **Errors**: throw a specific exception; `GlobalExceptionHandler` turns it into a `ProblemDetail` JSON body. 4xx shows the real message; 5xx logs the full error and shows a generic message. Any "already exists / wrong state" error extends `ConflictException` (409).
5. **Status codes**: 201 created, 200 "already existed, here it is", 202 accepted for async work, 204 for actions with no body, 409 wrong state, 429 rate limited, 503 LLM down.
6. **Idempotency**: anything that can be called twice (HTTP retries, Kafka redelivery) must give the same result the second time. Use a DB unique constraint as the final guard and catch `DataIntegrityViolationException`.
7. **Config**: no hard-coded addresses, passwords, intervals or limits. Use `${ENV_VAR:default}` in `application.properties`.
8. **Readable code**: plain `if/else`, small methods, no ternaries. Entities avoid class-level `@Setter` where state rules exist (e.g. `ResumeVersion.updateContent()` refuses FINAL).
9. **Keep slow calls out of DB transactions**: the Gemini call is not inside `@Transactional`.
