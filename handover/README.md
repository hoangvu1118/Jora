# Jora handover

Written 3 Nov 2026, at commit `2c2be3a` on `main`. Audience: a developer joining Jora, and future Hoang.

Read this folder top to bottom once. After that, you should be able to run Jora, find any piece of code, explain why it is built that way, and pick up the next task without asking anyone.

## What Jora is, in one paragraph

Jora (old name SeFuJo, Java package `com.sefujo`) is a **job application booster**. It crawls company job boards by itself, cleans up and de-duplicates the jobs into Postgres, scores every job against each user's search profile, and shows the best matches as recommendations. A user uploads their CV once; Gemini (an LLM) turns it into structured JSON (the BASE version), and for any job the user picks, Gemini rewrites the CV to fit that job (a TAILORED version) without inventing anything. It is a Spring Boot monolith that uses **Kafka** for the crawl pipeline, **Redis** for locks and rate limits, and runs on **AWS** (EC2 + RDS + S3).

## Read in this order

| # | File | What you get from it | Time |
|---|---|---|---|
| 1 | [01-architecture.md](01-architecture.md) | The big picture: components, packages, request flows, data model, code conventions | 20 min |
| 2 | [02-tech-stack.md](02-tech-stack.md) | Every technology, why it is here, and exactly where it is used | 15 min |
| 3 | [03-decisions.md](03-decisions.md) | Every important decision with the options we rejected and why. **Crawl** and **persistence** are covered in depth | 30 min |
| 4 | [04-run-and-deploy.md](04-run-and-deploy.md) | Run it on your laptop, the env vars, how production is deployed, day-to-day ops commands, how to shut AWS down | 15 min + doing it |
| 5 | [05-plan-and-progress.md](05-plan-and-progress.md) | The original roadmap, the launch sprint plan, and a timeline of what we achieved | 10 min |
| 6 | [06-whats-left.md](06-whats-left.md) | Known bugs, tech debt, what is left before launch, and proposed future work ranked by priority | 15 min |
| 7 | [07-learning-path.md](07-learning-path.md) | For each technology: what Jora already uses, and the next capabilities worth trying in Jora | 20 min |

## Quick facts

| Thing | Value |
|---|---|
| Repo | `github.com/hoangvu1118/Jora` (old URL `.../SeFuJo` redirects). One branch: `main` |
| Language / framework | Java 17, Spring Boot 4.1.1, Maven wrapper (`./mvnw`) |
| Database | PostgreSQL 17, schema owned by Flyway (`V1` .. `V14`), DB name `jora` |
| Messaging | Apache Kafka 4.1 (KRaft, single broker), 3 topics + 3 dead-letter topics |
| Cache / locks | Redis 7.4 |
| LLM | Google Gemini via `com.google.genai:google-genai` |
| File storage | Local folder in dev, Amazon S3 in prod (switch: `STORAGE_TYPE`) |
| Production | AWS Sydney (`ap-southeast-2`): one EC2 running Docker Compose (app + Kafka + Redis + Kafka UI), RDS Postgres, S3 bucket |
| Frontend | Not started. Plan: React + Vite in a separate repo `jora-web` |
| Launch target | Sun 8 Nov 2026, real users |
| Tests | 33 test methods in 7 classes (JUnit 5, Mockito, MockMvc, `@DataJpaTest`). Only the recommendation and Kafka pipeline areas are covered. The repository test needs a local Postgres DB `sefujo_test` |

## Glossary (words you will see everywhere)

| Word | Meaning in Jora |
|---|---|
| **Board token** | The company's id on Greenhouse, e.g. `axon` in `boards-api.greenhouse.io/v1/boards/axon/jobs` |
| **Crawl target** | A row in `crawl_targets`: (platform, board token, enabled). The scheduler crawls every enabled one |
| **Job** vs **JobPosting** | A **Job** is the real job (one per company + title + location). A **JobPosting** is one place it was seen (one per platform + external id). One Job can have many postings |
| **Search profile** | A user's wishes: job titles, locations, skills, employment types, level. One per user |
| **Recommendation** | (search profile, job, score, reasons, status NEW/VIEWED/SAVED/DISMISSED) |
| **Resume** | The uploaded PDF (file key in storage) + its extracted raw text |
| **BASE version** | The CV turned into structured JSON by Gemini. One per resume |
| **TAILORED version** | A BASE rewritten for one job. One per (resume, job) |
| **DRAFT / FINAL** | Edit status of a version. A DRAFT edit overwrites the same row. FINAL is locked; editing a FINAL makes a new row whose parent is that version |
| **DLT** | Dead-letter topic. Where a Kafka message goes after it failed 4 times |
| **Archived** | A soft-deleted resume (`archived_at` set). Read-only: any write returns 409 |

## How this project was run (so the history makes sense)

- Hoang writes all the code. Claude acted as mentor, reviewer and (from 19 Oct) scrum master. Every push to `main` was reviewed for bugs **and** SOLID.
- Work was planned as **vertical slices** with a "done when" check (a feature works end to end), not "implement layer X".
- Code style rule: readable over clever. Plain `if/else`, small single-purpose methods, no ternaries or long stream chains in new code.
- Older study material lives in the project shared files (outside this repo): `study/` (Kafka notes, pagination guide), `sprint2/`, `sprint3/` (Docker, hosting, scheduler explainers), `milestone6/`, `milestone7/` (LLM prompts).
