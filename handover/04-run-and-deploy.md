# 4. Run it, deploy it, operate it

## 4.1 Prerequisites

- JDK 17, Docker Desktop (or Docker Engine + compose plugin), Git. IntelliJ IDEA is what the owner uses.
- A Gemini API key (Google AI Studio) if you want to test CV parsing/tailoring. Without a real key, those endpoints return 503; everything else works.
- Windows note: the owner works in PowerShell. `mvnw` is committed as executable for Linux (needed on EC2).

## 4.2 Environment variables

Create a `.env` file in the repo root (it is git-ignored, **never commit it**). Compose reads it; IntelliJ needs the same values in its run configuration.

| Variable | Example (local) | Used for |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | `jora` / `change-me` | Postgres login (also creates the local container's user) |
| `DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/jora` (inside compose) | Mapped to `SPRING_DATASOURCE_URL` for the app container. Outside Docker the default `localhost:5432/jora` is used |
| `JWT_SECRET` | a long random string (at least 32 bytes) | Signs JWTs. Different value in production |
| `GEMINI_API_KEY` | `...` | Gemini. The app refuses to start without some value |
| `STORAGE_TYPE` | `local` or `s3` | Which `FileStorage` is used |
| `S3_BUCKET` | your bucket name | Only when `s3` |
| `AWS_REGION` | `ap-southeast-2` | S3 client region |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | | **Laptop only**, for testing S3. Leave empty on EC2 (the instance role is used) |
| `CRAWL_CRON` | `0 */1 * * * *` while testing | Scheduler timing (Spring cron has 6 fields: sec min hour day month weekday). Default `0 0 6,18 * * *`. Note: not passed through `docker-compose.yml` yet; add it under `jora.environment` if you need it in the container |
| `CRAWL_TIMEZONE` | `Asia/Ho_Chi_Minh` | Scheduler zone. Same compose note |

Inside compose, the app reaches other containers by **service name**: Kafka at `kafka:29092`, Redis at `redis`, Postgres at `postgres`. From your laptop it is `localhost:9092`, `localhost:6379`, `localhost:5432`. Getting this wrong is the #1 "can't connect" cause.

## 4.3 Run locally

Option 1: everything in Docker (closest to production)
```bash
docker compose up -d --build        # postgres, kafka, kafka-ui, redis, jora
docker compose logs -f jora         # watch the app; Flyway runs V1..V14 on first start
```

Option 2: infra in Docker, app in IntelliJ (fast edit/debug loop)
```bash
docker compose up -d postgres kafka kafka-ui redis
./mvnw spring-boot:run              # or Run in IntelliJ with the env vars set
```

Open: API `http://localhost:8080`, Kafka UI `http://localhost:8081`.

**Careful:** if your `.env` points `DATASOURCE_URL` at the production RDS, running from IntelliJ will run migrations against production. Test new migrations against the local DB first.

## 4.4 Smoke test (Postman or curl)

1. `POST /api/v1/auth/register` `{ "email": "...", "password": "..." }`, then `POST /api/v1/auth/login` -> copy `token`. Send `Authorization: Bearer <token>` on every other call.
2. `POST /api/v1/searchProfiles` with `name`, `level`, `jobTitles`, `skills`, `locations`, `employmentTypes`.
3. Crawl: make yourself admin (`UPDATE users SET role = 'ADMIN' WHERE email = '...';`, then log in again), call `POST /api/v1/admin/ingestion/greenhouse/axon` -> 202. Watch the three topics fill in Kafka UI and the logs. A second call within 30 min logs "Skipped crawl ... crawled recently".
4. `GET /api/v1/job` and `GET /api/v1/jobRecommendation`.
5. CV: `POST /api/v1/resume` (form-data `file` = a PDF) -> `POST /api/v1/resume/{id}/extract` -> `POST /api/v1/resume/{id}/versions` (201, then 200 on repeat) -> `POST /api/v1/resume/{id}/tailor/{jobId}`.

A longer manual checklist (written for Milestone 6, still mostly valid) is in `milestone6/manual-test-checklist.md` in the project shared files (not in this repo).

## 4.5 Run the tests

```bash
createdb sefujo_test          # once; needed by JobRecommendationRepositoryTest (profile "test")
./mvnw test                   # needs DB_USERNAME, DB_PASSWORD, JWT_SECRET in the environment
```

## 4.6 Production today (AWS, Sydney)

```
EC2 (Ubuntu, Arm)                               RDS PostgreSQL 17 (database "jora")
  ~/Jora  (git clone of main)                     reachable only from the EC2 security group
  .env    (production values, no AWS keys)      S3 bucket (private) for resume PDFs
  docker compose up -d --build                    reached through the EC2 instance role
```

- The server `.env` points `DATASOURCE_URL` at RDS and sets `STORAGE_TYPE=s3`.
- The `postgres` service in compose still starts on the server but isn't used (to-do: remove it there, e.g. a `docker-compose.prod.yml` override).
- Port 8080 is open to the internet; Kafka UI (8081), Kafka and Redis are not.
- Access details (account, IP, SSH key) are kept by the owner, not in this document.

### Deploy a new version (manual, until CI/CD exists)
```bash
ssh -i <key>.pem ubuntu@<ec2-ip>
cd ~/Jora && git pull
docker compose up -d --build jora     # rebuilds and restarts only the app
docker compose logs -f jora
```

### Common operations
| Task | Command |
|---|---|
| See containers | `docker compose ps` |
| App logs | `docker compose logs -f --tail 200 jora` |
| Kafka UI from your laptop | `ssh -i <key>.pem -L 8081:localhost:8081 ubuntu@<ec2-ip>` then open `http://localhost:8081` |
| List topics | `docker exec -it jora-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` |
| Consumer lag | `docker exec -it jora-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group jora` |
| Read a DLT | `docker exec -it jora-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic job.discovered.DLT --from-beginning` |
| Redis keys | `docker exec -it jora-redis redis-cli --scan --pattern 'crawl:*'` (use `SCAN`, never `KEYS *` in production) |
| Unlock a board early | `docker exec -it jora-redis redis-cli DEL crawl:greenhouse:<token>` |
| Add a board to crawl | `INSERT INTO crawl_targets (platform_id, company_board_token) SELECT id, '<token>' FROM platforms WHERE name = 'greenhouse';` |
| Pause a board | `UPDATE crawl_targets SET enabled = false WHERE company_board_token = '<token>';` |

More (in the project shared files, not in this repo): `sprint2/docker-inspect-commands.md` (Redis/Kafka inspection), `sprint2/kafka-docker-compose.md` (the Kafka listeners explained), `sprint3/dockerfile-explained.md`, `sprint3/hosting-basics.md`, `sprint3/scheduler-notes.md`.

### Gotchas we already hit
- **SSH times out** suddenly: the owner's home IP changed. Update the SSH rule in the EC2 security group to "My IP". Same for any RDS rule that uses a home IP.
- **The public IP changes** if the EC2 is stopped and started. Attach an Elastic IP before pointing a domain at it.
- **RDS create form** defaults to expensive production settings (Aurora, large instance, replicas). Always read the "Estimated monthly costs" box before clicking Create.
- **Kafka consumer group fails to start** on a single broker if the internal topics' replication factor is left at the default 3. The compose file sets them to 1.
- **`@Scheduled` silently never runs** without `@EnableScheduling`.

## 4.7 Shutting AWS down later (e.g. moving to free tiers)

Stopping is only a pause: a stopped EC2 still bills for its disk, and a stopped RDS **starts itself again after 7 days**. To really stop paying, delete things in this order:

1. **Back up first.** `pg_dump` the `jora` database (or take a final RDS snapshot and export it), and copy the S3 bucket to your laptop or the new host (`aws s3 sync s3://<bucket> ./resumes-backup`).
2. **EC2:** turn off termination protection, then **terminate** the instance (its disk goes with it). Release any **Elastic IP** (an unattached one is billed).
3. **RDS:** delete the instance (choose whether to keep a final snapshot), then delete any **manual snapshots** you no longer need (they are billed too).
4. **S3:** empty the bucket, then delete it.
5. **Clean-up:** delete the security groups, the IAM role, IAM user and policy for the app, and the EC2 key pair. Remove any Route 53 zone / domain records, load balancer or ECR images if they were added later.
6. **Check billing** in the Billing console and Cost Explorer a day later to confirm nothing is still running. Keep the budget alert on.
