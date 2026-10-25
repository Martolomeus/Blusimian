# Blusimian

A small CRM, built as a monorepo with the backend at the repository root:

- **`src/`, `pom.xml`** — Spring Boot 4 (Java 25) REST API built with Maven, Postgres + Flyway, JPA/Hibernate.
- **`Frontend/`** — Vite + React 19 SPA (React Router, React Query, react-hook-form + zod).

The schema is split into a `public` schema (global reference data, e.g. `iso_countries`)
and an `app` schema (tenant-scoped data: contacts, companies) to pre-draw the boundary
for a future tenant-isolation (RLS) layer. There is no auth or multitenancy yet.

## Prerequisites

- Docker (for the Postgres container)
- Java 25 (the Maven wrapper, `./mvnw`, is included)
- Node 20+

## Database

Postgres runs in a Docker container named `blusimian-db` (port `5434`, db/user
`blusimian`/`postgres`). Flyway applies `src/main/resources/db/migration` on
backend startup.

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
Makefile   dev orchestration
```

## Notes

- `src/main/resources/application.properties` holds a **local-only** dev DB
  password. Keep real/production secrets out of the repo — use environment variables
  when deploying.
