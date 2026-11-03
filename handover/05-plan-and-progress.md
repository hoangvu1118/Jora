# 5. The plan, and what we achieved

## 5.1 The original roadmap (milestones M0 to M10)

The project started from a roadmap document (kept outside the repo). Its user story:

1. Create an account. 2. Define search preferences. 3. The system keeps ingesting job postings. 4. It normalizes and de-duplicates them. 5. It matches jobs to each profile. 6. Good matches become recommendations. 7. The user's CV is tailored to a chosen job. 8. The user reviews it. 9. The user applies (by hand, later automatically). 10. Every application is tracked.

| Milestone | Goal | Status (3 Nov) |
|---|---|---|
| M0 Foundation | Spring Boot + Postgres + Flyway + `/health` | Done |
| M1 Authentication | Register, login, JWT, roles | Done |
| M2 Search profile | One profile per user, CRUD | Done |
| M3 Job domain | Company, Job, Platform, JobPosting | Done |
| M4 Job ingestion | One real source (Greenhouse), de-dup | Done (Greenhouse only) |
| M5 Recommendation engine | Rule-based scoring, statuses, paged list | Done (+ keyset paging) |
| M6 Resume management | Upload, extract, BASE version, get, archive | Done 21 Oct |
| M7 AI resume tailoring | BASE + job -> TAILORED | Done 24 Oct (simple version) |
| M8 Application tracking | Applications + status history + dashboard | Not started (stretch) |
| M9 Application automation | Auto-submit for one supported ATS | Cut (risky, terms of service) |
| M10 Kafka / event-driven | Events, retries, DLT, idempotency | Done for the crawl pipeline (pulled forward) |
| Beyond the roadmap | Redis, Docker, AWS deployment | Done early for the launch |

The roadmap's guiding rule, which we kept: *"Use Kafka because a real async problem exists, not merely because the project should contain Kafka."*

## 5.2 The launch plan (set 19 Oct, scope confirmed 20 Oct)

**Goal:** Jora live on AWS with real users by **Sun 8 Nov 2026**, using Kafka and Redis. Full plan: `sprint/launch-plan.md` (project shared files).

**MVP for launch day:** sign up/log in, a search profile, fresh jobs crawled automatically (Greenhouse), ranked recommendations, CV upload parsed into an editable BASE, a CV tailored to one job, and a simple web page for all of it.

**Cut until after launch:** application tracking (M8, stretch only), auto-apply (M9), Lever/Ashby/JSON-LD sources, email notifications, the Redis read cache, version history beyond BASE and TAILORED, async tailoring.

| Sprint | Dates | Goal | Result |
|---|---|---|---|
| 1 | 19-25 Oct | Finish M6 + simple M7 | **Done 24 Oct**, a day early |
| 2 | 26 Oct - 1 Nov | Kafka crawl pipeline, `job.created` -> recommendations, Redis crawl lock + Gemini/login rate limits, full compose test | Started 2 days late (Kafka refresher), **done 31 Oct**, a day early |
| 3 | 2-8 Nov | Docker, S3, RDS, EC2, scheduler, IAM role, frontend, domain + HTTPS, CI, freeze 6 Nov, testers 7 Nov, launch 8 Nov | Docker, S3, RDS, EC2, IAM role, scheduler **done by 1 Nov** (2 days ahead). Frontend, HTTPS, CI not started |
| 4 (optional) | 9-15 Nov | Frontend polish from user feedback, performance tuning, Redis cache only for a measured slow read | - |

## 5.3 What we achieved together (timeline)

Commit hashes are on `main` unless noted.

### Early October: foundations and recommendations
- Repo mapped on 5 Oct: Spring Boot monolith, Greenhouse-only ingestion through a synchronous admin endpoint, no Kafka/Redis/AWS, no CI, one test.
- **Milestone 5 review and fixes** (5-11 Oct): Flyway files that were silently skipped (`V8_`), entity vs migration mismatches, API returning entities, `ddl-auto=create` wiping data, an admin route not actually protected, nested response DTOs, PATCH status with an ownership check.
- **Keyset pagination** for recommendations (`7ea3e9e`) and jobs (`03947b5`), `default_batch_fetch_size` against N+1. Paging tests added (PR #1, #2).
- **Kafka and Redis plan** (11 Oct): which features go on Kafka (crawl first), which on Redis (lock, rate limits), build order.

### Mid October: resumes (Milestone 6)
- Designed the resume model through several review rounds: file in storage, raw text on `resume`, JSONB `ResumeContent` on `resume_version`, BASE/TAILORED, DRAFT/FINAL, parent links, soft delete (V11).
- Timestamp cleanup on all entities (PR #3, re-applied as PR #4).
- **Error handling rebuilt** around `ProblemDetail` and `ConflictException` (PR #5).
- `FileStorage` + local implementation, upload with orphan-file cleanup, PDFBox extraction (422 for broken PDFs), Gemini parser behind `ResumeContentParser`, idempotent BASE (200 vs 201), V12 unique index for races, GET version with IDOR fix, archive (204/409). **M6 complete `6236028` (21 Oct).**

### 19 Oct: the deadline
- Owner set a hard launch date of 8 Nov; Claude became scrum master with a daily standup and a day-by-day plan.

### Late October: tailoring and clean-up (Sprint 1)
- Tailoring rules agreed, tailor prompt written, endpoint built: existing returns 200 before any Gemini call, new returns 201 (`TailoredResult`), V13 unique index. **Sprint 1 done `a280a82` (24 Oct).**
- SOLID refactor: `GeminiClient` + `GeminiResumeContentParser` + `GeminiTailoring` (`12f7ba1`), `ResumeService` split from `ResumeVersionService` (`834ebca`).

### Late October: Kafka and Redis (Sprint 2)
- Kafka refresher (interactive lab, study notes in `study/kafka-knowledge-notes.md`, project shared files).
- Single KRaft broker in compose (`6d403a5`), topics + `crawl.requested` publisher + 202 endpoint (`9c17f8a`), crawl listener publishing `job.discovered` (`96d7c2a`).
- Save listener (`5836888`), `job.created` only for new jobs (`2a28098`), String keys (`8c19688`), idempotent retries (`2709e81`, `57b8d55`).
- Retries + dead-letter topics + `.join()` on sends (`7587e01`), tested by forcing failures.
- `job.created` -> recommendations listener (`ed5b989`, `6b3f2e2`), 13 unit tests for that flow (PR #6, `13b9ca4`).
- Redis crawl lock (`c6c9da9`, `52dee1e`), generic Lua rate limiter + Gemini limit (`471c6a1`, `3cdcd31`), login limit (`c4b4873`, `d7dcf73`).
- **Full `docker compose up` end-to-end run passed. Sprint 2 done 31 Oct.**

### End of October / 1 Nov: AWS (Sprint 3, first half)
- Multi-stage Dockerfile, app inside compose, all secrets in env vars (`4d4460e`).
- AWS account with MFA and a budget alert. S3 bucket + least-privilege IAM policy, `S3FileStorage` behind `STORAGE_TYPE` (`b484046`).
- RDS PostgreSQL 17; database renamed to `jora` everywhere.
- EC2 (Arm, Ubuntu) with Docker; app running on EC2 against RDS and S3 (`ab917d5` made `mvnw` executable for Linux).
- EC2 instance role replaces access keys on the server.
- `crawl_targets` table (V14, `ff119e7`) and the cron scheduler (`2c2be3a`). Jobs now arrive by themselves twice a day.

### What the owner learned along the way (worth keeping)
Keyset vs offset paging, partial unique indexes and race conditions, idempotency at HTTP and Kafka level, IDOR and 404 vs 403, `@Transactional` proxies and rollback, ProblemDetail, SOLID applied to real classes, Kafka keys/partitions/consumer groups/offsets/DLT, Redis atomic operations and Lua, Docker multi-stage builds and compose networking, AWS EC2/RDS/S3/IAM/security groups.
