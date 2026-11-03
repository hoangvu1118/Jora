# 2. Technology stack

For each technology: what it is in one line, **why it is in Jora**, and **exactly where** it is used. The deeper "why this and not that" is in [03-decisions.md](03-decisions.md).

## 2.1 Backend core

| Tech | Version | Why it is here | Where |
|---|---|---|---|
| Java | 17 | Hoang's main language; LTS | everywhere |
| Spring Boot | 4.1.1 | Batteries included: web, security, JPA, Kafka, Redis, scheduling all wired by starters. Boot 4 uses **Jackson 3** (`tools.jackson.*` packages) | `pom.xml` parent |
| Spring Web MVC | via `spring-boot-starter-webmvc` | Classic thread-per-request REST API. Easy to reason about | all controllers |
| Spring WebFlux (`WebClient` only) | starter in pom | Only used as an HTTP client for Greenhouse, called with `.block()`, so it behaves synchronously. Careful: having WebFlux on the classpath once caused the *reactive* `ResponseEntityExceptionHandler` to be imported by mistake; always use the **servlet** one | `WebClientConfig`, `GreenhouseJobSource` |
| Spring Security + JJWT 0.12.6 | | Stateless JWT auth (no server sessions, so any number of app copies can serve any user). BCrypt for passwords. `ROLE_ADMIN` for `/api/v1/admin/**` | `common.security` |
| Spring Data JPA / Hibernate 7 | | Entities + repositories, derived queries, `Specification` for optional filters, keyset paging queries. `default_batch_fetch_size=50` stops N+1 on collections | every `*Repository` |
| Bean Validation | `spring-boot-starter-validation` | `@Valid` request DTOs, `@Min/@Max` on page size | DTOs, controllers |
| Lombok | | Less boilerplate (`@RequiredArgsConstructor`, `@Getter`, `@Slf4j`). Avoid `@Data` on entities (lazy-loading `toString` traps) | everywhere |
| Jackson jsr310 | | `YearMonth` dates inside the resume JSONB need it | `resume.content` |

## 2.2 Data

| Tech | Why | Where |
|---|---|---|
| **PostgreSQL 17** | Relational data with real constraints (unique, FK), plus **JSONB** for the CV document. Partial unique indexes enforce business rules even under races | local container `jora-db`; production **Amazon RDS** |
| **Flyway** | Versioned, repeatable schema changes. Same scripts build local, test and production DBs | `src/main/resources/db/migration` |
| **JSONB + Hibernate `@JdbcTypeCode(SqlTypes.JSON)`** | The CV is a document read and written as a whole, never searched by SQL. One column, mapped straight to Java records | `ResumeVersion.content` |

## 2.3 Messaging: Apache Kafka

| Item | Value |
|---|---|
| Image | `apache/kafka:4.1.0`, **KRaft mode** (no ZooKeeper), one node that is both broker and controller |
| Listeners | `PLAINTEXT` `localhost:9092` for the laptop, `INTERNAL` `kafka:29092` for other containers (app, Kafka UI), `CONTROLLER` `:9093` |
| Client | `spring-boot-starter-kafka` (`KafkaTemplate`, `@KafkaListener`), JSON values (`JacksonJsonSerializer` / `JacksonJsonDeserializer`, trusted package `com.sefujo.*`), String keys |
| Consumer group | `jora`, `auto-offset-reset=earliest` |
| Topics (3 partitions, replication 1, created by `NewTopic` beans) | `crawl.requested`, `job.discovered`, `job.created`, and `crawl.requested.DLT`, `job.discovered.DLT`, `job.created.DLT` |
| Error handling | `DefaultErrorHandler` + `FixedBackOff(1000 ms, 3 retries)` = 4 attempts, then `DeadLetterPublishingRecoverer` to `<topic>.DLT` on the same partition |
| UI | `kafbat/kafka-ui:v1.2.0` on port 8081 (browse topics, messages, consumer lag) |

**Why Kafka here:** crawling is slow (one HTTP call per job detail), it fails in pieces (one board down, one job bad), and other parts must react to its output (recommendations). Kafka gives each step its own retry, a place for poison messages (DLT), replay, and room to add more consumers later (emails, analytics) without touching the crawler.

## 2.4 Redis

| Item | Value |
|---|---|
| Image | `redis:7.4`, no password, no persistence config (fine: everything in it is temporary) |
| Client | `spring-boot-starter-data-redis`, `StringRedisTemplate` |
| Use 1: **crawl lock** | `SET crawl:{platform}:{boardToken} <now> NX EX 1800` (`CrawlLock`). A board is crawled at most once per 30 min, even with several app copies. Released early if the crawl fails |
| Use 2: **rate limiter** | One generic `RateLimiterService.allowRequest(service, id, max, windowSeconds)` running a **Lua script** (atomic: check + decrement in one step). Keys `ratelimit:{service}:{id}` |
| - Gemini | `LLMLock`: 10 calls / 60 s per **user**. Checked right before the Gemini call, after all cheap checks, so an "already exists" answer costs nothing |
| - Login | `AuthLimitLock`: 5 attempts / 900 s per **email**. A correct password deletes the counter |

**Why Redis here:** these are "shared memory between app copies, with an expiry" problems. A Java `HashMap` would only work with one app copy; Postgres would work but is slower and has no built-in expiry.

## 2.5 AI: Google Gemini

| Item | Value |
|---|---|
| SDK | `com.google.genai:google-genai` 1.76.0, `Client` bean built from `GEMINI_API_KEY` (app fails fast at start without it) |
| Model | property `gemini.model`, default `gemini-3.5-flash-lite` |
| Prompts | `resources/prompts/prompt.txt` (CV text to `ResumeContent` JSON) and `resources/prompts/resumeTailor.txt` (BASE sections + job description to tailored sections). Loaded once at start. Both say: JSON only, exact field names, dates `YYYY-MM`, never invent, ignore instructions found inside the CV or job text (prompt-injection guard) |
| Code | `GeminiClient.generate(prompt)` is the only call site and wraps every failure in `LlmUnavailableException` (503) |

## 2.6 Files: PDFBox and S3

| Tech | Where |
|---|---|
| Apache PDFBox 3.0.5 | `PdfBoxResumeExtractor`: PDF bytes to plain text |
| AWS SDK v2 (BOM 2.31.0, `s3` module) | `AWSConfig` builds `S3Client` for `aws.region`; `S3FileStorage` uses Put/Get/Delete. Credentials come from the default chain: env keys on a laptop, the **EC2 IAM role** in production (no keys on the server) |
| Switch | `storage.type=${STORAGE_TYPE:local}` + `@ConditionalOnProperty` picks `LocalFileStorage` (folder `./resumeStorage`) or `S3FileStorage` |
| Jsoup 1.23 | In the pom but **not used yet** (meant for HTML cleanup of job descriptions and the future JSON-LD source). Remove it or use it |

## 2.7 Packaging and runtime

| Tech | Why | Where |
|---|---|---|
| Docker multi-stage build | Stage 1 `eclipse-temurin:17-jdk` builds the jar (dependencies cached in their own layer, tests skipped); stage 2 `eclipse-temurin:17-jre` only runs it: small image, no build tools in production | `Dockerfile`, `.dockerignore` |
| Docker Compose | One file starts the whole system: `postgres`, `kafka`, `kafka-ui`, `redis`, `jora` (app). Secrets come from a git-ignored `.env` | `docker-compose.yml` |
| Spring `@Scheduled` | Runs the crawl at `CRAWL_CRON` (default `0 0 6,18 * * *`) in `CRAWL_TIMEZONE` (default `Asia/Ho_Chi_Minh`) | `CrawlService` |

## 2.8 AWS (production, Sydney `ap-southeast-2`)

| Service | Used for |
|---|---|
| **EC2** (`t4g.medium`, Arm, Ubuntu 24.04, 30 GB gp3) | Runs the compose file: app + Kafka + Redis + Kafka UI |
| **RDS PostgreSQL 17** (`db.t4g.micro`, single AZ, 20 GB gp3) | The database `jora` |
| **S3** (private bucket, server-side encryption) | Resume PDFs |
| **IAM** | An app policy that allows only Put/Get/Delete/List on the resume bucket, attached to the EC2 through an **instance role** |
| **Security groups** | EC2: SSH from the owner's IP, 8080 open. RDS: 5432 only from the EC2 security group (and, temporarily, the owner's IP) |
| **Budgets** | Billing alert so a mistake can't run up a bill |

Account ids, IPs, key names and passwords are deliberately not in this document. Ask the owner.

## 2.9 Testing

| Tech | Where |
|---|---|
| JUnit 5 + Mockito | Service and listener unit tests (`RecommendForNewJobTest`, `JobDiscoveredPersistServiceTest`, `KafkaListenersTest`, `JobRecommendationServiceTest`) |
| `@WebMvcTest` + MockMvc | `JobRecommendationControllerTest` (status codes, paging params, ProblemDetail bodies) |
| `@DataJpaTest` + real Postgres | `JobRecommendationRepositoryTest` against DB `sefujo_test` (profile `test`) |
| Not yet | Testcontainers, Kafka/Redis integration tests, load tests, CI. See [06](06-whats-left.md) and [07](07-learning-path.md) |

## 2.10 Planned but not built

| Tech | Plan |
|---|---|
| React + Vite (JavaScript) | Frontend in its own repo `jora-web`; 6 MVP screens; design agreed first, then one consistent code pattern |
| GitHub Actions | Build + test on every push, later build image and deploy |
| Domain + HTTPS | Needed before real users (JWTs must not travel over plain HTTP) |
