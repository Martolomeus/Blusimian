# Entity Layouts & Entity Schema — Architecture Reference

**Status:** Read path, schema bundle and layout write path implemented & verified; role assignment not built · **Scope:** `COMPANY`, `CONTACT`, `DEAL`, per tenant (RLS, `Architecture/multitenancy.md`)
**Last updated:** 2026-11-08 · **Packages:** `com.blusimian.blusimian.entityLayouts`, `com.blusimian.blusimian.entitySchema`, `com.blusimian.blusimian.entityModel`

This is the as-built reference for entity layouts, the entity-schema endpoint, and the
record machinery they share with every entity. The frontend that consumes the schema is
documented in `Architecture/frontend-entity-pages.md`; the settings area and layout editor that use
the write path in `Architecture/settings.md`; custom fields in
`Architecture/custom-fields.md`.

---

## 1. Purpose

Custom fields decide **which fields exist**. Layouts decide **how they are arranged on
screen**: sections, order, and which fields (built-in and custom) appear where. The two
are independent: a field can exist without being placed, and a layout never owns values.

Two things are delivered:

- **Entity layouts** — a stored tree (`layout → sections → placed fields`) describing an
  entity's on-screen arrangement.
- **Entity schema bundle** — one cacheable endpoint per entity type that returns the
  field dictionary plus the **one layout that applies to the caller**. A client fetches
  it once per type and renders any record of that type from it.

The rule that shapes both: **a layout is presentation, not permission.** The bundle's
`fields` list names every field of the entity whatever the layout shows. Field-level
security, when it is built, belongs in the record read/write path.

---

## 2. Data model

Three tables in schema `app`, in `V1__init.sql` (dev-phase policy: all DDL in V1, edited
in place, database wiped with `make db-reset`). The tree is held by real foreign keys
with `ON DELETE CASCADE`. There is no archive lifecycle: a layout row is structure, not
data, so a hard delete is safe.

Every table also has a `tenant_id` under RLS, a primary key `(tenant_id, id)`, and
composite foreign keys `(tenant_id, …)` (`multitenancy.md` §5). The tables below show
their own columns only.

### `app.entity_layouts`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | `gen_random_uuid()` |
| `entity_type` | varchar(50) | CHECK `in ('COMPANY','CONTACT','DEAL')`; enum `EntityType` |
| `label` | varchar(255) | display name; served in the schema bundle |
| `is_default` | boolean | the layout served when no assignment applies (today: always) |
| `created_at` / `updated_at` | timestamptz | |

Partial unique index `uq_entity_layout_default on (tenant_id, entity_type) where is_default`:
at most one default per tenant and entity type. An entity type may have many layout rows.

### `app.entity_layout_sections`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | |
| `layout_id` | uuid | FK `(tenant_id, layout_id)` → `entity_layouts` ON DELETE CASCADE |
| `label` | varchar(255) | section title |
| `column_count` | int | CHECK `between 1 and 2` |
| `sort_order` | int | |
| `created_at` / `updated_at` | timestamptz | |

### `app.entity_layout_section_fields`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | |
| `section_id` | uuid | FK `(tenant_id, section_id)` → `entity_layout_sections` ON DELETE CASCADE |
| `field_key` | varchar(100), collation `app.case_insensitive` | the field's key in the record JSON (`legalName`, `lead_source`); case-insensitive |
| `sort_order` | int | |
| `created_at` / `updated_at` | timestamptz | |

- `unique (tenant_id, section_id, field_key)`: a field is placed at most once per section. "Once per
  layout" is the write path's validator's job (§5.6).
- **No foreign key to the field.** A placement names its field by key alone. Built-in
  fields are columns, not rows, so they cannot be foreign-keyed; a string key keeps every
  placement the same shape. The consequence is handled in the schema bundle (§5.3): a
  placement can outlive its field, or differ from it in case.
- Placements store no label and no data type. Those are read live from the field's own
  definition, so renaming a field cannot leave a stale copy here.

### Default layouts

Provisioning gives every new tenant one default layout per entity type:
`DefaultLayoutsInitializer` stores each of `DefaultLayoutTemplates` through
`EntityLayoutService.createDefault`, which validates it like the editor does, so a template
naming a missing field fails provisioning. Every required field is placed. The default
can't be deleted (§5.6), so every entity keeps one.

| Entity | Section (columns) | Fields |
|---|---|---|
| `COMPANY` | Overview (2) | `name`, `legalName`, `type`, `industry`, `employeeRange` |
| | Contact details (2) | `website`, `phone`, `phoneAlt`, `vatNumber` |
| | About (1) | `description` |
| `CONTACT` | Contact (2) | `salutation`, `firstName`, `lastName`, `email`, `phone` |
| | Address (2) | `street`, `city`, `state`, `postalCode`, `countryCode` |
| `DEAL` | Deal (2) | `dealName`, `amount`, `customerId` |

A new tenant has no custom fields. The dev demo tenant Acme gets a `lead_source` field,
appended to its company Overview (`DemoTenantSeeder`).

---

## 3. Components

### `entityModel` — vocabulary and record machinery

| File | Role |
|---|---|
| `EntityType` | enum `COMPANY, CONTACT, DEAL`. Shared discriminator for custom fields, layouts and the schema. |
| `StringToEntityTypeConverter` | binds a path segment to `EntityType`, case-insensitive; unknown → 400. |
| `FieldDataType` | enum `TEXT, URL, PHONE, EMAIL, PICKLIST, LONGTEXT, NUMBER, BOOLEAN, DATE, REFERENCE, COUNTRY`. `REFERENCE` and `COUNTRY` are built-in only (no slot pool, so not available to custom fields). Drives how the frontend renders a field. |
| `FieldDescriptor` | one field's metadata (`key, label, dataType, required, maxLength, referenceTo`) plus how to read and write its value. Built-in and custom fields both arrive as descriptors. |
| `StandardFieldProvider` | SPI each entity slice implements to declare its built-in fields. |
| `StandardFieldRegistry` | every provider indexed by `EntityType`; also the reserved keys a custom field may not take. |
| `EntityFieldDictionary` | every field an entity has right now: standard fields in provider order, then active custom fields. What the schema bundle serves and what a layout may place, in one place so the two can't disagree. |
| `RecordEntity` | what an entity must expose to be served as a record. |
| `RecordMapper` | entity → flat record (`toRecord`), and flat map → entity (`apply`, JSON Merge Patch: present sets, `null` clears, absent leaves). Validates unknown key, JSON type, max length, format and required, and reports all problems in one 400. |
| `CustomFieldSlots` | the generated custom-field column pool (see `custom-fields.md`). |

### `entityLayouts`

| File | Role |
|---|---|
| `EntityLayout`, `EntityLayoutSection`, `EntityLayoutSectionField` | JPA entities for the three tables. `@OneToMany(cascade ALL, orphanRemoval) @OrderBy("sortOrder")`, so children arrive in display order. `EntityLayout.create`, `rename`, `replaceSections`, `markDefault`, `clearDefault`; sections and placements have package-private constructors, used only by the service. |
| `EntityLayoutRepository` | `findByEntityTypeAndIsDefaultTrue(EntityType) → Optional`, `findAllByEntityTypeOrderByLabelAsc(EntityType)`, `countByEntityType(EntityType)`. |
| `EntityLayoutValidator` | plain class: `validate(label, sections, dictionary) → List<FieldError>`, the write rules of §5.6. |
| `EntityLayoutService` | `resolve(EntityType)` → the layout that applies to the caller, as a DTO, or empty (the read path). `list`, `create`, `replace`, `setDefault`, `delete` (the write path). `createDefault` for provisioning. |
| `DefaultLayoutTemplates`, `DefaultLayoutsInitializer` | the default layouts, and the `TenantInitializer` that stores them for each new tenant. |
| `EntityLayoutController` | the endpoints under `/api/entity-layouts` (§5.1). |
| `dto/EntityLayoutResponse` and section / field DTOs | the nested tree **with** row ids. |
| `dto/CreateEntityLayoutRequest`, `UpdateEntityLayoutRequest`, `EntityLayoutSectionRequest` | write bodies (§5.6). |
| `exception/EntityLayoutNotFoundException` | → 404 `NOT_FOUND`. |
| `exception/DefaultLayoutDeletionException`, `LayoutLimitReachedException` | → 409 `CONFLICT`. |

### `entitySchema`

| File | Role |
|---|---|
| `EntitySchemaService` | `getSchema(EntityType)`: composes the field dictionary (`EntityFieldDictionary`), each picklist field's options (`picklists.PicklistLookup`, one query, skipped when the entity has no picklist field) and the resolved layout, and makes the bundle self-consistent (§5.3). |
| `EntitySchemaController` | `GET /api/entities/{entityType}/schema`; ETag, `If-None-Match` → 304, `Cache-Control`. |
| `dto/EntitySchemaResponse` | `{ entityType, fields[], layout }`; `layout` is null when none is configured. |
| `dto/SchemaFieldResponse` | `{ key, label, dataType, required, maxLength, referenceTo, options }`. |
| `dto/SchemaLayoutResponse` | `{ id, label, sections[] }`. |
| `dto/SchemaSectionResponse` | `{ label, columnCount, sortOrder, fields[] }`. |
| `dto/SchemaPlacementResponse` | `{ key, sortOrder }`. |

### Entity slices

`CompanyStandardFieldProvider`, `ContactStandardFieldProvider`, `DealStandardFieldProvider`
declare each entity's built-in fields: key (the JSON property name), label, data type,
required, and the column's length cap. `Deal.customerId` is a `REFERENCE` to `COMPANY`;
`Contact.countryCode` is a `COUNTRY`. `Company.type`, `industry`, `employeeRange` and
`Contact.salutation` are `PICKLIST`s (`Architecture/picklists.md`).

---

## 4. Dependency direction

```
platform       ──► (nothing)
entityModel    ──► platform customFields picklists
customFields   ──► entityModel platform
picklists      ──► entityModel customFields platform tenants
entityLayouts  ──► entityModel platform
entitySchema   ──► entityModel entityLayouts customFields picklists
companies, contacts, deals ──► entityModel platform
```

- `entitySchema` is the aggregator. It depends into `entityModel` and `entityLayouts`;
  neither depends on it. Custom fields reach it through `EntityFieldDictionary`.
- Built-in field knowledge lives with the entity. Each slice implements
  `StandardFieldProvider`; `entityModel` collects the providers and never imports a slice.
- The `entityModel ⇄ customFields` package cycle is deliberate and explained in
  `custom-fields.md` §4. `EntityFieldDictionary` uses the same edge as `RecordMapper`.
  `entityModel ⇄ picklists` is the same kind (`picklists.md` §5.4): `RecordMapper` checks a
  picklist value through `PicklistLookup`, and `entitySchema` composes the options into the bundle.

---

## 5. HTTP API

### 5.1 Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/entities/{entityType}/schema` | 200 bundle + `ETag` + `Cache-Control: no-cache, private` | 400 unknown entity type |
| GET | same, with a matching `If-None-Match` | 304, no body | — |
| GET | `/api/entity-layouts/{entityType}` | 200 every layout of that entity, full trees with row ids | 400 unknown entity type |
| POST | `/api/entity-layouts` | 201 the created layout | 400 `VALIDATION_ERROR`; 409 layout limit |
| PUT | `/api/entity-layouts/{id}` | 200 the replaced layout | 400 `VALIDATION_ERROR`; 404 |
| PUT | `/api/entity-layouts/{id}/default` | 204 (idempotent) | 404 |
| DELETE | `/api/entity-layouts/{id}` | 204 | 404; 409 default layout |

`entityType` is case-insensitive (`company` = `COMPANY`). A malformed `{id}` is a 400.

Record pages only call the schema endpoint. The `/api/entity-layouts` endpoints are the
admin side, used by the layout editor (`settings.md`). There is no get-by-id: the list
of an entity's layouts is at most 20 small trees, and the editor reads its layout from it.

Records are served by each entity's own endpoint (`/api/companies`, `/api/contacts`,
`/api/deals`) as a flat map: `id`, one value per field key, `createdAt`, `updatedAt`.
`PATCH` is a JSON Merge Patch; `PUT` is still accepted as an alias.

### 5.2 The schema bundle

```jsonc
GET /api/entities/company/schema
{
  "entityType": "COMPANY",
  "fields": [                              // every field of the entity, built-in then custom
    { "key": "name",        "label": "Name",        "dataType": "TEXT", "required": true,  "maxLength": 255,  "referenceTo": null, "options": null },
    { "key": "website",     "label": "Website",     "dataType": "URL",  "required": false, "maxLength": null, "referenceTo": null, "options": null },
    { "key": "industry",    "label": "Industry",    "dataType": "PICKLIST", "required": false, "maxLength": 100, "referenceTo": null,
      "options": [ { "code": "agriculture", "label": "Agriculture", "active": true },
                   { "code": "shipping",    "label": "Shipping",    "active": false } ] },
    { "key": "lead_source", "label": "Lead Source", "dataType": "TEXT", "required": false, "maxLength": 255,  "referenceTo": null, "options": null }
  ],
  "layout": {                              // the one layout resolved for the caller, or null
    "id": "5c8e1f04-2b7a-4d39-9f61-0e3a7c2d4b18",
    "label": "Default",
    "sections": [
      { "label": "Overview", "columnCount": 2, "sortOrder": 10,
        "fields": [ { "key": "name", "sortOrder": 10 }, { "key": "website", "sortOrder": 20 } ] }
    ]
  }
}
```

- A field's `key` is the key its value has in the record JSON, so a client reads
  `record[field.key]` with no translation. Built-in and custom fields share one flat
  keyspace per entity and look identical here.
- `required` and `maxLength` are in the bundle so a client can validate before sending.
- `referenceTo` is set only on a `REFERENCE` field: the entity type the id points at.
- `options` is set only on a `PICKLIST` field: its options in display order, **inactive ones
  included**, so a client can label a record that holds one. A record holds the `code`.
- Sections and placements arrive in `sortOrder` order. **Array order is display order.**

### 5.3 Self-consistency

`EntitySchemaService` guarantees that every placement in the bundle has a matching entry
in `fields`, spelled exactly as it is there:

- a placement whose field is not in the dictionary (for example an archived custom
  field) is dropped;
- a placement key that differs from the field's key only in case is rewritten to the
  dictionary's spelling.

This is settled once on the server because placements are case-insensitive keys with no
foreign key. A client can join placements to fields without checking.

### 5.4 Which layout is served

The server decides; the client never chooses between layouts.
`EntityLayoutService.resolve(entityType)` returns the layout that applies to the caller.
Today that is always the entity's default layout.

`resolve` is the seam for role-based assignment. When roles exist it takes the caller's
role, tries the layout assigned to that role, and falls back to the default. The bundle
keeps returning exactly one layout, so its contract and every client stay unchanged.

### 5.5 Caching

- **ETag:** a strong content hash, SHA-256 over the response record's `toString()`. It
  changes exactly when the bundle's content changes (a field added, renamed or archived;
  a picklist edited; the layout edited; a different layout resolved).
- **`Cache-Control: no-cache, private`:** a browser may store the bundle but must
  revalidate before each use, which it does itself with `If-None-Match`. Clients get
  304s without any conditional-request code. `private` keeps shared caches out: the
  bundle varies by caller (by tenant now, by role later), and a shared cache keys on the
  URL alone.

### 5.6 Writing layouts

```jsonc
// POST /api/entity-layouts
{
  "entityType": "COMPANY",
  "label": "Sales (compact)",
  "sections": [
    { "label": "Overview", "columnCount": 2, "fields": ["name", "type", "industry"] },
    { "label": "About",    "columnCount": 1, "fields": ["description"] }
  ]
}

// PUT /api/entity-layouts/{id}  — the same without entityType, which is fixed at create
{ "label": "Sales (compact)", "sections": [ /* as above */ ] }
```

- **Whole tree.** A section's `fields` is its field keys in display order; the server
  assigns `sort_order` as `(index + 1) * 10`. Requests carry no ids and no
  sort orders. Labels are stored stripped of surrounding whitespace, and keys in the
  dictionary's spelling.
- **`isDefault` is in neither body.** A new layout is never the default; the default
  changes only through `PUT /{id}/default`.
- **Replace makes new section rows every time.** Hibernate flushes inserts before orphan
  deletes, so re-adding a field key to a reused section would insert
  `(section_id, field_key)` while the old row still holds it, and break `uq_section_field`.
  Fresh section ids rule that out; nothing references a section or placement id. The
  layout row's `updated_at` changes only when its own columns do.
- **No optimistic locking.** Two saves of one layout: the last one wins, as for records.

**Validation.** `EntityLayoutValidator` checks the tree against `EntityFieldDictionary`
and reports every problem in one `400 VALIDATION_ERROR` ("Layout is invalid") with
`details`, the same shape records use. Bean validation on the request records covers
only the shape (no nulls), so every rule below comes back with the same reasons.

| Rule | `field` | `reason` |
|---|---|---|
| label not blank, at most 255 (after stripping) | `label` | "Required" / "At most 255 characters" |
| 1 to 15 sections | `sections` | "At least one section" / "At most 15 sections" |
| section label not blank, at most 255 | `sections[0].label` | as for `label` |
| `columnCount` is 1 or 2 | `sections[0].columnCount` | "Must be 1 or 2" |
| each key is a field of the entity (case-insensitive) | `sections[0].fields[2]` | "Unknown field 'foo'" |
| a field is placed at most once in the layout | `sections[1].fields[0]` | "'name' is placed more than once" |
| every required field is placed | `sections` | "Required field 'Name' is not placed" |

The paths are informative, not a contract. Allowed on purpose, because they break
nothing: two layouts or two sections with the same label, and a section with no fields
(the read path already skips it).

**Set default** runs in one transaction: load the target (404 if missing; a no-op if it
is already the default), `clearDefault()` the entity's current default, **`flush()`**,
then `markDefault()` the target. The flush matters: `uq_entity_layout_default` is a
partial unique index, which Postgres checks per row and cannot defer, so the old default
must be written false first. No lock is taken: of two concurrent calls the index rejects
the second, and `GlobalExceptionHandler` turns that into a 409.

**Delete** is a hard delete; the cascade removes sections and placements. The default
layout can't be deleted (409 "The default layout can't be deleted"), so every entity
keeps the default provisioning gave it.

**Limit.** Create counts the entity's layouts and refuses the 21st with 409 "A company
can have at most 20 layouts". The count takes no lock, so two concurrent creates can
make a 21st; that is harmless.

---

## 6. Read-path mechanics

- `open-in-view=false`, so the lazy `@OneToMany` collections must be walked inside a
  transaction. `EntityLayoutService` maps the entity tree to DTOs inside its
  `@Transactional(readOnly = true)` method and returns the DTO, not the entity.
- `EntitySchemaService.getSchema` runs in its own read-only transaction: the fields from
  `EntityFieldDictionary` (built-in from `StandardFieldRegistry`, then active custom
  fields from `CustomFieldDescriptors`), picklist options from `PicklistLookup`, the layout
  from `EntityLayoutService.resolve`.
- `EntityLayoutService.list` maps the entity's layout trees to DTOs inside its transaction too.
- Both field kinds go through the same `FieldDescriptor → SchemaFieldResponse` mapping,
  so the dictionary is uniform by construction.

---

## 7. Error model

Reuses `platform/web`; there is no layout-specific `ErrorCode`.
`EntityLayoutNotFoundException` → 404 `NOT_FOUND`. An unknown `{entityType}` or a
malformed `{id}` → 400 `VALIDATION_ERROR`. Record and layout validation failures → 400
`VALIDATION_ERROR` with `details: [{ field, reason }]`, one entry per problem. Deleting
the default layout and the layout limit → 409 `CONFLICT`, through `ConflictException`
subclasses.

---

## 8. Tests

| Test | Covers |
|---|---|
| `EntitySchemaServiceTest` | the bundle carries the resolved layout's id, label and sections; built-in and custom fields share one dictionary (a real `EntityFieldDictionary`); no layout → `layout` null; a placement without a field is dropped; a placement key is spelled as in the dictionary; a picklist field carries its options (inactive included), others carry none |
| `StandardFieldProvidersTest` | every `EntityType` has a provider with unique, non-reserved keys; `referenceTo` is set exactly on `REFERENCE` fields |
| `RecordMapperTest`, `ContactRecordTest`, `DealRecordTest` | flat read shape and merge-patch write for each entity |

All are plain unit tests with no Spring context and no database.

**Not yet tested: the write path.** It was verified by hand against Postgres. When tests
are picked up, the likeliest places for bugs are: `EntityLayoutValidator` (each rule,
several problems reported together, case-insensitive keys); and, against Postgres
(Testcontainers), the default switch and a replace that moves a field between sections
and back (the flush-order trap of §5.6).

---

## 9. Status & known limitations

**Not built:**

- Role assignment: an assignment store and the role argument to `resolve` (§5.4). Until
  then only the default layout is served; other layouts can be built and kept.
- Permissions: every write on `/api/entity-layouts` will require `manage_layouts`
  (`roles-and-permissions.md` §2.5). The roles exist, but nothing is enforced yet, so any
  logged-in user of the tenant may call it. The schema bundle stays open to every
  signed-in user.

**Known limitations:**

- **A required field that is on no layout cannot be filled in on create.** Every write
  now requires every required field to be placed, but a required custom field created
  after a layout was saved leaves that layout short. It keeps being served (the read
  path doesn't validate) and must be fixed on its next save; the editor points it out.
- **N+1 on read.** One query per section for its fields; on the full list, a few hundred
  queries at the limits (60 layouts × 15 sections). `@BatchSize` on the two collections
  or a fetch join fixes it if it ever shows.
- **Last save wins** when two admins edit one layout.
- **Concurrent creates** can exceed the 20-layout limit by one.
- **The ETag is recomputed on every request**, including ones answered with 304. The
  saving is bandwidth, not server work.
- **Built-in field lists are code-defined.** A provider's keys are not checked against
  the entity's columns beyond what its getter and setter references enforce at compile
  time.
- **`Company.description` is typed `TEXT`**, so clients edit it in a single-line input.

---

## 10. Extension points

- **New entity type:** add it to `EntityType` and to the `entity_type` CHECKs; implement
  `RecordEntity` and a `StandardFieldProvider`; add its default to `DefaultLayoutTemplates`;
  give it a permission table (`roles-and-permissions.md` §10). No change inside
  `entityLayouts` or `entitySchema`. For the frontend side see
  `frontend-entity-pages.md` §8.
- **New built-in field:** one `FieldDescriptor` in the entity's provider, and a placement
  in its layout.
- **New data type:** a constant in `FieldDataType` (with a slot pool if custom fields may
  use it), plus a field component in the frontend registry.
