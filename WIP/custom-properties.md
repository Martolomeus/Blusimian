# Custom Properties — Spec

**Status:** Proposal, nothing implemented · **Scope:** a **Custom properties** section on each entity's settings page: list the entity's custom fields, create one (with its picklist options), and edit one (label, required, a picklist's options). Key and type are fixed once created. One admin API behind it
**Date:** 2026-11-14 · **Builds on (as-built):** `Architecture/custom-fields.md`, `Architecture/picklists.md`, `Architecture/settings.md`, `Architecture/entity-layouts.md`, `Architecture/roles-and-permissions.md`
**Supersedes:** `WIP/properties.md` (untracked draft, 2026-11-08). This spec keeps its API shape, narrows the UI to custom fields, and fixes its gaps around required fields (§2, R8)
**Code:** new package `entityFields`; `customFields`, `picklists`, `entityLayouts`, `roles`, `platform.web`, `tenants.DemoTenantSeeder`, `V1__init.sql`; `Frontend/src/features/settings/fields`, `Frontend/src/lib/entityFields`

> Each entity's settings page gets a **Custom properties** section above Layouts. It lists the
> entity's custom fields, has a "New property" button, and each row opens that field's page.
> One editor serves both pages. It shows label, API name, type and required, plus an options
> list for a picklist. On a stored field the API name and type are read-only.
>
> Behind it is one admin API, `/api/entity-fields/{entityType}`, in a new thin package
> `entityFields` that sits above `customFields` and `picklists`, the way `entitySchema` does.
> A save on either page is one request and one transaction. Creating a picklist field becomes
> three plain calls in one method (check, insert the definition, insert the options), and the
> event that carried options between packages goes away. Neither `customFields` nor
> `picklists` serves HTTP after this.
>
> A field that becomes required, at creation or later, publishes one event. `roles` already
> grants on it, and `entityLayouts` now places the field on every layout that lacks it. That
> closes a known gap: today a required field created after its layouts can't be filled in.

**Terms.** "Property" is the UI's word for a field, and the URL segment `properties`. Code, API,
database and docs say **field**. A picklist's entries are **options** on the wire and in the UI
(`picklists.md`, Terms).

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | "Property" is only display text and a URL segment. Everything else says "field". | Terms |
| D2 | A **Custom properties** section on `/settings/:entity`, above Layouts, lists the entity's active custom fields. Standard fields aren't listed in this build. | §5.3 |
| D3 | One admin API, `/api/entity-fields/{entityType}[/{key}]`, in a new package `entityFields`: `GET` all fields (standard and custom, with a `custom` flag), `POST` a custom field, `PATCH` any field, `DELETE` (archive) a custom field. It replaces `/api/custom-fields` and `/api/picklists`. The key is the only identifier on the wire. | §4.1, §4.2 |
| D4 | Creating is one method and one transaction: check everything, `CustomFieldService.create`, `PicklistService.seed`. `CustomFieldPicklists`, `CustomFieldOptionRequest` and the options on the event are deleted. | §3, §4.6 |
| D5 | Editing is one `PATCH` carrying any of `label`, `required` (custom fields only) and `options` (any picklist, the whole list). One Save on the page is one request. | §4.2 |
| D6 | **Fixed after creation:** key and type. **Editable:** label, required, options. | §4.3 |
| D7 | A custom key matches `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`, is at most 50 characters and isn't `new`. A DB CHECK backs this up. The UI suggests the key from the label, and the admin can change it until the first Save. (Namespace: **O1**.) | §4.3 |
| D8 | Every 400 comes back together, before the lock is taken. A 409 for a taken or reserved key carries a `key` detail, so the UI can show it under the API name. | §4.3 |
| D9 | When a custom field **becomes required** (created as required, or switched on later), `CustomFieldBecameRequired` is published. It replaces `CustomFieldCreated`. `roles` grants on it (unchanged behaviour), and `entityLayouts` places the field on every layout that lacks it. | §4.4 |
| D10 | The server assigns `sort_order` (last). The request no longer carries it. The section sorts by label. | §4.5 |
| D11 | `PicklistService` becomes a store: `seed`, `replace` and `check`. The caller holds the transaction and the lock. It no longer depends on `EntityFieldDictionary` or `customFields`. | §4.5 |
| D12 | Options editor: drag to reorder, rename, activate or deactivate, add, Sort A–Z. A saved option can't be removed. The UI never asks for a code: the server derives it, and the UI shows it read-only as "API name". | §5.5 |
| D13 | One `FieldEditor` serves both the new and the stored field. Its two pages decide what the draft starts from and how Save stores it, as with the layout and role editors. | §5.2 |
| D14 | `manage_custom_fields` gates the whole API once enforcement lands. No new permission. | §4.8 |

---

## 2. Review: where we are

What the review of the custom field, picklist and settings code found, and where this spec deals
with each finding. Things that work well and are reused unchanged are listed at the end.

### 2.1 Backend

| # | Finding | Handled by |
|---|---|---|
| R1 | **A custom key has no format rule.** Any string up to 100 characters is accepted (`"lead source"`, `"a.b"`, `"Ünï"`). A dot breaks the record form, because react-hook-form reads it as a nested path (`frontend-entity-pages.md` §10). A key over 100 characters fails in Postgres and comes back as the generic 409 from `DataIntegrityViolationException`. `custom-fields.md` §10 names a `ck_cfd_field_key_len`, which doesn't exist | D7 |
| R2 | **A label has no length rule and isn't trimmed.** Over 255 characters gives the same generic 409 | §4.3 |
| R3 | **`POST /api/custom-fields` needs `sortOrder` and `required`.** Both are primitives, and Jackson 3 refuses a missing primitive, so every client has to invent a sort order | D10 |
| R4 | **Create's 400s arrive one at a time.** Option rule errors arrive only after the lock is taken and a slot allocated, from inside the event listener | D8 |
| R5 | **Picklist creation takes an indirect route.** `CustomFieldCreated` carries the options to `picklists.CustomFieldPicklists`. You only see the order (definition, then options) by following the event. `CustomFieldOptionRequest` duplicates `PicklistOptionRequest`, and `roles` gets options it ignores. The event exists only because `customFields` can't call `picklists` without a constructor cycle (`picklists.md` §5.4). A caller above both doesn't have that problem | D4 |
| R6 | **Three identifiers for one field:** the definition's UUID (`DELETE /api/custom-fields/{id}`), `fieldKey` (`/api/picklists`), and `key` (the bundle) | D3 |
| R7 | **Labels and required can't change** (`custom-fields.md` §10, "Deferred"). The same section warns that switching required on must re-run the role grant | D5, D9 |
| R8 | **A required field created after its layouts leaves them short** (`entity-layouts.md` §9). On create, the record form doesn't show the field and the server demands it, so a record can't be created from the UI. `WIP/properties.md` offered "Required" on create without fixing this | D9 |
| R9 | **`PicklistService` has two jobs.** It resolves which fields exist (through `EntityFieldDictionary`) and it stores values. It also depends on `customFields` for the lock. Once a caller above both resolves the field, the service only needs to store | D11 |
| R10 | **A 409 carries no `details`,** so a client can't tell which input caused it. `ConflictException` has no constructor that takes them | D8 |
| R11 | **Stale DDL comment.** `uq_cfd_column` says "the service retries with the next free slot". Allocation has used the advisory lock for some time (`custom-fields.md` §7) | §4.5 |
| R12 | **No admin list.** Nothing lists an entity's fields and says which are custom. The bundle hides that on purpose (`entity-layouts.md` §5.2), and archiving needs an id that nothing else exposes | D3 |
| R13 | `ErrorCode.CUSTOM_FIELD_INVALID` is dead (`custom-fields.md` §9) | Not here |

### 2.2 Frontend

| # | Finding | Handled by |
|---|---|---|
| F1 | No UI for custom fields or picklist options | §5 |
| F2 | `DATA_TYPE_LABELS` lives in the layout editor's `FieldChip.tsx`, but the new pages need it too | Moves to `lib/entity/dataTypeLabels.ts` |
| F3 | The shared list and editor styles (`.badge`, `.row`, `.errorBanner`, `.editorHeader` …) live in `layouts.module.css`, and roles and users import them from there. This feature would be the third importer | Follows the pattern; a move is listed in §6 |
| F4 | Three copies of "back link plus message": `EditorMessage`, `RoleMessage`, and `UserPage`'s `message` | Follows the pattern (`FieldMessage`); merging them is in §6 |
| F5 | The layout and role editors list a 400's details in a banner. That suits them, because their 400s are rare. Here a 400 is a normal outcome (a taken key, a duplicate option label), so details must land under their controls | §5.6 |
| F6 | Writes elsewhere don't invalidate the role queries. A role's matrix rows come from the entity's fields, and a required field changes grants, so this feature's writes must | §5.7 |
| F7 | The settings card description reads "Layouts for company records." | §5.3 |

### 2.3 Kept as they are

- **The editor pattern:** a page decides the starting draft and how Save stores it, and the
  editor owns the reducer, the save and the unsaved-changes guard (`settings.md` §6.1).
- **One read per entity** that serves both the section and the editor, with writes putting
  their result into the cache before they invalidate (`settings.md` §4).
- `SettingsSection`, `ConfirmDialog`, `useUnsavedChangesGuard`, `Select`, `Input`, and
  `lib/api`, which already has `patch`.
- **Pure draft modules in `lib/`,** with no JSX, tested with Vitest.
- **The picklist rules** (`picklists.md` §10.2), code derivation (§10.3), the store, the lookup
  and the bundle.
- **Slot allocation,** the advisory lock, the archive lifecycle, and `CustomFieldDescriptors`.

---

## 3. The flows, in order

**Create** (`POST`), one transaction:

1. **Check the request.** Key, label, type, and the options' presence and rules. Every 400 comes
   back together, before anything is locked or claimed (§4.3).
2. **Insert the definition.** `CustomFieldService.create` takes the entity lock, checks the key
   against its rules as a backstop, refuses a reserved or taken key (409, with a `key` detail),
   claims the lowest free slot, sets `sort_order` to last, and saves. If the field is
   required, it publishes `CustomFieldBecameRequired`: `roles` grants, `entityLayouts` places.
3. **Insert the options.** For a `PICKLIST`, `PicklistService.seed` stores them and derives any
   missing codes.
4. **Return** the field, as the list shows it.

If anything after step 1 throws, the whole transaction rolls back, slot included.

**Edit** (`PATCH`), one transaction:

1. Take the entity lock.
2. Resolve the key to an active field (404 otherwise).
3. Check the request. For a picklist that includes the stored codes, which is why it comes after
   the lock. Every 400 comes back together.
4. Relabel, then switch required (publishing `CustomFieldBecameRequired` on false → true), then
   replace the options.
5. Return the field.

**Archive** (`DELETE`): lock, resolve, refuse a standard field (400), set `archived_at`. Its slot,
values and options stay (`custom-fields.md` §8). There's no UI for it in this build (**O2**).

---

## 4. Backend

### 4.1 New package `entityFields`

The admin view of an entity's fields. Like `entitySchema`, it is composed at the edge: it calls
into `customFields` and `picklists`, and neither of them knows it exists.

| File | Role |
|---|---|
| `EntityFieldService` | `list(type)`, `create(type, request)`, `update(type, key, request)`, `archive(type, key)`. The only entry point for creating or changing a field |
| `EntityFieldRules` | Pure, static: `checkCreate(request)` and `checkUpdate(field, request)` return `List<ApiError.FieldError>`. Unit-tested without Spring |
| `FieldRef` | Package-private record `(key, label, dataType, required, definition)`. `definition` is null for a standard field. `custom()`, `picklist()`, and the factories `standard(FieldDescriptor)` and `custom(CustomFieldDefinition)`. It is what `resolve` returns, so the service never asks "standard or custom?" twice |
| `EntityFieldController` | §4.2 |
| `dto/EntityFieldResponse` | `(key, label, dataType, required, custom, options)`. `options` is set only on a `PICKLIST`, inactive options included. Factories `from(FieldRef, List<PicklistOption>)` |
| `dto/CreateEntityFieldRequest` | `(key, label, dataType, Boolean required, List<PicklistOptionRequest> options)`. `required` defaults to false. No bean-validation annotations: `EntityFieldRules` checks everything, so all errors come back together |
| `dto/UpdateEntityFieldRequest` | `(label, Boolean required, List<PicklistOptionRequest> options)`, each optional. `null` means unchanged |
| `exception/EntityFieldNotFoundException` | 404 `NOT_FOUND`: no active field with that key |

`picklists.dto.PicklistOptionRequest` `(code, label, active)` is used as it is. `entityFields` may
depend on `picklists`, so no mirror DTO is needed.

### 4.2 API

Base path `/api/entity-fields`. `{entityType}` binds as everywhere else (`company`,
case-insensitive). `{key}` is matched case-insensitively and answered in the field's own spelling.
Errors use the platform `ApiError` shape.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/{entityType}` | — | 200 `[EntityFieldResponse]`: standard fields in provider order, then active custom fields in `sort_order, key` | 400 unknown entityType |
| POST | `/{entityType}` | `CreateEntityFieldRequest` | 201 `EntityFieldResponse` | 400 §4.3; 409 reserved or taken key (`key` detail), pool full |
| PATCH | `/{entityType}/{key}` | `UpdateEntityFieldRequest` | 200 `EntityFieldResponse` | 400 §4.3; 404 |
| DELETE | `/{entityType}/{key}` | — | 204 (archives) | 400 a standard field; 404 |

```
GET /api/entity-fields/company
200 → [ { "key": "name", "label": "Name", "dataType": "TEXT", "required": true, "custom": false, "options": null },
        { "key": "industry", "label": "Industry", "dataType": "PICKLIST", "required": false, "custom": false,
          "options": [ { "code": "agriculture", "label": "Agriculture", "active": true }, … ] },
        …,
        { "key": "lead_source", "label": "Lead Source", "dataType": "PICKLIST", "required": false, "custom": true,
          "options": [ … ] } ]

POST /api/entity-fields/company
{ "key": "region", "label": "Region", "dataType": "PICKLIST", "required": true,
  "options": [ { "label": "EMEA" }, { "label": "Americas" } ] }
201 → { "key": "region", "label": "Region", "dataType": "PICKLIST", "required": true, "custom": true,
        "options": [ { "code": "emea", "label": "EMEA", "active": true },
                     { "code": "americas", "label": "Americas", "active": true } ] }

PATCH /api/entity-fields/company/region
{ "label": "Sales region", "required": false,
  "options": [ { "code": "emea", "label": "EMEA", "active": true },
               { "code": "americas", "label": "Americas", "active": false },
               { "label": "APAC" } ] }
200 → the field, as above

PATCH /api/entity-fields/company/industry        (a standard picklist: options only)
{ "options": [ … the whole list … ] }

POST /api/entity-fields/company                   (the key exists, here archived)
{ "key": "region", "label": "Region", "dataType": "TEXT" }
409 → { "code": "CONFLICT", "message": "Custom field 'region' already exists for COMPANY",
        "details": [ { "field": "key", "reason": "Already used, possibly by an archived property" } ] }
```

- `options` in a `PATCH` is the whole list in display order, under the rules of `picklists.md`
  §10.2. It's whole-list replacement inside a `PATCH`, the same as the `PUT` it replaces.
- `key` and `dataType` aren't part of the `PATCH` body. Jackson ignores unknown properties, so
  sending them changes nothing, and the response shows the field unchanged.
- `custom` appears in this response and nowhere else. The schema bundle stays the renderer's
  contract, without it. Definition ids and slot names never leave the server.
- The `GET` lists standard fields even though the UI shows only custom ones. The New property
  page needs every key to check for collisions as the admin types. `PATCH` must keep standard
  picklists editable now that `/api/picklists` is gone. And a later standard-properties section
  needs nothing new from the API.

### 4.3 Rules

**`POST`**, checked by `EntityFieldRules.checkCreate` before the lock. Every failure comes back
in one 400 `VALIDATION_ERROR`:

| Rule | Detail |
|---|---|
| `key` present | `key: Required` |
| `key` at most 50 characters | `key: At most 50 characters` |
| `key` matches `^[a-z][a-z0-9]*(_[a-z0-9]+)*$` | `key: Lower-case letters and digits joined by single underscores, starting with a letter` |
| `key` isn't `new` (it is the create page's URL, §5.1) | `key: Reserved` |
| `label` present and not blank after trimming | `label: Required` |
| `label` at most 255 characters after trimming | `label: At most 255 characters` |
| `dataType` present | `dataType: Required` |
| `dataType` has a slot pool (`REFERENCE` and `COUNTRY` don't) | `dataType: Can't be the type of a custom property` |
| a `PICKLIST` has at least one option | `options: Required` |
| no other type has options | `options: Only a picklist has options` |
| the options follow `picklists.md` §10.2 (`PicklistService.validate(Set.of(), options)`, pure) | `options[i].label`, `options[i].code`, `options` |

`CustomFieldService.create` then applies the rules that need the database, unchanged apart from
the new detail. A key that matches a standard key or `id`/`createdAt`/`updatedAt` ignoring case
is a 409 with `key: Used by a standard property`. A key another custom field holds, **archived
ones included**, is a 409 with `key: Already used, possibly by an archived property`. A full pool
is a 409 with no detail. Its message names the pool, and the UI shows it in the banner.

The key rules live in one place, `customFields.CustomFieldKeys` (`MAX_LENGTH`, `problem(key)`).
`EntityFieldRules` reports through it, and `CustomFieldService.create` calls it again as a
backstop for callers that skip the rules (the seeder, tests). The DB CHECK is the last line:

```sql
-- field_key's collation is nondeterministic, and Postgres refuses regex on one: compare in "C".
constraint ck_cfd_field_key check (field_key collate "C" ~ '^[a-z][a-z0-9]*(_[a-z0-9]+)*$'
                                   and length(field_key) <= 50)
```

Standard keys are camelCase and custom keys snake_case, so the two kinds are easy to tell apart
by eye. They're still compared case-insensitively. Every key in use today (`lead_source`,
`region`, `vat`, `probe`, `existing`) already matches.

**`PATCH`**, checked under the lock once the field is resolved. Every failure comes back in one
400:

| Rule | Result |
|---|---|
| the key names no active field | 404 |
| `label` on a standard field | 400 `label: A standard property's label can't be changed` |
| `label` blank or over 255 after trimming | 400 `label`, as on `POST` |
| `required` on a standard field | 400 `required: A standard property's required setting can't be changed` |
| `options` on a field that isn't a `PICKLIST` | 400 `options: Only a picklist has options` |
| `options` break a rule of `picklists.md` §10.2 | 400 `options[i]…`, as today (`PicklistService.check`, which reads the stored codes) |

An empty body changes nothing and answers 200. So does a body that repeats the current values.

### 4.4 Required fields: one event, two listeners (D9)

`customFields.CustomFieldBecameRequired(entityType, fieldKey, columnName)` replaces
`CustomFieldCreated`. `CustomFieldService` publishes it, synchronously and in the field's
transaction, whenever a field **becomes** required:

- `create` with `required = true`;
- `setRequired(definition, true)` on a field that was optional.

Switching required off publishes nothing: no invariant depends on it.

| Listener | Package | Does | Why |
|---|---|---|---|
| `RequiredFieldGrants` (exists) | `roles` | `grantToRolesWithCreate(entityType, columnName)` | A role with Create must keep Read and Edit on every required field (`roles-and-permissions.md` §3.8). Unchanged, apart from the event type |
| `RequiredFieldPlacement` (new) | `entityLayouts` | `EntityLayoutService.placeOnEveryLayout(entityType, fieldKey)` | Every layout must place every required field (`entity-layouts.md` §5.6). Without this, a new required field can't be filled in on create (R8) |

**Placing.** `EntityLayout.placeIfAbsent(fieldKey)`: if no section of the layout places the key
(ignoring case), it is appended to the **first** section, after its last field. That's where the
demo seeder puts Lead Source and where `blankDraft` puts required fields. The validator isn't
involved, because nothing it checks can break: the key is in the dictionary, and it was placed
nowhere. `EntityLayoutSection.append(key)` adds the placement with `sort_order` = last + 10. The
bundle's ETag changes with its content.

**Existing records.** `RecordMapper` checks required against the merged result. Once a field is
required, an existing record without a value can't be saved until it gets one (the comment on
`RecordMapper.apply` says so). Because the field is now on every layout, the record form shows
it, and the 400 lands under it (`applySaveError`). That's standard CRM behaviour. The editor
warns about it before Save (§5.4).

The lock serialises all of this with role saves: `update` holds `lockEntityFields`, and role
creates and updates take it for every entity type (`RoleService.lockFields`, in enum order; a field
write takes one, so the two can't deadlock). That keeps the guarantee `custom-fields.md` §7 gives for creation: a role
can't miss a field becoming required.

### 4.5 Changes to existing code

| Where | Change |
|---|---|
| **`customFields`** | |
| `CustomFieldController`, `CustomFieldDefinitionResponse`, `CustomFieldOptionRequest`, `exception/CustomFieldNotFoundException` | **Deleted.** `/api/custom-fields` goes away. The 404 is `EntityFieldNotFoundException` now |
| `CustomFieldDefinitionRequest` | `(fieldKey, label, dataType, required)`: `options`, `sortOrder` and the bean-validation annotations go. It's a domain command now, and no controller binds it |
| `CustomFieldCreated` | **Replaced** by `CustomFieldBecameRequired(entityType, fieldKey, columnName)` (§4.4) |
| `CustomFieldKeys` | **New.** `MAX_LENGTH = 50`, the pattern, `RESERVED_WORDS = {"new"}`, `Optional<String> problem(key)` |
| `CustomFieldDefinition` | Gains `relabel(label)` and `setRequired(boolean)`. Key, type and slot stay write-once |
| `CustomFieldDefinitionRepository` | Gains `findByEntityTypeAndFieldKeyAndArchivedAtIsNull(type, key)` (an `Optional`, case-insensitive through the collation) and `findMaxSortOrder(type)` (`coalesce(max(sortOrder), 0)`, archived included) |
| `CustomFieldService` | `create` drops its option checks, backstops `CustomFieldKeys`, sets `sort_order` to the max + 10 (read under the lock), and publishes only when required. New: `findActive(type, key)`, `relabel(definition, label)`, `setRequired(definition, required)`. `archive(UUID)` becomes `archive(definition)`. The three mutators are `propagation = MANDATORY`: the caller holds the transaction and the lock |
| `exception/DuplicateCustomFieldKeyException`, `ReservedFieldKeyException` | Carry a `key` detail (§4.3) |
| **`picklists`** | |
| `PicklistController`, `CustomFieldPicklists`, `dto/PicklistResponse`, `dto/ReplacePicklistRequest`, `exception/PicklistNotFoundException` | **Deleted.** `/api/picklists` goes away |
| `PicklistService` | Injects only the repository and `JdbcClient`. `listFor` goes. `validate` becomes public (still pure). New: `check(type, fieldKey, options)`, which returns the §10.2 errors against the stored codes. `seed` and `replace(type, fieldKey, options)` take the field's own key (the caller has resolved it) and return the stored `List<PicklistOption>`. Both are `MANDATORY` and assume the caller holds the entity lock. `replace` still validates as a backstop |
| `PicklistLookup`, `EntityPicklists`, `PicklistCodes`, initializer, verifier | Unchanged |
| **`entityLayouts`** | |
| `RequiredFieldPlacement` | **New** listener (§4.4) |
| `EntityLayoutService` | New `placeOnEveryLayout(type, key)`, `MANDATORY` |
| `EntityLayout`, `EntityLayoutSection` | `placeIfAbsent(key)` and `append(key)` |
| **elsewhere** | |
| `roles.RequiredFieldGrants` | Listens to `CustomFieldBecameRequired` |
| `platform.web.ConflictException` | Gains `(message, details)` |
| `tenants.DemoTenantSeeder` | Creates Acme's Lead Source through `EntityFieldService.create`, the path the UI takes. It keeps placing it on the default layout itself, because the field is optional |
| `V1__init.sql` | `ck_cfd_field_key` (§4.3). The `uq_cfd_column` comment is fixed (R11): the advisory lock serialises allocation, and the unique is the backstop |

`RecordMapper`, `EntityFieldDictionary`, `CustomFieldDescriptors`, `EntitySchemaService` and the
bundle don't change.

### 4.6 `EntityFieldService`, in outline

```java
@Service
public class EntityFieldService {

    // StandardFieldRegistry, CustomFieldService, PicklistService, PicklistLookup

    @Transactional
    public EntityFieldResponse create(EntityType type, CreateEntityFieldRequest request) {
        List<ApiError.FieldError> errors = EntityFieldRules.checkCreate(request);
        if (!errors.isEmpty()) {
            throw invalid(type, errors);
        }
        CustomFieldDefinition definition = customFields.create(type, new CustomFieldDefinitionRequest(
                request.key(), request.label().strip(), request.dataType(), request.requiredOrDefault()));
        List<PicklistOption> options = definition.getDataType() == FieldDataType.PICKLIST
                ? picklists.seed(type, definition.getFieldKey(), request.options())
                : null;
        return EntityFieldResponse.from(FieldRef.custom(definition), options);
    }

    @Transactional
    public EntityFieldResponse update(EntityType type, String key, UpdateEntityFieldRequest request) {
        customFields.lockEntityFields(type);
        FieldRef field = resolve(type, key);
        List<ApiError.FieldError> errors = new ArrayList<>(EntityFieldRules.checkUpdate(field, request));
        if (request.options() != null && field.picklist()) {
            errors.addAll(picklists.check(type, field.key(), request.options()));
        }
        if (!errors.isEmpty()) {
            throw invalid(type, errors);
        }
        if (request.label() != null) {
            customFields.relabel(field.definition(), request.label().strip());
        }
        if (request.required() != null) {
            customFields.setRequired(field.definition(), request.required());
        }
        List<PicklistOption> options = request.options() != null
                ? picklists.replace(type, field.key(), request.options())
                : optionsOf(type, field);
        return EntityFieldResponse.from(resolve(type, key), options);
    }

    // list: standard fields from the registry, then customFields.listActive, with one
    //       PicklistLookup.forEntity when any is a picklist.
    // archive: lock, resolve, 400 if standard, customFields.archive(definition).
    // resolve: a standard key first (ignoring case), then customFields.findActive; else 404.
}
```

### 4.7 Dependency direction

```
entityFields   ──► entityModel customFields picklists platform      (new; only the demo seeder depends on it)
picklists      ──► entityModel platform tenants                     (the customFields edge is gone)
customFields   ──► entityModel platform                             (unchanged)
entityLayouts  ──► entityModel customFields platform                (new edge: the event it listens to)
roles          ──► entityModel customFields platform …              (unchanged)
```

Beans: `EntityFieldService → StandardFieldRegistry, CustomFieldService, PicklistService,
PicklistLookup`. `PicklistService → repository, JdbcClient`. Nothing points back at
`EntityFieldService`, so there's no cycle. The listeners hang off `ApplicationEventPublisher`,
which no constructor chain reaches. `tenants ⇄ entityFields` (the seeder one way, `picklists`'
`TenantInitializer` the other) is the same package-only loop `tenants ⇄ entityLayouts` already
is.

`EntityFieldDictionary` stays what the bundle and the layout validator read. The admin list
reads the registry and the definitions directly, as `roles.FieldPermissionCatalog` does, because
it needs to know which fields are custom.

### 4.8 Transactions, locking, permission

- `create`, `update` and `archive` are each `@Transactional`, and each takes
  `lockEntityFields(type)` first (`create` through `CustomFieldService.create`). Advisory locks
  taken twice in one transaction are re-entrant. The rule is simple: every write to an entity's
  fields is serialised per tenant and entity, with role saves too. `list` is read-only.
- `PicklistService.seed`, `replace` and `check`, and `CustomFieldService.relabel`, `setRequired`
  and `archive` are `MANDATORY`: they only run inside a caller's transaction.
- `manage_custom_fields` will guard the whole of `/api/entity-fields`, the `GET` included, since
  it's an admin view (`roles-and-permissions.md` §2.5). It isn't enforced yet, like every
  permission. The Roles page can relabel it "Manage properties".

---

## 5. Frontend

Code says field (D1): `features/settings/fields`, `lib/entityFields`. Text on screen says property.

### 5.1 Routes

```
/settings/:entity                          Entity settings: Custom properties, then Layouts
/settings/:entity/properties               → redirects to /settings/:entity
/settings/:entity/properties/new           New property
/settings/:entity/properties/:key          Property page (custom fields only in this build)
```

- `new` is a static segment, so React Router ranks it above `:key`. That's why the key `new` is
  refused (§4.3).
- `:key` is matched case-insensitively against the list, like `:entity`.
- Both pages are lazy routes. `@dnd-kit` (the options editor) stays out of the main bundle, as it
  does for the layout editor.
- Back link on both pages: `← Company settings`.

### 5.2 Shape

```
EntitySettingsPage
  ├─ CustomFieldsSection         the entity's custom fields; "New property"
  └─ LayoutsSection              (unchanged)

NewFieldPage (new)               reads :entity; waits for the field list (taken keys); Save = POST
  └─ FieldEditor
FieldPage (:key)                 reads :entity and :key; finds the field in the list; Save = PATCH
  ├─ PlacementNotice             "Not on any layout yet", from the layout list (optional fields only)
  └─ FieldEditor                 owns the reducer, issues, save, unsaved-changes guard
       ├─ header                 entity icon, title (the draft's label), subtitle, Cancel, Save
       ├─ error banner           a failed save whose errors belong to no control
       ├─ SettingsSection "Details"   Label · API name · Type · Required
       └─ SettingsSection "Options"   OptionsEditor (PICKLIST only)
```

`FieldEditor` takes `entityType`, `initial` (the draft), `isNew`, `takenKeys` (lower-cased, new
only), `notice?`, `save(draft, loaded) → Promise<EntityField>` and `isSaving`, the same props as
`LayoutEditor` and `RoleEditor`. The pages pass `save`:

| Page | Starts from | `save` | After Save |
|---|---|---|---|
| `NewFieldPage` | `blankDraft()` | `create.mutateAsync(toCreateRequest(draft))` | `navigate(fieldPath(type, saved.key), { replace: true })`, so Back can't create twice. `isSaving` stays true from success to the move |
| `FieldPage` | `fromField(field)` | `update.mutateAsync(toUpdateRequest(draft, loaded))` | The draft resets from the response, so the editor is clean again |

`FieldPage` mounts the editor keyed by the field's key, so another field starts from a fresh
draft. Background refetches don't reset the draft.

| State | Shown (`FieldMessage`, as `EditorMessage`) |
|---|---|
| unknown `:entity` | "This settings page doesn't exist." (`← Settings`) |
| list loading | "Loading…" |
| list error | `.page-error` with the message |
| `:key` not an active custom field | "This property doesn't exist (it may have been archived)." |

### 5.3 Custom properties section

`features/settings/fields/CustomFieldsSection.tsx`: a `SettingsSection` with
`storageKey={`${segment}/properties`}`, title "Custom properties", the count, and "New
property" in its header. It sits above Layouts: you create a field, then place it.

```
▾ Custom properties  3                                                [+ New property]
  Properties your organisation adds to companies. Place one on a layout to show it on
  company pages.
  Lead Source           Picklist · 6 options                            lead_source
  Region *              Picklist · 3 options                            region
  Seats                 Number                                          seats
```

| Column | Shows |
|---|---|
| Label | a link to the property page, with the `*` required mark the layout editor uses |
| Type | the data-type badge (`DATA_TYPE_LABELS`); a picklist adds "· N options" (active ones) |
| API name | the key, muted, in monospace |

- Rows are sorted by label (`localeCompare`). The whole row opens the page, as in Layouts.
- Empty: "No custom properties yet." Loading and errors as in Layouts: in the section, without
  blocking the page.
- `EntitySettingsPage` lists it above `LayoutsSection`, also keyed by entity type. Its comment
  lists both sections.
- The overview card's description becomes "Custom properties and layouts for company records."

### 5.4 The field editor

```
← Company settings
 [▣] Region                                                          [Cancel] [Save]
     Custom company property · Picklist
 ┌ Not on any layout yet. Place it on the default layout →                         ┐
 ▾ Details
     Label       [Region                              ]
     API name    region                     Can't be changed
     Type        Picklist                   Can't be changed
     Required    [x] Records can't be saved without a value
 ▾ Options  3
     ⠿  [EMEA                         ]   emea        [x] Active
     ⠿  [Americas                     ]   americas    [ ] Active          (muted)
     ⠿  [APAC                         ]   new         [x] Active     ✕
     [+ Add option]  [Sort A–Z]
```

| Control | New property | Stored property |
|---|---|---|
| Label | `Input`, focused on open, max 255 | `Input` |
| API name | `Input`, monospace, filled by `suggestKey(label)` until edited. Hint: "Used by the API and imports. Can't be changed later." | monospace text · "Can't be changed" |
| Type | `Select` over `CUSTOM_DATA_TYPES`, labelled by `DATA_TYPE_LABELS`, default Text | text · "Can't be changed" |
| Required | checkbox. Hint when on: "It's added to every layout." | checkbox. Hint when switched on: "Existing companies without a value need one the next time they're saved. It's added to every layout." |
| Options | `OptionsEditor`, shown only while the type is Picklist. Choosing Picklist adds one empty row if there are none | `OptionsEditor` on a picklist |

- **Title** is the draft's label, or "New property" while it's empty. The subtitle reads "Custom
  company property · Picklist", plus " · not saved yet" on a new one.
- **Cancel** goes to the entity settings page, through the guard.
- **Save**, from the same rules as the layout editor: on a new property it's enabled before any
  change, and on a stored one only while dirty. It's disabled while there's an issue (the first
  issue is its title) or while saving.
- **Leaving** with unsaved changes goes through `useUnsavedChangesGuard` and `ConfirmDialog`
  ("Discard unsaved changes?"). An untouched new property leaves without asking.
- **`PlacementNotice`** (stored, optional fields only): when no layout in the cached layout list
  places the key, a muted note says "Not on any layout yet. Place it on the default layout to
  show it on company pages." It links to that layout's editor. A required field never needs it,
  because the server places it (§4.4). The note isn't shown while the list loads.

### 5.5 The options editor

`OptionsEditor.tsx`, a controlled component: `{ options, issues, dispatch }`. It holds only its
drag state.

- **One row per option:** drag handle, label `Input` (max 100), API name (the code, muted and
  monospace; "new" on an unsaved row), and an Active checkbox (`aria-label="EMEA active"`).
  Inactive rows are muted but stay editable. Only an unsaved row has a ✕. A saved one is
  deactivated instead, and the Active checkbox's title says so ("Options in use are deactivated,
  not removed").
- **Under the list:** "Add option" appends an empty active row and focuses it. It is disabled at
  500, with that as its title. "Sort A–Z" reorders by label (`Intl.Collator`) and changes nothing
  else.
- **Enter** in a row's label adds a row after it and focuses it, so a list can be typed in one go.
- **Drag-and-drop:** one `DndContext` with one `SortableContext` (`verticalListSortingStrategy`).
  The pointer and keyboard sensors use `sortableKeyboardCoordinates`. The handle is the
  activator, so keys pressed in the inputs never start a drag. A drop is one
  `moveOption(uid, index)`. Announcements name the option: "EMEA moved to position 2 of 3".
  The ids are `option:<uid>`. This is the simple case of the layout editor's §6.4: one list, no
  preview, no custom collision detection.
- **Inactive options stay in the one list.** There's no "show inactive" toggle: lists are short,
  and a muted row says enough.

### 5.6 Validation and errors

Issues are one map from a path to a message: `Map<IssuePath, string>`, where `IssuePath` is
`"label" | "key" | "dataType" | "options" | `option:${uid}``. Client checks and server details use
the same paths, so both appear in the same place.

**Client checks** (`validateDraft`) mirror the server and are never its replacement:

| Path | Rule (as §4.3 and `picklists.md` §10.2) |
|---|---|
| `label` | required after trimming; at most 255 |
| `key` (new only) | required; the pattern; at most 50; not `new`; not in `takenKeys` (the field list's keys plus `id`, `createdAt` and `updatedAt`, ignoring case): "Already used by Industry" or "Reserved" |
| `option:<uid>` | label required; at most 100; unique ignoring case but not accents (`Intl.Collator` with `sensitivity: "accent"`): "Same label as row 2" |
| `options` (picklist only) | at least one option; at least one active; at most 500 |

**Showing them.** An issue shows under its control once that control has been edited. The
editor keeps a set of edited paths, with option rows by uid. A blank new form doesn't open on a
wall of "Required", and Save's title still says why it's disabled.

**Server errors.** A 400's or 409's `details` map onto the same paths through
`issuesFromServer(details, sentDraft)`. `key`, `label` and `dataType` map as they are.
`options[i]`, `options[i].label` and `options[i].code` map to `option:<uid of row i in the draft
as sent>`, because the request sends the rows in order. Everything else goes to the error
banner: `options` itself, a pool-full 409, any other failure. Server issues show at once, and
are cleared by the next change to their control or the next Save.

### 5.7 Data: `lib/entityFields`

No JSX, like `lib/entity` and `lib/layouts`.

| File | Role |
|---|---|
| `types.ts` | `EntityField` (the response), `CreateEntityFieldRequest`, `UpdateEntityFieldRequest`, `OptionRequest` (`{ code?, label, active }`) |
| `entityFields.ts` | `useEntityFieldList(type)` (query key `["entity-fields", type]`), `useCreateEntityField(type)`, `useUpdateEntityField(type, key)` |
| `fieldDraft.ts` | the pure model (§5.8) |
| `fieldDraft.test.ts` | §10.2 |
| `lib/entity/dataTypeLabels.ts` | `DATA_TYPE_LABELS`, moved out of `FieldChip.tsx` (which imports it), and `CUSTOM_DATA_TYPES`: the types with a slot pool, in the order the Type select offers them (Text, Long text, Number, Date, Yes/No, Picklist, Email, Phone, URL). A comment points at `FieldDataType.pool()` |

**Writes** put their result into the list cache: appended on create, replaced by key on update,
so the page reached after creating finds the field without a fetch. Then they invalidate:

| Query | Why |
|---|---|
| `["entity-fields", type]` | the list itself |
| `["entity-schema", type]` | record pages, the layout editor's palette, the role editor's rows. The ETag makes an unchanged refetch a 304 |
| `["entity-layouts", type]` | a field that became required was placed on every layout |
| `["role"]`, `["roles"]` | a role's matrix lists the entity's custom fields, and required changes grants |

### 5.8 The draft: `fieldDraft.ts`

```ts
type OptionDraft = { uid: string; code: string | null; label: string; active: boolean };
type FieldDraft = {
  label: string;
  key: string;
  /** Stops the label from suggesting the key. True from the start on a stored field. */
  keyEdited: boolean;
  dataType: CustomFieldDataType;
  required: boolean;
  /** Kept while another type is picked, so switching back loses nothing; sent only for PICKLIST. */
  options: OptionDraft[];
};
```

| Export | Does |
|---|---|
| `blankDraft()`, `fromField(field)` | the two starting points |
| `fieldDraftReducer(draft, action)` | `setLabel` (also `key = suggestKey(label)` until `keyEdited`), `setKey`, `setDataType` (Picklist with no options adds an empty row), `setRequired`, `addOption(afterUid?)` (the creator makes the uid, so the page can focus it), `setOptionLabel`, `setOptionActive`, `moveOption(uid, index)`, `removeOption(uid)` (refused for a saved option), `sortOptions`, `resetDraft` |
| `suggestKey(label)` | `picklists.md` §10.3 steps 1–4: strip accents, lower-case, turn runs outside `[a-z0-9]` into `_`, trim, cut to 50. If the result doesn't start with a letter, it suggests nothing (`"2nd phone"` → `""`) |
| `validateDraft(draft, { isNew, takenKeys })` | the issue map (§5.6) |
| `isDirty(draft, loaded)` | compares what a request would send: trimmed labels, required, and options as `[code, trimmed label, active]` in order. Plus key and type on a new field. Uids and `keyEdited` don't count |
| `toCreateRequest(draft)` | trims; `options` only for a picklist, each as `{ label, active }` |
| `toUpdateRequest(draft, loaded)` | only what changed: `label`, `required`, and `options` (the whole list) if any option changed |
| `issuesFromServer(details, sent)` | §5.6 |

---

## 6. Considered and left out

| Idea | Why not, or not yet |
|---|---|
| List standard fields in the section; edit standard picklists from the UI | Not asked for yet. The API already serves both (D3), and the editor works on any field, so a "Standard properties" section is UI work only |
| Keep `/api/custom-fields` and `/api/picklists`, and add a list | Three APIs behind one page, joined in the client, with two requests per Save that can half-succeed. Neither has a client today |
| Keep the event that seeds options | It hid the order, and existed only because of the constructor cycle. A caller above both packages does it in three lines |
| Orchestrate in `customFields` or in `picklists` | The first is the cycle (`picklists.md` §5.4). The second makes picklists create TEXT fields |
| Change a field's type | The slot comes from the type's pool and is write-once (`custom-fields.md` §2). A same-pool change (TEXT → URL) is technically possible but changes validation of stored values. Not now |
| Change a key | It's the record's JSON key, the API's and imports' identifier, and what layouts and `picklist_values` reference. Renaming would break integrations silently |
| Required on create only | Switching it on later costs one method once the event exists (§4.4), and switching off costs nothing |
| Place every new field on the default layout | Couples creation to layouts for fields that don't need it. The notice (§5.4) is one click away. Required fields are placed, because there an invariant depends on it |
| A modal for New property | The options editor needs room, and one editor serves both pages |
| Option codes in the UI | Derived, and shown once saved. The API still takes them for imports |
| A preview of the derived code on a new option | A second copy of `PicklistCodes.derive` (with deduplication) in TypeScript, for a value shown once. "new" is enough |
| Unique labels across properties | Two "Region"s confuse, but break nothing. Additive later |
| Help text, a default value, a description | A column on the definition each (or `config`). Nothing needs them yet |
| Reorder properties | Layouts decide where a field appears. The list is an admin view |
| Move the shared settings styles out of `layouts.module.css` (F3); merge the three message components (F4) | Worth doing, but they touch layouts, roles and users. A follow-up cleanup, not part of this feature |

---

## 7. Known limits

| Limit | Why it's acceptable now | What changes it |
|---|---|---|
| An archived field's key stays taken, and the list doesn't show why | The 409's `key` detail says an archived property may hold it | Listing archived fields, with purge (`custom-fields.md` §8) |
| Pools are finite: 40 text slots shared by Text, URL, Phone, Email and Picklist; 25 number; 25 Yes/No; 10 date; 5 long text | A real 409 with a message. Widening is a migration (`custom-fields.md` §12) | A `cf_pick_*` pool if picklists crowd out text (`picklists.md` §13) |
| A new optional field shows on no record page until it's placed | The notice says so and links to the layout | — |
| A new optional field is granted to no role. Only a required one reaches roles with Create | Roles aren't enforced yet | When enforcement lands, decide whether new fields default to the roles that can read the entity |
| Making a field required blocks saving existing records that lack a value | It's what required means. The field is on every layout and the editor warns | A "required on new records only" mode, if asked for |
| Standard picklists are editable through the API only | Nobody has asked for the UI yet | A standard-properties section (§6) |
| Last save wins | As in the layout and role editors | Optimistic locking, everywhere at once |
| A key could collide with a standard field added in a later release | Pre-production; see **O1** | O1 |

---

## 8. Open decisions

| # | Question | Options | Recommendation |
|---|---|---|---|
| O1 | **Key namespace.** Custom and standard keys share one flat keyspace, and the reserved-key check only sees today's standard fields. If a release adds a standard `region` to Company, every tenant with a custom `region` breaks: `RecordMapper`, layouts and `picklist_values` all address fields by key. `WIP/custom-objects-architecture.md` §5 raises the same point and picked `__c` for object keys | **(a)** Keep plain snake_case. Before adding a standard field, a release checks every tenant's custom keys and avoids the taken ones. **(b)** Custom keys end in `__c` (`region__c`): standard keys never contain `__`, so a collision can't happen. The UI shows `__c` as a fixed suffix after the input | **(b)**, decided before the build. This spec is written for (a). Switching to (b) changes the pattern in `CustomFieldKeys`, the CHECK, a suffix in `suggestKey`, the seeded `lead_source` and the examples. Once real tenants exist, (b) means renaming their keys |
| O2 | **Archive in the UI.** The API keeps `DELETE`. The question is whether the property page gets an Archive button now | **(a)** Not in this build, as asked: add and edit. **(b)** An Archive button in the header, through `ConfirmDialog` ("It disappears from forms, layouts and lists. Stored values are kept, and its API name stays taken."). It returns to the entity settings page | **(a)** for scope. (b) is about 30 lines and a hook, if it's wanted |

---

## 9. Build plan

Each phase ends green (`./mvnw test`, or `npm run build` and `npm test`), so each can be
committed on its own. V1 is edited in place, then `make db-reset`.

| # | Phase | Steps | Done when |
|---|---|---|---|
| 1 | Backend: the API | `ConflictException` details; `CustomFieldKeys`; the `customFields` and `picklists` changes of §4.5 (with `CustomFieldBecameRequired` published on create only, so `roles` behaves as before); the `entityFields` package; `ck_cfd_field_key` and the comment fix in V1; the seeder through `EntityFieldService`; tests moved (§10.1) | `EntityFieldApiTest` and `EntityFieldRulesTest` pass, and nothing outside `entityFields` serves HTTP for fields |
| 2 | Backend: required | `setRequired` and the `required` part of `PATCH`; `RequiredFieldPlacement`, `placeOnEveryLayout`, `placeIfAbsent`, `append` | `RequiredFieldPlacementTest` passes, plus the required cases in `EntityFieldApiTest` |
| 3 | Frontend: data | `lib/entityFields`; `dataTypeLabels.ts` (with `FieldChip` importing it) | `fieldDraft.test.ts` passes |
| 4 | Frontend: UI | routes; `fields/paths.ts`; `CustomFieldsSection`; `FieldEditor`, `NewFieldPage`, `FieldPage`, `PlacementNotice`, `OptionsEditor`; `fields.module.css`; the overview card text | `npm run build` and `npm test` pass |
| 5 | Verify | full suites, then §10.3 by hand in the browser | — |
| 6 | Docs | §11, then this file becomes `Architecture/custom-properties.md`, the as-built reference, and `WIP/properties.md` is deleted | — |

---

## 10. Tests

### 10.1 Backend

| Test | Covers |
|---|---|
| `EntityFieldRulesTest` (pure) | Every row of the §4.3 tables: the key pattern, its length and `new`; blank and long labels, trimmed; a type without a pool; options missing on a picklist and present on another type; option rule paths come out as `options[i]…`; several problems in one list. `checkUpdate`: label or required on a standard field, options on a non-picklist |
| `EntityFieldApiTest` (real Postgres, MockMvc, as `PicklistApiTest`) | **List:** standard then custom, `custom`, options on picklists only, inactive included. **Create:** TEXT in the first text slot and `sort_order` last; PICKLIST with codes derived and stored; every 400 in one response with nothing claimed (the next field still gets `cf_text_01`); reserved, taken and archived keys are a 409 with a `key` detail; a refused option rolls the field back. **Patch:** relabel; label or required on a standard field is a 400; a standard picklist's options; options on a non-picklist is a 400; label and option errors in one 400; a case-insensitive key in the path; 404 for an unknown or archived key; an empty body is a 200. **Required:** created required, then granted to roles with Create and placed in the first section of every layout; switched on later, same; an existing company without a value is refused until it gets one; switched off, nothing else changes. **Delete:** a custom field is archived and gone from the list and the bundle; a standard one is a 400. **The bundle's ETag** changes after each write |
| `RequiredFieldPlacementTest` | `placeIfAbsent`: appended to the first section after its last field; nothing happens when any section already places the key, ignoring case; every layout of the entity gets it, other entities' don't |
| `PicklistApiTest` | Its §10.2 cases stay, sent as `PATCH /api/entity-fields/company/{key}`. The `GET` cases move to the list |
| `CustomFieldPicklistTest` | **Deleted**, folded into `EntityFieldApiTest` |
| `RequiredFieldGrantsTest` | The new event |
| `CustomFieldSlotAllocationTest`, `RequiredFieldRuleIntegrationTest`, `RoleServiceIntegrationTest`, `TenantIsolationTest` | The four-argument `CustomFieldDefinitionRequest`. `TenantIsolationTest` reads picklists through `EntityFieldService.list` |
| `PicklistServiceTest` (or folded into `PicklistApiTest`) | `check` reports a missing stored code; `replace` still refuses one as a backstop |

### 10.2 Frontend

| Test | Covers |
|---|---|
| `fieldDraft.test.ts` | `suggestKey` (accents, symbols, a leading digit gives nothing, the 50-character cut with no trailing `_`); the reducer (the label drives the key until it's edited, Picklist adds a row, only unsaved rows can be removed, `addOption` after a row, move, sort A–Z); `validateDraft` (every row of §5.6, the taken key ignoring case, duplicate labels ignoring case but not accents); `isDirty` ignores uids and whitespace; `toUpdateRequest` sends only what changed; `issuesFromServer` maps `options[1].label` to the second row's uid and sends `options` and unknown paths to the banner |
| `features/settings/fields/paths.test.ts` | `fieldPath`, `newFieldPath` |

### 10.3 By hand

1. As alice, Company settings shows Custom properties above Layouts, with Lead Source
   ("Picklist · 6 options", `lead_source`). Contact and Deal show "No custom properties yet."
2. New property: type "Région commerciale". The API name reads `region_commerciale`. Change it
   to `region`, pick Picklist, add EMEA, Americas and APAC (Enter between them), drag APAC
   first, Save. It opens Region's page, with codes `apac`, `emea` and `americas`, and the
   "Not on any layout yet" notice. The link opens the default layout, and the palette has
   Region. Place it and save: the company page has the dropdown.
3. On Region's page: rename it "Sales region", deactivate Americas, Save. The record page's label
   follows, and its select offers no Americas. A company holding `americas` still shows
   "Americas (inactive)" and still saves.
4. Switch Required on (the hint appears) and Save. Every Company layout now places Region (in
   the editor). Editing an existing company without a region and saving shows "Required" under
   Region. A role with Create on companies (make one in Roles first) has Region's Read and Edit
   switched on.
5. Errors: key `new`, key `1st`, and key `industry` are each refused as typed; a duplicate option
   label and an empty one show under their rows; Save stays disabled and says why. Through the
   API, archive Region, then create `region` again: the 409 shows under API name.
6. Leaving with changes asks first. An untouched new property leaves without asking. Back after
   creating goes to Company settings, not to `/new`.
7. bob's Company settings has no Region and no Lead Source.

---

## 11. Docs and comments to update

| Where | Change |
|---|---|
| `Architecture/custom-fields.md` | §2: `ck_cfd_field_key`, and the key rules. §3: controller, response, option request and not-found exception gone; the request's new shape; `CustomFieldKeys`; `relabel`, `setRequired`, `archive(definition)`; the event. §4: the graph gains `entityFields`, and `picklists` loses `customFields`. §5: the HTTP API moves to `/api/entity-fields` (`custom-properties.md`). §7: step 5 is the new event, published when required; `sort_order` is assigned last. §10: labels and required are editable now; remove the deferred line, the `@Size` gap and the note about `CustomFieldCreated` |
| `Architecture/picklists.md` | D9 and §9.1: creation goes through `EntityFieldService`. §5.2: the controller, listener and DTOs are gone, and `PicklistService` is a store. §5.4: the graph and the two rules (the second one no longer applies). §10.1: `PATCH /api/entity-fields/{type}/{key}` and the list. §10.5: the caller takes the lock. §12.2: built, see `custom-properties.md` |
| `Architecture/entity-layouts.md` | §3 (`entityLayouts`): `RequiredFieldPlacement`. §9: the "required field on no layout" limitation is gone |
| `Architecture/roles-and-permissions.md` | §2.5: `manage_custom_fields` guards `/api/entity-fields`. §3.8 and §6.4: the event is `CustomFieldBecameRequired`, also published when a field is switched to required |
| `Architecture/settings.md` | §2: the property routes. §5: the Custom properties section, above Layouts. §7: the new files. §9: the new tests |
| `Architecture/frontend-entity-pages.md` | `dataTypeLabels.ts` in `lib/entity`. §10: custom keys can no longer contain a dot |
| `EntitySettingsPage.tsx`, `entityModel/package-info.java`, `CustomFieldService` class comment | the sections the page lists; `picklists` no longer depends on `customFields`; the event |
