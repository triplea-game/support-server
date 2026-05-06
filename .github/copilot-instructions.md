Use `make check` to validate the project

## Tech Stack
- Java 21, Quarkus (HTTP server), JDBI (no ORM), Postgres, Lombok
- Quarkus fast-jar (`build/quarkus-app/`) as deployment artifact
- Docker + docker-compose for local dev and integration tests

## Testing
- `src/test/` — unit tests; no database or server required
- `src/testInteg/` — integration tests; require Docker (`docker compose up database flyway` starts Postgres on port 5432; Quarkus starts in-process via `@QuarkusTest`)
- Do not put integration tests in `src/test/` or unit tests in `src/testInteg/`

## Code Style
- Google Java Format is enforced via Spotless (`make format` or `./gradlew spotlessApply`)
- No wildcard imports (Spotless removes them)
- Use Lombok annotations (`@Value`, `@Builder`, `@Data`, `@RequiredArgsConstructor`, etc.) instead of hand-written boilerplate

## Database
- No ORM — use JDBI with SQL object pattern
- Migration naming convention: `V{major}.{minor}.{patch}__description.sql`
