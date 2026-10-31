# Custom Fields — Architecture Reference

**Status:** Implemented & verified end-to-end · **Scope:** `COMPANY`, `CONTACT`, `DEAL` entities, per tenant (RLS, `Architecture/multitenancy.md`)
**Last updated:** 2026-10-28 · **Packages:** `com.blusimian.blusimian.customFields`, `com.blusimian.blusimian.entityModel`

This is the as-built reference for the custom fields feature. The record read/write machinery that
consumes custom fields is documented in `Architecture/entity-layouts.md` §3.

> **Revision note (storage).** Custom field *values* used to live in a `custom_fields
> jsonb` column and were checked by a `CustomFieldValidator`. Both are gone. Values now
> live in typed columns from a fixed slot pool, and validation happens in `RecordMapper`
> alongside built-in fields. §11 records why.
>
> **Revision note (packages).** The `entities` and `entitySchema` packages have been
> reorganised. `entities` is gone, merged with the record machinery into `entityModel`;
> `entitySchema` is now only the schema-bundle feature. `customFields` depends on
> `entityModel`, and `RecordMapper` reads custom fields back through `CustomFieldDescriptors`.
> §4 has the current graph.

---

## 1. Purpose

Lets the product carry tenant-defined extra fields on CRM entities without a schema
change per field. Two halves:

- **Definitions** (the schema): rows in `app.custom_field_definitions` declaring which
  keys exist on an entity type, their type, label, ordering — *and* which physical
  column stores their values.
- **Values** (the data): stored in the entity's `cf_*` slot pool, one allocated column
  per field (e.g. `app.companies.cf_text_01`).

The link is `column_name`: a definition with `field_key = 'lead_source'` and
`column_name = 'cf_text_01'` means every company's Lead Source lives in `cf_text_01`.
Without the definition row that column is unreadable — the catalog *is* the mapping.

Custom and built-in fields share one flat keyspace per entity, so a record's JSON is
flat (`{"name":"Acme","lead_source":"Web"}`, never a nested `customFields` object) and
a layout can place either kind by `key` alone.

`COMPANY`, `CONTACT` and `DEAL` are wired up; the model is entity-agnostic via an
`entity_type` discriminator, and adding another entity is additive (§12).
`company_contacts` is deliberately not a record entity: it is a plain link row served
through static request/response DTOs.

---

## 2. Data model

### `app.custom_field_definitions` (schema: `app`)

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | `gen_random_uuid()`; primary key `(tenant_id, id)` |
| `tenant_id` | uuid | filled by its default from the transaction's tenant; RLS (`multitenancy.md` §5) |
| `entity_type` | varchar(50) | CHECK `in ('COMPANY','CONTACT','DEAL')`; shared enum `entityModel.EntityType` |
| `field_key` | varchar(100), collation `app.case_insensitive` | case-insensitive; write-once; the record's JSON key |
| `label` | varchar(255) | display name (mutable) |
| `data_type` | varchar(30) | CHECK `in ('TEXT','URL','PHONE','EMAIL','NUMBER','BOOLEAN','DATE','LONGTEXT')`; enum `entityModel.FieldDataType` minus the built-in-only `REFERENCE` and `COUNTRY` |
| `column_name` | varchar(20) | **the allocated slot**, e.g. `cf_text_01`; write-once |
| `required` | boolean | default false; enforced on write (§6) |
| `sort_order` | int | display order |
| `config` | jsonb | default `'{}'`; reserved for type-specific rules (picklist options, min/max, stricter length); unmapped in JPA until needed |
| `archived_at` | timestamptz | NULL = active; soft-delete marker |
| `created_at` / `updated_at` | timestamptz | `@CreationTimestamp` / `@UpdateTimestamp` |

Constraints / indexes:

- `uq_custom_field_key unique (tenant_id, entity_type, field_key)` — case-insensitive
  through the column's collation. Spans archived rows: a key stays reserved until purge.
- `uq_cfd_column unique (tenant_id, entity_type, column_name)` — one definition per slot. Also the
  concurrency backstop for allocation (§7).
- `ck_cfd_column_name` — `column_name ~ '^cf_(text|num|date|bool|longtext)_[0-9]{2}$'`.
- `ck_cfd_type_matches_slot` — the slot's pool must match `data_type`. This is the
  constraint that makes the pool safe: a NUMBER field parked in a text column is
  *unrepresentable*, not merely discouraged.
- No separate index: the by-entity reads use the uniques' leading `(tenant_id, entity_type)`.

### Values: the slot pool on `app.companies`, `app.contacts` and `app.deals`

Every record table carries the same pool, mapped by the one `CustomFieldSlots` embeddable.

| Pool | Slots | Column type | Holds |
|---|---|---|---|
| `cf_text_01..40` | 40 | `varchar(255)` | `TEXT`, `URL`, `PHONE`, `EMAIL` |
| `cf_num_01..25` | 25 | `numeric(19,4)` | `NUMBER` |
| `cf_bool_01..25` | 25 | `boolean` | `BOOLEAN` |
| `cf_date_01..10` | 10 | `date` | `DATE` |
| `cf_longtext_01..05` | 5 | `text` | `LONGTEXT` |

105 slots, declared **last** in the table: Postgres deforms a tuple attribute-by-attribute
up to the highest column a query touches, so a projection (`select id, name, updated_at`)
stops before the pool. Moving the pool later would need a table rebuild.

An unused slot costs one bit in the row's null bitmap (~23 bytes for the whole row), so
an empty pool is effectively free on disk — but *not* on the wire, where every column
sends a 4-byte length prefix even when null. That is the real argument against
over-provisioning.

The pool is deliberately **not indexed**. A slot gets an index when a field is marked
filterable: `create index concurrently idx_companies_cf_text_03 on app.companies (tenant_id, cf_text_03);`
— online, reversible, invisible to Hibernate. It leads with `tenant_id` because the same
physical column holds different fields for different tenants.

Pool sizes are a hard product limit, but a recoverable one: widening is
`alter table … add column cf_text_41 varchar(255)`, which is nullable with no default and
therefore catalog-only and instant. The cost is a migration plus a regenerated
`CustomFieldSlots`, not a table rewrite.

DDL lives in `src/main/resources/db/migration/V1__init.sql` (dev-phase policy: all DDL in
V1, edited in place, DB wiped on change — no V2+ yet).

---

## 3. Components

| File | Package | Role |
|---|---|---|
| `EntityType` | `entityModel` | enum: `COMPANY, CONTACT, DEAL`. Shared discriminator, reused by layouts + schema. |
| `FieldDataType` | `entityModel` | enum: `TEXT, URL, PHONE, EMAIL, LONGTEXT, NUMBER, BOOLEAN, DATE, REFERENCE, COUNTRY`. Declares three things per type: which **pool** it allocates from (null = built-in only, so `REFERENCE` and `COUNTRY` can't be custom fields → 400), which **JSON type** it arrives as (`accepts`), and an optional **format** a non-blank value must match (`wellFormed`; only `EMAIL`). Shared with built-in fields. |
| `CustomFieldSlots` | `entityModel` | **Generated** `@Embeddable` holding the 105 slot columns plus the accessor registry (`accessor(columnName)`, `pool(prefix)`). Each `SlotAccessor` carries the column's **Java type and length cap** alongside its getter/setter — both generated from the column declaration, so neither can drift from the schema. Regenerate with `scripts/gen_slots.py`. |
| `RecordEntity` | `entityModel` | Capability contract: `getId/getCreatedAt/getUpdatedAt/getSlots`. Says nothing about how a custom value is stored. |
| `StringToEntityTypeConverter` | `entityModel` | path segment → `EntityType`, case-insensitive; unknown → 400. |
| `CustomFieldDefinition` | `customFields` | JPA `@Entity`. Public ctor for create; `archive()`; no setters — `fieldKey`, `dataType` and `columnName` are write-once (`updatable=false` on the key and the slot). |
| `CustomFieldDefinitionRepository` | `customFields` | Spring Data; four derived/`@Query` methods (below). |
| `CustomFieldDefinitionRequest` | `customFields` | POST body DTO (+ bean validation). Carries no `columnName` — the server allocates it. |
| `CustomFieldDefinitionResponse` | `customFields` | Response DTO + `from(entity)`. Deliberately omits `columnName`: storage is not part of the contract. |
| `StandardFieldRegistry.reservedKeysFor` | `entityModel` | The keys a new custom field may not take (built-in keys + `RecordMapper.META_KEYS`); `CustomFieldService` consumes it. |
| `CustomFieldService` | `customFields` | `listActive`, `listAll`, `create` (+ slot allocation), `archive`. |
| `CustomFieldDescriptors` | `customFields` | `activeFields` / `allFields`: custom fields as `FieldDescriptor`s, injected into `RecordMapper` and `EntitySchemaService`. The **only** place a definition becomes a `FieldDescriptor`: resolves `column_name` through `CustomFieldSlots.accessor` to the slot's typed getter/setter. |
| `CustomFieldController` | `customFields` | REST endpoints (§5). |
| `exception/DuplicateCustomFieldKeyException` | `customFields` | → 409 `CONFLICT` |
| `exception/ReservedFieldKeyException` | `customFields` | → 409 `CONFLICT` |
| `exception/CustomFieldSlotsExhaustedException` | `customFields` | → 409 `CONFLICT`; pool for that data type is full |
| `exception/CustomFieldNotFoundException` | `customFields` | → 404 `NOT_FOUND` |

**Where each fact about a field lives.** The type says what *kind* of value it is (pool,
JSON type); the slot says what the *column* holds (Java type, length cap); the provider
says what a *built-in* column holds (`FieldDescriptor.withMaxLength`). Facts about columns
are generated from the column declarations, so they cannot drift from the DDL — an earlier
revision duplicated them onto `FieldDataType`, where the value-type copy went unused from
the day it was written.

**Gone in this revision:** `CustomFieldType` (promoted to the shared `FieldDataType`),
`CustomFieldValidator` and `CustomFieldValidationException` (their job moved into
`entityModel.RecordMapper`, which validates built-in and custom fields in one pass).

### Repository queries
```
findByEntityTypeAndArchivedAtIsNullOrderBySortOrderAscFieldKeyAsc(entityType)  // read path: active only
findByEntityType(entityType)                                                   // write path: archived included
existsByEntityTypeAndFieldKey(entityType, fieldKey)                            // create: dup check
findAllocatedColumnNames(entityType)                                           // create: slot allocation
```
The two list queries differ on purpose: the form shows active fields, but the write path
must still recognise archived ones so existing values stay saveable. `fieldKey` is the
ordering tiebreaker because `sort_order` is not unique. The dup check is plain equality:
the collation makes it case-insensitive, and unlike `upper()` it can use the index under RLS.

---

## 4. Dependency direction

Verified from the imports, not asserted:

```
platform       ──► (nothing)
entityModel    ──► platform customFields         ← the foundation (customFields: RecordMapper only)
customFields   ──► entityModel platform
entityLayouts  ──► entityModel platform
entitySchema   ──► entityModel entityLayouts customFields
companies      ──► entityModel platform
contacts       ──► entityModel platform
deals          ──► entityModel platform
```

There is one deliberate cycle: `entityModel ⇄ customFields`. `customFields` needs the
vocabulary (`EntityType`, `FieldDataType`, `CustomFieldSlots`, `FieldDescriptor`), and
`RecordMapper` injects `customFields.CustomFieldDescriptors` to read custom fields. It
used to be broken by a `CustomFieldProvider` interface declared in `entityModel`; that
was removed because it had one implementation, nothing enforces package boundaries (one
Maven module, no ArchUnit/Modulith), and the interface hid the real dependency. It is a
package cycle only, not a bean cycle: `RecordMapper → CustomFieldDescriptors →
CustomFieldService → repository`, and nothing on that chain depends back on
`RecordMapper`. Reintroduce the interface if the backend is ever split into modules.

`StandardFieldProvider` stays an interface declared in `entityModel` and implemented by
each entity slice (`CompanyStandardFieldProvider`, `ContactStandardFieldProvider`,
`DealStandardFieldProvider`): there is one per entity,
collected by `StandardFieldRegistry`, so `entityModel` never imports `companies`.

The reserved-key check is a plain method, `StandardFieldRegistry.reservedKeysFor` (the
provider registry's keys plus `RecordMapper.META_KEYS`), called by `CustomFieldService`.

---

## 5. HTTP API

Base path `/api/custom-fields`. Failures use the platform `ApiError` shape (§9).

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/{entityType}` | — | 200 `[CustomFieldDefinitionResponse]` (active, ordered) | 400 unknown entityType |
| POST | `/{entityType}` | `CustomFieldDefinitionRequest` | 201 `CustomFieldDefinitionResponse` | 400 invalid body; 409 duplicate key, reserved key, or pool exhausted |
| DELETE | `/{id}` | — | 204 (archives) | 404 unknown id |

`entityType` is `company`, `contact` or `deal`, case-insensitive (`company` == `COMPANY`). GET `/{entityType}` and
DELETE `/{id}` coexist, disambiguated by HTTP method.

For rendering, prefer `GET /api/entities/{entityType}/schema`: it merges built-in and
custom fields into one uniform, ETagged dictionary and carries the layout. This endpoint
is for *administering* definitions.

### Where values are read and written

Custom values travel in the flat record, not a nested object. `GET/POST/PATCH` on
`/api/companies`, `/api/contacts` and `/api/deals` carry them at the top level next to
built-in fields; `RecordMapper` maps each key to its slot. There is no entity-side custom
field code.

```
POST /api/custom-fields/company
{ "fieldKey":"lead_source","label":"Lead Source","dataType":"TEXT","required":false,"sortOrder":40 }
201 → { "id":"…","fieldKey":"lead_source","label":"Lead Source","dataType":"TEXT",
        "required":false,"sortOrder":40 }          // columnName not exposed

POST /api/companies
{ "name":"Acme","lead_source":"Referral" }
201 → { "id":"…","name":"Acme",…,"lead_source":"Referral","createdAt":…,"updatedAt":… }

PATCH /api/companies/{id}          (unknown key)
{ "nope": 1 }
400 → { "status":400,"code":"VALIDATION_ERROR","message":"Invalid fields for COMPANY",
        "details":[{"field":"nope","reason":"Unknown field"}] }
```

---

## 6. Write-path semantics

`RecordMapper.apply(entity, provider, values)` handles built-in and custom fields
identically. It receives them from two sources — `StandardFieldProvider` and
`CustomFieldDescriptors` — and concatenates them into one ordered list; from that point on
every step (convert, length check, required check, write) sees only `FieldDescriptor`s
carrying a getter/setter, so nothing downstream can treat the two kinds differently. JSON Merge Patch semantics: **present sets, null clears, absent
leaves**.

Order of operations, with everything validated before anything is written so a bad
request can't half-apply:

1. `id`/`createdAt`/`updatedAt` are skipped — server-owned, so a client can GET a record,
   edit one field and PUT the whole thing back.
2. Each remaining key resolves to a descriptor, case-insensitively. Unknown → one
   `FieldError`, not a fail-fast.
3. The raw JSON type is checked (`FieldDataType.accepts`), then Jackson converts to the
   descriptor's `valueType` (`5` → `BigDecimal`, `"2026-03-01"` → `LocalDate`). **Custom
   fields go through exactly the same conversion as built-in ones** — under the jsonb
   model their `valueType` was `Object` and conversion was skipped, so a DATE field
   happily stored `"not-a-date"`. A `REFERENCE` arrives as a string and converts to a
   `UUID`; a type with a format (`EMAIL`) is then checked against it, blank values
   excepted.
4. Values over the column's cap are rejected here rather than left to Postgres. Custom
   fields take the cap from their slot (`varchar(255)`); built-in fields declare their own
   via `FieldDescriptor.withMaxLength`, because a built-in TEXT field may sit on an
   unbounded `text` column (`description`, `website`).
5. `required` is checked against the **merged result**: on create a required field must be
   sent, on update it may be omitted but not cleared. An archived field is never required.
6. All problems come back in one 400 `VALIDATION_ERROR` with per-field `details`.

The write path uses `listAll` (archived included) so a just-archived field stays writable;
the read path uses `listActive`, so an archived field's values are not returned.

Because `Company` is `@DynamicUpdate` and only the keys actually sent are written,
Hibernate emits an UPDATE touching just those columns — which is what makes concurrent
writes to *different* custom fields compose instead of clobbering each other.

---

## 7. Slot allocation

Creating a definition also claims a physical column. `CustomFieldService.create`:

1. Takes a transaction-scoped Postgres advisory lock per tenant and entity type
   (`pg_advisory_xact_lock(0x5107, (tenant + ":" + entityType).hashCode())`), released on
   commit. RLS doesn't scope advisory locks, so the key names the tenant.
2. Rejects a key that collides with a reserved key (built-in field or `id`/`createdAt`/
   `updatedAt`) → 409, or one already used by another custom field → 409.
3. Allocates the **lowest free slot** in the pool matching `data_type`, so allocation stays
   dense and gaps left by purges are refilled. Pool full → 409
   `CustomFieldSlotsExhaustedException`.
4. Saves. `uq_cfd_column` and `ck_cfd_type_matches_slot` remain the backstop.

The advisory lock is preferred over catch-and-retry on the unique violation: a constraint
violation marks the transaction rollback-only, so a retry needs a `REQUIRES_NEW` attempt
on a *separate bean* (self-invocation bypasses the Spring proxy). Serialising costs
nothing — creating a custom field is an admin action a few times a day.

---

## 8. Field-deletion lifecycle

`DELETE` performs **stage 1 only**:

1. **Archive** *(implemented)* — sets `archived_at`. The field disappears from the read
   path, the write path still recognises it, and **the slot stays allocated**. Reusing the
   column would silently hand one field's data to another.
2. **Purge** *(deferred)* — null the slot across every row of the entity's table, then
   delete the definition row, releasing both `field_key` and `column_name`. Needs a native
   UPDATE with the column name interpolated: safe, because the name comes from the
   regex-constrained catalog, but validate it against `CustomFieldSlots` before emitting.

There is no separate scrub stage any more — nulling one typed column *is* the scrub, and
it is cheap enough to do synchronously. (Under jsonb this needed an async job to strip a
key from every row's blob.)

---

## 9. Error model (platform-wide, `com.blusimian.blusimian.platform.web`)

Every error response is an `ApiError` carrying both an HTTP status and a stable,
machine-readable `code`:

```
ApiError(Instant timestamp, int status, String error, ErrorCode code,
         String message, List<FieldError> details)   // details omitted from JSON when empty
FieldError(String field, String reason)
```

`ErrorCode`: `VALIDATION_ERROR`, `CUSTOM_FIELD_INVALID`, `NOT_FOUND`, `CONFLICT`,
`UNAUTHENTICATED`, `INVALID_CREDENTIALS`.
`CUSTOM_FIELD_INVALID` is **dead** — record validation now reports `VALIDATION_ERROR`
whether the offending field is built-in or custom, since the API doesn't distinguish them.
Remove the constant, or start using it.

Exception hierarchy: `ApiException` (abstract; status + code + details) →
`NotFoundException`, `ConflictException`, `BadRequestException`. Domain exceptions extend
these. `GlobalExceptionHandler` has one handler for `ApiException`, plus handlers
normalising framework errors to clean 400s (no stack-trace leakage):
`MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`,
`HttpMessageNotReadableException`, and `DataIntegrityViolationException` → 409 with a
deliberately generic message (the raw text names constraints and echoes values). A failed
login (`AuthenticationException`) is a 401 `INVALID_CREDENTIALS`; a request without a
session gets the same `ApiError` shape from Spring Security's entry point (401
`UNAUTHENTICATED`).

Design note: house-style `ApiError`, deliberately not RFC 9457 `ProblemDetail`.

---

## 10. Status & known limitations

**Implemented:** definitions table with slot mapping; the 105-slot pool on `companies`;
GET/POST/DELETE for definitions; slot allocation with advisory locking and exhaustion
handling; full type, length and required validation on the record write path; archive
lifecycle; platform error model.

**Verified:** 34 unit tests (`RecordMapperTest`, `ContactRecordTest`, `DealRecordTest`,
`StandardFieldProvidersTest`, `CustomFieldSlotAllocationTest`) plus an
end-to-end pass against Postgres 16 — schema bundle, flat create/read, partial update,
GET→PUT round-trip, slot allocation across pools, and rejection of bad type, over-length
and unknown values.

**Deferred:** PUT/update of definitions (label and `sort_order` are the mutable parts);
purge + slot reclamation; picklist types and the `config` column; definition caching;
per-field filterable flag and its index management.

**Known limitations:**

- **Pool exhaustion is a hard product limit.** 40 text / 25 number / 25 boolean / 10 date
  / 5 long-text per entity. Pools are not fungible — a full text pool cannot borrow from
  an empty boolean pool. Text is the pool most likely to exhaust and the only one with no
  substitute; consider shifting boolean slack to text before tenants arrive.
- **`@Basic(fetch = LAZY)` on the long-text slots is currently inert.** Per-attribute lazy
  loading needs Hibernate bytecode enhancement, which is not enabled in `pom.xml`, so
  all five `text` columns load with every company.
- **No `@Size(max = 100)` on `fieldKey`.** A longer key hits `ck_cfd_field_key_len` and now
  surfaces as a 409 via the `DataIntegrityViolationException` handler — better than the
  old 500, but semantically it should be a 400 from bean validation.
- **Archived fields pin their slot forever** until purge exists (§8).
- **No pagination on record list endpoints**, so a filter/sort feature over slots has no
  paging story yet. When it lands, every sort needs `id` as a tiebreaker or pages will
  repeat and skip rows.

---

## 11. Why the storage model changed

The jsonb blob was replaced by the typed slot pool because a CRM has to filter, sort and
report on custom fields, and `custom_fields->>'x'` cannot do it well: `->>` always returns
text so every comparison needs a per-row cast (which throws if any row holds a bad value),
sorting without a cast is lexical, GIN supports containment but not ranges or ordering,
and the planner has no statistics for an expression it has never seen.

Typed columns fix all of that, and take three defects with them:

- Custom `DATE`/`NUMBER` values were never actually validated (conversion was skipped for
  `valueType == Object`). A `date` column makes the bug unrepresentable.
- Whole-blob read-modify-write silently lost concurrent writes to *different* fields.
  Per-column writes plus `@DynamicUpdate` compose correctly.
- `RecordEntity` had to expose a `Map<String,Object>`, leaking the storage model through
  the capability contract. It now hands out the slot pool and nothing else.

The cost is the pool's fixed size, and a `Company` entity 133 columns wide. Alternatives
weighed and rejected: EAV (indexable and per-value concurrent, but joins and pivots on
every read), per-tenant DDL (real columns, but Postgres caps at 1600 and DDL takes locks),
and JSONB plus generated columns (keeps the blob's weaknesses for everything not
promoted). Mature CRM platforms converge on pools or real DDL for this reason.

---

## 12. Extension points

- **New entity type:** add to `entityModel.EntityType` + the two CHECK constraints
  (`ck_cfd_entity_type`, `ck_layout_entity_type`); give the table the same `cf_*` pool;
  have the entity implement `RecordEntity`, embed `CustomFieldSlots` and carry
  `@DynamicUpdate`; add a `StandardFieldProvider` (and list it in
  `StandardFieldProvidersTest`, which fails for a type without one); serve it from a
  `Map`-in/`Map`-out controller over `RecordMapper`. No `customFields`-side changes.
  Contacts and deals were added exactly this way. A foreign key to another record is
  declared with `FieldDescriptor.reference(...)`: the schema exposes its target as
  `referenceTo`, and the entity service translates the FK violation (see `DealService`).
- **New field type:** add to `FieldDataType` with its pool and JSON type; extend the
  `data_type` CHECK and `ck_cfd_type_matches_slot`; add a pool to the table and regenerate
  `CustomFieldSlots` if it needs one of its own.
- **Widen a pool:** add columns in V1, update `scripts/gen_slots.py`, regenerate. Instant
  at the database level (nullable, no default).
- **Type-specific rules** (picklist options, min/max, stricter length): use the existing
  `config` jsonb column; map it on the entity when first needed.
- **Make a field filterable:** `create index concurrently` on its slot, and record the flag
  on the definition so the query builder knows the index exists.
- **A new tenant-scoped table:** follow the recipe in `multitenancy.md` §17.
