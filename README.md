# CodeKB Backend

Spring Boot API for CodeKB (knowledge bases, repos, graph jobs, search).

## Configuration

Copy `src/main/resources/application.example.yml` to `src/main/resources/application.yml`, then override with environment variables as needed. Set at least:

| Variable | Description |
|----------|-------------|
| `CODEKB_DATASOURCE_URL` | JDBC URL |
| `CODEKB_DATASOURCE_USERNAME` / `CODEKB_DATASOURCE_PASSWORD` | DB credentials |
| `CODEKB_REDIS_HOST` / `CODEKB_REDIS_PORT` / `CODEKB_REDIS_PASSWORD` | Redis |
| `CODEKB_JWT_SECRET` | At least 32 bytes |
| `CODEKB_OSS_*` | Aliyun OSS (if used) |
| `CODEKB_GRAPH_BASE_URL` | Graph worker service URL |
| `CODEKB_GITHUB_TOKEN` | GitHub API token for repo metadata; `GITHUB_TOKEN` / `GH_TOKEN` also work |

Optional: add `src/main/resources/application-local.yml` (gitignored) for local overrides.

PowerShell example:

```powershell
$env:CODEKB_GITHUB_TOKEN = "your_github_token"
mvn spring-boot:run
```

## Run

This project needs JDK 17+, and the current local machine already has `JDK 21`.

Recommended on Windows PowerShell:

```powershell
.\scripts\run-local.ps1
```

The script does three things before startup:

1. switches the shell to `C:\Program Files\Java\jdk-21`
2. removes stale `target/` output that can break Maven incremental compile on Chinese Windows paths
3. runs `mvn spring-boot:run`

Manual startup is still available:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean spring-boot:run
```

API base path: `http://localhost:8080/api/v1`
