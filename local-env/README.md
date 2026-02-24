# Local Environment

Docker-based local stack for end-to-end testing of the JAR Config Plugin.

```
Services:
  MinIO        → http://localhost:9000  (S3-compatible storage)
  MinIO UI     → http://localhost:9001  (minioadmin / minioadmin)
  Coordinator  → http://localhost:8081  (REST API)
  SPOT node    → (mock, internal — no exposed port)
```

## Prerequisites

- **Docker Desktop** with Compose v2
- **Java 17** and **Maven** (for building `test-algo-project`)
- Git

## Setup

### 1. Clone the coordinator repo

```bash
cd local-env/

git clone <coordinator-repo-url> coordinator
```

After cloning the directory tree should look like this:

```
local-env/
├── coordinator/          ← cloned coordinator repo (gitignored)
├── spot-node/            ← mock SPOT node (Python, part of this repo)
├── coordinator.Dockerfile
├── spot.Dockerfile
├── docker-compose.yml
└── test-algo-project/
```

> `coordinator/` is gitignored — it is not part of this repo.

### 2. Start all services

```bash
docker compose up -d --build
```

First run builds the coordinator from source (takes a few minutes).
The mock SPOT node starts automatically and connects to the coordinator.

### 3. Verify services are up

```bash
docker compose ps
```

All four containers (`local-minio`, `local-minio-init`, `local-coordinator`, `spot-node`)
should show status `running`. The `minio-init` container exits with code 0 after creating the bucket — that is expected.

Check coordinator health:

```bash
curl http://localhost:8081/api/v1/health
```

Expected response: `{"status":"UP","database":"OK"}`.

## Plugin configuration

Open **Settings → Tools → JAR Config Plugin** in IntelliJ and set:

| Setting         | Value                   |
|-----------------|-------------------------|
| Coordinator URL | `http://localhost:8081` |
| S3 Endpoint     | `http://localhost:9000` |
| Bucket          | `orhestra-algorithms`   |
| Key Prefix      | `experiments`           |
| Access Key      | `minioadmin`            |
| Secret Key      | `minioadmin`            |

Use **Test Coordinator** and **Test S3 Connection** buttons to verify connectivity before submitting a job.

## Running a test job

`test-algo-project/` is a ready-to-use Maven algorithm project. Open it in IntelliJ as a separate project, then:

1. In the **JAR Config** panel, select the `test-algo` module.
2. Set **Main Class** to `algorithms.Main`.
3. Set **Algorithms** to `sphere,rosenbrock`.
4. Leave parameter ranges at defaults (or adjust).
5. Click **Build & Submit**.

The plugin will build the JAR, upload it to MinIO, and submit a job to the Coordinator.
The mock SPOT node will pick up tasks, simulate execution, and report results back.

Watch the **Live Monitor** tab for SPOT node status and task progress.
When all tasks finish, switch to the **Results** tab to load and export results.

## Stopping the environment

```bash
docker compose down
```

To also remove volumes (wipe MinIO data and coordinator database):

```bash
docker compose down -v
```

## Troubleshooting

**Coordinator fails to start** — check that the source was cloned correctly into `coordinator/`.
View logs with `docker compose logs coordinator`.

**S3 upload fails with "bucket not found"** — the `minio-init` container may not have finished.
Wait a few seconds and retry, or run:
```bash
docker compose run --rm minio-init
```

**Test-algo build fails in IntelliJ** — make sure Maven is configured in IntelliJ
(**Settings → Build → Build Tools → Maven**) and that Java 17 is selected.
