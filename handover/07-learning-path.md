# 7. Learning path: getting the most out of each technology

For each technology: what Jora **already uses** (so you can read real code), then the **next capabilities worth trying in Jora**, ordered from "small step" to "big step". Every idea is tied to a real Jora feature, so learning and product work are the same task. Items marked **(spike)** directly help availability under traffic spikes.

---

## 7.1 Kafka

**Already used:** KRaft single broker, topics as `NewTopic` beans, String keys + JSON values, consumer group, `@KafkaListener`, `.join()` on sends, `DefaultErrorHandler` with fixed back-off, dead-letter topics, idempotent consumers, Kafka UI.

**Next in Jora:**
1. **Consumer concurrency** (spike): `spring.kafka.listener.concurrency=3` to use all 3 partitions; watch partitions get assigned in Kafka UI. Then run 2 app copies and watch a **rebalance**.
2. **Non-blocking retries** with `@RetryableTopic` (retry topics with growing delays, e.g. 1 s, 10 s, 1 min) so one bad message doesn't block its partition.
3. **A DLT consumer / replay tool**: an admin endpoint that lists DLT messages and re-publishes one after a fix.
4. **Producer settings**: learn `acks=all`, `enable.idempotence`, `linger.ms`, `batch.size`, compression; measure throughput with a fake crawl of 10,000 jobs.
5. **Async tailoring** (spike): `resume.tailoring.requested` -> `resume.tailored`, 202 + status endpoint. Learn request/reply patterns and how the frontend polls or uses SSE.
6. **Transactional outbox**: today we save to Postgres and then publish; if the publish fails after commit we rely on retry logic. An `outbox` table written in the same DB transaction + a relay (or Debezium CDC) is the textbook fix.
7. **Schemas**: Avro or JSON Schema with a schema registry, so a field rename can't break consumers. Version your events (`JobDiscoveredEvent v2`).
8. **Kafka Streams**: a live "jobs per company per day" or "most-saved jobs" stream for an analytics page.
9. **3-broker cluster** with RF 3 and `min.insync.replicas=2` (you have a compose for this in the old KafkaKnowledge repo): kill a broker and watch leaders move.
10. **Testing**: `@EmbeddedKafka` or Testcontainers Kafka to test the whole pipeline in one test.

## 7.2 Redis

**Already used:** `SET NX EX` lock with cooldown, an atomic Lua fixed-window rate limiter, key naming `area:thing:id`.

**Next in Jora:**
1. **Caching** (spike): Spring Cache with Redis (`@Cacheable("jobDetail")`, short TTL, `@CacheEvict` on update) for `GET /job/{id}`. Measure before and after.
2. **Sliding-window or token-bucket rate limit** (spike): compare to the fixed window you have (a fixed window allows 2x the limit around the window edge). Use a sorted set or the token bucket algorithm in Lua.
3. **Global limits** (spike): one shared Gemini budget for the whole app to respect the provider quota.
4. **Idempotency keys** for POST: the client sends `Idempotency-Key`; Redis remembers the response for a few minutes; a double-click never tailors twice.
5. **Redis Streams or Pub/Sub** to push "your tailored CV is ready" to the browser (with Server-Sent Events).
6. **Leaderboards with sorted sets**: "trending jobs this week".
7. **Session-free token revocation**: a Redis deny-list of JWT ids for logout.
8. **Operations**: persistence (RDB vs AOF), `maxmemory` + eviction policies, why `KEYS *` is dangerous, Redis Sentinel/Cluster vs a managed Redis (ElastiCache, Upstash).

## 7.3 Spring AI

**Already used:** none. Gemini is called with Google's SDK directly behind our own interfaces (`ResumeContentParser`, `ResumeTailor`), which makes switching easy.

**Next in Jora:**
1. **Swap `GeminiClient` for Spring AI's `ChatClient`** with the Google GenAI / Vertex starter. One new implementation class; the services don't change. Then try another provider (Anthropic Claude, OpenAI) by configuration only.
2. **Structured output**: `chatClient.prompt()...call().entity(ResumeContent.class)` gives you schema-guided JSON straight into the record (fixes bug B5 nicely).
3. **Prompt templates** as resources with variables instead of `String.replace`.
4. **Observability**: Spring AI emits Micrometer metrics for token usage and latency; chart cost per user.
5. **Evaluation**: a small set of real CVs + jobs, and an automated check (an "LLM as judge" or plain code) that tailoring never adds a skill or changes a number. Run it when you change the prompt or the model.
6. **Embeddings + vector store (pgvector)**: embed job descriptions and CVs, recommend by similarity, then combine with the rule score. This is the biggest product upgrade in the list.
7. **RAG**: "Why is this job a good fit for me?" answered from the CV and the job description.
8. **Tool calling**: a small assistant that can call Jora's own services ("save this job", "tailor for job 42").
9. **Advisors**: guardrails (strip personal data before sending), retries, logging.

## 7.4 AWS

**Already used:** EC2 (Arm), RDS PostgreSQL, S3 with server-side encryption, IAM user + least-privilege policy, **instance role**, security groups, Budgets.

**Next in Jora:**
1. **Elastic IP + Route 53** domain, then **ACM** certificate.
2. **Application Load Balancer + Auto Scaling Group** (spike): 2 instances, health checks on `/actuator/health`, scale on CPU or request count. Rolling deploys with no downtime.
3. **ECR** for images (CI pushes, EC2 pulls) instead of building on the server.
4. **ECS on Fargate**: run the container without managing a server. Compare effort and price with EC2.
5. **Secrets Manager or SSM Parameter Store** for `.env` values, read through the instance role.
6. **CloudWatch**: ship container logs, alarms on 5xx and CPU, a dashboard.
7. **S3 extras**: lifecycle rules, presigned URLs (download a CV straight from S3 without passing through the app), versioning.
8. **Frontend hosting**: S3 + CloudFront for the Vite build.
9. **RDS**: automated backups and a restore drill, a read replica for heavy reads, Multi-AZ for failover; RDS Proxy for connection pooling.
10. **Managed messaging**: MSK Serverless or Amazon SQS/SNS (compare SQS + Lambda with Kafka for the crawl pipeline: a great trade-off exercise).
11. **Infrastructure as code**: Terraform or AWS CDK for everything above, so the whole environment can be rebuilt with one command.
12. **Cost**: Cost Explorer, tags per resource, Savings Plans.

## 7.5 Hosting and free tiers

**Already decided:** AWS lean for the first month, then consider the free mix.

**Try:**
1. Deploy the **same Docker image** to Render or Koyeb with Neon (Postgres), Upstash (Redis, has a REST API and per-request pricing) and Aiven or Upstash Kafka. Note what changed: only env vars, if the portability rule held.
2. Frontend on Vercel or Netlify; compare with S3 + CloudFront.
3. Cloudflare in front (DNS, free TLS, caching, basic WAF and rate limits) (spike).
4. Learn the limits of each free plan: sleeping apps (cold starts), connection limits (use pooled connection strings on Neon), message/day caps. Write them in a table before moving.
5. A **staging** environment on free tiers, production on AWS: practice promoting the same image.
6. Supabase Storage is S3-compatible: point `S3FileStorage` at it by changing the endpoint.

## 7.6 Docker

**Already used:** multi-stage build with a cached dependency layer, `.dockerignore`, compose with service-name networking, named volume, `restart: on-failure`, env from `.env`.

**Next in Jora:**
1. **Healthchecks** in compose + `depends_on: condition: service_healthy` (no more app restarts while Kafka boots).
2. A **`docker-compose.prod.yml`** override (no local Postgres, resource limits, `restart: unless-stopped`).
3. Run as a **non-root user** in the image; pin image versions by digest.
4. **Smaller/faster images**: Spring Boot layered jars, or Cloud Native Buildpacks (`./mvnw spring-boot:build-image`), or a GraalVM native image (very fast start, good for scaling out quickly).
5. JVM in containers: `-XX:MaxRAMPercentage`, why the default heap may be too small or too big on a t4g.
6. **Multi-arch builds** with `docker buildx` (your laptop is x86, the EC2 is Arm).
7. Image scanning (Trivy, `docker scout`).

## 7.7 CI/CD (GitHub Actions)

**Already decided:** GitHub Actions. Nothing built yet.

**Steps:**
1. **CI**: on every push and PR, `actions/setup-java` with Maven cache, `./mvnw verify`. Use a Postgres **service container** (or Testcontainers) for DB tests. Put `JWT_SECRET` etc. in repository secrets.
2. Branch protection: `main` only via PR with green CI (start working in PRs instead of pushing to `main`).
3. **Build and push the image** to GHCR or ECR on merge to `main`, tagged with the commit SHA.
4. **CD**: deploy to EC2 (SSH action or AWS SSM Run Command: `docker compose pull && up -d`), then call the health check; roll back to the previous tag if it fails.
5. Use **OIDC** to let GitHub assume an AWS role, no long-lived AWS keys in GitHub.
6. Quality gates: JaCoCo coverage report, dependency updates (Dependabot), static analysis (SpotBugs or SonarCloud), secret scanning.
7. Frontend pipeline: lint, build, deploy preview per PR.
8. Run the k6 **smoke load test** after each deploy to staging (spike).

## 7.8 Testing

**Already used:** Mockito unit tests (services, listeners), `@WebMvcTest` for controllers (status codes, ProblemDetail bodies, paging params), `@DataJpaTest` against a real Postgres.

**Next in Jora:**
1. **Fill the gaps first**: `ResumeVersionService` (200 vs 201, rate limit order, archived 409, ownership 404), `AuthService` (login limit reset), `CrawlLock`, `RateLimiterService`, `S3FileStorage` (with a fake S3 such as LocalStack or a mocked client).
2. **Testcontainers** for Postgres, Kafka and Redis: real services in Docker started by the test. Test the full crawl pipeline: publish `crawl.requested` with a stubbed Greenhouse (**WireMock**), assert jobs and recommendations in the DB.
3. **Race tests**: fire two tailor or BASE requests in parallel and assert exactly one row (proves V12/V13 + the catch).
4. **Contract tests** for Kafka events (a JSON sample per event, both sides test against it).
5. **Flyway migration test**: start an empty Postgres and run all migrations in CI.
6. **Load tests** with k6 or Gatling (spike): scenarios in [06](06-whats-left.md#64-availability-under-a-traffic-spike-the-priority-after-launch), thresholds like "p95 < 300 ms at 200 users", run against staging.
7. **Resilience tests** (spike): Toxiproxy or simply stopping containers during a test; assert clean 503s and recovery.
8. **Frontend tests**: Vitest + React Testing Library for components, Playwright for one end-to-end happy path (sign up -> upload CV -> tailor).
9. **LLM evaluation set** (see 7.3) as a separate, slower test suite.

## 7.9 Also worth learning along the way

- **Observability**: Spring Boot Actuator, Micrometer, Prometheus + Grafana, distributed tracing with OpenTelemetry (follow one crawl from scheduler to recommendation across three topics).
- **Resilience4j**: timeouts, retries, circuit breakers, bulkheads around Gemini and Greenhouse.
- **Java 21**: virtual threads, records/pattern matching (Jora is on 17; upgrading is small and unlocks virtual threads).
- **Security**: refresh tokens, OWASP Top 10 against Jora's endpoints, file-upload hardening (check PDF magic bytes, virus scan).
- **Database**: `EXPLAIN ANALYZE`, indexes for each paging query, connection pool sizing, zero-downtime migrations (expand, migrate, contract).

## 7.10 Suggested order

If you only do a few: **CI (7.7.1)** -> **Testcontainers pipeline test (7.8.2)** -> **Actuator + metrics (7.9)** -> **k6 baseline (7.8.6)** -> **Redis cache + concurrency + timeouts (7.2.1, 7.1.1, 7.9)** -> **async tailoring (7.1.5)** -> **Spring AI structured output (7.3.1-2)** -> **load balancer + 2 instances (7.4.2)** -> **embeddings (7.3.6)**. Each step makes the next one safer to try.
