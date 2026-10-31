# Blusimian

A small CRM, built as a monorepo with the backend at the repository root:

- **`src/`, `pom.xml`** — Spring Boot 4 (Java 25) REST API built with Maven, Postgres + Flyway, JPA/Hibernate.
- **`Frontend/`** — Vite + React 19 SPA (React Router, React Query, react-hook-form + zod).

The app is multi-tenant. The schema is split into a `public` schema (global reference
data, e.g. `iso_countries`) and an `app` schema (tenant data), where Postgres row-level
security isolates tenants on every table. Login is a minimal username + password with an
HTTP session. See `Architecture/multitenancy.md`.

## Prerequisites

- Docker (for the Postgres container)
- Java 25 (the Maven wrapper, `./mvnw`, is included)
- Node 20+

## Database

Postgres runs in a Docker container named `blusimian-db` (port `5434`, db `blusimian`),
started with `docker compose up -d`. On an empty volume, `docker/initdb` creates two
roles: `blusimian_owner`, which Flyway uses to apply `src/main/resources/db/migration` on
backend startup, and `blusimian_app`, which the app uses and RLS applies to. A volume
created before those roles existed needs one `docker compose down -v`.

In dev, the backend provisions two demo tenants on startup: Acme (log in as `alice`) and
Globex (`bob`); each password is the username.

## Common tasks (`make`)

```bash
make backend     # run the Spring Boot API on :8080
make frontend    # run the Vite dev server on :3000
make db-reset    # wipe the database (drops app + public schemas, recreates)
make db-shell    # open a psql shell in the container
make help        # list targets
```

Run the backend and frontend in separate terminals. The Vite dev server proxies
`/api/*` to the backend on `:8080`, so the browser talks same-origin (no CORS).

## Layout

```
src/       Spring Boot API (src/main/java/com/blusimian/blusimian/...)
pom.xml    Maven build (./mvnw)
scripts/   code/SQL generators for the backend
Frontend/  Vite + React SPA (src/features, src/components, src/lib)
docker/    Postgres init scripts (the database roles)
Makefile   dev orchestration
```

## Notes

- `src/main/resources/application.properties` and `docker/initdb` hold **local-only**
  dev DB passwords, as do the demo logins. Keep real/production secrets out of the repo —
  use environment variables when deploying.
