# Picklists — Spec

**Status:** Proposal, nothing implemented · **Scope:** a `PICKLIST` field type for standard and custom fields, tenant-editable values, seeded defaults for Company Type, Industry and Employee Range and Contact Salutation
**Date:** 2026-11-08 · **Builds on (as-built):** `Architecture/custom-fields.md`, `Architecture/entity-layouts.md`, `Architecture/multitenancy.md`, `Architecture/roles-and-permissions.md`
**Code:** new package `picklists`; `entityModel`, `entitySchema`, `customFields`, `companies`, `contacts`, `V1__init.sql`; `Frontend/src/components/fields`, `Frontend/src/lib/entity`

> A picklist field stores one **code** from a list the tenant controls. Every tenant's lists live
> in one table, `app.picklist_values`, keyed by entity type and field key. Options are resolved
> the same way for every picklist field, standard or custom: they are looked up in that table.
>
> Which fields are picklists is said where it's already said: a custom field's definition has
> `data_type = 'PICKLIST'`, and a standard field's provider gives it that data type. The values a
> new tenant starts with are global reference data, `public.standard_picklist_values`, seeded in
> V1 next to `iso_countries` and copied into each tenant when it is provisioned. After that copy
> nothing distinguishes a standard list from a custom one.
>
> Records carry the code (`"technology"`) and the schema bundle carries the code → label mapping,
> so renaming a label touches no record. The settings UI for editing values comes later, merged
> into the entity settings page (§12.2). This spec builds the backend, the API that UI will use,
> and the dropdown on record pages.

**Terms.** A **picklist** is the list belonging to one field: the `picklist_values` rows that
share its entity type and field key. A **value** is one row: a **code** (stored on records,
write-once), a **label** (shown, renameable) and an active flag. On the wire (the bundle, the
picklist API and the custom field `POST`) the same entries are always called **options**, as a
select calls them, so they can't be confused with a record's value. "Value" is the database's word.

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | A record stores the value's **code**, a stable snake-case key that is write-once. It stores neither the label nor the value's id. | §3 |
| D2 | One tenant table, `app.picklist_values`, keyed by `(entity_type, field_key)`. There is no picklist table: the custom field definition and the standard field provider already say which fields are picklists. | §4 |
| D3 | No foreign key from a record column to a value. `RecordMapper` checks the code, as it checks every other rule, for standard and custom fields alike. | §4.4 |
| D4 | One new `FieldDataType`, `PICKLIST`. A standard field becomes a picklist by its data type alone. A custom picklist takes a slot from the **text** pool, so neither the slot pools nor the permission tables change. | §5.1, §9.2 |
| D5 | Options are resolved by one lookup, `PicklistLookup`, by entity type and field key, the same way for standard and custom fields. On the record path, `RecordMapper` and the schema bundle are the only callers. `FieldDescriptor` doesn't change. | §5.3 |
| D6 | Values are deactivated, never deleted. A record holding an inactive value keeps it and still saves unchanged; no record can newly pick it. | §6 |
| D7 | Picklists are always restricted: a code not in the list is a 400. `""` is not a value; `null` means none. | §6 |
| D8 | Standard starting values are global reference data, `public.standard_picklist_values`, seeded in V1. Provisioning copies them into the new tenant with one `insert … select`. A later standard picklist is backfilled by the same kind of statement in its migration. A startup verifier keeps the seed in step with the providers. | §8 |
| D9 | A custom picklist field is created with its values in the one `POST /api/custom-fields/{entityType}`. `picklists` stores them by listening to `CustomFieldCreated`, so `customFields` doesn't know it exists. | §9 |
| D10 | Values are edited with one `PUT` per picklist, carrying the whole ordered list in one transaction. A new value's code is derived from its label unless one is given. | §10 |
| D11 | Editing values needs `manage_custom_fields`. No new permission column. | §10.4 |
| D12 | Job titles keep their own table (`WIP/job-title-layouts.md` §3). A picklist is for a choice stored in a field. Anything other rows point at, or anything with attributes of its own, gets its own table. | §2.2 |

---

## 2. Where we are

### 2.1 Today

- `companies.type` (`varchar(100)`), `industry` (`varchar(100)`), `employee_range` (`varchar(30)`)
  and `contacts.salutation` (`varchar(50)`) are free text with a text box in the UI. One tenant
  ends up with `Software`, `software`, `SaaS` and `IT` for the same industry, and nothing can be
  grouped or reported on.
- Custom fields have no choice type. `custom-fields.md` §2 reserved `custom_field_definitions.config`
  for "picklist options". This spec replaces that plan (§13).
- `COUNTRY` is already a picklist in all but name: an ISO code on the record, names in
  `public.iso_countries`. Its list is global and read-only; these lists belong to the tenant.

### 2.2 Picklist, or a table of its own?

`WIP/job-title-layouts.md` §3 rejected a generic picklist table **for job titles**. That still
holds. The difference is what references the value:

| | Job title | Picklist value |
|---|---|---|
| Referenced by | FKs: `users.job_title_id`, `job_title_layouts` | A code in one field's column, and nothing else |
| Carries data of its own | Layout assignments, later more | A label and a position |
| Integrity needed | An FK that proves "a job title", not just "some value" | "One of this field's codes", checked on write (§6) |

**Rule of thumb.** If other rows point at it, or it has attributes of its own → its own table
(job titles; later deal stages, which carry a probability and won/lost). If it's a choice stored
in a field → a picklist.

---

## 3. What a record stores (D1)

| Store | Renaming a label | SQL / reports | Imports, integrations | Fits a text slot | |
|---|---|---|---|---|---|
| The label (`"Food & Beverage"`) | Rewrites every record | Readable | Readable | Yes | ❌ a rename is a mass update, and labels can't be translated later |
| The value's uuid | Free | Opaque without a join | Opaque ids in every payload | Only as text, and a slot can't carry an FK | ❌ |
| **A code** (`"food_beverage"`) | Free | Readable; join for the label | Stable, readable keys | Yes | ✅ |

The large CRMs agree. Salesforce stores a value's API name, which is separate from its label.
HubSpot stores an option's internal value, and Dynamics stores an integer per option. All of
them store a stable key, and none stores the label.

**Code rules**

- `^[a-z0-9]+(_[a-z0-9]+)*$`, at most 50 characters: `technology`, `food_beverage`, `1_10`.
- Unique within its picklist and **write-once**, like `field_key`. Users see the label; a code
  is never renamed.
- Case-sensitive: `"Technology"` is not a valid Industry. Strict, so the database holds one spelling.
- Derived from the label when a tenant adds a value without one (§10.3). Seed codes are written out.

Every column holding a standard picklist must fit 50 characters. Three already do;
**`employee_range` widens from `varchar(30)` to `varchar(50)`**. A custom picklist's slot is
`varchar(255)`.

---

## 4. Data model (D2, D3)

Both tables go in `V1__init.sql`: the reference table with the other `public` data, the tenant
table after `app.custom_field_definitions`. Then `make db-reset` (dev-phase policy).

```
which fields are picklists            custom: custom_field_definitions.data_type = 'PICKLIST'
                                      standard: dataType PICKLIST in the provider

public.standard_picklist_values ──copied at provisioning──► app.picklist_values
   (global, read-only seed)                                    (per tenant, editable)
                                                                       ┊
                                   matched by (entity_type, field_key) + code in RecordMapper, no FK
                                                                       ┊
                            app.companies.industry = 'technology'      app.companies.cf_text_07 = 'referral'
                               (standard field)                           (custom field "lead_source")
```

### 4.1 `app.picklist_values`

The tenant's lists, for standard and custom fields alike. It follows the tenant-table recipe
(`multitenancy.md` §17).

```sql
create table app.picklist_values
(
    id          uuid         not null default gen_random_uuid(),
    tenant_id   uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    entity_type varchar(50)  not null
        constraint ck_picklist_values_entity_type check (entity_type in ('COMPANY', 'CONTACT', 'DEAL')),
    field_key   varchar(100) collate app.case_insensitive not null, -- standard key or custom field_key
    code        varchar(50)  not null
        constraint ck_picklist_values_code check (code ~ '^[a-z0-9]+(_[a-z0-9]+)*$'),
    label       varchar(100) collate app.case_insensitive not null,
    sort_order  int          not null,
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint pk_picklist_values primary key (tenant_id, id),
    constraint uq_picklist_values_code unique (tenant_id, entity_type, field_key, code),
    -- Deferred, so one PUT can swap two labels.
    constraint uq_picklist_values_label unique (tenant_id, entity_type, field_key, label)
        deferrable initially deferred
);
```

- `field_key` has the same collation as `custom_field_definitions.field_key` and holds the
  field's own spelling. Standard and custom keys share one keyspace per entity
  (`StandardFieldRegistry.reservedKeysFor`), so two fields can't share a picklist by accident.
- `uq_picklist_values_code` leads with `(tenant_id, entity_type, field_key)`. It serves both
  reads, all of an entity's values and one field's, so no extra index is needed.
- The flag is `active` rather than `archived_at`. Switching a value off and on again is a routine
  toggle, not a step towards a purge.
- `sort_order` isn't unique: the order is whatever the last `PUT` sent.
- `label` is unique ignoring case (`Retail` and `retail` clash). The collation affects
  comparison only; the label keeps the case it was typed in.

RLS, the `tenant_isolation` policy, and `select, insert, update, delete` for `blusimian_app`, as
on every tenant table. `RowLevelSecurityVerifier` checks them at startup.

### 4.2 `public.standard_picklist_values`

The values a new tenant's standard picklists start with. Global reference data, like
`public.iso_countries`: no `tenant_id`, no RLS, read-only to the app.

```sql
create table public.standard_picklist_values
(
    entity_type varchar(50)  not null, -- entity_type and field_key: held to the providers by the verifier
    field_key   varchar(100) collate app.case_insensitive not null,
    code        varchar(50)  not null
        constraint ck_spv_code check (code ~ '^[a-z0-9]+(_[a-z0-9]+)*$'),
    label       varchar(100) collate app.case_insensitive not null,
    sort_order  int          not null,

    constraint pk_standard_picklist_values primary key (entity_type, field_key, code),
    constraint uq_standard_picklist_values_label unique (entity_type, field_key, label)
);

grant select on public.standard_picklist_values to blusimian_app;

insert into public.standard_picklist_values (entity_type, field_key, code, label, sort_order)
values ('COMPANY', 'type', 'prospect', 'Prospect', 1),
       ('COMPANY', 'type', 'customer', 'Customer', 2),
       …
       ('CONTACT', 'salutation', 'prof', 'Prof.', 6);
```

The code and label constraints mirror `app.picklist_values`, so anything the seed holds copies
cleanly. `entity_type` and `field_key` get no CHECK: the verifier (§8.4) holds every seeded pair
to a standard `PICKLIST` field, which is stricter than an entity list, and adding an entity type
then needs no edit here. The values are in §8.2.

### 4.3 Why there's no picklist table

Whether a field is a picklist is already stored exactly once: on the custom field's definition,
or in the standard field's provider. A `picklists` table with one row per field would be a
second copy that has to be seeded and kept in step with them. What such a row would do is
covered without it:

| A parent row would… | Covered by |
|---|---|
| say that a field is a picklist | The definition's `data_type`, or the provider: one source each |
| be what a `PUT` locks | `CustomFieldService.lockEntityFields`, the advisory lock field creation and role saves already take (§10.5) |
| cascade its values away on a purge | The purge deletes by `(entity_type, field_key)` (§9.3) |
| hold a picklist-level setting later | A custom field's definition. A standard field's provider, if the setting is code-owned. §17 covers a tenant-adjustable one |

The one cost is orphans. If a field stops being a picklist (a provider change, or a purge that
forgets its values), its rows stay behind. They do no harm, because only `PICKLIST` fields are
ever looked up. But a purge must delete them before the key can be reused, or the next field
with that key would inherit them.

### 4.4 Why the record has no FK to the value (D3)

A custom picklist's value lives in a slot, and a slot can't carry an FK: `cf_text_07` is Lead
Source for one tenant and Region for another. A standard column could, but only with constants in
the key, `(tenant_id, 'COMPANY', 'industry', industry)`. Postgres can express that only with an
extra generated column per field. That is a lot of schema to protect one write path, which
already checks every rule, and standard and custom picklists would then behave differently.

| An FK would… | Covered instead by |
|---|---|
| reject an unknown code | `RecordMapper` (§6) |
| block deleting a value in use | Values are never deleted (D6) |
| cascade a code rename | Codes are write-once; only labels change |

### 4.5 In SQL

A report gets labels with one join:

```sql
select c.name, v.label as industry
from app.companies c
left join app.picklist_values v
       on v.tenant_id = c.tenant_id
      and v.entity_type = 'COMPANY' and v.field_key = 'industry'
      and v.code = c.industry;
```

Filtering needs no join: `where industry = 'technology'` compares codes, and can use an index on
the column once filters exist.

---

## 5. Backend

### 5.1 What changes in existing code

Small on purpose. `FieldDescriptor`, `EntityFieldDictionary` and `CustomFieldDescriptors` don't change.

| Where | Change |
|---|---|
| `FieldDataType` | `PICKLIST(CustomFieldSlots.POOL_TEXT, String.class, null)`: text pool, arrives as a JSON string, no format |
| `CompanyStandardFieldProvider`, `ContactStandardFieldProvider` | `type`, `industry`, `employeeRange` and `salutation` change their data type to `PICKLIST`. `employeeRange` becomes `withMaxLength(50)` |
| `RecordMapper.apply` | One check after conversion, for `PICKLIST` fields only (§6) |
| `EntitySchemaService` / `SchemaFieldResponse` | The bundle carries each picklist field's options (§7) |
| `CustomFieldDefinitionRequest`, `CustomFieldService`, `CustomFieldCreated` | Options on create, carried on the event (§9.1) |
| `V1__init.sql` | §4.1 and §4.2; `ck_cfd_data_type` gains `'PICKLIST'`, `ck_cfd_type_matches_slot` gains `when 'PICKLIST' then column_name like 'cf\_text\_%'`; `employee_range varchar(50)` |

A standard field's declaration changes by one word:

```java
FieldDescriptor.of("industry", "Industry", FieldDataType.PICKLIST, String.class,
        Company::getIndustry, Company::setIndustry).withMaxLength(100),
```

The keys stay the same, so the permission columns (`industry_read`, …), layouts and
`FieldPermissionCatalog` are untouched.

### 5.2 New package `picklists`

| File | Role |
|---|---|
| `PicklistValue` | `@Entity` on `app.picklist_values`. `entityType`, `fieldKey` and `code` are `updatable = false`; `relabel`, `moveTo`, `activate`/`deactivate` |
| `PicklistValueRepository` | `findByEntityTypeOrderBySortOrderAsc(type)`, `findByEntityTypeAndFieldKeyOrderBySortOrderAsc(type, key)` |
| `PicklistOption` | Record `(code, label, active)`: one option as the bundle serves it |
| `EntityPicklists` | Immutable: one entity's options, by field key (case-insensitive). `optionsFor(key)` and `allows(key, value, current)` (§6) |
| `PicklistLookup` | **The resolver.** `forEntity(type)` → `EntityPicklists`, in one query. The same path for standard and custom fields |
| `PicklistService` | Admin side: `listFor(type)` for the `GET`, `replace(type, fieldKey, request)` for the `PUT`, `seed(type, fieldKey, values)` for a new custom field |
| `PicklistCodes` | Pure functions: derive a code from a label and deduplicate it (§10.3) |
| `StandardPicklistsInitializer` | `TenantInitializer`: copies the reference table into the new tenant (§8.3) |
| `StandardPicklistsVerifier` | Refuses to start if the reference table and the providers disagree (§8.4) |
| `CustomFieldPicklists` | Listens to `CustomFieldCreated` and seeds a new custom picklist (§9.1), as `roles.RequiredFieldGrants` listens for required fields |
| `PicklistController` | §10.1 |
| `dto/` | `PicklistResponse` (`entityType`, `fieldKey`, `label`, `options` as `PicklistOption`s, the bundle's shape); `ReplacePicklistRequest` (`options`); `PicklistOptionRequest` (`code` optional, `label`, `active`) |
| `exception/PicklistNotFoundException` | 404 `NOT_FOUND`. A broken request rule is a `BadRequestException(VALIDATION_ERROR, …)` with details per value, as on records |

### 5.3 Resolving options (D5)

A picklist field's options are whatever `app.picklist_values` holds for its entity type and field
key. That holds for every picklist field, standard or custom. Nothing at runtime knows that a
standard list started from a seed.

```java
public final class EntityPicklists {

    /** The field's options in display order, inactive included; empty if it has none. */
    public List<PicklistOption> optionsFor(String fieldKey) { … }

    /** §6: null, an active code, or the code the record already holds. */
    public boolean allows(String fieldKey, Object value, Object current) { … }
}

@Component
public class PicklistLookup {

    /** Every picklist value of the entity, grouped by field key: one query. */
    public EntityPicklists forEntity(EntityType entityType) { … }
}
```

It has two runtime users, and each calls it at most once per request:

| Caller | Uses | When |
|---|---|---|
| `RecordMapper.apply` | `allows` | Only if the request writes a `PICKLIST` field. Other writes, and every read, skip the query |
| `EntitySchemaService.getSchema` | `optionsFor` | Once per bundle |

`PicklistService` uses it too, for the `GET`.

Why a lookup and not options on the descriptor: the descriptor describes a field's shape and
storage, which for a standard field is a constant. Options are tenant data, needed in exactly two
places. Asking for them there keeps `FieldDescriptor` and its five constructor sites unchanged,
and the layout editor's dictionary reads don't pay for a query they'd ignore. The write path and
the bundle still can't disagree, because both read the same lookup.

A picklist field with no rows has an empty list: an empty select, and a 400 for any new value.
For standard fields provisioning, the verifier and the backfill rule (§8) make that state
unreachable. A custom picklist can't be created without options.

### 5.4 Dependency direction

```
entityModel   ──► platform customFields picklists                (RecordMapper → PicklistLookup)
entitySchema  ──► entityModel entityLayouts customFields picklists (the bundle)
picklists     ──► entityModel customFields platform tenants      (vocabulary; CustomFieldCreated
                                                                  and the lock; TenantInitializer)
customFields  ──► entityModel platform                           (unchanged: it doesn't know picklists exists)
companies,
contacts      ──► entityModel                                    (unchanged: only a data type changes)
```

`entityModel ⇄ picklists` is a package cycle of the same kind as `entityModel ⇄ customFields`
(`custom-fields.md` §4): vocabulary one way, a lookup the other. `entitySchema` composing picklists
into the bundle is what that slice is for: "composed at the edge while each feature stays
modular". None of it is a bean cycle, and the build must keep it that way:

```
RecordMapper, EntitySchemaService → PicklistLookup → PicklistValueRepository
PicklistService → EntityFieldDictionary, CustomFieldService (lock only), PicklistLookup, repository
CustomFieldService ⇢ publishes CustomFieldCreated ⇢ CustomFieldPicklists → PicklistService
```

Two rules for the build:

- **`PicklistLookup` injects its repository and nothing else.** It sits under `RecordMapper`.
- **`CustomFieldService` must not inject `PicklistService`.** `PicklistService → EntityFieldDictionary
  → CustomFieldDescriptors → CustomFieldService` would close into a constructor cycle, and startup
  would fail. It publishes the event instead, as it already does for `roles`.

---

## 6. Validation on write (D6, D7)

`RecordMapper.apply` calls `EntityPicklists.allows(key, value, current)` for each `PICKLIST` field
in the request, after conversion. It is true when any of these holds:

1. `value` is `null`, i.e. the field is being cleared (required is checked separately, as for every field);
2. `value` is the code of an **active** option of that field;
3. `value` equals `current`, the code the record already holds.

Otherwise the field gets `FieldError(key, "Not one of the options")`, collected with any other
errors into the one 400.

Rule 3 is what makes deactivation safe under merge-patch semantics. Say a client GETs a company
whose industry has been deactivated, edits the name and PUTs the whole record back. The industry
arrives unchanged, so it saves. A *different* inactive code, or one that never existed, is
refused. Rule 3 also means a stored code that isn't in the list at all never blocks a save that
leaves it alone.

```
PATCH /api/companies/{id}  { "industry": "technology" }   → 200
PATCH /api/companies/{id}  { "industry": "Technology" }   → 400  industry: Not one of the options
PATCH /api/companies/{id}  { "industry": "" }             → 400  industry: Not one of the options
PATCH /api/companies/{id}  { "industry": null }           → 200  cleared
PUT   /api/companies/{id}  (industry 'shipping', now inactive, sent back unchanged) → 200
POST  /api/companies       { "name": "X", "industry": "shipping" }                  → 400
```

`""` is refused rather than turned into `null`, so the database has one spelling of "none". The
frontend sends `null` for "—".

A value deactivated while someone is saving a record with it may still be stored, because record
writes don't lock the list. That's harmless: an inactive value is a valid stored value (rule 3).

---

## 7. The schema bundle

`EntitySchemaService.getSchema` calls `PicklistLookup.forEntity` once and passes the result to
`toResponse`. `SchemaFieldResponse` gains `List<PicklistOption> options`. It is `null` except on a
`PICKLIST` field, comes in `sort_order`, and **includes inactive values** (`"active": false`), so
the client can label a record that holds one.

```json
{ "key": "industry", "label": "Industry", "dataType": "PICKLIST", "required": false,
  "maxLength": 100, "referenceTo": null,
  "options": [ { "code": "agriculture", "label": "Agriculture", "active": true },
               { "code": "shipping",    "label": "Shipping",    "active": false } ] }
```

The ETag is a hash of the bundle's content, so editing a picklist changes it with no extra code,
and the next revalidation gets a 200. The bundle stays "everything the client needs to render
and validate a record".

Size: the seeded picklists add about 55 options across two bundles. A picklist is capped at 500
values (§10.2).

---

## 8. Standard picklists (D8)

### 8.1 Declared by data type, seeded from SQL, then the tenant's

Which standard fields are picklists is code: the provider's data type. What they start with is
reference data in V1. After provisioning the values belong to the tenant, to edit like default
layouts and roles. Changing the seed later affects new tenants only (§17).

### 8.2 Seed values

Codes are the label in snake case unless shown.

| Field | Values |
|---|---|
| Company · Type (7) | Prospect, Customer, Partner, Reseller, Vendor, Competitor, Other |
| Company · Industry (33) | Agriculture, Apparel, Banking, Biotechnology, Chemicals, Communications, Construction, Consulting, Education, Electronics, Energy, Engineering, Entertainment, Environmental, Finance, Food & Beverage (`food_beverage`), Government, Healthcare, Hospitality, Insurance, Machinery, Manufacturing, Media, Not for Profit, Real Estate, Recreation, Retail, Shipping, Technology, Telecommunications, Transportation, Utilities, Other |
| Company · Employee Range (8) | 1–10 (`1_10`), 11–50 (`11_50`), 51–200 (`51_200`), 201–500 (`201_500`), 501–1,000 (`501_1000`), 1,001–5,000 (`1001_5000`), 5,001–10,000 (`5001_10000`), 10,001+ (`10001_plus`) |
| Contact · Salutation (6) | Mr. (`mr`), Ms. (`ms`), Mrs. (`mrs`), Mx. (`mx`), Dr. (`dr`), Prof. (`prof`) |

- **Industry** is the standard sales-oriented list: Salesforce's long-standing default Industry
  list, with Real Estate added because that list lacks it. It is alphabetical, with Other last.
  Check it against the current Salesforce list before seeding.
- **Employee Range** uses LinkedIn's company-size bands, with its "1" and "2–10" merged. Data
  enrichment providers use the same bands, so imported data maps one to one. EU SME bands (1–9,
  10–49, 50–249, 250+, as KvK and Eurostat use) were the alternative. They don't line up with
  LinkedIn's, so it had to be one or the other.
- Labels are English for now. Translations would be keyed on the value later (§13).

### 8.3 Provisioning

`StandardPicklistsInitializer.initialize` runs one statement inside the provisioning transaction:

```sql
insert into app.picklist_values (entity_type, field_key, code, label, sort_order)
select entity_type, field_key, code, label, sort_order
from public.standard_picklist_values;
```

`tenant_id` comes from its default, which is the tenant being provisioned, and RLS's `with
check` confirms it. The initializer doesn't depend on the order of the other initializers.

**Flush first.** At this point the tenant row is still a pending JPA insert, and
`picklist_values.tenant_id` references it. Hibernate doesn't flush for a statement it didn't
issue, so the initializer calls `entityManager.flush()` and then runs the copy through the
`JdbcClient`. That is the same connection and tenant binding `lockEntityFields` uses.

### 8.4 Keeping the seed and the providers in step

The seed is SQL and the providers are Java, so nothing at compile time ties them together.
`StandardPicklistsVerifier` does it at startup, modelled on `PermissionColumnsVerifier`: a
`SmartInitializingSingleton`, a query on a `public` table (no tenant needed), and a pure `compare`
function that is unit-tested without a database. It refuses to start when:

- a standard `PICKLIST` field has no seed rows;
- seed rows name an entity and key that isn't a standard `PICKLIST` field (spelled exactly as the provider spells it);
- a seed code is longer than its field's `maxLength`.

Every `@SpringBootTest` boots through it, so a mismatch fails the test suite as well as startup.

### 8.5 A standard picklist added later

Its migration does both halves in SQL. It adds the seed rows, then copies them into every
existing tenant:

```sql
insert into public.standard_picklist_values (entity_type, field_key, code, label, sort_order)
values ('DEAL', 'lossReason', 'price', 'Price', 1), …;

insert into app.picklist_values (tenant_id, entity_type, field_key, code, label, sort_order)
select t.id, s.entity_type, s.field_key, s.code, s.label, s.sort_order
from app.tenants t
cross join public.standard_picklist_values s
where s.entity_type = 'DEAL' and s.field_key = 'lossReason';
```

Flyway runs as `blusimian_owner`, which owns the table and isn't subject to its RLS (it isn't
`FORCE`d, `multitenancy.md` §4), so one statement covers every tenant. `tenant_id` is given
explicitly because no tenant is bound in a migration. New tenants get the field through §8.3
unchanged. In the dev phase, editing V1 and `make db-reset` does the same.

---

## 9. Custom picklist fields (D9)

### 9.1 Creating one

`CustomFieldDefinitionRequest` gains `List<CustomFieldOptionRequest> options`, each a
`(code, label)` with `code` optional:

```
POST /api/custom-fields/company
{ "fieldKey": "lead_source", "label": "Lead Source", "dataType": "PICKLIST", "sortOrder": 40,
  "options": [ { "label": "Web" }, { "label": "Referral" }, { "label": "Event" },
               { "label": "Cold outreach", "code": "outbound" } ] }
201 → the definition, as today
```

1. `CustomFieldService.create` checks the shape: `PICKLIST` needs at least one option, and any
   other type with `options` → 400. Both are checked before a slot is allocated.
2. It allocates a text slot as it would for `TEXT`, saves the definition, and publishes
   `CustomFieldCreated`. The event gains `fieldKey` and `options`. `roles.RequiredFieldGrants`
   ignores both.
3. `picklists.CustomFieldPicklists` hears the event in the same transaction and calls
   `PicklistService.seed`, which applies the same rules as a `PUT` (§10.2). A broken rule throws
   out of `publishEvent`, so the whole create rolls back and the client gets the 400.

`CustomFieldOptionRequest` lives in `customFields` and mirrors `picklists.PicklistOptionRequest`
without `active`, because a new option is always active. The near-duplicate is the price of
`customFields` not knowing `picklists`, the same trade the event already makes for `roles`.

From then on its options resolve through `PicklistLookup`, exactly like a standard field's.
`CustomFieldDefinitionResponse` doesn't return them. The bundle and `GET /api/picklists/{entityType}` do.

### 9.2 Slots and permissions

A custom picklist takes one of the 40 text slots, and with it that slot's `_read`/`_edit` pair in
the permission tables. There is no new pool, no new permission column, and no change to
`scripts/gen_slots.py`. The cost is that picklists compete with text fields for the pool that
`custom-fields.md` §10 already names as the most likely to run out. If it does, the fix is a
dedicated `cf_pick_*` pool (§13).

### 9.3 Archive and purge

- **Archive** (today's `DELETE`) leaves the values in place. The archived field is still
  writable, and rule 3 needs its codes.
- **Purge** (deferred, `custom-fields.md` §8) must also
  `delete from app.picklist_values where entity_type = ? and field_key = ?`, in the same
  transaction and before the key is released. That goes on the purge checklist, next to resetting
  the slot's permission pair.

---

## 10. Editing values (D10, D11)

### 10.1 Endpoints

Base path `/api/picklists`. `{entityType}` binds as everywhere else (`company`, case-insensitive),
and `{fieldKey}` is case-insensitive.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/{entityType}` | — | 200 `[PicklistResponse]`: every picklist field of the entity (standard and active custom), with its label and options, inactive ones included | 400 unknown entityType |
| PUT | `/{entityType}/{fieldKey}` | `ReplacePicklistRequest` | 200 `PicklistResponse` | 400 a rule broken (§10.2); 404 no such picklist field |

Both resolve fields through `EntityFieldDictionary`, which is already the truth about which
fields exist, and values through `PicklistLookup`. A key that isn't there, belongs to an archived
custom field, or isn't a `PICKLIST` is a 404. There's no POST or DELETE: a standard picklist exists
because its provider declares it, and a custom one is created and removed with its field. Value
ids never leave the server: the API identifies values by code, just as it never exposes `columnName`.

```
PUT /api/picklists/company/industry
{ "options": [
    { "code": "agriculture", "label": "Agriculture",          "active": true  },
    …
    { "code": "shipping",    "label": "Shipping & Logistics", "active": false },
    {                        "label": "Renewable Energy",     "active": true  }
] }
200 → { "entityType": "COMPANY", "fieldKey": "industry", "label": "Industry",
        "options": [ …, { "code": "renewable_energy", "label": "Renewable Energy", "active": true } ] }
```

### 10.2 What a PUT may do

The array is the whole list, in display order. Compared with the stored list:

| An item… | means |
|---|---|
| with a stored `code` | update its label, position and `active` |
| without a `code` | a new value; the code is derived from the label (§10.3) |
| with a `code` not stored yet | a new value with that code (for imports and integrations) |
| a stored code missing from the array | **400**: "Deactivate a value instead of removing it" |

The rules are all checked before anything is written. Every failure comes back in one 400, with
details per item (`options[3].label`):

- a label is not blank, at most 100 characters, and unique in the list ignoring case. "Ignoring
  case" must mean exactly what the column's collation means (`und-u-ks-level2`: case-insensitive,
  accent-sensitive), so compare with a root-locale `java.text.Collator` at `SECONDARY` strength,
  not `toLowerCase`. Where the two disagree, the deferred unique fires at commit, outside the
  service, and the client gets a generic error instead of a field detail;
- a given code matches the pattern, is at most 50 characters, and appears once;
- at least one value is active;
- the list has at most 500 values.

`seed` applies the same rules to a new list. The database constraints (`ck_picklist_values_code`
and both uniques) remain the backstop.

### 10.3 Deriving a code

`PicklistCodes.derive(label, taken)` works in five steps:

1. Strip accents: NFD, then drop combining marks.
2. Lower-case.
3. Replace each run of characters outside `[a-z0-9]` with `_`, and trim `_` from both ends.
4. Cut to 50 characters, then trim `_` again.
5. An empty result (a label with no Latin letters or digits) becomes `value`. A code that's
   already taken, active or not, gets `_2`, `_3`, … appended, still within 50 characters.

`"Food & Beverage"` → `food_beverage` · `"Café"` → `cafe` · `"日本"` → `value`.

It's only used for values that tenants add. The seed spells its codes out. The settings UI will
later show the code read-only, as "API name".

### 10.4 Permission

`manage_custom_fields` gates the `PUT`, and the custom field `POST` as it already does. It isn't
enforced yet (`roles-and-permissions.md` D14); when enforcement lands, this endpoint joins its
list. A separate `manage_picklists` column isn't worth a change to the role editor: whoever may
add fields may edit their values. The Roles page can relabel the permission "Manage fields" to
say so.

### 10.5 Concurrency

`replace` first takes `CustomFieldService.lockEntityFields(entityType)`, the transaction-scoped
advisory lock that field creation and role saves already use. Two admins saving the same list run
one after the other, and the second sees the first's new codes. A save also can't interleave with
a custom field being created on the same entity. On labels and order the last write wins, as in
the layout editor. It's an admin action a few times a day, so serialising per entity costs nothing.

---

## 11. Lifecycle of a value

| Action | How | Effect on records |
|---|---|---|
| Add | `PUT` with a new item | — |
| Rename | `PUT` with a new label | None: they hold the code |
| Reorder | `PUT` in the new order | — |
| Deactivate | `PUT` with `active: false` | They keep it, still shown with its label. No record can newly pick it |
| Reactivate | `PUT` with `active: true` | — |
| Delete, or merge into another value | **Deferred** (§13) | Would rewrite the records that hold it |

---

## 12. Frontend

### 12.1 Record pages (in this build)

| File | Change |
|---|---|
| `lib/entity/types.ts` | `FieldDataType` gains `"PICKLIST"`; `SchemaField.options: PicklistOption[] \| null`; `type PicklistOption = { code: string; label: string; active: boolean }` |
| `lib/entity/displayValue.ts` (new) | `displayValue(field, value)`: a picklist code → its option's label (an unknown code → the code itself), anything else → `text(value)`. The one place a code becomes a label |
| `components/fields/PicklistField.tsx` | Display: `displayValue`. Input: the existing `Select`, with "—" (→ `null`), the active options, and the current value when it's inactive ("Shipping (inactive)") |
| `components/fields/fieldTypes.tsx` | `PICKLIST: PicklistField`, the one registry line |
| `components/entity/RecordDetail.tsx` | The header subtitle (`subtitleKeys`, `type` and `industry` for a company) prints `text(record[key])` today, which would be the code. It goes through `displayValue`, finding the field in the schema (`useEntitySchema`, already cached), not in the layout: the subtitle shows those keys even when the layout doesn't place them |
| `features/companies/CompaniesPage.tsx` | The Type and Industry columns print raw values today. They call `displayValue`, and the page loads the schema with `useEntitySchema("COMPANY")` (one cached, ETagged request) |
| `features/settings/layouts/editor/FieldChip.tsx` | The type-label map gains `PICKLIST: "Picklist"` (TypeScript will insist) |
| `lib/entity/validateField.ts` | No new rule: the select can only produce valid codes, and the server is the authority. Its "mirrors RecordMapper rule for rule" comment says the option rule is left to the select |

### 12.2 Settings (later, not in this build)

The settings UI belongs on the entity settings page (`/settings/:entity`, `settings.md` §5), next
to Layouts, built together with the custom fields UI:

- A **Fields** section lists the entity's fields, standard and custom. A picklist field opens its
  values editor at `/settings/:entity/fields/:fieldKey`.
- The editor has one row per value (label, API name read-only, active toggle), drag to reorder
  (`@dnd-kit`, as in the layout editor), "Add value" and "Sort A–Z", which only rewrites the
  order. Inactive values sit behind a toggle.
- Save sends one `PUT`, with `useUnsavedChangesGuard`.
- Once enforcement exists, the section is gated by `manage_custom_fields`.

The API in §10 is complete for it, so nothing on the backend waits for the UI.

---

## 13. Considered and left out

| Idea | Why not, or not yet |
|---|---|
| A parent `app.picklists` table, one row per picklist field | It would repeat what the definition and the provider already say, and would have to be seeded and kept in step with them. Its jobs are covered (§4.3) |
| Options on `FieldDescriptor`, attached per request | It changes the record and its five constructor sites, and makes every dictionary read pay for a lookup, when only two callers need options (§5.3) |
| Seed lists in Java (on the descriptor, or in a template class) | Provisioning would work the same, but the seed would sit on runtime code that never needs it, and a later backfill would have to repeat the values in SQL or loop over tenants in Java. In SQL, provisioning and migrations read one table |
| Serve the seed until a tenant first edits (copy-on-write) | It saves the copy, but every lookup would need a fallback, which is two sources of truth, and SQL would find no labels for a tenant who never edited |
| Options in `custom_field_definitions.config` (the earlier plan) | Standard fields have no definition row. Inside jsonb, codes and labels can't be kept unique, every edit rewrites the blob, and SQL can't join to labels |
| An FK from the record to the value | §4.4 |
| A dedicated `cf_pick_*` slot pool | New columns on three record tables and three permission tables. Build it if the text pool runs out |
| Global value sets (several fields share one list) | They'd need a value-set table that fields bind to, which is when a parent table earns its place. Additive |
| Multi-select picklists | They store several codes per record (a `text[]` pool or a link table). The value table works for them as it is |
| Dependent picklists (state depends on country) | A mapping table between value ids; additive |
| A default value | An `is_default` column on the value, with a partial unique index (one per picklist), applied on create; additive |
| Translated labels | `picklist_value_labels (value_id, locale, label)`; additive |
| Delete or merge a value | "Replace with" must rewrite every record that holds the value, a native UPDATE on the field's column (a standard column or a slot). It's the same machinery as the custom field purge, so build them together |
| Deal stages as a picklist | A stage carries a probability and open/won/lost, and later a pipeline: its own table (§2.2) |
| Accept codes case-insensitively | One more normalisation rule, when an import can map instead. Revisit if it hurts |

---

## 14. Known limits and settled questions

### 14.1 Known limits

| Limit | Why it's acceptable now | What changes it |
|---|---|---|
| No cache: one indexed query per bundle, and per write that touches a picklist | Custom field definitions are loaded the same way, on every `apply`. Sizes are small | A bulk import repeats the lookup per record. Cache both together, per tenant and entity, evicted by the admin writes |
| The bundle carries every option inline | About 55 options today. The worst case (44 picklist fields × 500) is far beyond real use | If a tenant gets there, serve large lists from a per-field endpoint, as countries are |
| Sorting a list by a picklist field sorts by code | Lists have no sorting yet | The sort feature joins `picklist_values` (§4.5) to sort by label or position. Search by label must translate label → code first |
| A mistaken value can only be deactivated | Values in use must never vanish (D6) | Delete-with-replace (§13) |
| A deactivated value's label stays taken | Correct: re-adding "Retail" should mean reactivating it, not creating a twin | The settings UI should say so |

### 14.2 Settled questions

None are open.

| Question | Decision |
|---|---|
| Employee range bands | LinkedIn's bands, with "1" and "2–10" merged (§8.2) |
| Salutation | A picklist, like the three company fields |
| Industry list | The short, sales-oriented list (§8.2), not a statistical taxonomy |
| Backfilling a standard picklist added later | Its own migration, in SQL (§8.5) |

---

## 15. Build plan

Each phase ends with `./mvnw test` green, so each can be committed on its own.

| # | Phase | Steps | Done when |
|---|---|---|---|
| 1 | Schema | V1: `public.standard_picklist_values` with its seed rows (§4.2, §8.2), `app.picklist_values` (§4.1); `PICKLIST` in `ck_cfd_data_type` and `ck_cfd_type_matches_slot`; `employee_range varchar(50)`. Then `make db-reset` | The app starts and `RowLevelSecurityVerifier` passes |
| 2 | Resolver | `FieldDataType.PICKLIST`; `PicklistValue`, repository, `PicklistOption`, `EntityPicklists`, `PicklistLookup`; the check in `RecordMapper.apply`; `options` in the bundle | `EntityPicklistsTest` and `RecordMapperTest` cover §6 |
| 3 | Standard picklists | The four providers' data types; `StandardPicklistsInitializer`; `StandardPicklistsVerifier`; fix the tests that write free text into those fields | The verifier passes; a new tenant has the four lists; `"Software"` as industry is a 400; the bundle carries the options |
| 4 | Admin API | `PicklistCodes`, `PicklistService` (`listFor`, `replace`, `seed`), controller, DTOs, exception | `PicklistCodesTest` and `PicklistApiTest` pass; the bundle's ETag changes after a `PUT` |
| 5 | Custom picklists | `options` on `CustomFieldDefinitionRequest`, keeping a constructor without it for the existing call sites (the demo seeder, tests); `fieldKey` and `options` on `CustomFieldCreated`; `CustomFieldPicklists`; Acme's demo `lead_source` becomes a `PICKLIST` (Web, Referral, Event, Partner, Cold outreach, Other) | `CustomFieldPicklistTest` passes; Acme's Lead Source is a dropdown |
| 6 | Frontend | §12.1, including the record header and the companies list | `npm run build` and `npm test` pass; company and contact pages show dropdowns; no code is shown where a label belongs |
| 7 | Verify | Full suites, then §15.3 by hand | — |
| 8 | Docs | §16, then turn this file into `Architecture/picklists.md` as the as-built reference | — |

### 15.1 Tests to add

| Test | Covers |
|---|---|
| `EntityPicklistsTest` | The three rules of §6; keys matched case-insensitively; an unknown field has no options |
| `RecordMapperTest` (extended) | An active code saves; unknown, wrong case and `""` are refused; an inactive code is refused on create but kept when unchanged; `null` clears; no lookup when no picklist field is written |
| `StandardPicklistsVerifierTest` | `compare`, without a database: a field with no seed, a seed for a field that isn't a picklist, a code over `maxLength` |
| `PicklistCodesTest` | Derivation, accents, empty → `value`, the dedup suffix, the 50-character cut |
| `PicklistApiTest` (real Postgres, as `blusimian_app`) | Provisioning copies every seed row; every row of §10.2; swapping two labels in one `PUT` (the deferred unique); 404 for a field that isn't a picklist and for an archived custom one |
| `CustomFieldPicklistTest` | Create with options; `PICKLIST` without options → 400; options on `TEXT` → 400; a bad option rolls back the field and frees its slot |
| `TenantIsolationTest` (extended) | Tenant B sees none of A's values, and A's edits to Industry don't change B's list |

### 15.2 Existing tests to update

`RecordMapperTest` writes `"Software"` / `"Hardware"` into `industry`, and `ContactRecordTest`
writes `"Dr."` into `salutation`. Move them to another `TEXT` field, or to codes with a stub
`PicklistLookup`.

### 15.3 By hand

1. `make db-reset`, start the app, log in as alice. Company Type, Industry and Employee Range, and
   Contact Salutation, are dropdowns with the seeded values.
2. Save a company with an industry. The record JSON holds `"technology"`. The page, its header
   subtitle and the companies list all show "Technology".
3. `PUT` a new label ("Technology" → "Tech & Software"). After a reload the page shows the new
   label, and the record JSON still says `technology`.
4. Deactivate it. The company still shows it, marked inactive in edit mode. Saving another field
   on that company works, and a new company can't pick it.
5. Acme's Lead Source is a dropdown. bob's company form has no Lead Source, and his Industry list
   doesn't have alice's change.
6. `PATCH` with `"industry": "Software"` → 400 with a field error.
7. In `make db-shell`, delete Salutation's rows from `public.standard_picklist_values` and
   restart: the verifier refuses to start and names the field. `make db-reset` afterwards.

---

## 16. Docs and comments to update

| Where | Change |
|---|---|
| `Architecture/custom-fields.md` | §2: `config` no longer holds picklist options, and the `data_type` CHECK gains `PICKLIST`. §3: the `FieldDataType` row. §4: the graph gains `entityModel → picklists` and `entitySchema → picklists`. §7 step 5: `CustomFieldCreated` carries `fieldKey` and `options`, and `picklists` listens too. §8: purge also deletes the field's values. §10: picklists come off "Deferred". §12: a new entity type also extends `ck_picklist_values_entity_type` |
| `entityModel/package-info.java` | Its one exception to "depends on nothing of ours" (`RecordMapper` → `customFields`) gains `RecordMapper` → `picklists.PicklistLookup` |
| `Architecture/entity-layouts.md` | The bundle's field shape gains `options` |
| `Architecture/frontend-entity-pages.md` | The field registry gains `PICKLIST` |
| `Architecture/multitenancy.md` | §5.7: the new tenant table and the new `public` table |
| `Architecture/roles-and-permissions.md` | §2.5: `manage_custom_fields` also covers picklist values. §3.8: `lockEntityFields` is also taken by picklist saves |
| `WIP/job-title-layouts.md` §3 | The two rows about picklists: custom picklists don't use `config`, and `Company.type` and `industry` are picklists now |
| `V1__init.sql` | The header's schema layout (`public` also holds `standard_picklist_values`); the `config` comment on `custom_field_definitions` ("picklist options, …") |
| `FieldDataType.java`, `CustomFieldCreated.java` | Their Javadoc: `PICKLIST`; the new event components |

---

## 17. Recipes (after this lands)

| To | Do |
|---|---|
| Make a standard field a picklist | Set its data type to `PICKLIST` in the provider; add its rows to `public.standard_picklist_values`; make sure its column fits 50 characters. Once real tenants exist, the same migration copies the rows into them (§8.5). The verifier refuses to start until the first two match |
| Change the seed | Edit `public.standard_picklist_values`. It affects new tenants only, because existing lists belong to their tenants. To offer a new value to everyone too, the migration can insert it per tenant, skipping any tenant that already has that code or label |
| Offer "Restore standard values" later | Read the field's rows from `public.standard_picklist_values` and add whichever codes the tenant lacks |
| Add an entity type | Extend `ck_picklist_values_entity_type` along with the other entity CHECKs (`custom-fields.md` §12). Nothing else here is entity-specific |
| Add a per-value attribute | A column on `app.picklist_values` (and on the seed table if standard values need it). If other rows start to reference values by id, reconsider §2.2 |
| Add a picklist-level setting | Custom fields: a column (or `config`) on the definition. Standard fields: on the provider if code-owned. If tenants must adjust it on standard fields, that's when a small `(entity_type, field_key)` table earns its place, holding only the fields a tenant changed |
