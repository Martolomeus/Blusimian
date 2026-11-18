# Roles & Permissions — Architecture Reference

**Status:** Implemented & verified (§9): stored and editable, not enforced yet · **Scope:** roles, their permission tables, role assignments, and the Roles and Users settings pages. Resolving what a user may do, and enforcing it, are out of scope (§7 lists only what this design requires of them).
**Last updated:** 2026-11-08 · **Builds on:** `Architecture/multitenancy.md`, `Architecture/custom-fields.md`, `Architecture/entity-layouts.md`, `Architecture/settings.md`
**Companion:** `WIP/job-title-layouts.md`. Layouts follow a user's job title, so nothing here touches layouts.
**Code:** package `roles`; `users`, `tenants`, `customFields`, `V1__init.sql`; `Frontend/src/features/settings/roles`, `features/settings/users`, `lib/roles`, `lib/users`

> A **role** is a named set of permissions, defined per tenant. A user holds any number of
> roles through a **role assignment**. Each role has child tables: one per entity (contact,
> company, deal) and one for app-wide permissions.
>
> An entity permission row has two layers. The **CRUD columns** say whether the role may
> read, create, edit or delete that entity's records. The **field columns** then hold a
> read and an edit boolean per field, for every standard field and every `cf_*` slot.
>
> **Every permission starts as `false`.** Access exists only where someone granted it.
>
> A new **General** section in settings holds a Roles card (list, then an edit page per
> role) and a Users card (list, then a user page with the role assignments).
>
> This is the storage and admin side only: permissions are stored and editable, and
> nothing obeys them yet. Custom objects bring their own permission table
> when they're built (`WIP/custom-objects-architecture.md` §12).

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | `app.roles` is the parent. Children: `contact_permissions`, `company_permissions`, `deal_permissions`, `app_permissions`. Users hold roles through `app.role_assignments`. | §2 |
| D2 | Every role has exactly one row in each of the four tables, created in the role's transaction. `unique (tenant_id, role_id)` holds this. | §2.3 |
| D3 | Create, Edit and Delete each require Read on the entity, by a CHECK. | §2.3 |
| D4 | **Everything defaults to `false`:** CRUD, field and app columns. A new role can do nothing, and a new field is closed to every role until someone grants it. The one automatic grant is the one D12 needs. | §2.4, §6.3 |
| D5 | Roles only add. `false` means "this role doesn't grant it", never "deny". Inside one role the entity and field layers are ANDed; across a user's roles the results are ORed. | §7 |
| D6 | Field columns follow **the field dictionary, not the table**: a pair per provider field (keyed by field key) and a pair per `cf_*` slot (keyed by slot). `Company`'s address and owner columns aren't served as fields, so they get none. | §2.4 |
| D7 | **The permission columns live only in V1.** They have no entity classes and nothing is generated: `JdbcClient` reaches them by name, and a startup check compares them to the fields. The API speaks field keys only; slot names never leave the server. | §3.2, §3.3, §4.2 |
| D8 | One **Administrator** role per tenant (`is_admin`). It can't be edited or deleted, the resolver will grant it everything without reading its rows, and its last holder can't lose it. | §2.1, §3.5 |
| D9 | Provisioning creates Administrator and Standard user, writing their grants explicitly, and makes the first user an Administrator. `TenantInitializer.initialize` receives the first user's id. | §3.6 |
| D10 | A role still held by a user can't be deleted (409). Once free, its child rows go by cascade. | §2.2, §3.4 |
| D11 | `users` doesn't know roles exist. `roles` depends on `users`, assignments are read separately, and the page joins them. | §3.1 |
| D12 | **A role with Create must keep Read and Edit on every required field.** `RoleValidator` checks it on every save, a new required custom field is granted to roles with Create in its own transaction, and a CHECK backs it up for standard fields. | §3.8, §6.4 |
| D13 | Settings gain a **General** section, first on the overview. The role editor copies the layout editor's pattern: a draft in the page, whole-document save, unsaved-changes guard. | §5 |
| D14 | Permissions aren't enforced yet, and the Roles page says so. | §5.3 |

---

## 2. Data model

All tables are in `app`, follow the recipe in `multitenancy.md` §17 (tenant block, `(tenant_id, id)` primary key, composite FKs, RLS, four grants), and are in `V1__init.sql` per the dev-phase policy.

```
app.users ──< app.role_assignments >── app.roles
                                          ├─ 1:1  app.app_permissions
                                          ├─ 1:1  app.contact_permissions
                                          ├─ 1:1  app.company_permissions
                                          └─ 1:1  app.deal_permissions
```

`app.roles` and its children sit after `app.entity_layout_section_fields`, before `find_login_user`.

### 2.1 `app.roles`

```sql
create table app.roles
(
    id         uuid         not null default gen_random_uuid(),
    tenant_id  uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    name       varchar(100) collate app.case_insensitive not null,
    is_admin   boolean      not null default false,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),

    constraint pk_roles primary key (tenant_id, id),
    constraint uq_roles_name unique (tenant_id, name)
);

-- at most one Administrator per tenant
create unique index uq_roles_admin on app.roles (tenant_id) where is_admin;
```

`is_admin` is set only by provisioning and is in no request body. Same pattern as `uq_entity_layout_default`.

### 2.2 `app.role_assignments`

```sql
create table app.role_assignments
(
    id         uuid        not null default gen_random_uuid(),
    tenant_id  uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    user_id    uuid        not null,
    role_id    uuid        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint pk_role_assignments primary key (tenant_id, id),
    -- Its leading (tenant_id, user_id) also serves "this user's roles".
    constraint uq_role_assignments unique (tenant_id, user_id, role_id),
    constraint fk_role_assignments_user_id foreign key (tenant_id, user_id)
        references app.users (tenant_id, id) on delete cascade,
    constraint fk_role_assignments_role_id foreign key (tenant_id, role_id)
        references app.roles (tenant_id, id) on delete restrict
);

create index idx_role_assignments_role on app.role_assignments (tenant_id, role_id);
```

The two delete rules differ on purpose. A deleted user's assignments mean nothing, so they cascade. A deleted role would silently strip access from every holder, so it's refused.

### 2.3 The entity permission tables: CRUD layer

`contact_permissions`, `company_permissions` and `deal_permissions` share this shape:

```sql
create table app.contact_permissions
(
    id         uuid        not null default gen_random_uuid(),
    tenant_id  uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    role_id    uuid        not null,

    can_read   boolean     not null default false,
    can_create boolean     not null default false,
    can_edit   boolean     not null default false,
    can_delete boolean     not null default false,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    -- One pair per field in the dictionary: standard by key, custom by slot.
    salutation_read boolean not null default false, salutation_edit boolean not null default false,
    first_name_read boolean not null default false, first_name_edit boolean not null default false,
    -- … one pair per ContactStandardFieldProvider field …
    cf_text_01_read boolean not null default false, cf_text_01_edit boolean not null default false,
    -- … one pair per cf_* slot, 105 in all …

    constraint pk_contact_permissions primary key (tenant_id, id),
    constraint uq_contact_permissions_role unique (tenant_id, role_id),
    constraint fk_contact_permissions_role_id foreign key (tenant_id, role_id)
        references app.roles (tenant_id, id) on delete cascade,
    constraint ck_contact_permissions_crud
        check (can_read or not (can_create or can_edit or can_delete)),
    -- The provider's required fields (§3.8).
    constraint ck_contact_permissions_required
        check (not can_create or (first_name_read and first_name_edit
                                  and last_name_read and last_name_edit))
);
```

- **Every column is written out in V1,** as the slot pool is: about 115 lines per table.
- **`unique (tenant_id, role_id)` makes the row one-to-one.** Each table keeps its own surrogate `id` so it matches the recipe.
- **No `entity_type` column.** The table name says which entity it is.
- **`ck_*_crud`** is why a role can't hold Edit without Read. It's cheap: four columns, one constraint.
- **`ck_*_required`** names each table's required fields: `first_name` and `last_name` on contacts, `name` on companies, `deal_name` on deals.

### 2.4 The field layer

Two booleans per field: `<field>_read` and `<field>_edit`. Which fields get a pair follows `EntityFieldDictionary`, not the table's columns:

| Fields | Column name | Example | Identity |
|---|---|---|---|
| Standard (from the entity's `StandardFieldProvider`) | the field **key** in snake case | key `firstName` → `first_name_read` (the record column happens to match); key `street` → `street_read` (the record column is `address_street`) | Stable: keys are code |
| Custom | the **slot** name | `cf_text_03_read` | Stable while the slot is allocated: `column_name` is write-once |

Keyed by field key, not by record column, because keys are what the whole API and layout layer already speak.

| Table | Standard pairs | Slot pairs | Booleans incl. CRUD | Total columns |
|---|---|---|---|---|
| `contact_permissions` | 10 | 105 | 234 | 239 |
| `company_permissions` | 10 | 105 | 234 | 239 |
| `deal_permissions` | 3 | 105 | 220 | 225 |

Slots mean the same thing here as on the record tables: `cf_text_03_read` is meaningless until a row in `custom_field_definitions` owns `cf_text_03` for that tenant and entity. The catalog is the mapping, as it already is for values. A role's bits for an unallocated slot stay `false`.

**Field columns default to `false`** (D4). Ticking Read on an entity opens nothing by itself: a field is readable only when its own bit is on as well. What that changes is in §6.3.

Edit requires Read at field level too, but that rule is in `RoleValidator`, not a CHECK. It would take 115 clauses per table to protect a single write path.

### 2.5 `app.app_permissions`

One row per role, for permissions that aren't about an entity. It starts with one boolean per admin area that exists in the code today.

```sql
create table app.app_permissions
(
    id                   uuid        not null default gen_random_uuid(),
    tenant_id            uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    role_id              uuid        not null,

    manage_users         boolean     not null default false,
    manage_roles         boolean     not null default false,
    manage_job_titles    boolean     not null default false,
    manage_layouts       boolean     not null default false,
    manage_custom_fields boolean     not null default false,

    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),

    constraint pk_app_permissions primary key (tenant_id, id),
    constraint uq_app_permissions_role unique (tenant_id, role_id),
    constraint fk_app_permissions_role_id foreign key (tenant_id, role_id)
        references app.roles (tenant_id, id) on delete cascade
);
```

| Column | Will guard |
|---|---|
| `manage_users` | writes on `/api/users`, role assignment |
| `manage_roles` | writes on `/api/roles` |
| `manage_job_titles` | writes on `/api/job-titles` (companion spec) |
| `manage_layouts` | writes on `/api/entity-layouts` |
| `manage_custom_fields` | writes on `/api/custom-fields` and `/api/picklists` (picklist values, `picklists.md` §10.4): whoever may add fields may edit their values. The Roles page can label it "Manage fields" |

Whether the Settings link shows is derived (the caller holds any of these), so it needs no column of its own.

### 2.6 What happens when…

| Event | Effect on permission data |
|---|---|
| Role created | One row in each of the four tables, same transaction, `false` unless the request sets it |
| Optional custom field created | **Nothing.** It's closed to every role until an admin grants it |
| Required custom field created | Read and Edit are granted to every role with Create on that entity, in the field's transaction (§3.8) |
| Custom field archived | The pair stays and is hidden from the API, like the field |
| Custom field purged (deferred) | **Must** reset the slot's pair to `false` on every role, in the purge's transaction. Otherwise the next field to take that slot inherits the old field's grants (§6.2) |
| Optional standard field added to a provider | Two columns join V1, closed for every role. The app won't start until they do (§3.3) |
| Required standard field added | The same, and `ck_*_required` can't be added until roles with Create are backfilled (§6.4) |
| New `EntityType` | One more table. The `switch` in `EntityPermissionRepository` stops compiling until then (§3.2) |
| Role deleted | Refused while held; otherwise cascade removes its child rows |
| User deleted (no endpoint yet) | Assignments cascade. That future endpoint must run the last-Administrator check (§3.5) |

---

## 3. Backend

### 3.1 Package and dependency direction

Package `com.blusimian.blusimian.roles`, with `dto/` and `exception/` as elsewhere.

```
platform     ──► (nothing)
users        ──► platform                                         (unchanged: knows nothing of roles)
customFields ──► entityModel platform                             (publishes an event; knows nothing of roles)
roles        ──► entityModel customFields tenants users platform
tenants      ──► users
```

No new cycle. `roles` reads custom field definitions, so `customFields` can't call back into it; it publishes `CustomFieldCreated` and `roles` listens (§3.8). `roles → tenants` is only `TenantInitializer`, the same edge `entityLayouts` already has.

| File | Role |
|---|---|
| `Role`, `RoleRepository` | The parent row. `Role.create(name)`, `Role.createAdministrator(name)`, `rename`. No setter for `isAdmin`. |
| `AppPermission`, `AppPermissionRepository` | The row of §2.5. A small table, so an ordinary entity. |
| `EntityPermissionRepository` | A plain `@Repository` over `JdbcClient`, for the three entity tables (§3.2). |
| `FieldPermissionCatalog`, `PermissionField` | Field key ↔ its two column names, for every field an entity has now, and a row ↔ a document (§3.2). |
| `PermissionColumnsVerifier` | Startup check: the columns match the fields (§3.3). |
| `RoleValidator` | Plain class, like `EntityLayoutValidator` (§4.3). |
| `RequiredFieldGrants` | Listens for `CustomFieldCreated` (§3.8). |
| `RoleService`, `RoleController` | §3.4, §4.1. |
| `RoleAssignment`, `RoleAssignmentRepository`, `RoleAssignmentService`, `RoleAssignmentController` | §3.5. |
| `RolesInitializer` | §3.6. |

`Role`, `RoleAssignment` and `AppPermission` hold their references as a plain `UUID`, as `Deal.customerId` does, and the FKs live in the database.

### 3.2 Reaching the permission columns

The columns live in V1 and nowhere else. The three entity permission tables have no entity classes, and nothing is generated.

`CustomFieldSlots` is a Java mirror of the slot pool because record values really need one: five Java types, length caps, and Hibernate loading them with each record. Permission columns need none of that. Every column is a boolean, and every access is by name, so a row can simply be a map:

```java
/** One role's row in an entity's permission table. Column names come only from FieldPermissionCatalog. */
@Repository
class EntityPermissionRepository {

    private final JdbcClient jdbc;

    void insert(EntityType type, UUID roleId) {
        jdbc.sql("insert into app." + table(type) + " (role_id) values (:roleId)")
                .param("roleId", roleId)
                .update();
    }

    /** The whole row, by column name. Every role has one, so a missing row is a bug. */
    Map<String, Object> find(EntityType type, UUID roleId) {
        return jdbc.sql("select * from app." + table(type) + " where role_id = :roleId")
                .param("roleId", roleId)
                .query()
                .singleRow();
    }

    void update(EntityType type, UUID roleId, Map<String, Boolean> columns) {
        String assignments = columns.keySet().stream()
                .map(column -> column + " = :" + column)
                .collect(Collectors.joining(", "));
        jdbc.sql("update app." + table(type) + " set " + assignments
                        + ", updated_at = now() where role_id = :roleId")
                .params(columns)
                .param("roleId", roleId)
                .update();
    }

    void grantToRolesWithCreate(EntityType type, String slot) {
        jdbc.sql("update app." + table(type) + " set " + PermissionField.readColumn(slot) + " = true, "
                        + PermissionField.editColumn(slot) + " = true, updated_at = now() where can_create")
                .update();
    }

    /** No default branch, so a new EntityType doesn't compile until it has a table. */
    static String table(EntityType type) {
        return switch (type) {
            case CONTACT -> "contact_permissions";
            case COMPANY -> "company_permissions";
            case DEAL -> "deal_permissions";
        };
    }
}
```

`FieldPermissionCatalog.fieldsFor(entityType)` returns the entity's fields as `PermissionField(key, required, readColumn, editColumn)`, in dictionary order. It's the only place that turns a field key into column names:

- standard fields: from `StandardFieldRegistry`, the key in snake case: `firstName` → `first_name_read`;
- custom fields: `CustomFieldService.listActive(entityType)` gives each key's `column_name`: `cf_text_03` → `cf_text_03_read`.

`FieldDescriptor` carries no column name and shouldn't start to, so the catalog reads definitions directly, composing the same two sources as `EntityFieldDictionary`. It also turns a document into `column → value` (`toColumns`, matching keys case-insensitively) and a row back into a document (`toPermissions`), so `RoleValidator` and `RoleService` work from the same conversion and can't disagree.

Three things to get right:

- **Column names never come from a request.** They're built into the SQL, so a request's key is only ever looked up in the catalog, and an unknown key is a 400 before any SQL runs. `custom-fields.md` §8 accepts the same reasoning for its purge.
- **The role row must exist first.** Hibernate holds a new `Role`'s INSERT until it flushes, and `JdbcClient` goes straight to the database. So `RoleService.create` calls `saveAndFlush` before inserting the permission rows, or the FK fails.
- **It runs inside the service's transaction.** `JdbcClient` then shares the JPA connection, so RLS and the `tenant_id` default apply (`multitenancy.md` §3), as they do for the slot lock today.

### 3.3 Keeping columns and fields in step

With no entity classes, Hibernate's `ddl-auto=validate` doesn't look at these tables, so a small startup check takes over that job. `PermissionColumnsVerifier` is a `SmartInitializingSingleton`, in the style of `RowLevelSecurityVerifier`. For each table it reads the `_read` and `_edit` column names from `information_schema.columns` and compares them with what should exist: a pair per provider field and a pair per `CustomFieldSlots` slot.

`can_read` and `can_edit` end the same way but are CRUD columns, so the comparison skips them. Anything missing or extra stops the app, with a message naming the column. So a provider field added without its V1 columns, or a slot pool widened on the record tables only, fails at boot rather than at the first request. The comparison is a pure function, unit-tested without a database, as in `RowLevelSecurityVerifier`.

### 3.4 `RoleService`

| Method | Rules |
|---|---|
| `list()` | Roles by name, with `userCount`. Two queries: roles, then one grouped count. Never reads the wide tables. |
| `get(id)` | The whole document, through the catalog: three entity rows and the app row. |
| `create(request)` | Takes the field locks (§3.8), validates, then writes the role and four rows in one transaction. |
| `replace(id, request)` | 409 on the Administrator. Takes the field locks, then updates the name and applies the document. **Fields absent from the document stay as they are**, so a custom field created while an admin had the editor open keeps its bits. |
| `delete(id)` | 409 on the Administrator. 409 with the count if held: "12 users still have this role." The FK is the backstop for a concurrent assignment. |
| `createWithFullRecordAccess(role, app)` | Provisioning only (§3.6): all CRUD and both bits on every field the entity has now. |

A name clash is translated from `uq_roles_name` into `RoleNameTakenException`, the way `UserService` handles `uq_users_username`.

### 3.5 Assignments and the last Administrator

`RoleAssignmentService`:

| Method | Rules |
|---|---|
| `list()` | Every assignment in the tenant as `(userId, roleId)`. One query, no N+1 for the Users page. |
| `assign(userId, roleId)` | Loads both first, so a guessed id is a 404 (RLS hides it). Idempotent: `insert … on conflict do nothing`, so two identical requests at once both succeed. |
| `unassign(userId, roleId)` | Loads both first. Idempotent: one bulk `delete`. When the role is the Administrator and the user really holds it, runs the check below first. |

**At least one enabled user must hold Administrator.** There's no cross-tenant admin console (`multitenancy.md` §1), so a tenant that loses its last Administrator can't recover by itself. Two admins removing each other at the same moment could each see the other as still present, so the check takes a transaction-scoped advisory lock per tenant, the same technique as `CustomFieldService.lockEntityFields`, with its own namespace:

```java
// pg_advisory_xact_lock(ROLE_ADMIN_LOCK_NAMESPACE, tenant hash)
if (assignments.countOtherEnabledHolders(roleId, userId) == 0) {
    throw new LastAdministratorException();
}
```

It counts the *other* enabled holders. So removing Administrator from someone who doesn't hold it stays a no-op, removing a disabled holder is allowed while an enabled one remains, and a disabled holder doesn't count as the one left.

Only a real Administrator removal takes the lock, so normal assignment never waits. This counts enabled users because `enabled` is a real column, even though no endpoint changes it yet. The future disable-user path must run the same check.

### 3.6 Provisioning

`TenantInitializer.initialize` is told who the first user is, since `RolesInitializer` has to assign them:

```java
public interface TenantInitializer {
    /** Called once per new tenant, inside the provisioning transaction, with the tenant bound. */
    void initialize(TenantId tenant, UUID firstUserId);
}
```

```java
User first = users.create(request.adminUsername(), request.adminDisplayName(), request.adminPassword());
initializers.forEach(initializer -> initializer.initialize(request.id(), first.getId()));
```

`DefaultLayoutsInitializer` ignores the new argument.

| Role | `is_admin` | Entities | Fields | App permissions |
|---|---|---|---|---|
| Administrator | yes | all CRUD | all standard fields | all |
| Standard user | no | all CRUD on Contact, Company, Deal | all standard fields | none |

With everything closed by default, `RolesInitializer` writes these grants itself, through `RoleService.createWithFullRecordAccess` and the catalog. A new tenant has no custom fields, so the standard fields are all there is. Standard user is an ordinary role: editable, deletable, and only a starting point. Failing here fails provisioning as a whole, as for layouts.

Administrator's stored bits are never read (D8), so they fall behind as custom fields are added. That's why its page shows no matrix and it can't be duplicated (§5.3).

`DemoTenantSeeder` needs no change. Acme's `lead_source` is created after provisioning, so it starts unticked on Standard user, which is the default working as intended.

### 3.7 Users

`users` has what the User page needs, and nothing about roles:

- `GET /api/users/{id}`, with `UserNotFoundException` → 404.
- `PUT /api/users/{id}`, body `UpdateUserRequest(@NotBlank @Size(max = 255) String displayName)`, with `User.rename(displayName)`.

`username` stays write-once: it's the global login name. `enabled` stays read-only here, because disabling needs the session re-check deferred in `multitenancy.md` §9.5 to mean anything. A `PUT` rather than a record-style `PATCH`, because a Java record can't tell absent from null, and the companion spec adds a nullable `jobTitleId` to this same body.

### 3.8 The required-field rule

**A role with Create on an entity must have Read and Edit on each of that entity's required fields.** `RecordMapper` refuses a create that leaves out a required field, so without this rule a role could hold Create and never succeed. The rule spans two things that change separately, a role's bits and a field's `required` flag, so each side enforces it. §6.4 reviews the result.

**Saving a role.** `RoleValidator` checks the merged result: the stored bits with the request laid over them, as `RecordMapper` does for records. A stale document can leave a field out, so checking the request alone isn't enough. The editor's reducer ticks the bits so a person never meets the error (§5.4).

**Creating a required custom field.** Its slot's bits are `false` on every role, so every role with Create would break the rule at that moment. `CustomFieldService.create` publishes an event after the save, and `roles` grants the field to exactly those roles:

```java
// customFields
public record CustomFieldCreated(EntityType entityType, String columnName, boolean required) {
}
```

```java
// roles
/** Keeps a role with Create able to fill in a new required field. */
@Component
class RequiredFieldGrants {

    // Synchronous on purpose: it must share the field's transaction and tenant.
    @EventListener
    void on(CustomFieldCreated field) {
        if (field.required()) {
            permissions.grantToRolesWithCreate(field.entityType(), field.columnName());
        }
    }
}
```

Spring runs an `@EventListener` on the publisher's thread, inside its transaction, so a failure rolls the field back too. It has to stay that way: `@Async` would lose the tenant (`MissingTenantContextException`), and `@TransactionalEventListener` would run after commit, leaving a moment where the rule is broken.

This is the only code that sets a bit to `true` without an admin asking, and it's narrow: required fields only, and only on roles that already hold Create. There's no way around it, because the alternatives are a role that silently can't create, or taking Create away from it.

**Two admins at the same moment.** A role save and a required-field create could each miss the other's work, so both take the lock custom fields already use. That lock is the public `CustomFieldService.lockEntityFields(entityType)`, marked `@Transactional(propagation = MANDATORY)` because a transaction-scoped lock outside a transaction is released at once. `RoleService.create` and `replace` take it for every `EntityType`, in enum order, before reading anything. Creating a field takes only one, so the two can't deadlock. Role saves then run one at a time within a tenant, which costs nothing for an admin action. A picklist save (`PicklistService.replace`) takes the same lock for its one entity, so it can't interleave with a field being created there either.

**The database backstop.** For standard fields the rule is also the `ck_*_required` CHECK in V1. No CHECK can cover custom fields, because whether a slot is required depends on the tenant and lives in another table.

---

## 4. HTTP API

### 4.1 Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/roles` | 200 `[{ id, name, isAdmin, userCount }]`, by name | — |
| GET | `/api/roles/{id}` | 200 the role document | 404 |
| POST | `/api/roles` | 201 the role document | 400 `VALIDATION_ERROR`; 409 name taken |
| PUT | `/api/roles/{id}` | 200 the role document | 400; 404; 409 name taken or Administrator |
| DELETE | `/api/roles/{id}` | 204 | 404; 409 Administrator or still held |
| GET | `/api/role-assignments` | 200 `[{ userId, roleId }]` | — |
| PUT | `/api/users/{userId}/roles/{roleId}` | 204 (idempotent) | 404 |
| DELETE | `/api/users/{userId}/roles/{roleId}` | 204 (idempotent) | 404; 409 last Administrator |
| GET | `/api/users/{id}` | 200 `UserResponse` | 404 |
| PUT | `/api/users/{id}` | 200 `UserResponse` | 400; 404 |

The existing `ApiError` shape covers all of these, with no new `ErrorCode`. `FORBIDDEN` arrives with enforcement.

### 4.2 The role document

```jsonc
{
  "id": "…",
  "name": "Sales",
  "isAdmin": false,
  "app": {
    "manageUsers": false, "manageRoles": false, "manageJobTitles": false,
    "manageLayouts": false, "manageCustomFields": false
  },
  "entities": {
    "CONTACT": {
      "canRead": true, "canCreate": true, "canEdit": true, "canDelete": false,
      "fields": {
        "firstName":   { "read": true, "edit": true },
        "lead_source": { "read": true, "edit": false }   // a key, never cf_text_03
      }
    },
    "COMPANY": { /* … */ },
    "DEAL":    { /* … */ }
  },
  "createdAt": "…", "updatedAt": "…"
}
```

- **Keys, not slots.** `CustomFieldDefinitionResponse` hides `column_name` because storage isn't part of the contract; the same holds here.
- **`fields` lists what `EntityFieldDictionary` lists:** standard then active custom fields. Unallocated slots and archived fields aren't in it.
- **Values only.** Labels and `required` come from the schema bundle, which the page already has a query for. So the document stays small, and a blank role needs no template endpoint.

`POST` and `PUT` take the same body minus `id`, `isAdmin` and timestamps. `isAdmin` is in neither. A field missing from a `POST` is `false`; a field missing from a `PUT` keeps its stored value.

### 4.3 Validation

`RoleValidator` returns every problem in one `400 VALIDATION_ERROR`, as `EntityLayoutValidator` does. Bean validation covers only the shape. Field rules run on the merged result (§3.8).

| Rule | `field` | `reason` |
|---|---|---|
| name not blank, at most 100 (after stripping) | `name` | "Required" / "At most 100 characters" |
| every `EntityType` present | `entities` | "Missing CONTACT" |
| create, edit or delete needs read | `entities.CONTACT.canRead` | "Required by Create, Edit and Delete" |
| each key is a field of the entity (case-insensitive) | `entities.CONTACT.fields.foo` | "Unknown field 'foo'" |
| edit needs read | `entities.CONTACT.fields.email` | "Edit requires Read" |
| with Create, a required field has read and edit | `entities.CONTACT.fields.firstName` | "Required field: needs Read and Edit while Create is on" |

The CRUD rule and the required rule on standard fields are also CHECKs; the validator gives them a usable message. Keys are stored by way of the catalog, so their spelling doesn't reach storage.

---

## 5. Settings UI

### 5.1 Information architecture

```
/settings                     ▾ General  ← new, first section
                                [Roles] [Users]
                              ▾ CRM …  ▾ Sales …
/settings/roles               Roles list
/settings/roles/new           Role editor, on a role not stored yet
    ?from=<id>                  a copy of that role
/settings/roles/:id           Role editor
/settings/users               Users list
/settings/users/:id           User page
```

`roles` and `users` are static segments, so they outrank `settings/:entity`, as `settings-sections.md` §3 planned. Neither is an entity's plural. Add one entry at the top of `SECTIONS` in `SettingsPage.tsx`, using the existing `PersonIcon` and a new `ShieldIcon` in `lib/icons.tsx`:

```ts
{
  key: "general",
  title: "General",
  cards: [
    { title: "Roles", description: "What each role can see and change.", to: ROLES_PATH, icon: ShieldIcon },
    { title: "Users", description: "The people in your organisation and their roles.", to: USERS_PATH, icon: PersonIcon },
  ],
},
```

Back links as elsewhere: `← Settings` on the lists, `← Roles` and `← Users` on the detail pages.

### 5.2 Data: `lib/roles`, `lib/users`

No JSX, same rule as `lib/layouts`.

| File | Role |
|---|---|
| `lib/roles/types.ts` | The document and request types. |
| `lib/roles/roles.ts` | `useRoles`, `useRole(id)`, `useEntityFields` (every entity's schema bundle, combined), `useCreateRole`, `useUpdateRole`, `useDeleteRole`, `useRoleAssignments`, `useAssignRole`, `useUnassignRole`. |
| `lib/roles/roleDraft.ts` | The editor's model (§5.4). Pure. |
| `lib/users/users.ts` | `useUsers`, `useUser(id)`, `useUpdateUser`. |

Writes put their result in the cache and invalidate, as `lib/layouts` does.

### 5.3 Roles list (`/settings/roles`)

A title, a `.page-intro`, then a `.list-table`: Name (a link, with an `Administrator` badge), Users (the count), actions. Default order is the server's.

The intro carries D14, the same way the Layouts section admits what isn't live yet: *"Roles are stored here but aren't enforced yet: every user can still do everything."* It goes when enforcement ships.

| Control | Behaviour |
|---|---|
| New role | Opens `/settings/roles/new`. Nothing is stored until Save. |
| Row | Opens the role, as list tables do. |
| ⧉ Duplicate | Opens `/settings/roles/new?from=<id>`. Disabled on Administrator: its stored bits don't track custom fields (§3.6). |
| 🗑 Delete | `ConfirmDialog`. Disabled on Administrator and while `userCount > 0`, with the reason as its title. The server checks again. |

### 5.4 Role editor

```
← Roles
[Sales                    ] (title-sized input)                     Cancel   [Save]

▾ General
   ☐ Manage users      ☐ Manage roles       ☐ Manage job titles
   ☐ Manage layouts    ☐ Manage custom fields

▾ Contacts
   Records    ☑ Read   ☑ Create   ☑ Edit   ☐ Delete
   Field                         ☑ Read   ☑ Edit      ← sets the whole column
   First Name *                   ☑ 🔒     ☑ 🔒        ← locked while Create is on
   Email                          ☑        ☑
   Lead Source                    ☐        ☐

▾ Companies …
▾ Deals …
```

- One `SettingsSection` per block, in `ENTITIES` order, so collapsed state works without new code. `storageKey`: `roles/app`, `roles/CONTACT` and so on, shared across roles.
- Field rows come from the entity's schema bundle (`useQueries` over `entitySchemaQuery`), in dictionary order. Standard and custom aren't told apart, matching the layout palette.
- The **column headers** set every field at once, since a new role starts with all of them off. Locked rows stay as they are.
- Required fields show ticked and disabled **while Create is on**, with the reason as a title. With Create off they're ordinary rows.
- With Read off, the field rows are disabled and keep their values, so unticking and reticking loses nothing.
- With Read on and no field ticked, a muted line says "This role will see empty records."
- **Administrator:** no matrix. The name, a badge, and "The Administrator role has every permission and can't be changed."

`roleDraft.ts` follows `layoutDraft.ts`: `fromRole`, `blankDraft`, `copyDraft`, `toRequest`, `isDirty`, `validateDraft`, and one reducer that takes the required keys, like `layoutDraftReducer(draft, action, requiredKeys)`. A key missing from the document is closed, the same as the column default. A key the document has but the dictionary doesn't is left out of the draft and so out of the save, which keeps its stored bits. A required field arriving closed while Create is on (it can only be mid-race) opens in the draft, so the editor can't hold what the validator rejects.

| Action | Effect |
|---|---|
| `renameRole(name)` | |
| `setApp(key, value)` | |
| `setCrud(entity, op, value)` | Ticking Create, Edit or Delete also ticks Read. Ticking Create also ticks Read and Edit on every required field. Unticking Read unticks the other three. |
| `setField(entity, key, access, value)` | Ticking Edit also ticks Read. Unticking Read unticks Edit. Refused for a required field while Create is on. |
| `setAllFields(entity, access, value)` | The same rules, field by field. |
| `resetDraft(draft)` | After a save |

So the UI can't produce what the validator rejects, and the server stays the authority: a 400 appears in the error banner, line per `details` entry, as in the layout editor.

**Saving** behaves as in `settings.md` §6.6: `PUT` resets the draft from the response; `POST` moves to `/settings/roles/:id` with `replace`. The guard (`useBlocker`, `beforeunload`, the `leaving` ref) is `lib/useUnsavedChangesGuard.ts`, shared with the layout editor and the User page. Last save wins.

### 5.5 Users list (`/settings/users`)

A `.list-table`: Name (link), Username, Roles (comma-separated names, or `—`), Status (a "Disabled" badge when applicable). It joins `useUsers`, `useRoleAssignments` and `useRoles` on the page. The row opens the user. There's no New user button (§8).

### 5.6 User page (`/settings/users/:id`)

```
← Users
Alice Example
alice · Active · Joined 28 Oct 2026

▾ General
   Display name   [Alice Example        ]                               [Save]
   Username       alice

▾ Roles (2)                                                    [Add role ▾]
   Administrator                                                         ✕
   Standard user                                                         ✕
```

- **General** has one explicit Save, with the unsaved-changes guard while the name differs.
- **Roles** apply immediately: each add or ✕ is one small reversible action, so there's nothing to save or guard. Add role is a `Select` of the roles not yet held. An error shows at the top of the section, as in `LayoutsSection`, including the 409 "At least one user must stay an Administrator."
- A user with no roles shows "No roles yet." A user with no roles is a valid state.

---

## 6. Architectural integrity analysis

Each claim in this section is covered by a test or was run by hand; §9 says which.

### 6.1 What holds up

**Boolean columns per field are sound here, and fit the slot model.** A column per field usually fails because fields are data and columns are schema. That's not the case in this codebase: the slot pool already turned custom fields into a fixed set of columns, so a bit per slot needs no DDL at runtime. Size isn't the concern either: 239 columns against Postgres's limit of 1600, and about 330 bytes per row, with four rows per role. Reading one entity's permissions for a user will be one indexed row per role, where a row-per-field table returns one row per field. Real columns are also what let a CHECK hold the required rule for standard fields (§6.4).

**Tenant isolation carries over unchanged.** Every table takes the standard block, and the startup verifier picks up new tables with no change to it. A role of another tenant can't be assigned even by guessed UUID: the composite FK rejects it.

**Multi-role users have a clear rule.** A grant-only model means no precedence between roles and no deny conflicts (D5). With everything `false` by default, a grant exists only because someone gave it.

**Dependencies stay one-way.** `roles` reads from `users` and `customFields`, and neither learns it exists (§3.1).

### 6.2 What the brief needed added

| Gap | Why it matters | Resolved by |
|---|---|---|
| **Nobody can administer the first role** | A fresh tenant has no roles, and a roles page guarded by a role can't be used to create the first | Provisioning creates Administrator and assigns the first user (D9) |
| **A tenant can lock itself out** | No cross-tenant console exists | Administrator is locked, and its last holder is protected, with a lock against the two-admins race (§3.5) |
| **Standard columns can drift from the provider** | Adding a provider field without columns would leave a field that no permission covers | `PermissionColumnsVerifier` stops the app at boot (§3.3) |
| **A reused slot inherits grants** | A slot's bits have no link to the field that owned them, so a new field would start out granted to whoever held the old one. That defeats D4 | Purge resets the pair to `false` (§2.6). Purge is deferred; `custom-fields.md` §8 records the step |
| **Create and required fields can conflict** | §6.4 | The required-field rule (D12) |
| **Which of `Company`'s 29 non-slot columns are "fields"?** | Only 10 are served by the API; billing address, owner and so on aren't | Pairs follow the dictionary (D6) |

### 6.3 What `false` by default changes

Closed by default is the right default for permissions: a forgotten bit hides a field instead of exposing it. The design improves in two ways:

- **Mistakes fail closed.** A missed bit or a bug can only take access away.
- **A grant means someone chose it.** A user holding Sales and Marketing can't read Margin just because nobody thought about Margin on Marketing.

It also has costs, each handled here:

| Cost | Handled by |
|---|---|
| **A new optional custom field reaches nobody** but Administrator until granted, role by role. A layout can place it, and under enforcement the bundle will drop it for everyone else | Accepted. The custom fields UI, when built, should ask which roles get the field at creation. Until then a field created through the API needs a visit to each role |
| **A required field can't start closed** on a role with Create | `RequiredFieldGrants` (§3.8): the one automatic grant |
| **`customFields` now has a consequence in `roles`** | An event keeps the dependency one-way (§3.1) |
| **A new role takes more clicks** | Column header ticks, and Duplicate |
| **Slot reuse became a security concern.** It used to inherit restrictions; now it would inherit grants | The purge step (§6.2) is a requirement, not a tidy-up |
| **Standard user can't rely on defaults** | `RolesInitializer` writes its grants (§3.6) |
| **A new optional standard field is closed for every role in every tenant** once there's production data | Each release decides: leave it closed, or backfill in its migration |
| **Read with no fields ticked shows empty records** | A note in the editor (§5.4) |

### 6.4 Required fields and Create (D12)

The rule involves a role's bits, a field's `required` flag and a provider's code, and they change at different times. That's why a single validator isn't enough. Every way to break the rule:

| How it could break | Closed by | Evidence |
|---|---|---|
| Saving a role with Create on and a required field closed | `RoleValidator` on the merged result; the reducer; `ck_*_required` for standard fields | `RoleValidatorTest`, `RequiredFieldCheckTest`, `roleDraft.test.ts` |
| Closing a required field while Create is on | The same | `RequiredFieldRuleIntegrationTest`, `roleDraft.test.ts` |
| A new required custom field, against roles that already hold Create | `RequiredFieldGrants`, in the field's transaction | `RequiredFieldRuleIntegrationTest` |
| A required field created while an admin has that role open | It's absent from their document, so its granted bits stay | `RequiredFieldRuleIntegrationTest` |
| That stale document also turns Create on, for a role that missed the grant | The merged check rejects it with a 400 | `RequiredFieldRuleIntegrationTest` |
| A role save and a required-field create at the same moment | The shared advisory lock (§3.8) | `RequiredFieldRuleIntegrationTest`, which fails with the lock removed (§9.1) |
| A new required standard field in a later release | Adding its `ck_*_required` clause **fails the migration** until roles with Create are backfilled, so it can't be forgotten | Run on Postgres 16: refused, then accepted after the backfill |
| A purged slot taken by a new required field | The purge resets the pair, then the field goes through the normal create path | Purge is deferred |
| `required` switched on for an existing field, or a field un-archived | Neither has an endpoint today. Whichever adds one must publish the same event | Must be remembered: `custom-fields.md` §10 says so |
| Hand-written SQL | `ck_*_required` for standard fields. Custom fields aren't covered | Trusted, as in `multitenancy.md` §13 |

Two cases need no protection. A role **without Create** may have a required field closed. An **archived** field stops being required (`CustomFieldDescriptors` already does this), so its bits can't conflict.

**It's checked per role, not per user, and that matters.** With the AND-inside-a-role rule of §7, a user who may create holds the required bits in that same role, so the create can always succeed. A rule across a user's roles wouldn't give that.

**The rule has one gap: Edit without Create.** `RecordMapper.apply` checks required fields on updates too, against the merged record, and its own comment notes that "a newly added required custom field blocks saving existing records until they're given a value". A role with Edit, no Create, and no edit bit on that field can't supply the value, so every save of an older record fails with a 400 the user can't fix.

Widening the rule to Edit would close this, but it would stop you restricting required fields on Edit roles at all. The better fix is at enforcement: `apply` shouldn't demand a field the caller can't edit. The record stays blank until someone with access fills it in. §7 records that.

### 6.5 Risks that remain

| Risk | Severity | Note |
|---|---|---|
| **Until enforcement, anyone can make themselves Administrator.** Any signed-in user can call every endpoint here. That's no worse than today, since nothing is restricted, but these endpoints must be the first ones guarded. | High once real users exist | The Roles page says so. Enforcement must start with `/api/roles`, assignments and `/api/users`. |
| **Record-level access can't be expressed.** The booleans mean all of an entity's records. "Sales reps see only their own deals" is the usual next CRM requirement, and only `companies` has an `owner_id`. | Medium, later | The path is additive: a `read_scope` column beside `can_read`, restrictive RLS policies as `multitenancy.md` §5.3 foresees, and `owner_id` on the other tables. No table here is reshaped. |
| **Wide DDL.** 688 booleans across three tables, about 350 lines of V1, and widening a slot pool now touches six tables instead of three. | Low | The startup check catches a table that was missed (§3.3). |
| **Column names are built into SQL strings.** Spring can't parameterise a column name, so `EntityPermissionRepository` concatenates them. | Low | They only ever come from the catalog, never from a request (§3.2). |
| **A new `EntityType` costs a table.** The one real cost of per-entity tables over a single table. | Low | Entity types are rare and already need code. The `switch` makes it impossible to forget. |
| **Renaming yourself leaves the sidebar stale.** `/api/auth/me` reads `displayName` from the session principal. | Low | Fixed at next login. Accepted. |
| **Last save wins on a role.** Two admins editing one role: the later save overwrites. Fields absent from the document are safe. | Low | Same as layouts and records. |

---

## 7. What this design requires of the resolver

Not designed here. These are the constraints the tables above put on it.

```
may(op on entity)  = some role of the user: role.entity.can_<op>
may read field     = some role of the user: role.entity.can_read and role.field.read
may write field    = some role of the user: role.entity.can_<create|edit> and role.field.edit
```

1. **AND inside a role, OR across roles.** Not "any role has entity read, and any role has field read". A role keeps its field bits when its entity Read is unticked, so mixing across roles would count bits from a role with no access to the entity. `bool_or(can_read and email_read)` over the user's rows does this in one query.
2. **`is_admin` grants everything, without reading rows.** That's what lets new permissions and fields need no backfill for Administrator.
3. **No exemption for required fields.** D12 guarantees a role with Create holds them, so the bits are the whole answer. If that's ever wrong, the user gets a refused create, not extra access.
4. **Resolve per request, not at login.** `TenantUserPrincipal.getAuthorities()` should stay out of it: sessions persist (`server.servlet.session.persistent=true`), so a revoked role would keep working until the session ended.
5. **Field checks belong in the record path:** `RecordMapper.toRecords`, `RecordMapper.apply` and the schema bundle's `fields`, as `entity-layouts.md` §1 already says. Filtering `fields` also removes the matching placements, through the self-consistency step in `EntitySchemaService`.
6. **A hidden field stays absent, not null,** so merge-patch leaves it untouched on a GET-then-PUT round trip.
7. **`RecordMapper.apply` must not demand a required field the caller can't edit** on update (§6.4).
8. **Escalation:** only an Administrator should assign or remove the Administrator role, and a `manage_roles` holder shouldn't be able to grant more than they hold.
9. **Still to decide then:** `company_contacts` is a link row with no permission table of its own; linking probably requires Edit on the company. A role without Company read can't use the Customer picker on a deal. `ENTITIES[…].title` builds a record's name from fields, so a role without them sees blank names.

---

## 8. Considered and left out

| Left out | Why |
|---|---|
| Field columns defaulting to `true` | Needs no grant when a field is created, but a missed bit exposes a field, and roles dilute each other's restrictions. Rejected for D4. |
| Exempting required fields in the resolver | It would paper over a broken rule rather than prevent it (§7.3). |
| Requiring required fields on every role | Only Create needs them. A role that only reads may be denied a required field. |
| Granting new optional custom fields automatically | Contradicts D4. |
| A direct call from `customFields` into `roles` | `roles` reads definitions, so this would be a bean cycle. |
| A database trigger for the required rule on custom fields | Whether a slot is required is per tenant and in another table. That logic belongs in the service. |
| One `role_field_permissions` table, a row per role and field | The usual alternative. With everything `false` by default it would be sparse, one row per grant, so it's closer than it was. Left out because a CHECK can't span rows, reading a role's access returns many rows, field keys there have no FK, and the brief's columns match the slot model. |
| JSONB of field bits on each row | No wide DDL, but no database meaning, and keys that can drift. |
| One `smallint` per field (none/read/edit) | Would make Edit without Read unrepresentable and halve the columns. The brief asks for two booleans, which also read better in SQL; the validator holds the rule. |
| One table for all entities | Standard fields differ per entity, so it would need an anonymous `std_01` pool. |
| CHECKs for field-level Edit requires Read | 115 clauses per table, guarding a single write path. |
| JPA entities for the permission tables, with a generated Java mirror like `CustomFieldSlots` | An earlier draft of this design. It needs about 230 fields per table, each with a getter and setter, and a new script, for columns that are only ever reached by name. It wouldn't add compile-time safety, since all access is by key anyway. |
| A `do $$ … $$` loop in V1 that adds the columns | Far fewer lines, but the columns can't be found by searching V1, and V1 spells everything else out. |
| Deny rules, role inheritance, permission sets | Each needs a precedence model. Grants only, for now. |
| New user, disable user, delete user, password reset on the Users pages | Not asked for. `POST /api/users` exists, so New user is a small addition later. The companion spec seeds a second demo user for testing. |
| A role description column | Not asked for. One column when it's wanted. |
| A limit on roles per tenant | Protects nothing. |
| Audit of who changed what, `assigned_by` | No audit exists anywhere yet. It deserves its own design. |
| Optimistic locking | Records and layouts are last-write-wins too. |

---

## 9. Verification

Verified 2026-10-31 against Postgres 16 (`postgres:16`, the dev container), as `blusimian_app` under RLS.

### 9.1 Tests

| Test | Kind | Covers |
|---|---|---|
| `PermissionColumnsVerifierTest` | unit | A missing column and an extra column are each reported by name; a matching list passes; `can_read` and `can_edit` aren't mistaken for field columns. |
| `FieldPermissionCatalogTest` | unit | Standard keys become snake-case columns; a custom field becomes its slot's columns; an archived field is absent; a document becomes columns without its own spelling reaching SQL; a row becomes a document under keys. |
| `RoleValidatorTest` | unit | Each rule of §4.3, several reported together, case-insensitive keys, the required rule on the merged result. |
| `RequiredFieldGrantsTest` | unit | An optional field writes nothing; a required field is granted on its entity. |
| `roleDraft.test.ts` (Vitest) | unit | Each reducer rule; ticking Create ticks required fields; a locked field refuses, and unlocks with Create off; `setAllFields`; `copyDraft`; `isDirty`. |
| `EntityPermissionRepositoryTest` | integration | Insert, find and update as `blusimian_app`. A new row is closed everywhere. Another tenant's row can't be read or updated. `RoleService.create` flushes before the inserts. |
| `RequiredFieldCheckTest` | integration | Driven by the providers: each required field closed in turn is refused while Create is on, and no optional field is demanded. Fails if the CHECK and `required` drift apart. |
| `RoleServiceIntegrationTest` | integration | One row in each table; a document round-trips; keys, never slots; a field left out of a `PUT` keeps its bits; `Sales` and `SALES` clash; the Administrator is locked; delete cascades. |
| `RoleAssignmentIntegrationTest` | integration | Last Administrator, disabled holders, idempotent assign and unassign, concurrent removals and assigns, held-role delete, the FK backstop, user delete cascades. |
| `RequiredFieldRuleIntegrationTest` | integration | A required custom field reaches only roles with Create. A failed create rolls the grant back. A concurrent role save and field create leave the rule intact. A stale `PUT` keeps the granted bits, and is refused if it turns Create on without them. |
| `RoleApiTest` | integration (MockMvc) | JSON shapes, status codes, no `cf_` in a document, `isAdmin` in a body ignored, malformed documents are 400s, every endpoint needs a session. |
| `TenantIsolationTest`, extended | integration | Provisioning gives each tenant both roles and makes its first user Administrator; a role or user of another tenant can't be read or assigned, by service or by raw SQL; another tenant's user is a 404. |

**The two concurrency tests can fail.** Each was run once with its lock removed: without the Administrator lock a tenant ended with no Administrator, and without the field locks a role held Create without a required field. Both tests went red for that reason.

### 9.2 By hand

Passed in Chrome against the running app, on a freshly reset database.

1. `/settings` shows General first, with Roles and Users; other sections are unchanged.
2. A fresh tenant lists Administrator (1 user, badge) and Standard user. Log in as `alice` and `bob`: each sees only their own tenant's data.
3. Administrator opens with no matrix and can't be deleted, renamed or duplicated.
4. New role: everything off. Tick Read on Contacts: field rows enable, all unticked, and the empty-records note shows. The Read header ticks them all. Untick Read on Email: its Edit unticks.
5. Tick Create: First Name and Last Name tick and lock. Untick Create: they unlock and stay ticked.
6. Save: the URL becomes `/:id`, and Back goes to the list. Cancel while dirty: "Discard unsaved changes?".
7. Duplicate copies every tick and leaves the name `Copy of …`, stored only on Save.
8. Through the API, create an optional custom field: it's unticked on every role. Create a required one: ticked and locked on roles with Create, unticked on the rest.
9. Users lists everyone. A user page: rename, Save, the list updates.
10. Add and remove a role: applied at once, no Save. Removing Administrator from the last holder shows the error and changes nothing.
11. A role with holders: Delete is disabled. Remove the holders: it enables, and delete works.

Also run: a name clash in the editor's banner; `?from=` on the Administrator; unknown role and user ids; the layout editor's and the User page's unsaved-changes guards.

**Not checked:** phone-width layouts and keyboard-only use of the matrix.

---

## 10. Extension recipes

| To add | Do |
|---|---|
| An optional standard field | The provider, then two columns in V1 |
| A required standard field | The same, plus its `ck_*_required` clause. With production data, the migration backfills Read and Edit on roles with Create before adding the clause |
| An app permission | A column in `app_permissions`, a field in `AppPermission` and its document, an action label in the editor |
| An entity | A table in V1, a case in `EntityPermissionRepository.table`, a section in the editor (driven by `ENTITIES`) |
| Widen a slot pool | The new slot on each record table and `gen_slots.py`, as today, plus its pair on each permission table |
| Custom objects | `WIP/custom-objects-architecture.md` §12 |
| Enforcement | §7. Start with the admin endpoints |
