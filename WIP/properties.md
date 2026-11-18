# Properties — Spec

**Status:** Proposal, nothing implemented · **Scope:** a Properties section on each entity's settings page: list an entity's fields, create a custom field (with its picklist options), edit a field (a custom field's label, any picklist's options), archive a custom field. One admin API behind it
**Date:** 2026-11-08 · **Builds on (as-built):** `Architecture/custom-fields.md`, `Architecture/picklists.md`, `Architecture/settings.md`, `Architecture/roles-and-permissions.md`
**Code:** new package `entityFields`; `customFields`, `picklists`, `tenants.DemoTenantSeeder`; `Frontend/src/features/settings/fields`, `Frontend/src/lib/entityFields`

> Each entity's settings page gets a **Properties** section next to Layouts. It lists every
> field of the entity, standard and custom, with a "New property" button, and each row opens
> that field's page. There an admin edits what is editable: a custom field's label, and the
> options of any picklist, standard or custom.
>
> Behind it sits one admin API, `/api/entity-fields/{entityType}`, in a new thin package
> `entityFields`. Creating a custom field is one method, in the order you'd expect: check the
> request, insert the definition (which claims its slot), insert the picklist options. All of
> it happens in one transaction. The event that seeds options today goes away, and so do
> `/api/custom-fields` and `/api/picklists`, which no client uses yet. `customFields` and
> `picklists` become services with no HTTP of their own.

**Terms.** "Property" is the UI's word for a field. Code, API, docs and the database keep saying
**field**. "Property" appears only in text the user reads and in the URL segment `properties`.
A picklist's entries are **options** on the wire and in the UI, as in `picklists.md`.

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | "Property" is display text and a URL segment only. Everything else says "field". | Terms |
| D2 | One admin API for an entity's fields: `GET`, `POST`, `PATCH`, `DELETE` on `/api/entity-fields/{entityType}[/{fieldKey}]`, in a new package `entityFields`. It replaces `/api/custom-fields` and `/api/picklists`. | §4.2 |
| D3 | Creating a field is one transaction in one method: `EntityFieldService.create` validates, calls `CustomFieldService.create`, then `PicklistService.seed`. The `CustomFieldPicklists` listener and the options on `CustomFieldCreated` go away. | §3, §4.4 |
| D4 | Editing is one `PATCH` per field carrying `label` (custom fields only) and/or `options` (any picklist). The picklist rules are unchanged (`picklists.md` §10.2). | §4.2 |
| D5 | A standard field's label is code-owned and read-only. Its picklist options are editable. | §6 |
| D6 | A custom field is archived, never deleted, and the UI says "Archive". Standard fields can't be archived. Archived fields aren't listed. | §4.3, §5.4 |
| D7 | A custom field's key is chosen once, at creation. The UI suggests it from the label, the admin can change it until Save, and then it's fixed. Pattern `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`, at most 50 characters, and not `new`. | §4.3 |
| D8 | Type and required are chosen at creation and fixed after it. Only types with a slot pool are offered. | §4.3, §6 |
| D9 | The server puts a new custom field last (`sort_order`). Properties aren't reordered: placing fields is what layouts are for. | §4.4 |
| D10 | The UI never asks for an option code. The server derives it (`picklists.md` §10.3), and the UI shows it read-only as "API name" once saved. | §5.5 |
| D11 | Every write is gated by `manage_custom_fields` once enforcement lands. No new permission. | §4.6 |

---

## 2. Where we are

- **No UI.** Custom fields and picklist values can only be changed through the API.
- **Two APIs for one job.** `POST /api/custom-fields/{entityType}` creates a field and
  `DELETE /api/custom-fields/{id}` archives one; `PUT /api/picklists/{entityType}/{fieldKey}`
  edits options. Nothing lists an entity's fields for an admin: the schema bundle has them,
  but it deliberately hides which are custom (`entity-layouts.md` §5.2), and archiving needs a
  definition id the bundle doesn't carry.
- **Creating a picklist field takes an indirect route.** `CustomFieldService.create` publishes
  `CustomFieldCreated` with the options, and `picklists.CustomFieldPicklists` hears it and stores
  them, in the same transaction. The order is right, but you only see it by following the event.
  The event was there because `customFields` can't call `picklists` without a constructor cycle
  (`picklists.md` §5.4). A caller above both doesn't have that problem.
- **Labels can't be changed,** not even a custom field's (`custom-fields.md` §10, "Deferred").

---

## 3. The flow, in order

**Create** (`POST`), one transaction:

1. **Check the request.** Key, label, type, and the options' presence and rules. Every 400 comes
   back together, before anything is locked or claimed (§4.3).
2. **Insert the definition.** `CustomFieldService.create` takes the entity lock, refuses a
   reserved or taken key (409), claims the lowest free slot of the type's pool, and saves the row.
   `CustomFieldCreated` still goes to `roles` for a required field's grants.
3. **Insert the options.** For a `PICKLIST`, `PicklistService.seed` stores them in
   `app.picklist_values`, deriving any missing codes.
4. **Return** the new field, as it will be listed.

If step 3 refuses anything (it checks the rules again as a backstop), the whole transaction
rolls back, and the slot is free again.

**Edit** (`PATCH`), one transaction: take the entity lock, resolve the field (404 if it's not an
active field), relabel the definition if `label` was sent, and replace the options if `options`
was sent.

**Archive** (`DELETE`): resolve the field. A standard one is a 400; a custom one gets
`archived_at`. Its slot, values and picklist options stay (`custom-fields.md` §8).

---

## 4. Backend

### 4.1 New package `entityFields`

The admin view of an entity's fields, composed at the edge like `entitySchema`: it calls into
`customFields` and `picklists`, and neither knows it exists.

| File | Role |
|---|---|
| `EntityFieldService` | `list(type)`, `create(type, request)`, `update(type, key, request)`, `archive(type, key)`. The only entry point for creating or changing a field |
| `EntityFieldController` | §4.2 |
| `dto/EntityFieldResponse` | `(key, label, dataType, required, custom, options)`; `options` only on a `PICKLIST`, inactive ones included |
| `dto/CreateEntityFieldRequest` | `(key, label, dataType, required, options)`; `required` is a `Boolean`, default false |
| `dto/UpdateEntityFieldRequest` | `(label, options)`, both optional |
| `exception/EntityFieldNotFoundException` | 404 `NOT_FOUND`: no active field with that key |

The option items are `picklists.dto.PicklistOptionRequest`, used directly. `entityFields` may
depend on `picklists`, so no mirror DTO is needed.

### 4.2 API

Base path `/api/entity-fields`. `{entityType}` binds as everywhere (`company`, case-insensitive);
`{fieldKey}` is matched case-insensitively and answered with the field's own spelling.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/{entityType}` | — | 200 `[EntityFieldResponse]`: standard fields in provider order, then active custom fields in `sort_order` | 400 unknown entityType |
| POST | `/{entityType}` | `CreateEntityFieldRequest` | 201 `EntityFieldResponse` | 400 §4.3; 409 reserved or taken key, pool full |
| PATCH | `/{entityType}/{fieldKey}` | `UpdateEntityFieldRequest` | 200 `EntityFieldResponse` | 400 §4.3; 404 |
| DELETE | `/{entityType}/{fieldKey}` | — | 204 (archives) | 400 a standard field; 404 |

```
GET /api/entity-fields/company
200 → [ { "key": "name", "label": "Name", "dataType": "TEXT", "required": true, "custom": false, "options": null },
        { "key": "industry", "label": "Industry", "dataType": "PICKLIST", "required": false, "custom": false,
          "options": [ { "code": "agriculture", "label": "Agriculture", "active": true }, … ] },
        …,
        { "key": "lead_source", "label": "Lead Source", "dataType": "PICKLIST", "required": false, "custom": true,
          "options": [ … ] } ]

POST /api/entity-fields/company
{ "key": "region", "label": "Region", "dataType": "PICKLIST",
  "options": [ { "label": "EMEA" }, { "label": "Americas" } ] }
201 → { "key": "region", "label": "Region", "dataType": "PICKLIST", "required": false, "custom": true,
        "options": [ { "code": "emea", "label": "EMEA", "active": true },
                     { "code": "americas", "label": "Americas", "active": true } ] }

PATCH /api/entity-fields/company/region
{ "label": "Sales region",
  "options": [ { "code": "emea", "label": "EMEA", "active": true },
               { "code": "americas", "label": "Americas", "active": false },
               { "label": "APAC" } ] }
200 → the field, as above

PATCH /api/entity-fields/company/industry        (a standard picklist: options only)
{ "options": [ … the whole list … ] }
```

`custom` is in this response and nowhere else. The schema bundle stays the renderer's contract,
without it. The key is the identifier, as everywhere in the API. Definition ids and slot names
never leave the server.

### 4.3 Rules

**`POST`.** All of step 1 is checked before the lock, and every failure comes back in one 400:

| Rule | 400 detail |
|---|---|
| `key` present, matches `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`, at most 50 characters | `key` |
| `key` isn't `new` (it's the create page's URL, §5.1) | `key` |
| `label` present, not blank after trimming, at most 255 characters | `label` |
| `dataType` present and has a slot pool (`REFERENCE` and `COUNTRY` don't) | `dataType` |
| `options` present with at least one item if `PICKLIST`, absent or empty otherwise | `options` |
| the options follow `picklists.md` §10.2 (`PicklistService.validate`, pure) | `options[i].label`, `options[i].code`, `options` |

Then step 2's rules, unchanged: a key that matches a standard key or `id`/`createdAt`/`updatedAt`
(case-insensitively), or that another custom field already has, **archived ones included**, is a
409. So is a full pool.

The key pattern is new. It keeps keys usable as JSON keys and form names (no dots,
`frontend-entity-pages.md` §10), and it closes the old "no size check on `fieldKey`" gap
(`custom-fields.md` §10). Standard keys are camelCase and custom keys snake_case, so the two kinds
can't be confused by eye. They're still compared case-insensitively.

**`PATCH`.**

| Rule | Result |
|---|---|
| the key names no active field | 404 |
| `label` on a standard field | 400 `label`: a standard field's label can't be changed |
| `label` blank or over 255 characters | 400 `label` |
| `options` on a field that isn't a `PICKLIST` | 400 `options` |
| `options` breaks a rule of `picklists.md` §10.2 | 400 `options[i]…`, as today |

An empty body changes nothing and answers 200.

### 4.4 What changes in `customFields` and `picklists`

Both get smaller: their HTTP layer moves to `entityFields`, and so does the creation order.

| Where | Change |
|---|---|
| `customFields.CustomFieldController`, `CustomFieldDefinitionResponse` | **Deleted.** `/api/custom-fields` goes away |
| `customFields.CustomFieldOptionRequest` | **Deleted.** Options never reach `customFields` |
| `customFields.CustomFieldDefinitionRequest` | `(fieldKey, label, dataType, required)`. `options` and `sortOrder` go, and so do the bean-validation annotations (no controller validates it now) |
| `customFields.CustomFieldCreated` | Back to `(entityType, columnName, required)`: only `roles` listens |
| `customFields.CustomFieldService` | `create` no longer checks options, and assigns `sort_order` = the entity's highest + 10, read under the lock. New: `relabel(type, key, label)`. `archive(UUID)` becomes `archive(type, key)` |
| `customFields.CustomFieldDefinition` | Gains `relabel(label)` |
| `customFields.CustomFieldDefinitionRepository` | Gains `findByEntityTypeAndFieldKey` (an `Optional`, archived included) and `findMaxSortOrder(type)` |
| `picklists.PicklistController`, `CustomFieldPicklists`, `dto/PicklistResponse`, `dto/ReplacePicklistRequest`, `exception/PicklistNotFoundException` | **Deleted.** `/api/picklists` goes away |
| `picklists.PicklistService` | `listFor` goes. `replace(type, fieldKey, options)` takes the field's own key (the caller has resolved it) and returns the stored `List<PicklistOption>`. It no longer needs `EntityFieldDictionary`, only the lock, the repository and `JdbcClient`. `seed` and `validate` stay as they are |
| `tenants.DemoTenantSeeder` | Creates Acme's Lead Source through `EntityFieldService.create`, the path the UI takes |

`PicklistLookup`, `EntityPicklists`, the initializer, the verifier and the write check in
`RecordMapper` don't change.

### 4.5 Dependency direction

```
entityFields  ──► entityModel customFields picklists platform   (new; nothing depends on it but the demo seeder)
picklists     ──► entityModel customFields platform tenants      (unchanged, minus EntityFieldDictionary)
customFields  ──► entityModel platform                           (unchanged)
```

Beans: `EntityFieldService → StandardFieldRegistry, CustomFieldService, CustomFieldDescriptors,
PicklistService, PicklistLookup`. Nothing points back at it, so there's no cycle. `tenants ⇄
entityFields` (the demo seeder one way, `TenantInitializer` through `picklists` the other) is the
same package-only loop `tenants ⇄ entityLayouts` already has.

### 4.6 Transactions, locking, permission

- `create`, `update` and `archive` are each `@Transactional`. `CustomFieldService.create` and
  `PicklistService.seed` join the transaction; `seed` stays `MANDATORY`.
- `update` takes `CustomFieldService.lockEntityFields` before it resolves the field, as `replace`
  does today. `create` gets the lock from `CustomFieldService.create`. Advisory locks taken twice
  in one transaction are re-entrant, so `replace` keeps its own call and stays safe on its own.
- `manage_custom_fields` will guard every write on `/api/entity-fields`, and the `GET` too: it's
  an admin view (`roles-and-permissions.md` §2.5). Not enforced yet, like every permission.

---

## 5. Frontend

Code says field (D1): `features/settings/fields`, `lib/entityFields`. Text on screen says property.

### 5.1 Routes

```
/settings/:entity                         Entity settings: Properties, then Layouts
/settings/:entity/properties              → redirects to /settings/:entity
/settings/:entity/properties/new          New property
/settings/:entity/properties/:key         Property page
```

`new` is a static segment, so React Router ranks it above `:key`. That's why the key `new` is
refused (§4.3). Back links: `← Company settings` on both pages. Both are lazy routes, so
`@dnd-kit` stays out of the main bundle, as it does for the layout editor.

### 5.2 Properties section

`features/settings/fields/FieldsSection.tsx`: a `SettingsSection` titled "Properties" with
`storageKey={`${segment}/properties`}`, the count, and a "New property" button in its header. It
comes first on the page, above Layouts: fields come first, layouts arrange them.

| Column | Shows |
|---|---|
| Label | a link to the property page |
| Type | the data type badge ("Picklist", "Text", …); for a picklist also "· N options" |
| Kind | "Standard" or "Custom" (muted) |
| API name | the key, muted, monospace |

Rows are in the API's order: standard, then custom. The whole row opens the property page, as in
the Layouts section. A required field gets the `*` mark the layout editor uses.

### 5.3 New property page

`NewFieldPage.tsx`, on a draft that lives only in the page until Save, like a new layout:

| Control | Behaviour |
|---|---|
| Label | text input, focused on open |
| API name | text input, filled from the label (`suggestKey`, §5.6) until the admin edits it. Hint: "Used by the API and imports. Can't be changed later." Checked as typed against the pattern and the keys in the loaded list |
| Type | `Select` over the types with a pool, labelled as in the layout editor |
| Required | checkbox. Hint: "Records can't be saved without a value. Can't be changed later." |
| Options | the options editor (§5.5), shown only for Picklist, starting with one empty row |

Save posts once, then opens the new property's page with `replace`, so Back doesn't return to
`/new`. A 400's details land under their controls (`key`, `label`, `options[i].label` on row i);
a 409 shows in the page's error banner ("API name already used. It may belong to an archived
property."). `useUnsavedChangesGuard` covers leaving with a filled-in form.

### 5.4 Property page

`FieldPage.tsx`, one page for every field. It finds the field in the cached list (no get-by-id,
as with layouts):

- **Details:** Label (an input on a custom field; plain text on a standard one, with "Standard
  properties keep their label"), API name, Type, Required, Kind, all read-only except the label.
- **Options**, on a picklist only: the options editor (§5.5).
- **Save** sends one `PATCH` with whatever changed (`label`, `options`), then resets the draft
  from the response. Save is disabled while nothing changed. Errors land as in §5.3. Same guard.
- **Archive** (custom fields only), in the header, through `ConfirmDialog`: "Archive property
  ‘Region’? It disappears from forms, layouts and lists. Stored values are kept, and its API
  name stays taken." After archiving it goes back to the entity settings page.
- A custom field that's on no layout yet gets a note under the header: "Not on any layout yet.
  Place it in Layouts to show it on company pages." It links to the Layouts section.

A standard field that isn't a picklist has nothing to edit, and its page says so.

### 5.5 The options editor

`OptionsEditor.tsx`, a controlled component over the draft's option list, shared by both pages:

- **A row per option:** drag handle (`@dnd-kit/sortable`, pointer and keyboard), label input, API
  name (the code, muted, read-only; "new" on an unsaved row), and an Active checkbox. Inactive rows
  are muted. A row that isn't saved yet has a remove button. A saved one doesn't, because values
  are deactivated, never removed (`picklists.md` D6).
- **Under the list:** "Add option" (appends an empty row and focuses it) and "Sort A–Z" (only
  reorders).
- **Checks as you type,** mirroring `picklists.md` §10.2: a label is required, at most 100
  characters, and unique ignoring case but not accents (`localeCompare` with
  `sensitivity: "accent"`). At least one option must be active, and there can be at most 500. The
  server stays the authority, and its `options[i]` details map back to row i, because the request
  sends the rows in order.

One list, with no "show inactive" toggle: lists are short, and a muted row says enough.

### 5.6 Data and draft: `lib/entityFields`

No JSX, as in `lib/entity` and `lib/layouts`.

| File | Role |
|---|---|
| `types.ts` | `EntityField` (the response), `CreateEntityFieldRequest`, `UpdateEntityFieldRequest` |
| `entityFields.ts` | `useEntityFieldList(type)` (query key `["entity-fields", type]`), `useCreateEntityField`, `useUpdateEntityField`, `useArchiveEntityField`. Each write puts its result into the list cache, then invalidates the list and `["entity-schema", type]`, so record pages and the layout editor's palette pick the change up (a 304 when nothing changed) |
| `fieldDraft.ts` | pure: the draft (`label`, `key`, `dataType`, `required`, `options` with a local row id), `suggestKey(label)`, the reducer (rename, edit key, pick type, add / relabel / toggle / move / remove-new option, sort A–Z), `validate(draft, takenKeys)`, `toCreateRequest`, `toUpdateRequest` (only what changed) |
| `lib/entity/dataTypeLabels.ts` | `DATA_TYPE_LABELS`, moved out of the layout editor's `FieldChip.tsx` so both use it, and `CUSTOM_DATA_TYPES`, the types offered on the New property page |

`suggestKey` follows `picklists.md` §10.3, steps 1–4: strip accents, lower-case, runs outside
`[a-z0-9]` become `_`, trim, cut to 50. If the result doesn't start with a letter it suggests
nothing, and the admin types a key.

---

## 6. Considered and left out

| Idea | Why not, or not yet |
|---|---|
| Keep `/api/custom-fields` and `/api/picklists` and add a list endpoint | Three APIs behind one page, joined in the client. Neither has a client today, so they can simply go |
| Keep the event that seeds options | It hid the order and existed only because `customFields` can't call `picklists`. A caller above both says it in three lines |
| Orchestrate in `customFields` or in `picklists` | The first is the constructor cycle (`picklists.md` §5.4). In the second, picklists would create TEXT fields |
| Editing a standard field's label | The label lives in the provider, a constant. Renaming per tenant needs an override table that every dictionary read consults: additive (`app.field_labels (tenant, entity_type, field_key, label)`), but it touches the bundle, layouts and roles. Do it when it's asked for |
| Changing required after creation | Turning it on blocks saving every record without a value, and needs the role grant `CustomFieldCreated` gives (`custom-fields.md` §10). Additive |
| Changing the type | The slot comes from the type's pool: write-once (`custom-fields.md` §2) |
| Reordering properties | Layouts already decide where a field appears. The list order is only an admin view |
| Listing archived fields, unarchiving | No restore exists, and it belongs with purge (`custom-fields.md` §8) |
| A modal for New property | The options editor needs room, and the page reuses the property page's editor |
| A server-derived field key | The key is the API's and imports' identifier, so the admin should see and confirm it. The UI suggests one |
| Option codes in the UI | Derived, and shown once saved. The API still takes them for imports |
| A description or help text per field | A column on the definition later; nothing needs it yet |

---

## 7. Known limits

| Limit | Why it's acceptable now | What changes it |
|---|---|---|
| An archived field's key stays taken, and the list doesn't show why | The 409 message says an archived property may hold it | Listing archived fields, with purge |
| A new custom field shows on no record page until it's placed on a layout | Placing is the Layouts section's job; the property page says so | An "Add to default layout" action, if wanted |
| A new optional custom field is granted to no role. Only a required one reaches roles with Create (`roles-and-permissions.md` §3.8) | Roles aren't enforced yet | When enforcement lands, decide whether new fields default to the roles that can read the entity |
| Last save wins | As in the layout and role editors | Optimistic locking, everywhere at once |

---

## 8. Build plan

Each phase ends green (`./mvnw test`, or `npm run build` and `npm test`), so each can be committed alone.

| # | Phase | Steps | Done when |
|---|---|---|---|
| 1 | Backend | `entityFields` package (§4.1–4.3); the `customFields` and `picklists` changes (§4.4); the demo seeder through `EntityFieldService`; tests moved (§9) | `EntityFieldApiTest` passes, and nothing outside `entityFields` serves HTTP for fields |
| 2 | Frontend data | `lib/entityFields`, `dataTypeLabels.ts` (`FieldChip` imports it) | `fieldDraft.test.ts` passes |
| 3 | Frontend UI | routes, `paths.ts`, `FieldsSection`, `NewFieldPage`, `FieldPage`, `OptionsEditor` | `npm run build` and `npm test` pass |
| 4 | Verify | full suites, then §9.3 by hand in the browser | — |
| 5 | Docs | §10, then turn this file into `Architecture/properties.md` as the as-built reference | — |

---

## 9. Tests

### 9.1 Backend

| Test | Covers |
|---|---|
| `EntityFieldApiTest` (real Postgres, MockMvc) | The list: standard then custom, `custom`, options on picklists only. Create: `TEXT` in a text slot; `PICKLIST` with options, codes derived and stored; every 400 of §4.3 in one response with nothing claimed (the next field still gets `cf_text_01`); reserved, taken and archived keys are a 409; a bad option rolls back the field. `PATCH`: a custom label; a label on a standard field is a 400; a standard picklist's options; options on a non-picklist are a 400; a case-insensitive key in the path; 404 for an unknown or archived key. `DELETE`: a custom field is archived and gone from the list and the bundle, a standard one is a 400. The bundle's ETag changes after each write |
| `PicklistApiTest` | Its §10.2 cases stay, sent through `PATCH /api/entity-fields/company/{key}` instead of the old `PUT` |
| `CustomFieldPicklistTest` | **Deleted**: folded into `EntityFieldApiTest` |
| `CustomFieldSlotAllocationTest`, `RequiredFieldGrantsTest`, the roles integration tests, `TenantIsolationTest` | The new `CustomFieldDefinitionRequest` and `CustomFieldCreated` shapes; `archive(type, key)`; `sort_order` assigned last |

### 9.2 Frontend

| Test | Covers |
|---|---|
| `fieldDraft.test.ts` | `suggestKey` (accents, symbols, a leading digit → nothing, the 50-character cut); `validate` (key pattern, `new`, taken key, blank and long labels, duplicate option labels ignoring case but not accents, none active, 500); the reducer (remove only unsaved rows, sort A–Z, move); `toUpdateRequest` sends only what changed |
| `features/settings/fields/paths.test.ts` | The property URLs |

### 9.3 By hand

1. As alice, Company settings shows Properties above Layouts: Name … VAT Number as Standard, Lead
   Source as Custom with "6 options".
2. Industry's page: the label is plain text; rename "Technology", deactivate "Shipping", add
   "Renewable Energy", drag it up, Save. A company page shows the new label; the select offers no
   Shipping.
3. New property "Region", Picklist with EMEA and Americas, Save: it opens Region's page with codes
   `emea` and `americas`. The layout editor's palette has Region. Place it, and a company page has
   the dropdown.
4. Rename Region to "Sales region": the record page's label follows.
5. Archive it: gone from Properties, the layout and the record page. New property "region" is
   refused with the archived hint.
6. Errors: a duplicate option label and an empty one show under their rows before Save; key
   `new` and key `1st` are refused.
7. bob's Company settings has no Region and no Lead Source, and his Industry is unchanged.

---

## 10. Docs and comments to update

| Where | Change |
|---|---|
| `Architecture/custom-fields.md` | §3: the controller and response DTO are gone; the request's new shape; `relabel`, `archive(type, key)`. §4: the graph gains `entityFields`. §5: the HTTP API is `/api/entity-fields` (`properties.md`). §7: step 5's event is back to three fields; `sort_order` is assigned last. §10: labels are editable now |
| `Architecture/picklists.md` | §5.2: the controller, listener and DTOs are gone. §5.4: the graph, and `CustomFieldService` publishing no options. §9.1: creation goes through `EntityFieldService`. §10.1: the endpoints are `PATCH`/`GET` on `/api/entity-fields`. §12.2: built, see `properties.md` |
| `Architecture/settings.md` | §2: the property routes. §5: the Properties section, above Layouts. §7: the new files. §9: the new tests |
| `Architecture/roles-and-permissions.md` | §2.5: `manage_custom_fields` guards `/api/entity-fields` |
| `Architecture/frontend-entity-pages.md` | `dataTypeLabels.ts` in `lib/entity` |
| `EntitySettingsPage.tsx` | The comment listing the page's sections |
