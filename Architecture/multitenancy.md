# Multitenancy — Tenant Isolation with Row-Level Security — Architecture Reference

**Status:** Implemented & verified (§15) · **Scope:** tenants, users, `tenant_id` + RLS on every tenant-scoped table, binding the request's tenant to the database, minimal username/password login
**Last updated:** 2026-10-31 · **Builds on:** the `app`/`public` schema split in `V1__init.sql`, `Architecture/custom-fields.md`, `Architecture/entity-layouts.md`
**Code:** `src/main/resources/db/migration/V1__init.sql`, `platform/tenancy`, `platform/security`, `tenants`, `users`, `auth` packages; `docker/`, `docker-compose.yml`, `Makefile`, `application.properties`; `Frontend/src/features/auth`

> Tenant data lives in `app`, global reference data in `public`. This is the tenant layer
> on top of that split.
>
> **Postgres is the isolation boundary.** Every table in `app` gets a `tenant_id`, row-level
> security, and one policy that compares it to a transaction-local setting,
> `app.tenant_id`. The application sets that setting at the start of every transaction
> from the logged-in user's tenant, and connects as a role that cannot bypass RLS.
>
> **Business code stays tenant-unaware.** No CRM entity, repository or service reads or
> writes `tenant_id`. `CompanyService.list()` still calls `repository.findAll()`. The
> database decides what "all" means.
>
> **Every known way around RLS is closed.** Owning a table, a definer-rights view,
> `TRUNCATE`, a cache, a default tenant on the role: each one has a rule (§5.8). The ones
> Postgres can report are checked before the app accepts its first request (§8).
>
> **Auth is deliberately minimal**: username + password, BCrypt, an HTTP session. All of it
> sits behind one seam, the authenticated principal, so it can be replaced later without
> touching anything below.

---

## 1. Goals and non-goals

**Goals**

- A tenant can never read, write or reference another tenant's rows. Postgres enforces
  this; it doesn't depend on every query remembering a `where` clause.
- A missing or inconsistent tenant context is an **error**. It never quietly returns all
  rows, no rows, or another tenant's rows.
- CRM business code (entities, repositories, services, `RecordMapper`) keeps its shape.
- All tenancy plumbing sits in one place, and is checked at startup and in tests.
- The auth mechanism can be swapped without touching isolation.

**Non-goals (for now)**

- Secure auth: CSRF tokens, rate limiting, lockout, password rules, password reset, MFA,
  OIDC/JWT.
- One user belonging to several tenants.
- Self-service signup, tenant deletion/offboarding, a cross-tenant admin console.
- Per-tenant reference data (e.g. "this tenant's enabled countries").
- Search. Its RLS implications are recorded (§5.6, §18), but nothing is built.

---

## 2. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | Postgres RLS is the isolation boundary. One policy per table: `tenant_id = (select app.current_tenant_id())`, for all commands, as both `using` and `with check`. | §5.3 |
| D2 | The tenant reaches Postgres as a **transaction-local** setting `app.tenant_id`, set at the start of every Spring transaction. Postgres reverts it on commit/rollback, so a pooled connection never carries a tenant to its next borrower. | §7.3 |
| D3 | A missing or inconsistent tenant is loud. A transaction can't begin without a tenant (`MissingTenantContextException`); `app.current_tenant_id()` **raises** when the setting is absent; and the tenant can't be switched while a transaction is open. | §5.2, §7.2, §7.3 |
| D4 | Three DB roles: `postgres` (bootstrap only), `blusimian_owner` (Flyway; owns every object), `blusimian_app` (runtime: not owner, not superuser, `NOBYPASSRLS`, member of no role that is, DML grants only). | §4 |
| D5 | `tenant_id` belongs to the database, not to entities: `not null default app.current_tenant_id()`, **not mapped in JPA**. | §6 |
| D6 | Every tenant table's primary key is **`(tenant_id, id)`**, and every reference between tenant tables is a **composite FK** to it. FK checks ignore RLS; composite FKs make cross-tenant references unrepresentable. JPA keeps `@Id id`. | §5.3, §5.4 |
| D7 | Every unique constraint and secondary index leads with `tenant_id`. The single exception is `users.username` (D9). | §5.5 |
| D8 | Tenant context in Java is a `ScopedValue<TenantId>` (final in Java 25). A filter binds it per request from the authenticated principal, **never** from a header, path or body. | §7.2, §7.4 |
| D9 | `users` is tenant-scoped (RLS), but `username` is **globally** unique, so login is just username + password with no tenant picker. The only read that happens before a tenant is known is one `SECURITY DEFINER` function, `app.find_login_user`. | §9.1 |
| D10 | Minimal auth: Spring Security, `DelegatingPasswordEncoder` (BCrypt), server-side HTTP session, a JSON login endpoint. Tenancy reads only the `TenantPrincipal` interface. | §9 |
| D11 | `TenantProvisioningService` creates a tenant by running **as** that tenant. It binds the new id, then inserts the tenant row, the first user, and runs every slice's `TenantInitializer`, all in one transaction. No RLS bypass. | §10 |
| D12 | V1's seed (default layouts, `lead_source`) moves to Java. Default layouts become a per-tenant initializer, and `lead_source` becomes demo data for one dev tenant. Two demo tenants are seeded in dev. | §10.2, §10.3 |
| D13 | Case-insensitive text is a nondeterministic ICU collation, **not `citext`**. Under RLS only *leakproof* operators can use an index, and `citext` equality isn't one. | §5.6 |
| D14 | Every known way around RLS is closed by a rule. The ones Postgres can report are checked at startup, before the port opens, and the app refuses to start if any check fails. | §5.8, §8 |

---

## 3. How a request reaches the database

```
Browser ── cookie: JSESSIONID ──▶ Spring Security filter chain
                                    │  session → SecurityContext → TenantUserPrincipal(userId, tenantId, …)
                                    ▼
                                  TenantContextFilter
                                    │  ScopedValue.where(TenantContext.CURRENT, principal.tenantId())
                                    ▼
                                  Controller → Service (@Transactional)
                                    ▼
                                  TenantBindingJpaDialect.beginTransaction
                                    │  BEGIN; select set_config('app.tenant_id', '<uuid>', true)
                                    ▼
                                  Repositories / JdbcClient   (same connection, same transaction)
                                    │  select … from app.companies        ← no tenant predicate in Java
                                    ▼
                                  Postgres RLS adds: where tenant_id = app.current_tenant_id()
                                    ▼
                                  COMMIT → app.tenant_id reverts; the connection goes back to Hikari clean
```

Where each mistake ends up:

| Mistake | Result |
|---|---|
| Unauthenticated request to `/api/**` | 401 from Spring Security; never reaches a service |
| Service method touches the DB outside a transaction | No setting on the connection → `app.current_tenant_id()` raises → 500 |
| Transaction started with no tenant bound (e.g. on an `@Async` thread) | `MissingTenantContextException` before any SQL runs → 500 |
| Code switches tenant (`runAs`) while a transaction is open | `IllegalStateException` from `TenantContext` before anything runs |
| App role can bypass RLS: superuser, `BYPASSRLS`, owner, or member of the owner | Startup check refuses to boot (§8) |
| Table without RLS, a too-wide policy, a definer-rights view, a `TRUNCATE` grant, a default tenant on the role | Startup check refuses to boot (§8) |
| Hand-written SQL inserts or updates a row with another tenant's id | `with check` rejects it: *new row violates row-level security policy* |
| Request references another tenant's row by a guessed UUID | Read: 404 (RLS hides it). Write: composite FK violation (§5.4) |

---

## 4. Database roles and privileges

| Role | Login | Owns | Used by | RLS |
|---|---|---|---|---|
| `postgres` | yes | the cluster | docker init, `make db-shell` / `db-reset` | bypassed (superuser) |
| `blusimian_owner` | yes | database `blusimian`, schemas `app` and `public`, every table, function and collation | Flyway (`spring.flyway.user`) | bypassed on its own tables (owner; RLS is not `FORCE`d) |
| `blusimian_app` | yes | nothing | the Hikari pool (`spring.datasource.username`) | **enforced** |

**`blusimian_app` never becomes a member of `blusimian_owner`**, or of any role that owns
something in `app`. Postgres treats a member of the owning role as the owner, and owners
skip RLS. After `grant blusimian_owner to blusimian_app`, the app role read every
tenant's rows with no tenant set and no error (§15.3). Startup check C2 enforces this
(§8).

Roles are created by infrastructure, not by Flyway, because roles are cluster-level and
have passwords. `docker/initdb/01-roles.sql`, mounted into `/docker-entrypoint-initdb.d/`,
runs once, on an empty volume:

```sql
create role blusimian_owner login password 'blusimian_owner';
create role blusimian_app   login password 'blusimian_app'
    nosuperuser nobypassrls nocreatedb nocreaterole;
alter database blusimian owner to blusimian_owner;
```

`blusimian_owner` becomes the database owner on purpose. On Postgres 15+ the `public`
schema belongs to `pg_database_owner`, so the database owner may create in it, and V1
creates `public.iso_countries` there.

**Grants live in V1**, next to the tables they cover (§5.3). They're versioned with the
schema:

```sql
grant usage on schema app to blusimian_app;
grant usage on schema public to blusimian_app;           -- see make db-reset below
grant select on public.iso_countries to blusimian_app;   -- global, read-only
-- each tenant table: exactly select, insert, update, delete, beside its policy (§5.3)
```

Grants are explicit, per table. There's no `alter default privileges`. A new table that
nobody granted fails with *permission denied* the first time it's used, which prompts
adding its RLS block too. `app.flyway_schema_history` gets no grant.

The role name `blusimian_app` is hard-coded in V1. If production needs a different name,
it becomes a Flyway placeholder.

**Config changes**

```properties
# application.properties
spring.datasource.username=blusimian_app
spring.datasource.password=blusimian_app
# With a user set, Boot gives Flyway its own connection (same URL) as the owner.
spring.flyway.user=blusimian_owner
spring.flyway.password=blusimian_owner
```

```yaml
# docker-compose.yml, service db
    volumes:
      - blusimian-db-data:/var/lib/postgresql/data
      - ./docker/initdb:/docker-entrypoint-initdb.d:ro
```

`make db-reset` recreates `public` owned by `pg_database_owner`, as Postgres creates it,
so the owner can create `iso_countries` in it again:

```sql
drop schema if exists app cascade; drop schema if exists public cascade;
create schema public authorization pg_database_owner;
```

The recreated schema lacks the default `USAGE` for `PUBLIC` that a fresh database has,
which is why V1 grants it to the app role explicitly.

Existing dev volumes were initialized without these roles, so they need one
`docker compose down -v`. The dev-phase rule already says to wipe the database rather than
migrate, so this changes nothing.

---

## 5. Schema

Everything below is in `V1__init.sql`, following the single-migration dev-phase rule.
Order inside V1: schema and its grants → collation (§5.6) → `current_tenant_id` →
`tenants` → `users` → `iso_countries` → CRM tables → layout tables → `find_login_user`.
Each table's grant sits beside its policy.

### 5.1 `app.tenants` and `app.users`

```sql
create table app.tenants
(
    id         uuid primary key,              -- assigned by provisioning (§10.1)
    name       varchar(255) not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table app.users
(
    id            uuid         not null default gen_random_uuid(),
    tenant_id     uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    username      varchar(100) collate app.case_insensitive not null,
    password_hash text         not null,      -- DelegatingPasswordEncoder: {bcrypt}$2a$10$…
    display_name  varchar(255) not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),

    constraint pk_users primary key (tenant_id, id),
    -- Global on purpose: login has no tenant picker (D9).
    constraint uq_users_username unique (username)
);
```

`tenants` has no `tenant_id`: its `id` *is* the tenant. Its policy compares `id` (§5.3).
For now a tenant is just a name. Status, slug and plan come when something needs them.
The username's minimum length is request validation (`@Size(min = 3, max = 100)`); the
column type carries the maximum.

### 5.2 `app.current_tenant_id()`

```sql
create function app.current_tenant_id() returns uuid
    language plpgsql
    stable
    parallel safe
as
$$
declare
    v text := nullif(current_setting('app.tenant_id', true), '');
begin
    if v is null then
        raise exception 'tenant context not set (app.tenant_id)';
    end if;
    return v::uuid;
end
$$;
```

- **`missing_ok = true` with `nullif(…, '')`** covers both forms of "unset". A setting
  never set in this session reads as `NULL`. One set transaction-locally earlier in the
  session reads as `''` after that transaction ends (reproduced, §15.3).
- **Why raise instead of returning `NULL`.** `tenant_id = NULL` matches nothing, which
  fails closed but silently: a missing-context bug looks like a tenant with no data.
  Raising turns it into a stack trace that points at the query.
- **`parallel safe`.** Functions default to parallel-unsafe, and an unsafe function in a
  policy rules out parallel plans for *every* query on *every* tenant table. Reproduced:
  without it, no parallel plan; with it, a parallel aggregate with the correct count.
  Reading a setting is safe (`current_setting` is itself marked safe), and the leader
  evaluates the InitPlan once and hands the value to the workers.
- **Precisely when it raises.** The policy wraps the call in `(select …)` (§5.3), so it
  is evaluated lazily, once per query. It raises as soon as the table has a row to check.
  A query against a completely empty table returns nothing instead, which leaks nothing.
  Writes always raise, because the column default calls the function directly.

### 5.3 The tenant block — on every tenant table

Each tenant table gets exactly this. Shown here on `companies`:

```sql
create table app.companies
(
    id        uuid         not null default gen_random_uuid(),
    tenant_id uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    name      varchar(255) not null,
    -- … existing columns; the cf_* slot pool stays LAST (custom-fields.md) …

    constraint pk_companies primary key (tenant_id, id)
);

alter table app.companies enable row level security;

create policy tenant_isolation on app.companies
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.companies to blusimian_app;
```

- **The primary key is `(tenant_id, id)`.** One index serves both access paths.
  `findById` becomes a single lookup on both columns, with the policy supplying
  `tenant_id` (reproduced). A tenant's list is a range scan on its first column. It's
  also the target composite FKs point at (§5.4), and the key a table needs before it can
  be partitioned by tenant.
- **JPA keeps `@Id UUID id`.** Hibernate's `validate` checks mapped columns and declared
  indexes, never the primary key (checked in Hibernate 7.4.5's `AbstractSchemaValidator`).
- **What the database no longer promises.** `id` is now guaranteed unique per tenant
  rather than globally. With random UUIDs that loses nothing, and a session only ever
  sees one tenant. An owner-side `where id = …` without `tenant_id` can't use the key on
  Postgres 16, so admin queries name the tenant.
- **`tenant_id` is the second column**, right after `id`. This only matters for reading
  the table in psql, but the rewrite of V1 makes it free.
- **`(select app.current_tenant_id())`** makes the call an InitPlan: it is evaluated
  once per query rather than once per row, and compared as a constant, so
  `(tenant_id, …)` indexes are usable.
- **`using` + `with check`**: `using` filters what you can see, update or delete;
  `with check` stops an insert or update from *writing* another tenant's id. Together they
  mean an update can't move a row from one tenant to another (both reproduced).
- **`to blusimian_app`**: any other non-owner role that is added later sees nothing by
  default.
- **One permissive policy.** Future finer-grained rules, such as record owner or team
  visibility, must be `as restrictive`, so they can only narrow tenant isolation and never
  widen it.
- **Exactly four privileges.** `TRUNCATE` ignores RLS: tenant B emptied tenant A's rows
  (reproduced). `REFERENCES` and `TRIGGER` have no business on the runtime role. Startup
  check C5 enforces this.
- **`tenants`** gets the same block, with `id = (select app.current_tenant_id())` in both
  clauses and `grant select, insert, update` (no delete). Its primary key stays `(id)`.

### 5.4 Composite foreign keys

Postgres checks foreign keys as the table owner, **ignoring RLS**. With today's
`customer_id references app.companies (id)`, tenant B could save a deal pointing at
tenant A's company if it knew the UUID. The FK would pass, and B's deal would reference a
row B can't see. Success versus failure would also reveal whether that UUID exists in
another tenant.

A composite FK puts `tenant_id` on both sides, so the referenced row must belong to the
same tenant. Its target is the referenced table's primary key:

```sql
create table app.deals
(
    id          uuid         not null default gen_random_uuid(),
    tenant_id   uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    deal_name   varchar(255) not null,
    amount      numeric(19, 2),
    customer_id uuid,
    -- …

    constraint pk_deals primary key (tenant_id, id),
    constraint fk_deals_customer_id foreign key (tenant_id, customer_id)
        references app.companies (tenant_id, id) on delete set null (customer_id)
);
```

Reproduced on Postgres 16:
- With the plain FK, tenant B inserted a deal pointing at tenant A's company.
- The composite FK rejected the same insert.
- `on delete set null (customer_id)` left `tenant_id` intact.

- **`on delete set null (customer_id)`** (Postgres 15+) nulls only that column. A plain
  `set null` would null `tenant_id` as well and violate `not null`.
- With MATCH SIMPLE (the default), a null `customer_id` skips the check, as it does today.
- **The constraint name keeps `customer_id`.** `DealService.save` turns the FK violation
  into `InvalidDealCustomerException` by matching that name, so "no such company" and
  "another tenant's company" both stay a 400 and look identical to the caller. No code
  change.
- Cascading children use the same form: `foreign key (tenant_id, layout_id) references
  app.entity_layouts (tenant_id, id) on delete cascade`.

The services mostly catch this earlier. For example, `CompanyContactService` loads the
`Company` and `Contact` through repositories, RLS hides the other tenant's row, and the
caller gets a 404. The composite FK is the database backstop for paths that don't check
first, and `DealService` relies on the FK by design.

### 5.5 Unique constraints and indexes

Postgres enforces unique constraints across the whole table, whatever RLS says. A unique
without `tenant_id` would stop tenant B from using a key tenant A already has, and in
doing so tell B that A has it. So every unique leads with `tenant_id`, just like the
primary key:

| Before | After |
|---|---|
| `uq_custom_field_key (entity_type, field_key)` | `(tenant_id, entity_type, field_key)`, case-insensitive through the column's collation (§5.6) |
| `uq_cfd_column (entity_type, column_name)` | `(tenant_id, entity_type, column_name)` |
| `uq_entity_layout_default (entity_type) where is_default` | `(tenant_id, entity_type) where is_default` |
| `uq_section_field (section_id, field_key)` | `(tenant_id, section_id, field_key)`: section ids are now unique per tenant only |
| — | `uq_users_username (username)`: the deliberate global exception (D9) |

**Every secondary index leads with `tenant_id`.** Every query carries
`tenant_id = <const>` from the policy, so the leading column is always usable. An index
that a unique constraint already covers goes away:

| Before | After |
|---|---|
| `idx_companies_name (name)` | `(tenant_id, name)` |
| `idx_companies_owner_id (owner_id)` | `(tenant_id, owner_id)` |
| `idx_companies_archived (archived_at) where …` | `(tenant_id, archived_at) where …` |
| `idx_companies_parent (parent_company_id) where …` | `(tenant_id, parent_company_id) where …` |
| `idx_deals_customer (customer_id) where …` | `(tenant_id, customer_id) where …` |
| `idx_custom_field_defs_entity (entity_type)` | dropped: `uq_custom_field_key` starts with `(tenant_id, entity_type)` |
| `idx_layout_sections_layout (layout_id)` | `(tenant_id, layout_id)` |
| `idx_layout_section_fields_section (section_id)` | dropped: `uq_section_field` starts with `(tenant_id, section_id)` |
| — | `idx_company_contacts_company (tenant_id, company_id)` and `idx_company_contacts_contact (tenant_id, contact_id)`, new: `findByCompanyId` / `findByContactId` and the FK checks when a company or contact is deleted |

Future slot indexes become `(tenant_id, cf_*)`, as `custom-fields.md` already says: the
same physical column holds different fields for different tenants.

### 5.6 Case-insensitive text: a collation, not `citext`

```sql
create collation app.case_insensitive (provider = icu, locale = 'und-u-ks-level2', deterministic = false);
```

`und-u-ks-level2` compares at "secondary strength": case is ignored, accents are not
(`'ALICE' = 'alice'`, `'é' <> 'e'`).

| Column | Was | Becomes |
|---|---|---|
| `users.username` | (new) | `varchar(100) collate app.case_insensitive` |
| `contacts.email` | `public.citext` | `text collate app.case_insensitive` (unbounded, as today) |
| `custom_field_definitions.field_key` | `public.citext` + `ck_cfd_field_key_len` | `varchar(100) collate app.case_insensitive` |
| `entity_layout_section_fields.field_key` | `public.citext` + `ck_lsf_field_key_len` | `varchar(100) collate app.case_insensitive` |

**Why**

- **The leakproof rule.** On a table with RLS, Postgres lets a condition use an index
  only if its function is *leakproof*, meaning it can't reveal anything about rows the
  policy hasn't approved yet. Text, uuid, int and boolean equality are leakproof;
  `citext_eq` isn't. Reproduced:
  - `email = …` on a citext column ignored the `(tenant_id, email)` index and scanned all
    200,001 rows of both tenants.
  - The same lookup on the collated column was an index scan.
- **Postgres recommends it.** The citext documentation suggests nondeterministic
  collations instead of the module.
- **A bound parameter just works.** `field_key = ?` with a `varchar` parameter compares
  under the column's collation, so it's case-insensitive (reproduced).
  - With citext it compared as plain text, which is why
    `existsByEntityTypeAndFieldKeyIgnoreCase` exists.
  - It becomes `existsByEntityTypeAndFieldKey` (§11), and `upper(field_key) = upper(?)`
    goes away. `upper()` isn't leakproof either.
- **Uniqueness stays case-insensitive.** A unique index on a collated column rejects
  `Lead_Source` next to `lead_source` (reproduced).
- **Less schema.**
  - No extension.
  - `varchar(100)` carries the length cap that citext needed a CHECK for.
  - The login lookup no longer depends on `search_path` (§9.1).
  - Hibernate maps these columns as plain `String`, so the `columnDefinition = "citext"`
    annotations go (§11).

**Caveats**

- **`LIKE`/`ILIKE` on these columns is an error on Postgres 16**: *nondeterministic
  collations are not supported for LIKE* (reproduced). A pattern match names a
  deterministic collation explicitly, e.g. `email collate "default" ilike ?`. Postgres 18
  lifts this (§18).
- **Search can't use indexes under RLS, whatever the column type.** `ILIKE` and the
  trigram operators aren't leakproof. Reproduced: a trigram `ILIKE` under RLS scanned
  both tenants in full, while without RLS the same query used the GIN index. Nothing
  searches yet. When search is built, it needs one of the options in §18.

### 5.7 Table by table

| Table | `tenant_id` | Primary key | Composite FKs (all `(tenant_id, col)` → the target's primary key) | Grants to `blusimian_app` |
|---|---|---|---|---|
| `app.tenants` | is `id` | `(id)` | — | select, insert, update |
| `app.users` | ✓ | `(tenant_id, id)` | — | all four |
| `app.contacts` | ✓ | `(tenant_id, id)` | — | all four |
| `app.companies` | ✓ | `(tenant_id, id)` | `parent_company_id` → companies, `set null (parent_company_id)`; `owner_id`, `account_manager_id`, `created_by_id` → users, `set null (col)`. Closes V1's "FK to users omitted" TODO. | all four |
| `app.company_contacts` | ✓ | `(tenant_id, id)` | `contact_id` → contacts; `company_id` → companies | all four |
| `app.deals` | ✓ | `(tenant_id, id)` | `customer_id` → companies, named `fk_deals_customer_id` | all four |
| `app.custom_field_definitions` | ✓ | `(tenant_id, id)` | — | all four |
| `app.entity_layouts` | ✓ | `(tenant_id, id)` | — | all four |
| `app.entity_layout_sections` | ✓ | `(tenant_id, id)` | `layout_id` → entity_layouts, cascade | all four |
| `app.entity_layout_section_fields` | ✓ | `(tenant_id, id)` | `section_id` → sections, cascade | all four |
| `app.roles` | ✓ | `(tenant_id, id)` | — | all four |
| `app.role_assignments` | ✓ | `(tenant_id, id)` | `user_id` → users, cascade; `role_id` → roles, restrict | all four |
| `app.app_permissions` | ✓ | `(tenant_id, id)` | `role_id` → roles, cascade | all four |
| `app.contact_permissions`, `app.company_permissions`, `app.deal_permissions` | ✓ | `(tenant_id, id)` | `role_id` → roles, cascade | all four |
| `public.iso_countries` | — (global) | `(code)` | — | select only; no RLS |

"All four" means select, insert, update and delete, never more (§5.3).

Children (sections, section fields, company_contacts) carry their own `tenant_id` rather
than inheriting it through a policy that looks up the parent. That keeps one simple
equality policy on every table, and the composite FK guarantees the child's tenant equals
the parent's.

### 5.8 Every way around RLS, and what closes it

RLS is only as strong as the paths that skip it. This is the full list this design knows
of. "Startup" means checked by §8; the app refuses to start when it fails.

| Way around | Why it skips RLS | Closed by | Enforced |
|---|---|---|---|
| Superuser or `BYPASSRLS` role | by definition | `blusimian_app` has neither, directly or through membership (§4) | startup, C1 |
| Owning the table | owners skip RLS unless `FORCE`d. Postgres treats a member of the owning role as the owner (reproduced) | `blusimian_app` owns nothing and belongs to no owning role (§4) | startup, C2 |
| A table without RLS, or a too-wide policy | `using (true)` is a policy too | the exact `tenant_isolation` policy on every table (§5.3) | startup, C3–C4 |
| `TRUNCATE` | ignores RLS (reproduced) | never granted; exactly four privileges (§5.3) | startup, C5 |
| Views | run with the view owner's rights. Reproduced: a plain view returned every tenant's rows | `with (security_invoker = true)` on every view | startup, C6 |
| Materialized views | can't have RLS; refreshed by the owner with every tenant's rows | none in `app` | startup, C6 |
| `SECURITY DEFINER` functions | run as the owner | only `find_login_user`, plus `tenant_ids` once jobs exist (§7.6) | startup, C7 |
| A default for `app.tenant_id` on the role, the database or the JDBC URL | every session starts inside that tenant (reproduced) | none set | startup, C8 |
| Foreign key checks | run as the owner, ignoring RLS (reproduced) | composite FKs (§5.4) | the migration fails without a matching target key |
| Unique constraints | checked across all tenants, so a violation reveals another tenant's value | uniques lead with `tenant_id` (§5.5) | review |
| Hibernate second-level/query cache, `@Cacheable` | answer from memory: no SQL runs, so no policy does | no second-level or query cache on tenant entities; cache keys include the tenant (§7.5) | review |
| Switching tenant inside an open transaction | the setting is applied when the transaction begins | `TenantContext` refuses (§7.2) | runtime |
| Arbitrary SQL as `blusimian_app` | can call `set_config` itself | not closed: see the threat model (§13) | — |

### 5.9 Removed from V1

- **The seed inserts** (`lead_source`, the three default layouts, their sections and
  fields). They can't run anyway: without a tenant the column default raises. They move
  to Java (§10).
- **The `citext` extension** and the two length checks that existed only because of it
  (`ck_cfd_field_key_len`, `ck_lsf_field_key_len`) (§5.6).
- **`idx_custom_field_defs_entity` and `idx_layout_section_fields_section`**, which unique
  constraints now cover (§5.5).
- **The "single-tenant for now"**, "FK to users omitted" and "becomes (tenant_id, …)
  later" comments. The header comment changes to: *public = global reference data; app
  = tenant-scoped, RLS on every table.*

---

## 6. `tenant_id` belongs to the database, not to entities (D5)

CRM entities don't map `tenant_id`. Hibernate's insert omits the column, and the column
default fills it from the transaction's tenant. Reads are filtered by the policy. This
gives you:

- **No code path can write the wrong tenant.** Java never supplies the value.
- **No code path can forget it.** Same reason.
- **JPA, `JdbcClient` and native SQL all get the same treatment.** The advisory lock
  query in `CustomFieldService` and any future report query are covered without anyone
  thinking about it.
- `ddl-auto=validate` only checks mapped columns, so an unmapped `tenant_id` is fine,
  just like the unmapped company address columns today.

The cost: an entity doesn't know its tenant in Java. Nothing needs that today, and code
that needs "the current tenant" asks `TenantContext`. If something ever needs it on an
entity, map it read-only (`insertable = false, updatable = false`), never writable.

The primary key includes `tenant_id`, but the entity's identity stays `@Id id` (§5.3).
Hibernate's identity map is keyed by `id` alone. That's safe because a session only
ever sees one tenant, and ids are random UUIDs.

---

## 7. Application architecture

### 7.1 Packages

```
platform/tenancy/                     no dependency on any slice
  TenantId                            record TenantId(UUID value)
  TenantContext                       ScopedValue holder: current(), require(), callAs(), runAs()
  MissingTenantContextException
  TenantBindingJpaDialect             sets app.tenant_id when a transaction begins
  TenantAwareJpaTransactionManager    installs that dialect
  TenancyConfiguration                the transactionManager bean (mirrors Boot's)
  RowLevelSecurityVerifier            startup checks C1–C8 (§8), a SmartInitializingSingleton
  TenantDirectory                     app.tenant_ids(); added with the first scheduled job (§7.6)

platform/security/                    depends on tenancy
  TenantPrincipal                     interface: tenantId(), userId(): the seam (§9.2)
  TenantUserPrincipal                 UserDetails + TenantPrincipal; what the session holds
  TenantUserDetailsService            UserDetailsService over LoginLookup
  LoginLookup                         JdbcClient call to app.find_login_user
  TenantContextFilter                 principal → TenantContext, once per request
  ApiErrorAuthenticationEntryPoint    401 UNAUTHENTICATED as an ApiError
  SecurityConfiguration               filter chain, password encoder, authentication manager

auth/       AuthController (login, me), dto/LoginRequest, dto/CurrentUserResponse
tenants/    Tenant, TenantRepository, TenantService (current tenant, for /me),
            TenantProvisioningService, NewTenant, TenantInitializer, DemoTenantSeeder
users/      User, UserRepository, UserService, UserController, dto/CreateUserRequest,
            dto/UserResponse, exception/UsernameTakenException
entityLayouts/
  DefaultLayoutTemplates              the old V1 layout seed, as Java
  DefaultLayoutsInitializer           implements TenantInitializer
```

CRM slices depend on `platform/tenancy` only where they need the id: `CustomFieldService`
for its lock key (§7.5), and `entityLayouts` for its initializer. None of them depends on
`platform/security`.

### 7.2 `TenantId` and `TenantContext`

```java
public record TenantId(UUID value) implements Serializable {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
```

A tenant id and a record id are both UUIDs. With its own type, a method that takes a
`TenantId` can't be handed a company id by mistake. It's `Serializable` because the
session stores it inside the principal.

```java
public final class TenantContext {

    private static final ScopedValue<TenantId> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static Optional<TenantId> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static TenantId require() {
        if (!CURRENT.isBound()) {
            throw new MissingTenantContextException();
        }
        return CURRENT.get();
    }

    public static <T, X extends Throwable> T callAs(TenantId tenant,
                                                    ScopedValue.CallableOp<? extends T, X> op) throws X {
        refuseSwitchInsideTransaction(tenant);
        return ScopedValue.where(CURRENT, tenant).call(op);
    }

    public static void runAs(TenantId tenant, Runnable op) {
        refuseSwitchInsideTransaction(tenant);
        ScopedValue.where(CURRENT, tenant).run(op);
    }

    // The tenant reaches Postgres when a transaction begins, so a new scope inside an
    // open transaction would keep reading and writing the old tenant.
    private static void refuseSwitchInsideTransaction(TenantId tenant) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !current().map(tenant::equals).orElse(false)) {
            throw new IllegalStateException("Cannot switch to tenant " + tenant + " inside an open transaction");
        }
    }
}
```

**Why `ScopedValue` and not `ThreadLocal`:**

- **The binding has a lexical scope.** It ends when `call`/`run` returns, including on an
  exception. There's no `finally { remove(); }` to forget, so a pooled Tomcat thread can't
  carry tenant A into the next request, which belongs to tenant B.
- **It's immutable inside the scope.** Nothing can switch tenants halfway through a
  request. The only way is a nested `runAs`, which is explicit and greppable.
- **Other threads don't inherit it.** Work handed to `@Async` or an executor starts with
  no tenant and fails loudly. With a `ThreadLocal` it would silently have no tenant, and
  with an `InheritableThreadLocal` it would silently keep a stale one (§7.6).

It's a static holder for the same reason `SecurityContextHolder` is one: the tenant is a
property of the current call, not of any bean.

**No switching inside a transaction.**

- The tenant is applied when a transaction *begins*. A `runAs(B, …)` inside tenant A's
  transaction would join A's transaction. Java would believe it's working for B while
  Postgres keeps filtering, and filling column defaults, with A, so reads and writes
  would silently go to A.
- So `callAs`/`runAs` throw when a transaction is open and the tenant differs.
  Rebinding the same tenant is allowed.
- Work for several tenants runs one transaction per tenant, each begun inside its own
  scope (§7.6, §10.1).

**Consequence for tests.** A test method can't be `@Transactional`: Spring begins the
test's transaction before the test can bind a tenant, so it fails at begin. Isolation
tests use fresh tenants instead of rollback (§15.1).

### 7.3 Binding the tenant to the transaction

Spring's `HibernateJpaDialect.beginTransaction` is where Spring already prepares the
connection for a new transaction (isolation level, read-only, flush mode). Setting the
tenant is one more step in that preparation:

```java
public class TenantBindingJpaDialect extends HibernateJpaDialect {

    private static final String SET_TENANT = "select set_config('app.tenant_id', ?, true)";

    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
            throws PersistenceException, SQLException, TransactionException {
        TenantId tenant = TenantContext.require();
        Object transactionData = super.beginTransaction(entityManager, definition);
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
                statement.setString(1, tenant.toString());
                statement.execute();
            }
        });
        return transactionData;
    }
}
```

```java
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    // super.afterPropertiesSet() takes the dialect from the EntityManagerFactory,
    // so ours has to be installed after it.
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        setJpaDialect(new TenantBindingJpaDialect());
    }
}
```

```java
@Configuration
class TenancyConfiguration {

    // Mirrors Boot's own bean, which then backs off: the no-arg constructor finds the
    // EntityManagerFactory itself, and Boot's customizers (spring.transaction.*,
    // TransactionExecutionListener beans) still apply.
    @Bean
    JpaTransactionManager transactionManager(ObjectProvider<TransactionManagerCustomizers> customizers) {
        JpaTransactionManager transactionManager = new TenantAwareJpaTransactionManager();
        customizers.ifAvailable(c -> c.customize(transactionManager));
        return transactionManager;
    }
}
```

**Why the dialect.** `JpaTransactionManager.doBegin` calls the dialect inside its own
`try`. If the dialect throws, for a missing tenant or a failed `set_config`, Spring rolls
back and closes the half-begun `EntityManager`. The two alternatives don't get that
cleanup:
- code placed after `super.doBegin()` in an override, and
- a `TransactionExecutionListener.afterBegin`.

Both run after `doBegin` has returned, so a failure there would leave the transaction
open and the connection bound (confirmed in spring-orm and spring-tx 7.0.9).

**What a missing tenant looks like.** `doBegin` wraps every exception that isn't a
`TransactionException`. A missing tenant therefore surfaces as
`CannotCreateTransactionException` (*Could not open JPA EntityManager for transaction*),
with `MissingTenantContextException` as its cause. Logs and tests look at the cause.

**Why transaction-local (`set_config(…, true)`):**

- Postgres reverts the setting at `COMMIT`/`ROLLBACK`. A connection returned to Hikari
  needs no cleanup and cannot leak a tenant, even if the app dies mid-request.
- It still works if PgBouncer in transaction-pooling mode is added later. A session-level
  `SET` would not.
- The value goes in as a bind parameter. `SET LOCAL` can't take one, so it would need
  string concatenation.
- It costs one extra statement per transaction (~0.1 ms on localhost).

**`JdbcClient` uses the same connection.** Inside a JPA transaction Spring binds the
Hibernate connection for the `DataSource`, so the advisory lock in `CustomFieldService`
already runs on the transaction's connection. It sees the tenant with no change.

**Rules**

- **Every database access goes through a Spring transaction.** Service methods that
  touch tenant tables are `@Transactional`.
  - Spring Data only gives a transaction to the methods inherited from `JpaRepository`.
    **Declared query methods** (`findByCompanyId`, `findByEntityType…`) get none of their
    own (confirmed in spring-data-commons 4.1.1).
  - Called from a non-transactional service, such a method runs in autocommit, without a
    tenant, and the database raises.
  - Today's gaps: `CompanyContactService.list` / `get`; `CustomFieldService.listActive` /
    `listAll` (`CustomFieldController.list` calls them directly); and
    `CountryService.list`, which reads a global table and would work, but gets annotated
    for consistency.
- **Never `SET app.tenant_id` or `set_config(…, false)`.** A session-level value would
  outlive the transaction and travel with the pooled connection.
- **Database work after a commit uses `REQUIRES_NEW`.** This covers
  `@TransactionalEventListener(AFTER_COMMIT)` and `afterCommit` callbacks. After `COMMIT`
  the setting is gone, so the old connection has no tenant.

### 7.4 `TenantContextFilter`

```java
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.callAs(principal.tenantId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);   // unreachable: doFilter throws nothing else
        }
    }
}
```

- It's added to the Spring Security chain right after `AuthorizationFilter`, so only
  authenticated, authorized requests get a tenant. `SecurityConfiguration` constructs it
  directly; it's **not** a `@Component`. As a component, Boot would also register it as a
  plain servlet filter, and it would run twice.
- **The tenant comes only from the principal.** No `X-Tenant-Id` header, no
  `/tenants/{id}/…` paths, no `tenantId` in request bodies. A client has nothing to tamper
  with, and no endpoint needs its own "does this tenant id belong to you" check.

### 7.5 Code that needs the tenant id explicitly

There are only a few such places:

- **`CustomFieldService.lockSlotAllocation`.** Advisory locks are database-wide; RLS
  doesn't scope them. Keyed by entity type alone, the lock would serialise slot allocation
  across *all* tenants, which is correct but needless contention. The key becomes
  `(TenantContext.require() + ":" + entityType.name()).hashCode()`. A hash collision only
  serialises two unrelated admin actions, which is harmless. (`WIP/custom-objects-architecture.md`
  §11 already anticipates this.)
- **`TenantProvisioningService`** binds the new tenant's id (§10.1).
- **`GET /api/auth/me`** reads the tenant row by `TenantContext.require()`
  (`TenantService.current()`).
- **Caches.** A cache answers without running SQL, so no policy applies.
  - Hibernate's second-level and query caches stay off for tenant entities. They would
    serve `findById` from memory to any tenant holding the id.
  - A Spring `@Cacheable` key must include `TenantContext.require()`.
  - There are none today. The schema bundle's `Cache-Control: no-cache, private` with a
    content ETag is already correct per caller (`entity-layouts.md` §5.5).

### 7.6 Work outside a request

**Background jobs.** The app role sees only its own tenant row, so a scheduled job can't
list tenants. The first job adds a second definer function, as narrow as the login
lookup, and `TenantDirectory` to call it:

```sql
create function app.tenant_ids()
    returns setof uuid
    language sql
    stable
    security definer
    set search_path = pg_catalog
as
$$
select id
from app.tenants
order by id
$$;

revoke execute on function app.tenant_ids() from public;
grant execute on function app.tenant_ids() to blusimian_app;
```

- The job loops over tenants and calls `TenantContext.runAs(tenant, work)`. `work`
  calls a `@Transactional` service method, so each tenant gets its own transaction.
- The job method itself is **not** `@Transactional`. A transaction around the loop would
  begin without a tenant and fail.
- `tenant_ids` joins the definer allowlist of startup check C7.

**Other threads.** A scoped value isn't inherited, so `@Async` and executor tasks start
without a tenant and fail loudly. To hand tenant work to another thread, the task binds
the tenant itself: capture `TenantContext.require()` before submitting, and run the task
inside `TenantContext.runAs(tenant, …)`. Once there's any `@Async`, a `TaskDecorator`
can do that for every task.

---

## 8. Startup verification — `RowLevelSecurityVerifier`

RLS fails **open** in the likeliest configuration mistakes:
- a table without `enable row level security`, or a too-wide policy;
- a runtime role that can bypass RLS;
- a definer-rights view.

None of these shows up in normal use, because the app keeps working; it just sees too
much. The verifier turns each one into a boot failure.

**When it runs.**
- It's a `SmartInitializingSingleton`. Spring calls it once every singleton exists, so
  Flyway has migrated, and before Boot starts Tomcat. The web server is a
  `SmartLifecycle`, started at the end of the context refresh.
- An `ApplicationRunner` would run after that, with the port already open (checked in
  Boot 4.1.1).
- It runs as `blusimian_app` on a pooled connection and reads only the catalog, in one
  short transaction that it rolls back. The transaction exists only to pin `search_path`
  to `pg_catalog` (`set_config(…, true)`) for C4. Any failed check throws, and the app
  doesn't start.

```sql
-- C1. No superuser or BYPASSRLS role among the app role and the roles it belongs to.
select r.rolname
from pg_roles r
where (r.rolsuper or r.rolbypassrls)
  and pg_has_role(current_user, r.oid, 'MEMBER');

-- C2. Nothing in app is owned by the app role or a role it belongs to
--     (a member of the owning role counts as the owner).
select c.relname
from pg_class c
         join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'app'
  and pg_has_role(current_user, c.relowner, 'MEMBER');

-- C3. Every table: RLS enabled, and a tenant_id column.
select c.relname,
       c.relrowsecurity as rls_enabled,
       exists (select 1
               from pg_attribute a
               where a.attrelid = c.oid
                 and a.attname = 'tenant_id'
                 and not a.attisdropped) as has_tenant_id
from pg_class c
         join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'app'
  and c.relkind in ('r', 'p')
  and c.relname <> 'flyway_schema_history';

-- C4. Every policy, as Postgres prints it.
select tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'app';

-- C5. No privilege beyond DML.
select c.relname, p.privilege
from pg_class c
         join pg_namespace n on n.oid = c.relnamespace
         cross join unnest(array ['TRUNCATE', 'REFERENCES', 'TRIGGER']) as p(privilege)
where n.nspname = 'app'
  and c.relkind in ('r', 'p')
  and has_table_privilege(current_user, c.oid, p.privilege);

-- C6. No definer-rights views, no materialized views.
select c.relname, c.relkind
from pg_class c
         join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'app'
  and (c.relkind = 'm'
    or (c.relkind = 'v' and not exists (select 1
                                         from pg_options_to_table(c.reloptions) o
                                         where o.option_name = 'security_invoker'
                                           and o.option_value::boolean)));

-- C7. SECURITY DEFINER functions, compared against the allowlist.
select p.proname
from pg_proc p
         join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'app'
  and p.prosecdef;

-- C8. The connection carries no tenant of its own.
select coalesce(current_setting('app.tenant_id', true), '') as tenant_setting;
```

| Check | Passes when |
|---|---|
| C1, C2, C5, C6 | no rows |
| C3 | every row has `rls_enabled`, and `has_tenant_id` except `tenants` |
| C4 | Each table from C3 has exactly one `PERMISSIVE` policy: `tenant_isolation`, `cmd = ALL`, `roles` = the connected role (`{blusimian_app}`), and `qual` = `with_check` = `(tenant_id = ( SELECT app.current_tenant_id() AS current_tenant_id))` (for `tenants`: `(id = ( SELECT app.current_tenant_id() AS current_tenant_id))`). Every other policy is `RESTRICTIVE`. |
| C7 | the names are within `{find_login_user}`, plus `tenant_ids` once it exists |
| C8 | `''` |

- **C4 compares the printed policy text exactly**, as Postgres 16 prints it
  (reproduced). If a future major version prints it differently, the check fails at
  startup, which is the safe direction, and the constant gets updated.
- **Why `search_path` is pinned.** Postgres prints a function schema-qualified only when
  it isn't on the `search_path`. Under the app's `currentSchema=app,public` the policy
  prints as `( SELECT current_tenant_id() AS current_tenant_id)` (verified), so C4 would
  fail on a correct policy.
- **C8 reads the connection's own value.** Nothing in the verifier's transaction sets
  `app.tenant_id`, so on any pooled connection it reads `''` unless a role-level,
  database-level or URL default exists.
- **The rules are a pure function over the query results**, so each is unit-tested
  without a database (§15.1).
- **Every query was run** against a schema with one planted mistake per check. Each
  flagged its mistake and passed the good tables (§15.3), and again through the verifier
  itself, as built (§15.4).

---

## 9. Minimal authentication

### 9.1 Login lookup — the one read before a tenant is known

At login the tenant isn't known yet, so the `users` policy hides every row. Exactly one
function crosses that line:

```sql
create function app.find_login_user(p_username varchar)
    returns table
            (
                user_id       uuid,
                tenant_id     uuid,
                username      varchar,
                password_hash text,
                display_name  varchar,
                enabled       boolean
            )
    language sql
    stable
    security definer
    set search_path = pg_catalog
as
$$
select u.id, u.tenant_id, u.username, u.password_hash, u.display_name, u.enabled
from app.users u
where u.username = p_username collate app.case_insensitive
$$;

revoke execute on function app.find_login_user(varchar) from public;
grant execute on function app.find_login_user(varchar) to blusimian_app;
```

- **`security definer`** runs the function as `blusimian_owner`. The owner owns
  `app.users` and RLS isn't `FORCE`d, so the policy doesn't apply inside it.
- **Narrow by design.** It looks up one row by exact username. It can't list users, can't
  write, and its parameter is bound, never concatenated.
- **`set search_path = pg_catalog`** is required hygiene for definer functions. Every name
  in the body is schema-qualified, including the collation, so nothing resolves through
  `search_path`.
- **The explicit `COLLATE`** makes the comparison case-insensitive regardless of how the
  argument's collation is derived, and it uses `uq_users_username`. Reproduced: one index
  lookup, and `'ALICE'` finds `'Alice'`.
- **For contrast:** with `citext`, the `=` operator lived in `public`. With only
  `pg_catalog` on the path, it silently fell back to case-sensitive text equality
  (reproduced).
- `LoginLookup` calls it through `JdbcClient`, **outside a transaction**. A transaction
  would require a tenant, and there isn't one yet. If someone later adds `@Transactional`
  there, it fails loudly (`MissingTenantContextException`), not silently.

### 9.2 The principal — the seam

```java
public interface TenantPrincipal {
    TenantId tenantId();
    UUID userId();
}
```

```java
public final class TenantUserPrincipal implements UserDetails, CredentialsContainer, TenantPrincipal {

    private final UUID userId;
    private final TenantId tenantId;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private String passwordHash;          // erased after authentication

    // constructor, accessors …

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();                 // roles: later
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;              // ProviderManager calls this; the session never stores the hash
    }
}
```

Tenancy reads `TenantPrincipal` and nothing else. When this auth is replaced, whether by
OIDC, JWT or an external IdP, the new mechanism only has to produce an `Authentication`
whose principal implements `TenantPrincipal`, for example by mapping the token's subject
to a `users` row. `TenantContextFilter` and everything below it stay unchanged.

### 9.3 Spring Security configuration

New dependencies: `spring-boot-starter-security`, and `spring-boot-starter-security-test`
(test scope).

```java
@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new ApiErrorAuthenticationEntryPoint(jsonMapper)))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable())                 // §9.5
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterAfter(new TenantContextFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // {bcrypt} today, upgradable
    }

    @Bean
    AuthenticationManager authenticationManager(TenantUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
```

- `ApiErrorAuthenticationEntryPoint` writes the usual `ApiError` JSON with
  `ErrorCode.UNAUTHENTICATED`, through Boot's `JsonMapper`, so a 401 looks like every
  other API error. It replaces Spring's empty body and its redirect to a login page.
- **Logout is `POST` only.** With CSRF off, `logoutUrl` alone would also log out on
  `GET`, `PUT` and `DELETE`; the explicit matcher keeps the endpoint table true.
- **No request cache.** A JSON API has no page to return to after login, so a 401
  doesn't need to save the request, and an anonymous request opens no session.
- `DaoAuthenticationProvider` already reports an unknown username as bad credentials and
  does a dummy hash for unknown users, so response timing doesn't reveal which usernames
  exist.
- **Session:** Tomcat's in-memory `HttpSession`. Cookie settings (all exist in Boot
  4.1.1):

  ```properties
  server.servlet.session.cookie.same-site=lax
  server.servlet.session.cookie.http-only=true
  # Dev: survive devtools restarts instead of logging everyone out.
  server.servlet.session.persistent=true
  ```

### 9.4 Endpoints

| Method and path | Auth | Body | Response |
|---|---|---|---|
| `POST /api/auth/login` | public | `{ "username", "password" }` | 204 + session cookie. 401 `INVALID_CREDENTIALS` for an unknown user, wrong password or disabled user alike. |
| `POST /api/auth/logout` | session | — | 204, session invalidated |
| `GET /api/auth/me` | session | — | 200 `CurrentUserResponse`, or 401 |
| `GET /api/users` | session | — | users of the caller's tenant (never the hash) |
| `POST /api/users` | session | `{ "username", "displayName", "password" }` | 201, created in the caller's tenant. 409 `CONFLICT` if the username is taken anywhere, in any case. |

```jsonc
// GET /api/auth/me
{
  "userId": "…",
  "username": "alice",
  "displayName": "Alice Example",
  "tenant": { "id": "10000000-0000-0000-0000-000000000001", "name": "Acme" }
}
```

**Login** (in `AuthController`):
1. Authenticate through the `AuthenticationManager`.
2. Put the result in a new `SecurityContext` and store it with
   `HttpSessionSecurityContextRepository`.
3. Rotate the session id (`ChangeSessionIdAuthenticationStrategy`), so a pre-login
   session id doesn't survive into the authenticated session.

It returns 204 rather than the user. The frontend then loads `/me`, so
`TenantContextFilter` stays the **only** place that binds a tenant for HTTP requests.

**`POST /api/users`** relies on `uq_users_username`, which is case-insensitive through
the collation (§5.6). An existence check under RLS only sees the caller's own tenant, so
it can't detect a name taken elsewhere. The violation is translated by constraint name
into `UsernameTakenException`, the same pattern `DealService` uses.

`AuthenticationException` maps to 401 in `GlobalExceptionHandler`. New `ErrorCode`
values: `UNAUTHENTICATED`, `INVALID_CREDENTIALS`.

### 9.5 Deferred to the secure-auth work

- **CSRF tokens.** They're off for now. Until then, two things cover the dev phase.
  `SameSite=Lax` keeps the cookie off cross-site `POST`/`PUT`/`PATCH`/`DELETE`. And the
  API only accepts `application/json`: a cross-site HTML form can't send that, and a
  cross-site `fetch` with it needs a CORS preflight, which nothing allows. When this work
  happens, turn on Spring Security's SPA CSRF support (cookie token plus an
  `X-XSRF-TOKEN` header from `api.ts`).
- Login throttling and lockout, password policy, password change and reset, MFA.
- Re-checking `enabled` on each request. Today a disabled user keeps their session until
  it expires.
- A shared session store (Spring Session JDBC or Redis) for more than one instance.
- Swapping in OIDC/JWT. Only §9.1–§9.4 change; the `TenantPrincipal` seam stays.

---

## 10. Tenant provisioning

### 10.1 `TenantProvisioningService`

```java
public record NewTenant(TenantId id, String name,
                        String adminUsername, String adminDisplayName, String adminPassword) {
}

public interface TenantInitializer {
    /** Called once per new tenant, inside the provisioning transaction, with the tenant bound. */
    void initialize(TenantId tenant, UUID firstUserId);
}
```

```java
@Service
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final UserService users;
    private final List<TenantInitializer> initializers;   // in @Order order
    private final TransactionTemplate transaction;

    // constructor …

    public TenantId provision(NewTenant request) {
        return TenantContext.callAs(request.id(), () -> transaction.execute(status -> {
            tenants.save(Tenant.create(request.id(), request.name()));
            User first = users.create(request.adminUsername(), request.adminDisplayName(), request.adminPassword());
            initializers.forEach(initializer -> initializer.initialize(request.id(), first.getId()));
            return request.id();
        }));
    }
}
```

- **It runs as the new tenant.** The `tenants` policy allows the insert because
  `id = current tenant`. The user's `tenant_id` comes from the column default. The
  initializers write under the same tenant. Nothing bypasses RLS.
- **`TransactionTemplate`, not `@Transactional`.** The tenant is applied when the
  transaction begins, so the transaction has to begin *inside* the scope that binds the
  new id. On `provision()`, `@Transactional` would begin the transaction before the method
  body could bind anything, and `require()` would throw.
- **It can't join a caller's transaction.** `callAs` refuses to switch tenants inside an
  open transaction (§7.2). Calling `provision()` from transactional code therefore fails
  loudly, rather than running the new tenant's work under the caller's tenant.
- **All or nothing.** If an initializer fails, the tenant and its user roll back with it.
- **`RolesInitializer` needs the first user:** it creates Administrator and Standard user
  and makes that user an Administrator (`roles-and-permissions.md` §3.6).
  `DefaultLayoutsInitializer` ignores the argument.
- `Tenant` has an assigned id, so it implements `Persistable<UUID>` (`isNew()` is true
  until it's persisted). Otherwise `save()` would `merge`, which starts with a `select`.

### 10.2 Default layouts become an initializer

V1's layout seed (one default layout per entity type, with sections and placed standard
fields) becomes `DefaultLayoutTemplates`, a set of Java constants.

- `DefaultLayoutsInitializer` is a `TenantInitializer` in `entityLayouts`. It builds each
  template through the **same validation the editor uses** (`EntityLayoutValidator`
  against the `EntityFieldDictionary`) and stores it as that entity's default.
- This needs a `createDefault` path next to `create`, because `create` only makes
  non-default layouts.
- A template that names a field that no longer exists then fails provisioning, and the
  provisioning test, rather than producing a broken layout.
- `lead_source` drops out of the template. It's a custom field, and a new tenant has
  none.
- The fixed `a0000000-…`/`b0000000-…` UUIDs go away; each tenant's layouts get random ids.
  The only place that mentions one is `EntitySchemaServiceTest`, where it's an arbitrary
  constant on a mocked DTO, so nothing breaks.

### 10.3 Demo tenants (dev)

`DemoTenantSeeder` is an `ApplicationRunner` (it doesn't matter that the port is already
open), enabled by `blusimian.demo-tenants.enabled=true`. It provisions two tenants with
fixed ids if they're missing:

| Tenant | Fixed id | Login | Extras |
|---|---|---|---|
| Acme | `10000000-0000-0000-0000-000000000001` | `alice` / `alice` | `lead_source` custom field on COMPANY, placed in the default company layout |
| Globex | `10000000-0000-0000-0000-000000000002` | `bob` / `bob` | none |

"Missing" is checked under RLS: `TenantContext.callAs(id, () -> tenants.existsById(id.value()))`.
A tenant can always see its own row. Acme's extras run as Acme in a second transaction
after provisioning, through `CustomFieldService.create` and `EntityLayoutService.replace`:
*Lead Source* is appended to the default company layout's first section, Overview.

Two tenants with different custom fields make isolation visible by hand. Log in as alice
and create a company. Log in as bob: the company isn't there, and bob's company form has
no *Lead Source*.

---

## 11. Changes to existing code

| Where | Change |
|---|---|
| `V1__init.sql` | §5 throughout; seed removed; header comment rewritten |
| `application.properties` | datasource → `blusimian_app`; Flyway → `blusimian_owner`; session cookie; `blusimian.demo-tenants.enabled` |
| `docker-compose.yml`, new `docker/initdb/01-roles.sql` | roles (§4) |
| `Makefile` | `db-reset` recreates `public` with `authorization pg_database_owner` |
| `pom.xml` | `spring-boot-starter-security`, `spring-boot-starter-security-test` |
| `Contact`, `CustomFieldDefinition`, `EntityLayoutSectionField` | drop `columnDefinition = "citext"`: plain `String` on a collated column (§5.6); their citext comments go |
| `CustomFieldDefinitionRepository` | `existsByEntityTypeAndFieldKeyIgnoreCase` → `existsByEntityTypeAndFieldKey`: the collation makes `=` case-insensitive, and `upper()` couldn't use an index under RLS. Its comment is rewritten; the `CustomFieldService` call and the `CustomFieldSlotAllocationTest` mock follow. |
| `CustomFieldService` | advisory-lock key includes the tenant; `@Transactional(readOnly = true)` on `listActive` / `listAll` |
| `CompanyContactService` | `@Transactional(readOnly = true)` on `list` / `get` |
| `CountryService` | `@Transactional(readOnly = true)` on `list` (consistency) |
| `EntityLayoutService` | `createDefault` for the initializer (§10.2) |
| `GlobalExceptionHandler`, `ErrorCode` | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`; `AuthenticationException` → 401 |
| Comments that mention citext | `EntityLayoutValidator`, `EntitySchemaService`, `ContactStandardFieldProvider`, `EntitySchemaServiceTest` |
| Other entities and repositories, `RecordMapper`, `DealService`, controllers | **none.** `DealService` relies on the FK keeping `customer_id` in its name (§5.4). |
| Entity/V1 comments that say "single-tenant for now" | removed |
| `README.md`, `Architecture/custom-fields.md`, `Architecture/entity-layouts.md`, `frontend-entity-pages.md`, `settings.md` | the tenancy, citext and sign-in lines |

---

## 12. Frontend (kept small)

- **`features/auth/LoginPage.tsx`** at `/login`, outside the app layout: username,
  password, submit. It posts to `/api/auth/login`; on 204 it runs `queryClient.clear()`
  and navigates to `/`.
- **`features/auth/auth.ts`**: `useCurrentUser`, `useLogin`, `useLogout`. Login and logout
  clear the query cache in their `onSuccess`.
- **The auth gate** is `routes/AppLayout.tsx`: `useQuery(["auth", "me"])`, without retry. A
  401 goes to `/login`. The sidebar footer shows the user, the tenant name and a *Log out*
  button (`POST /api/auth/logout`, then `queryClient.clear()`, then `/login`).
- **`lib/api.ts` needs no change.** A same-origin `fetch` sends the session cookie already,
  and the Vite proxy keeps everything same-origin.
- **Why `queryClient.clear()` matters for isolation.** TanStack Query's cache is keyed by
  query key, not by user. Without clearing it, if bob logs in in a tab where alice was,
  Acme's cached `["companies"]` renders until it refetches. The server is isolated, but
  the client cache isn't unless it's cleared on every change of identity.

---

## 13. Threat model

**Protects against**

- **Application bugs**: a forgotten `where`, a new repository method, a native query, a
  `JdbcClient` call, a report. All of them are filtered.
- **IDOR**: using another tenant's record UUID returns 404, and that UUID can't be
  referenced either (§5.4).
- **Tenant leakage through the connection pool**: the setting is transaction-local (§7.3).
- **Leakage through threads, or a tenant switch mid-transaction**: a scoped value isn't
  inherited, and switching inside an open transaction is refused (§7.2).
- **The known ways around RLS**: owner or member of the owner, definer views and
  functions, `TRUNCATE`, a default tenant on the role, caches (§5.8). The ones Postgres
  can report are checked at startup (§8).

**Does not protect against**

- **Arbitrary SQL as `blusimian_app`** (SQL injection). The attacker can call
  `set_config('app.tenant_id', …)` with any tenant. The setting is something the app is
  trusted to set, not a credential. The defence there is never building SQL by string
  concatenation, which JPA and `JdbcClient` parameters already ensure. Stronger options,
  such as a signed setting checked inside the policy or per-tenant DB roles, are out of
  scope.
- **A compromised app server.** It holds every session.
- **Owner or superuser sessions** (psql, migrations). Those are trusted by design.
- **Side channels.** Global username uniqueness reveals that a username exists somewhere
  (§9.4), as on any email-login SaaS. Row estimates in `EXPLAIN` and `pg_class.reltuples`
  are table-wide, so they hint at the total row count across tenants.
- **Noisy neighbours.** One tenant's load can slow the others. That's a capacity concern,
  not a confidentiality one.

---

## 14. Considered and left out

| Option | Why not |
|---|---|
| Schema-per-tenant or database-per-tenant | Migrations × N, a connection pool per tenant, catalog bloat (the 105-column slot pool × 3 tables × N). RLS was the given direction anyway. |
| Hibernate `@TenantId` (discriminator) instead of RLS | Enforced in Hibernate only. `JdbcClient`, native queries and psql are unprotected. |
| Hibernate `@TenantId` **and** RLS | Two sources of truth for one rule, and `tenant_id` mapped on every entity. It could be added later as an extra net; it isn't needed. |
| Session-level `SET` when a connection is checked out (`DataSource` wrapper, or Hibernate's `MultiTenantConnectionProvider`) | Leaks if the reset is ever missed, and breaks under PgBouncer transaction pooling. `MultiTenantConnectionProvider` also doesn't cover `JdbcClient` outside Hibernate. |
| Setting the tenant in a `doBegin` override or a `TransactionExecutionListener` | Both run after `doBegin` has returned, so a failure isn't cleaned up and the transaction stays open (§7.3). |
| Policy returns no rows when the context is missing | Fails closed, but silently: a missing context looks like an empty tenant. |
| `FORCE ROW LEVEL SECURITY` | It would apply RLS to the owner too, which breaks `find_login_user` and makes every migration or backfill need a tenant. The app never connects as the owner (checked at boot), so it adds nothing on the app's path. |
| `primary key (id)` plus `unique (tenant_id, id)` | Two indexes per table doing one job. `(tenant_id, id)` as the primary key does both (§5.3). |
| `citext` | Its equality isn't leakproof, so under RLS it can't use an index. Reproduced: a full scan across both tenants. Postgres's own docs point to nondeterministic collations (§5.6). |
| Marking `citext_eq` (or search operators) `LEAKPROOF` | Superuser-only, which most managed Postgres doesn't offer, and it's an assertion about code we don't own. It stays an option for search (§18). |
| The startup check as an `ApplicationRunner` | Runs after Tomcat is already accepting requests (§8). |
| `ThreadLocal` tenant holder | Must be cleared in `finally`. When that's forgotten it leaks across pooled threads, and child threads either lose it silently or inherit it. |
| Tenant from a header, URL or body | Client-controlled; every endpoint would need its own check against IDOR. |
| Tenant slug + username at login | An extra field for users, and it still needs a lookup before the tenant is known. It adds nothing until users span tenants. |
| Child tables without `tenant_id`, policy via `exists` on the parent | A subquery per row, a different policy on each table, and FK checks still ignore it. |
| JWT instead of a session | More moving parts (signing keys, refresh, revocation) with no isolation benefit now. That's for the secure-auth work. |

---

## 15. Verification

### 15.1 Integration tests — real Postgres, as `blusimian_app`

Tests **must** connect as `blusimian_app`. A test that connects as `postgres` passes every
isolation check vacuously, because superusers bypass RLS. The startup verifier makes such
a test context fail to start, and that's the point.

`TenantIsolationTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`, against the dev database
like `BlusimianApplicationTests`) provisions **two fresh tenants for every test**, with
random `TenantId`s, through `TenantProvisioningService`. Their admins are named
`isolation-<tenant id>`, because usernames are global. It doesn't depend on demo data or on
test order. Test methods aren't `@Transactional` (§7.2): fresh tenants replace rollback,
and they stay in the dev database until `make db-reset`.

| # | Case | Expect |
|---|---|---|
| 1 | A creates a company. As B: list, get, update, delete it. | Not in B's list; get/update/delete → `CompanyNotFoundException`. The row is still there for A. |
| 2 | As B, raw `JdbcClient` insert into `app.companies` with `tenant_id` = A | RLS violation |
| 3 | As B, raw update of a B row to `tenant_id` = A | RLS violation |
| 4 | As B, create a deal with `customerId` = A's company | `InvalidDealCustomerException` (400) |
| 5 | As B, link B's contact to A's company | `CompanyNotFoundException` (404) |
| 6 | A and B both create custom field `region` (TEXT) | Both succeed, both get `cf_text_01` |
| 7 | A creates `Region`, then `region` | `DuplicateCustomFieldKeyException`: plain equality, case-insensitive through the collation |
| 8 | `setDefault` on a layout in A | B's default is unchanged |
| 9 | Repository call with no tenant bound | `CannotCreateTransactionException` caused by `MissingTenantContextException`, not an empty list |
| 10 | Inside a transaction as A: `runAs(B, …)`, then `runAs(A, …)` | `IllegalStateException`; then it runs |
| 11 | Pool size 1: transaction as A, then a raw query with no transaction (`PooledConnectionTenantTest`, its own context) | Raises "tenant context not set", not A's rows |
| 12 | Inside A's scope, submit a repository call to an executor | Fails on the worker thread with `MissingTenantContextException` as the cause |
| 13 | `LoginLookup.find` (`find_login_user`) with A's admin username upper-cased, no tenant bound | Finds that user, of tenant A |
| 14 | MockMvc: log in as A's admin; `GET /api/companies`, `GET /api/users` | Only A's rows |
| 15 | MockMvc: `GET /api/companies` without a session | 401 with an `ApiError` body |

Also in `TenantIsolationTest`: a username taken in another tenant, in another case, is a
`UsernameTakenException` (409); a wrong password and an unknown user get the same 401
`INVALID_CREDENTIALS`; logout ends the session. Plus plain unit tests for the verifier's
rules (§8), `RowLevelSecurityVerifierTest`: one passing and one failing input per check,
C1–C8.

### 15.2 By hand

```
make db-shell
  set role blusimian_app;
  select * from app.companies;                          -- ERROR: tenant context not set
  begin;
  select set_config('app.tenant_id', '10000000-0000-0000-0000-000000000001', true);
  select count(*) from app.companies;                   -- Acme's only
  commit;
  select * from app.companies;                          -- ERROR again: the setting reverted
```

In the browser: log in as `alice`, create a company and look at the company form (it has
*Lead Source*). Log out, log in as `bob`: no company and no *Lead Source*. Then log back
in as `alice` and check that nothing of bob's flashes from the cache.

### 15.3 Already reproduced (Postgres 16.14, 2026-10-28)

These ran in a throwaway container: two tenants, ~100,000 companies each, the policy and
function from §5. They're the facts the design rests on, not things the integration tests
need to rediscover.

| Claim | Observed |
|---|---|
| FK checks ignore RLS | Plain FK: tenant B inserted a deal referencing A's company. Composite FK: rejected. |
| `on delete set null (customer_id)` | `customer_id` nulled, `tenant_id` kept |
| Missing tenant | Error on a non-empty table and on insert; an empty table returns no rows and no error |
| Setting after commit | `''`, not `NULL` |
| `with check` | Under B, inserting with A's id and moving a row to A were both rejected |
| A member of the owning role is the owner | After `grant owner to app`: all 200,001 rows, no tenant set, no error |
| Definer-rights view | Under B: 200,001 rows. With `security_invoker`: B's 100,000. |
| `TRUNCATE` | B emptied A's rows |
| Role-level default | A fresh session saw A's rows without any `set_config` |
| `parallel safe` | Unsafe: no parallel plan. Safe: a parallel aggregate, correct count. |
| `citext` under RLS | `email = …` scanned all 200,001 rows despite the `(tenant_id, email)` index |
| Case-insensitive collation | Index scan under RLS; `'ALICE'` finds `'Alice'`; a case-variant duplicate rejected; a bound `varchar` parameter matches |
| `LIKE` on that collation | Error on 16 |
| Trigram `ILIKE` | Under RLS: full scan of both tenants. Without RLS: the GIN index. |
| PK `(tenant_id, id)` | `findById` under RLS is one primary-key lookup on both columns |
| Definer lookup, `search_path` | citext with only `pg_catalog`: case-sensitive (0 rows instead of 1). Collation with explicit `COLLATE`: 1 row, via the unique index. |
| Startup checks C1–C8 | Each flagged its planted mistake and passed the good tables |

### 15.4 Verified as built (2026-10-28)

| What | Result |
|---|---|
| `./mvnw test` | 66 tests pass: the 15 isolation tests and the 3 auth tests above, the 8 verifier rule tests, and the existing suite |
| The verifier against the dev database, with one mistake planted per check: `BYPASSRLS` on the app role, a table owned by it, RLS disabled, a second permissive policy, a `TRUNCATE` grant, a definer view, an unlisted definer function, a role-level `app.tenant_id` | The app refused to start, listing all eight; with them reverted it started |
| Over HTTP (curl, real Tomcat) | No session: 401 `ApiError`, and no session cookie. `ALICE` logs in; the cookie is `HttpOnly; SameSite=Lax`; `/me` is Acme; bob sees neither alice's company nor *Lead Source*; logout is `POST` only |
| In the browser (§15.2) | alice → bob → alice with client-side navigation: nothing of the previous user's data or schema shows |
| Frontend | `tsc` clean; 20 vitest tests pass |

---

## 16. Setting up a dev database

- **A new volume:** `docker compose up -d`. `docker/initdb/01-roles.sql` creates the two
  roles and hands the database to `blusimian_owner`. The backend's first start runs V1 as
  the owner and provisions the demo tenants (§10.3).
- **A volume from before the roles:** `docker compose down -v` once. Init scripts only
  run on an empty volume.
- **`make db-reset`** drops both schemas and keeps the roles: the everyday reset.

---

## 17. Recipe: adding a tenant-scoped table (after this lands)

1. `tenant_id uuid not null default app.current_tenant_id() references app.tenants (id)`
   as the second column.
2. `constraint pk_<table> primary key (tenant_id, id)`.
3. References to other tenant tables are composite FKs to their primary key:
   `foreign key (tenant_id, x_id) references app.x (tenant_id, id)`. Use
   `on delete set null (x_id)`, never plain `set null`.
4. Every unique and index leads with `tenant_id`. Case-insensitive text is
   `collate app.case_insensitive`, never `citext`.
5. `enable row level security`, the `tenant_isolation` policy, and a grant of exactly
   `select, insert, update, delete` to `blusimian_app`.
6. A view gets `with (security_invoker = true)`. No materialized views in `app`.
7. Don't map `tenant_id` in the entity, and don't put the entity in a Hibernate
   second-level cache.
8. Service methods that touch it are `@Transactional`.

The startup check catches a missing step 5 or 6, and a missing `tenant_id`. Integration
tests catch a missing step 8. Steps 2–4 and 7 are review items.

---

## 18. Open questions

1. **Self-service signup** (`POST /api/signup` → tenant + admin user)? The provisioning
   service is ready for it; it needs abuse protection first.
2. **Users in several tenants.** Split `users` (global identity) from
   `memberships (tenant_id, user_id, role)` before real customers arrive? Changing it later
   means a data migration. Recommendation: not now. A globally unique username already
   acts as a global identity, so the split stays open.
3. **Roles within a tenant.** Answered: `roles-and-permissions.md`. Enforcing them is still
   to come, and layouts follow job titles rather than roles (`WIP/job-title-layouts.md`).
4. **Tenant offboarding**: hard delete with cascades, a soft `status`, or export first.
5. **Testcontainers** for integration tests (a fresh database per run, CI-friendly). The
   container needs the roles init script, and the tests need app-role credentials, not the
   superuser `@ServiceConnection` hands out by default.
6. **Support access across tenants**: a separate role with `BYPASSRLS` and its own audit
   trail, never the app role.
7. **Search under RLS** (§5.6). No pattern or trigram operator is leakproof, so under RLS
   search can't use an index. Two ways out, to choose when search is built:
   - a narrow `SECURITY DEFINER` search function that adds
     `tenant_id = app.current_tenant_id()` itself, joining the C7 allowlist;
   - a superuser marking the specific operators `LEAKPROOF`, which most managed Postgres
     doesn't allow.
8. **Postgres 18 as the baseline?** It lifts two limits met above: `LIKE` on
   nondeterministic collations (§5.6), and lookups by `id` alone on a `(tenant_id, id)` key
   (skip scan, §5.3). The `postgres:18` image is already pulled locally.
