# JAR Config Plugin

IntelliJ IDEA plugin for submitting algorithm jobs to a distributed computing cluster.

The plugin handles the full pipeline: build the project JAR, upload it to S3, submit a job to the **Coordinator** API, and monitor execution across **SPOT** worker nodes in real time.

## How it works

```
IntelliJ IDE
    │
    ├─ JAR Config panel ──► Gradle/Maven build ──► S3 upload ──► POST /api/v1/jobs
    │                                                                    │
    └─ JAR Monitor panel ◄─────── polling GET /api/v1/jobs/{id} ◄───────┘
                                  GET /api/v1/spots
```

1. Developer configures algorithm parameters (which algorithms, iteration/agent/dimension ranges) in the **JAR Config** panel.
2. Clicks **Build & Submit** — the plugin builds the JAR, uploads it to S3, and submits a job to the Coordinator.
3. The Coordinator distributes tasks across SPOT nodes. Each task runs the JAR with specific parameter values.
4. The **JAR Monitor** polls for task status every 4 seconds. When tasks complete, results (fopt values, best positions, runtimes) are available in the **Results** panel.

## Features

- **JAR Config** tool window (right panel)
  - Module and main class selection
  - Algorithm list (comma-separated)
  - Parameter sweep: iterations, agents, dimension — each with min / max / step
  - Build log with progress output and Clear button
  - One-click **Build & Submit**

- **JAR Monitor** tool window (bottom panel)
  - **Live Monitor** tab — SPOT nodes table (CPU load, running tasks, heartbeat) + task queue with status coloring
  - **Results** tab — load completed task results by Job ID
    - Table view: all task parameters and metrics
    - **Chart** view: scatter plot (X = any parameter, Y = fopt, color = algorithm, point size = 3rd parameter)
    - Export to CSV or JSON

- **Settings** (`Settings → Tools → JAR Config Plugin`)
  - Coordinator URL
  - S3 endpoint, bucket, key prefix
  - S3 access/secret key (stored in IDE settings)
  - **Test Coordinator** button — checks `/api/v1/health` with a 5-second timeout
  - **Test S3 Connection** button — verifies bucket access with a 12-second timeout

## User cases

### Case 1: Running a parameter sweep

You have a Java/Kotlin optimization algorithm and want to benchmark it across combinations of agent count, iteration count, and problem dimension.

1. Open your algorithm project in IntelliJ.
2. In **JAR Config**, set algorithms (e.g. `sphere,rosenbrock`), and ranges for each parameter.
3. Click **Build & Submit**. The plugin creates and submits a job that generates all parameter combinations as separate tasks.
4. Watch **Live Monitor** — tasks are picked up by SPOT nodes and run in parallel.
5. When done, go to **Results → Load Results** and explore the fopt vs. agents scatter plot to find which configuration converges fastest.

### Case 2: Comparing multiple algorithms

Set algorithms to `pso,de,ga` with identical parameter ranges. After the job completes, the chart colors each algorithm differently, making cross-algorithm comparison immediate.

### Case 3: Iterative tuning

Submit a broad sweep first. After loading results, narrow the parameter ranges to the promising region and submit again. The Results panel accepts any Job ID so you can compare across runs.

### Case 4: Exporting data for a report

Load results, switch to the Table tab, and click **Export CSV** or **Export JSON**. The CSV includes all task parameters, fopt, best position, and runtime.

## Requirements

- IntelliJ IDEA 2024.1 – 2024.3 (build 241 – 243.x)
- JDK 17+
- Gradle wrapper (`gradlew`) or Maven wrapper (`mvnw`) in the project being built
- Coordinator backend accessible over HTTP
- S3-compatible storage (Yandex Cloud Object Storage, MinIO, AWS S3, etc.)

## Installation

**From disk (development build):**
```bash
.\gradlew.bat build
```
Then **Settings → Plugins → ⚙ → Install Plugin from Disk** and select
`build/distributions/JARConfigPlugin-1.0.0.zip`. Restart IDE.

**Run in sandbox (without installing):**
```bash
.\gradlew.bat runIde
```

## Configuration

**Settings → Tools → JAR Config Plugin**

| Setting | Default | Description |
|---------|---------|-------------|
| Coordinator URL | `http://localhost:8081` | Base URL of the Coordinator REST API |
| S3 Endpoint | `https://storage.yandexcloud.net` | S3-compatible endpoint |
| Bucket | `orhestra-algorithms` | Bucket for uploaded JARs |
| Key Prefix | `experiments` | Object key prefix, e.g. `experiments` → `experiments/my-algo-1.0.jar` |
| Access Key / Secret Key | — | Stored in IDE settings file (not in project files) |

## Local environment

See [local-env/README.md](local-env/README.md) for instructions on running
the full stack locally with Docker (MinIO + Coordinator + SPOT node).

## Build support

The plugin detects the build tool from the project root:

| Build tool | Detection | Commands tried (in order) |
|------------|-----------|---------------------------|
| Gradle | `build.gradle.kts` or `build.gradle` present | `shadowJar`, `:module:shadowJar`, `jar`, `:module:jar` |
| Maven | `pom.xml` present | `mvn package -DskipTests` |

The JAR is searched in `build/libs/` (Gradle) and `target/` (Maven), including one level of subdirectories.

## Project structure

```
plugin root/
├── src/main/kotlin/com/example/jarconfigplugin/
│   ├── actions/        OpenConfigAction
│   ├── model/          JobRequest, JobResult, SpotStatus, TaskResult, ...
│   ├── services/       CoordinatorApiClient, CoordinatorService, S3Service,
│   │                   JarBuildService, JobStateService, BuildToolDetector
│   ├── settings/       PluginSettingsState, PluginSettingsConfigurable, CredentialStore
│   └── ui/             ConfigPanel, MonitorPanel, ResultsPanel, FoptChartPanel,
│                       ConfigToolWindowFactory, MonitorToolWindowFactory
├── src/test/           Unit tests (JUnit 5, MockK, WireMock)
├── ui-tests/           Remote Robot UI tests
├── local-env/          Docker stack for local development
│   ├── coordinator/    ← clone coordinator repo here (gitignored)
│   ├── spot-node/      mock SPOT node (Python)
│   ├── coordinator.Dockerfile
│   ├── spot.Dockerfile
│   ├── docker-compose.yml
│   └── test-algo-project/  sample Maven algorithm project
└── build.gradle.kts
```

## Tech stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1 |
| Build | Gradle 8.13, IntelliJ Platform Gradle Plugin 2.11 |
| HTTP client | OkHttp 4.x |
| JSON | Jackson (Kotlin module) |
| S3 | AWS SDK for Java v2 |
| UI | IntelliJ Platform Swing (JB* components) |
| Tests | JUnit 5, MockK, WireMock |

## Compatibility

- **sinceBuild**: 241 (IntelliJ IDEA 2024.1)
- **untilBuild**: 243.* (IntelliJ IDEA 2024.3.x)

## Publishing to JetBrains Marketplace

1. Register on [plugins.jetbrains.com](https://plugins.jetbrains.com) and create a plugin entry.
2. Obtain a signing certificate from the plugin page → **Certificates**.
3. Set environment variables and build a signed ZIP:
   ```powershell
   $env:CERTIFICATE_CHAIN    = Get-Content -Raw path\to\chain.crt
   $env:PRIVATE_KEY          = Get-Content -Raw path\to\private.key
   $env:PRIVATE_KEY_PASSWORD = "password"
   .\gradlew.bat build
   ```
4. Verify compatibility across IDE versions:
   ```bash
   .\gradlew.bat runPluginVerifier
   ```
5. Create a publish token on the plugin page and publish:
   ```powershell
   $env:PUBLISH_TOKEN = "token"
   .\gradlew.bat publishPlugin
   ```

Before publishing, update the vendor name, email, and repository URL in `plugin.xml` and `gradle.properties`.
