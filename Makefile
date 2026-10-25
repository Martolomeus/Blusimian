.PHONY: help db-reset db-shell backend frontend

# Local dev settings — match the manually-started `blusimian-db` Postgres container
# (postgres:16, port 5434, db/user/password all "blusimian"/"postgres").
DB_CONTAINER := blusimian-db
DB_USER      := postgres
DB_NAME      := blusimian

help:
	@echo "Targets:"
	@echo "  make db-reset   Wipe the database (drop & recreate the public schema)"
	@echo "  make db-shell   Open a psql shell in the database container"
	@echo "  make backend    Run the Spring Boot backend (port 8080)"
	@echo "  make frontend   Run the Vite dev server (Frontend/)"

# Wipe all data: drops the app + public schemas (every table, the seeded
# reference data, the citext extension, and Flyway's history) and recreates an
# empty public schema. The next `make backend` re-runs V1__init.sql from scratch
# (which recreates the app schema). Stop the backend first so nothing is mid-migration.
db-reset:
	@docker exec $(DB_CONTAINER) psql -U $(DB_USER) -d $(DB_NAME) \
		-c "drop schema if exists app cascade; drop schema if exists public cascade; create schema public;"
	@echo "Wiped '$(DB_NAME)'. Start the backend to re-apply migrations."

# Interactive psql shell.
db-shell:
	docker exec -it $(DB_CONTAINER) psql -U $(DB_USER) -d $(DB_NAME)

backend:
	./mvnw spring-boot:run

frontend:
	cd Frontend && npm run dev
