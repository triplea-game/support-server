Use `make check` to validate the project

## Tech Stack
- Java 25, Quarkus (HTTP server), JDBI (no ORM), Postgres, Lombok
- Quarkus fast-jar (`build/quarkus-app/`) as deployment artifact
- Quarkus Dev Services (Testcontainers) provides Postgres for local dev and integration tests

## Testing
- `src/test/` — unit tests; no database or server required
- `src/testInteg/` — integration tests; require Docker (Quarkus Dev Services auto-starts a disposable Postgres via Testcontainers; Quarkus starts in-process via `@QuarkusTest`)
- Do not put integration tests in `src/test/` or unit tests in `src/testInteg/`

## Code Style
- Google Java Format is enforced via Spotless (`make format` or `./gradlew spotlessApply`)
- No wildcard imports (Spotless removes them)
- Use Lombok annotations (`@Value`, `@Builder`, `@Data`, `@RequiredArgsConstructor`, etc.) instead of hand-written boilerplate
- never use `Optional` class variables, annotate them as nullable instead
- never pass null to another method, favor using overloaded methods to avoid null parameters
- never return null (use Optional instead)

## Commentary style

- Use /// doclet style, using markdown

## Database
- No ORM — use JDBI with SQL object pattern
- Migration naming convention: `V{major}.{minor}.{patch}__description.sql`

## Session / context management

At the end of each reply during a multi-step task, add a short context note:

- A rough, approximate estimate of context used so far. Flag clearly that this is a
  guess, not a measurement — run `/context` for the exact figure.
- Whether the next step is a clean break (unrelated work) or a continuation that needs
  the current context.
- A recommendation: start a fresh session, or proceed in this one.

Only recommend switching when it clearly reduces cost or improves quality — a clean
break and/or a context that has grown heavy. A continuation that depends on the current
context should stay put.
