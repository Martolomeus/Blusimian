# Custom Objects — Architecture Proposal

**Status:** Proposal, nothing implemented · **Scope:** tenant-defined object types next to the built-in ones
**Date:** 2026-09-23 · **Builds on (as-built):** `Architecture/custom-fields.md`, `Architecture/entity-layouts.md` · **Must also extend:** `Architecture/roles-and-permissions.md` (§12)

> Third pillar of entity-level customization. Custom fields decide **which fields exist**
> on an object and layouts decide **how they're arranged**. Custom objects decide **which
> objects exist**: a tenant can define `Project` or `Invoice` next to the built-in
> `Company`, with its own custom fields, layout and records, all served through the same
> record machinery.

---

## 1. Goal and the constraint that shapes it

A tenant defines a new object type at runtime and immediately gets:

- custom fields on it (same definitions table, same slot pool, same allocation);
- a layout and a schema bundle (`GET /api/entities/{object}/schema`);
- CRUD on its records through the flat record API (`RecordMapper`, merge-patch semantics).

**The shaping constraint.** The app will be multi-tenant. Standard objects (`COMPANY`,
later `CONTACT`, `DEAL`) are the same for every tenant and are defined by code: they have
a Java entity, a table, a service and business logic. Giving every tenant a copy of those
objects as rows would mean:

- N × (number of standard objects) rows that say nothing a tenant chose;
- a seeding step on tenant creation, plus a backfill every time a standard object ships;
- two sources of truth (the Java class and the row) that can drift.

So **standard objects stay an application-wide enum with no rows**, and only custom objects
live in a table (tenant-scoped once `tenant_id` lands). This doc is about joining those two
worlds cleanly, in Java and in the database.

---

## 2. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | Java: `sealed interface ObjectType permits StandardObject, CustomObjectType`. The enum stays the enum. | §3 |
| D2 | DB: every table that points at "an object" carries **two** columns (`standard_object` CHECK-constrained, `custom_object_id` FK) plus a **stored generated `object_key`** that queries and unique constraints use. | §4 |
| D3 | Custom object keys live in their own namespace (`^[a-z][a-z0-9_]*__c$`) so they can never collide with a standard object, now or later. | §5 |
| D4 | Custom records share one table, `app.custom_records`, with the same `cf_*` pool. No runtime DDL. | §6 |
| D5 | New slice `customObjects`; `entityModel` reaches it only via an SPI (`CustomObjectCatalog`). | §7 |
| D6 | The union "all objects for this tenant" is built **in Java** (`StandardObject.values()` + catalog), not in SQL. | §8 |
| D7 | Tenant-specific tweaks to standard objects (a renamed label, say) go in a **sparse** override table later: a row only when a tenant actually changed something. | §11 |
| D8 | Custom objects join the role and permission setup: `app.custom_entity_permissions`, one sparse row per role and object. `Architecture/roles-and-permissions.md` leaves this out because custom objects don't exist yet, so **it's part of building them.** | §12 |

---

## 3. Java model (D1)

```java
// entityModel
public sealed interface ObjectType permits StandardObject, CustomObjectType {
    /** API/URL key: "COMPANY", "project__c". */
    String key();
    /** Value stored in the generated object_key column (§4). */
    String storageKey();
}

public enum StandardObject implements ObjectType {
    COMPANY("Company", "Companies");
    // CONTACT, DEAL join as they get the record machinery

    private final String label;
    private final String pluralLabel;
    ...
    @Override public String key()        { return name(); }
    @Override public String storageKey() { return name(); }
}

public record CustomObjectType(UUID id, String key, String label, String pluralLabel)
        implements ObjectType {
    @Override public String storageKey() { return id.toString(); }
}
```

Why a sealed interface rather than one class with a `kind` flag:

- **The enum stays exactly what it is:** compile-time constants, an `EnumMap`-able key
  for the `StandardFieldRegistry`, `@Enumerated(STRING)` in JPA, and the source of truth
  for the CHECK constraint. Nothing about standard objects becomes data.
- **The places that really do differ are forced to handle both cases.** A pattern
  `switch` over a sealed type is exhaustive, so the compiler flags any spot that forgot
  custom objects (the registry lookup, the record endpoint), not a runtime `null`:

  ```java
  return switch (type) {
      case StandardObject s   -> registry.providerFor(s).fields();
      case CustomObjectType c -> CustomRecordFields.forObject(c).fields();
  };
  ```
- **Everything that doesn't care accepts `ObjectType`:** `CustomFieldProvider`,
  `ReservedFieldKeys`, `EntityLayoutService`, `EntitySchemaService`. Today they take
  `EntityType`, so for them this is a rename.

`CustomObjectType` is a **value snapshot**, not the JPA entity. The entity
(`CustomObjectDefinition`) lives in the `customObjects` slice. `entityModel` only ever sees
the record, which keeps the rule "entityModel imports no feature package" (§7).

**Naming.** `EntityType` → `ObjectType` (object is the CRM term for this, and "custom
object" is what admins will call it). Package names (`entityModel`, `entityLayouts`,
`entitySchema`) stay; renaming them is churn with no payoff.

---

## 4. Database: referencing "an object" (D2)

Two tables already point at an entity type (`custom_field_definitions.entity_type`,
`entity_layouts.entity_type`), and more will follow: list views, validation rules,
permissions, lookup targets. Each needs a column that holds *either* a standard object
*or* a custom one.

### Options weighed

| Option | Standard objects | Custom objects | Verdict |
|---|---|---|---|
| **A. One string column** (`'COMPANY'` / `'project__c'`) | CHECK impossible (the column must also allow custom keys) | no FK: deleting an object leaves orphans; every purge has to know every referencing table | ✗ loses integrity |
| **B. Global rows for standard objects** in `object_definitions` with `tenant_id NULL` | one row each, app-wide | FK | ✗ standard objects become data again: seeding, drift, RLS `tenant_id IS NULL OR …` special case. Violates §1. |
| **C. Fixed UUIDs for standard objects** in one `object_id` column | constants in the enum | FK impossible (standard ids aren't in the table) | ✗ same integrity loss as A |
| **D. Two columns + generated key** | `standard_object` + CHECK | `custom_object_id` + FK `ON DELETE CASCADE` | ✓ **chosen** |

### The shape (D)

```sql
-- in custom_field_definitions, entity_layouts, and every future object-scoped table
standard_object  varchar(50)
    constraint ck_cfd_standard_object check (standard_object in ('COMPANY')),
custom_object_id uuid
    references app.custom_object_definitions (id) on delete cascade,
object_key       text generated always as
    (coalesce(standard_object, custom_object_id::text)) stored,

constraint ck_cfd_one_object check (num_nonnulls(standard_object, custom_object_id) = 1),
```

- **Writes** set exactly one of the two real columns. `num_nonnulls = 1` makes "both" or
  "neither" impossible to store.
- **Integrity comes from the real columns.** The CHECK mirrors the enum exactly as today.
  The FK guarantees a custom reference points at a real object, and `on delete cascade` is
  what makes purging a custom object one `DELETE` (§10).
- **Reads, indexes and uniques use `object_key`.** It turns two columns into one, so each
  table keeps a single unique constraint and one derived repository method per query,
  where two columns would need both of each:

  ```sql
  constraint uq_custom_field_key unique (object_key, field_key),
  constraint uq_cfd_column       unique (object_key, column_name),
  create index idx_cfd_object on app.custom_field_definitions (object_key);
  ```

  Once `tenant_id` lands these become `(tenant_id, object_key, …)`. `tenant_id` has to lead
  because `'COMPANY'` is shared by every tenant. For custom objects it's redundant (the id
  already implies the tenant) but harmless.

`object_key` holds the custom object's **id**, not its API key. So even if a custom
object's key or label changes later, no referencing row has to be rewritten.

### JPA: one embeddable, reused everywhere

```java
// entityModel
@Embeddable
public class ObjectRef {
    @Enumerated(EnumType.STRING)
    @Column(name = "standard_object", updatable = false)
    private StandardObject standardObject;

    @Column(name = "custom_object_id", updatable = false)
    private UUID customObjectId;

    @Generated                       // DB computes it; Hibernate reads it back after insert
    @Column(name = "object_key", insertable = false, updatable = false)
    private String objectKey;

    public static ObjectRef of(ObjectType type) {
        ObjectRef ref = new ObjectRef();
        switch (type) {
            case StandardObject s   -> ref.standardObject = s;
            case CustomObjectType c -> ref.customObjectId = c.id();
        }
        return ref;
    }
}
```

`CustomFieldDefinition` and `EntityLayout` replace their `@Enumerated EntityType
entityType` field with `@Embedded ObjectRef object`. Repositories query by key:

```java
List<CustomFieldDefinition> findByObjectObjectKeyAndArchivedAtIsNullOrderBySortOrderAscFieldKeyAsc(String objectKey);
```

and services call them with `type.storageKey()`. A repository `default` method taking an
`ObjectType` can hide the string if the call sites get noisy.

`custom_object_id` is mapped as a plain `UUID`, **not** `@ManyToOne CustomObjectDefinition`.
A JPA association would make `entityModel` (and `customFields`, `entityLayouts`) import the
`customObjects` slice. The FK lives in the database, where integrity belongs; Java never
needs to navigate it.

**Why not query the embeddable directly** (`findByObject(ObjectRef)`)? Hibernate compares a
component column by column, and one of the two columns is always NULL. `NULL = NULL` is
not true in SQL, so the query silently matches nothing. The generated key avoids this
trap entirely.

**Drift point:** `storageKey()` in Java and the `coalesce(...)` expression in the DDL
encode the same rule twice. One unit test pins it: insert one reference of each kind and
assert the column equals `storageKey()`.

---

## 5. Custom object definitions and key namespace (D3)

```sql
create table app.custom_object_definitions
(
    id           uuid primary key default gen_random_uuid(),
    -- tenant_id uuid not null,        -- added with the multitenancy layer (+ RLS)

    object_key   public.citext not null,   -- write-once; the API/URL key
    label        varchar(255)  not null,
    plural_label varchar(255)  not null,
    archived_at  timestamptz,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),

    constraint ck_cod_key_format check (object_key ~ '^[a-z][a-z0-9_]{0,37}__c$'),
    -- becomes (tenant_id, object_key)
    constraint uq_cod_key unique (object_key)
);
```

**Why a reserved suffix.** Object keys are resolved case-insensitively from one URL
segment (`/api/entities/{object}/schema`). Without a separate namespace:

- a tenant creates `company`, which collides with `COMPANY` and forces a precedence rule;
- worse, a tenant creates `invoice` today, then we ship a standard `INVOICE` next year
  and that tenant's object gets shadowed, or the release breaks that tenant.

The `__c` suffix rules both out structurally: standard keys never contain `__`, and
custom keys always end with it. CRM admins know this convention from Salesforce, so it
won't surprise them. The CHECK enforces it in the database, and bean validation on the
request DTO turns a bad key into a 400 before the database ever sees it.

> **Side finding, not in scope here.** Custom *fields* have the same latent problem. If we
> later add a standard field `industry_code` to `Company` and a tenant already has a custom
> field with that key, the flat keyspace collides on upgrade. `ReservedFieldKeys` only
> protects against standard fields that exist *at creation time*. The same suffix rule
> for field keys would close this, and it's cheapest to decide before real tenants exist.

---

## 6. Custom records storage (D4)

```sql
create table app.custom_records
(
    id               uuid primary key default gen_random_uuid(),
    -- tenant_id uuid not null,

    custom_object_id uuid not null
        references app.custom_object_definitions (id) on delete cascade,
    name             varchar(255) not null,       -- every custom object has a name
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),

    -- cf_* slot pool, declared LAST (same deforming argument as app.companies)
    cf_text_01 varchar(255),
    ...
);

create index idx_custom_records_object on app.custom_records (custom_object_id);
```

Java side, a near-copy of `Company`:

```java
@Entity @Table(name = "custom_records") @DynamicUpdate
public class CustomRecord implements RecordEntity {
    @Id @GeneratedValue private UUID id;
    @Column(name = "custom_object_id", nullable = false, updatable = false) private UUID customObjectId;
    @Column(nullable = false) private String name;
    @Embedded private CustomFieldSlots slots;
    ...
}
```

**Why one shared table.** Per-object tables would mean runtime DDL: locks, the 1600-column
and catalog-bloat problems, and Hibernate can't map a table that didn't exist at startup.
These are the reasons `Architecture/custom-fields.md` §11 already rejects for fields.
The slot pool exists precisely so that storage is fixed and meaning lives in the catalog.
A custom object is simply the next step: the *row's* meaning comes from the catalog too.

**Each object gets its own 105 slots for free.** Slot allocation is already namespaced
by object (`uq_cfd_column (object_key, column_name)`), so `project__c.cf_text_01` and
`invoice__c.cf_text_01` are different fields in the same physical column.

### The one new failure mode: forgetting the object filter

`app.companies` holds only companies. `app.custom_records` holds every custom object's
records, so any query without `custom_object_id = ?` returns other objects' records (and,
before RLS, other tenants' as well). Defence in the repository itself:

```java
// extends Repository, NOT JpaRepository: no inherited findAll()/findById() without the object
public interface CustomRecordRepository extends Repository<CustomRecord, UUID> {
    List<CustomRecord> findByCustomObjectId(UUID customObjectId);
    Optional<CustomRecord> findByIdAndCustomObjectId(UUID id, UUID customObjectId);
    CustomRecord saveAndFlush(CustomRecord record);
    void delete(CustomRecord record);
}
```

This is also how `GET /api/records/project__c/{id}` with an invoice's id correctly returns
404 instead of the invoice.

### Physical costs to know about

- **Mixed column statistics.** `cf_text_03` holds a different field for every object (and
  tenant), so Postgres's per-column statistics blend them. Filters on slots will get
  coarser selectivity estimates than on `app.companies`. The same is already true across
  tenants, so it's no new category of problem.
- **Slot indexes lead with the object:** `(custom_object_id, cf_text_03)`, later
  `(tenant_id, custom_object_id, cf_text_03)`.
- **Partitioning** later, if this table dominates: hash-partition by `tenant_id`. List
  partitioning by object doesn't work, because objects are created at runtime.

---

## 7. Packages and dependency direction (D5)

```
platform       ──► (nothing)
entityModel    ──► platform                       ← still imports no feature
customObjects  ──► entityModel platform           ← NEW
customFields   ──► entityModel platform           (unchanged: speaks ObjectType)
entityLayouts  ──► entityModel platform
entitySchema   ──► entityModel entityLayouts
companies      ──► entityModel platform
```

`entityModel` needs to *resolve* custom objects (URL segment → `ObjectType`, record API)
but must not import `customObjects`. The same inversion as `CustomFieldProvider` handles
this:

```java
// entityModel — declared here, implemented in customObjects
public interface CustomObjectCatalog {
    Optional<CustomObjectType> findActiveByKey(String key);
    Optional<CustomObjectType> findById(UUID id);
    List<CustomObjectType> listActive();
}
```

Note what does **not** change: `customFields` gains no dependency on `customObjects`.
Allocation, archive and the descriptor provider all work on `ObjectType`/`object_key` and
never need the definition entity. The FK in the database is the only link between the two.

### New slice `customObjects`

| File | Role |
|---|---|
| `CustomObjectDefinition` | JPA entity; write-once `objectKey`; `archive()`. |
| `CustomObjectDefinitionRepository` | Spring Data. |
| `CustomObjectCatalogImpl` | Implements `CustomObjectCatalog`; maps entity → `CustomObjectType`. |
| `CustomObjectService` / `CustomObjectController` | Admin CRUD: `GET/POST /api/objects`, `DELETE /api/objects/{key}` (archive). |
| `CustomRecord`, `CustomRecordRepository` | Storage (§6). |
| `CustomRecordFields` | Builds the per-object `StandardFieldProvider<CustomRecord>` (§9). |
| `CustomRecordService` / `CustomRecordController` | `/api/records/{objectKey}`: what `CompanyService` does, with the object as a parameter. |
| `exception/…` | `CustomObjectNotFoundException` (404), `DuplicateCustomObjectKeyException` (409). |

---

## 8. Joining the two worlds (D6)

"Which objects exist for this tenant" is answered in Java, in `entityModel`:

```java
@Component
public class ObjectTypeResolver {

    private final CustomObjectCatalog customObjects;

    /** URL segment → object. Standard first (no DB hit), then the tenant's catalog. */
    public Optional<ObjectType> resolve(String key) {
        String k = key.trim();
        for (StandardObject s : StandardObject.values()) {
            if (s.key().equalsIgnoreCase(k)) return Optional.of(s);
        }
        return customObjects.findActiveByKey(k).map(c -> c);
    }

    /** Every object this tenant can use: the enum, then its custom objects. */
    public List<ObjectType> all() {
        List<ObjectType> all = new ArrayList<>(List.of(StandardObject.values()));
        all.addAll(customObjects.listActive());
        return all;
    }
}
```

Thanks to §5 the lookup order doesn't matter for correctness; standard goes first only
because it's free.

**Why not a SQL union** (`select … from (values ('COMPANY', …)) union all select … from
custom_object_definitions`)? It would copy the enum into SQL text, a third copy after the
enum and the CHECK. Joining in Java keeps the enum as the only list, and nothing needs
"all objects" inside a single query.

`StringToEntityTypeConverter` becomes `StringToObjectTypeConverter`, a `@Component`
delegating to `ObjectTypeResolver`. Spring Boot registers `Converter` beans with MVC
automatically, so every `@PathVariable ObjectType` keeps working. One behaviour change:
an unknown object becomes **404** (`ObjectNotFoundException`), not 400. Now that objects
are data, an unknown key is a missing resource, not a malformed request.

The resolver hits the database once per request for a custom key. That's fine for now,
and it's the first place to cache per tenant later (catalog changes are rare admin
actions, like custom field definitions).

---

## 9. Record machinery changes

Small. `RecordMapper`'s logic is untouched; only types move.

- **`StandardFieldProvider<T>`:** `EntityType entityType()` → `ObjectType objectType()`.
  Standard providers remain Spring beans returning their enum constant.
- **Custom objects get a provider instance per object, not a bean:**

  ```java
  // customObjects
  public final class CustomRecordFields {
      private static final List<FieldDescriptor<CustomRecord>> FIELDS = List.of(
          FieldDescriptor.of("name", "Name", FieldDataType.TEXT, String.class,
                             CustomRecord::getName, CustomRecord::setName)
              .asRequired().withMaxLength(255));

      public static StandardFieldProvider<CustomRecord> forObject(CustomObjectType type) {
          return new StandardFieldProvider<>() {
              public ObjectType objectType() { return type; }
              public List<FieldDescriptor<CustomRecord>> fields() { return FIELDS; }
          };
      }
  }
  ```

  `RecordMapper.apply(record, CustomRecordFields.forObject(type), values)` then works
  as-is. The mapper takes `provider.objectType()` for the custom field lookup and gets the
  custom object's key.
- **`StandardFieldRegistry.fieldsFor(ObjectType)`** switches on the sealed type: an
  `EnumMap<StandardObject, …>` for standard objects, and the fixed `name` field for custom
  ones. Because the registry lives in `entityModel` and can't import `CustomRecordFields`,
  the "fields every custom object has" come through the `CustomObjectCatalog` SPI
  (`standardFields()`) or a second small SPI. Either works; decide when coding.
- **`ReservedFieldKeys`** follows automatically: `name` is reserved on every custom
  object.
- **Schema bundle:** `EntitySchemaResponse` gains `kind` (`STANDARD`/`CUSTOM`) and
  `recordsPath` (`/api/companies`, `/api/records/project__c`), so the frontend never
  special-cases which endpoint serves which object.

Standard objects **keep their own controllers and services** (`/api/companies`). They carry
real business logic and typed relationships. A generic `/api/records/company` alias
isn't needed, and it would just be a second way in to keep in sync.

---

## 10. Lifecycle

Mirrors custom fields: archive now, purge later.

1. **Archive** (`DELETE /api/objects/{key}`): sets `archived_at`. The resolver no longer
   finds it, so its record, schema and field endpoints return 404. Data, fields and
   layouts are kept.
2. **Purge** (deferred): `delete from app.custom_object_definitions where id = ?`. The
   `on delete cascade` FKs from §4, §6 and §12 remove its custom field definitions, layouts
   (and their sections, via existing cascades), all records and its role permission rows. The whole payoff of D2:
   purge doesn't need a list of referencing tables, and a future object-scoped feature
   that uses the §4 column shape is purged automatically.

A newly created custom object has no layout. `EntityLayoutService.findDefault` already
returns `Optional` and the schema bundle already tolerates `layout: null`, so no
`customObjects → entityLayouts` dependency is needed. Auto-creating a default layout
(one section with `name`) is an optional nicety for later.

---

## 11. Multitenancy notes

- **Standard objects: zero rows, ever.** Adding `CONTACT` is an enum constant, a CHECK
  update and a Java slice. No tenant backfill.
- `tenant_id` goes on `custom_object_definitions`, `custom_records`,
  `custom_field_definitions` and `entity_layouts`. Uniques lead with it (§4).
- **Slot-allocation lock:** today it's `pg_advisory_xact_lock(0x5107,
  entityType.name().hashCode())`. It becomes a hash of `tenant_id || object_key`. A hash
  collision only serialises two unrelated admin actions, which is harmless.
- **Tenant tweaks to standard objects (D7).** When a tenant wants to rename "Company" to
  "Account" or hide `DEAL`, use a sparse `standard_object_overrides (tenant_id,
  standard_object, label, …)` table: a row only for what a tenant changed, merged over
  the enum's defaults in `ObjectTypeResolver.all()`. The no-copies principle from §1
  holds: a tenant that customises nothing has no rows.
- RLS reminder from `Architecture/custom-fields.md` §12 applies doubly to
  `custom_records`: RLS is a no-op for the table owner, and the app currently connects
  as `postgres`.

---

## 12. Roles and permissions (D8)

`Architecture/roles-and-permissions.md` covers Contact, Company, Deal and app-wide permissions. It
leaves custom objects out because they don't exist yet, so that setup has to be added here.
Skipping it isn't an option once permissions are enforced: only Administrator could reach
custom records.

The rules are that spec's. Everything defaults to `false`, and a role with Create must keep
Read and Edit on every required field (its D4 and D12).

```sql
create table app.custom_entity_permissions
(
    id               uuid        not null default gen_random_uuid(),
    tenant_id        uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    role_id          uuid        not null,
    custom_object_id uuid        not null,

    can_read         boolean     not null default false,
    can_create       boolean     not null default false,
    can_edit         boolean     not null default false,
    can_delete       boolean     not null default false,

    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),

    name_read boolean not null default false, name_edit boolean not null default false,
    -- … one pair per cf_* slot, 105 in all …

    constraint pk_custom_entity_permissions primary key (tenant_id, id),
    constraint uq_custom_entity_permissions_role unique (tenant_id, role_id, custom_object_id),
    constraint fk_custom_entity_permissions_role_id foreign key (tenant_id, role_id)
        references app.roles (tenant_id, id) on delete cascade,
    constraint fk_custom_entity_permissions_object foreign key (tenant_id, custom_object_id)
        references app.custom_object_definitions (tenant_id, id) on delete cascade,
    constraint ck_custom_entity_permissions_crud
        check (can_read or not (can_create or can_edit or can_delete)),
    constraint ck_custom_entity_permissions_required
        check (not can_create or (name_read and name_edit))
);
```

- **One table for every custom object.** Each object has its own 105 slots (§6), so a bit
  named `cf_text_03_read` means whatever that object's `cf_text_03` means. Slot-keyed bits
  carry over unchanged.
- **Sparse, unlike the standard tables.** No row means no access. A row is created when an
  admin first ticks something for that role and object. So creating a custom object writes
  nothing here, `customObjects` needs no dependency on `roles`, and Administrator needs no
  row, because the resolver grants it everything.
- **`name` is required on every custom object** (§6), which is what
  `ck_custom_entity_permissions_required` holds.
- **Not the two-column shape of §4.** This table only ever points at custom objects:
  standard objects have tables of their own. A plain `custom_object_id` with an FK is enough.
- **Purge** cascades (§10). Purging one custom field must also reset its slot's pair on this
  table, as it must on the standard ones: otherwise the next field in that slot inherits
  the old field's grants.

What changes in `roles` (Phase 0 first moves it from `EntityType` to `ObjectType`):

| Code | Change |
|---|---|
| `EntityPermissionRepository` | Takes `ObjectType`. The `table` `switch` gains the sealed `CustomObjectType` case, and statements on this table also filter by `custom_object_id`. A row may not exist, so `find` can't use `singleRow()` here. |
| `FieldPermissionCatalog` | Takes `ObjectType`: `name`, then the object's active custom fields. |
| `PermissionColumnsVerifier` | Also checks this table: the `name` pair plus a pair per slot. |
| `CustomFieldCreated`, `RequiredFieldGrants` | The event names the object. A required field on a custom object reaches only that object's rows with Create. |
| `RoleService` | Takes the field lock for each custom object the request names too (§11 already keys that lock by object). |
| `RoleValidator` | `entities` must still hold every standard object, but custom objects are optional. An unknown or archived key is a 400. |
| Role document | `entities` gains one entry per custom object the role has a row for, keyed by object key (`project__c`). |
| Role editor | One section per active custom object, after the standard ones. A missing entry shows everything off. |
| `app_permissions` | Gains `manage_custom_objects`, guarding writes on `/api/objects`. |
| The resolver, if built by then | A missing row means no access. |

---

## 13. Implementation phases

Each phase ends green, following the dev-phase V1 policy (edit V1, wipe the DB).

| Phase | Work | Behaviour change |
|---|---|---|
| **0. ObjectType refactor** | Sealed `ObjectType` + `StandardObject` enum replace `EntityType`; `ObjectRef` embeddable; two-column + generated `object_key` on `custom_field_definitions` and `entity_layouts`; repositories query by `object_key`. Custom side of the columns exists but is never set. | None. All existing tests pass. |
| **1. Object catalog** | `custom_object_definitions`, `customObjects` slice (admin CRUD), `CustomObjectCatalog` SPI, `ObjectTypeResolver`, new converter (404 on unknown). | Custom fields and layouts can be defined on a custom object. |
| **2. Records** | `custom_records` + `CustomRecord`, `CustomRecordFields`, `CustomRecordRepository` (object-scoped), `/api/records/{objectKey}`. | Custom objects are usable end to end. |
| **3. Permissions** | §12: `custom_entity_permissions`, the `roles` changes, the role editor sections. | Roles can be granted access to custom objects. |
| **4. Schema bundle** | `kind` + `recordsPath` on the schema response; `GET /api/objects` lists standard + custom. | Frontend can render any object generically. |
| **5. Purge** | Hard delete with cascades. | — |

If permissions are enforced by the time this is built, Phases 2 and 3 ship together: without
Phase 3, only Administrator can reach custom records.

Phase 0 is worth doing now, while only `COMPANY` exists: it's the change that touches every
file with `EntityType`, and it only gets more expensive as `CONTACT` and `DEAL` arrive.

**Tests to add alongside:** `object_key` generation matches `storageKey()` (§4); the
`num_nonnulls` CHECK rejects both/neither; resolver precedence and the 404; cross-object
isolation (reading project A's id through invoice's path is a 404); purge cascades to
fields, layouts, records and permission rows; a role with no row has no access; a role with
Create can't close `name`; a required field reaches only that object's rows with Create.

---

## 14. Out of scope / open questions

- **Lookup (relationship) fields.** Needed before custom objects are really useful in a
  CRM (`project__c → Company`). Sketch: a `LOOKUP` `FieldDataType` with a `cf_ref_NN uuid`
  pool, target stored on the definition as its own `ObjectRef` (target_standard_object /
  target_custom_object_id: §4's shape again). No DB FK is possible on a slot (the same
  column points at different targets), so existence checks happen in the app and delete
  behaviour needs a decision. Deserves its own proposal.
- **Field-key namespace** (§5 side finding): adopt `__c` for custom fields too?
- **`@Generated` inside an `@Embeddable` on Hibernate 7:** expected to work, but verify in
  Phase 0. Fallback: map `object_key` directly on each entity instead of in `ObjectRef`.
- **Record-level `name` for custom objects:** always a required TEXT, or should a tenant
  be able to pick an auto-number (`INV-0001`)? Salesforce offers both; start with TEXT.
- **Limits:** max custom objects per tenant (a product decision; the table has no
  inherent limit).
