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

Optional: add `src/main/resources/application-local.yml` (gitignored) for local overrides.

## Run

```bash
mvn spring-boot:run
```

API base path: `http://localhost:8080/api/v1`
