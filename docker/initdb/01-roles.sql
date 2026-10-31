-- Runs once, on an empty data volume (/docker-entrypoint-initdb.d), as postgres.
-- blusimian_owner owns the database and every object in it (Flyway connects as it).
-- blusimian_app is the runtime role, which RLS applies to. Never grant one to the
-- other: a member of the owning role counts as the owner, and owners skip RLS.
create role blusimian_owner login password 'blusimian_owner';
create role blusimian_app   login password 'blusimian_app'
    nosuperuser nobypassrls nocreatedb nocreaterole;

-- On Postgres 15+ the public schema belongs to pg_database_owner, so this also
-- lets the owner create public.iso_countries.
alter database blusimian owner to blusimian_owner;
