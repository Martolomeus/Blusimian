# Frontend — Schema-Driven Entity Pages — Architecture Reference

**Status:** Implemented & verified · **Scope:** detail pages and create dialogs for `COMPANY`, `CONTACT`, `DEAL`
**Last updated:** 2026-11-08 · **Stack:** Vite + React 19 + react-router 7 + @tanstack/react-query 5 + react-hook-form
**Code:** `Frontend/src/lib/entity`, `Frontend/src/components/fields`, `Frontend/src/components/entity`

This is the as-built reference for how the frontend renders entity records. The backend
contract it consumes is in `Architecture/entity-layouts.md`; the settings area where
layouts are edited is in `Architecture/settings.md`.

---

## 1. Purpose

A detail page renders itself from an entity type and a record id. No component knows a
specific entity's fields: which fields exist, how they are grouped, and how each is shown
and edited all come from the schema bundle (`GET /api/entities/{entityType}/schema`).

A custom field added by a tenant, a field moved in a layout, or a whole new entity needs
no rendering code.

---

## 2. Shape

```
EntityDetailPage (entityType)        reads :id, loads layout + record, loading / error states
  └─ RecordDetail (key = record.id)  owns the form, edit state, save, delete
       ├─ EntityHeader               title and subtitle from entityConfig
       └─ RecordForm                 FormProvider + <form>
            ├─ LayoutSection[]       title + one- or two-column grid
            │    └─ FieldRow         label + value (view) or input (edit)
            │         └─ FIELD_TYPES[field.dataType]
            └─ edit bar              Save / Cancel / error banner
```

Data flows one way:

```
useEntitySchema(type) ─► resolveLayout(schema) ─► ResolvedSection[] ─► components
useRecord(type, id) ──────────────────────────────────────────────────► components
```

Two rules hold everywhere:

- **Components never look a placement up.** `resolveLayout` hands them sections whose
  fields are full `SchemaField` objects.
- **Only `FieldRow` reads the field type registry.** No other component branches on
  `dataType`.

---

## 3. Modules

### `lib/entity` — data and logic, no JSX

| File | Role |
|---|---|
| `types.ts` | TypeScript mirror of the backend contract: `EntityType`, `FieldDataType`, `SchemaField`, `PicklistOption`, `EntitySchema`, `EntityRecord`, `RecordValues`. |
| `entityConfig.ts` | `ENTITIES`: per-entity facts the schema does not serve (§4). Also `text()`. |
| `displayValue.ts` | pure: `(field, value) → string`. A picklist code becomes its option's label (an unknown code shows as itself); anything else is `text(value)`. **The one place a code becomes a label**: the picklist display, the record header's subtitle and the companies list all go through it. |
| `useEntitySchema.ts` | the schema bundle for a type, held in the react-query cache (§5). Also exports `entitySchemaQuery(type)`, the query options, for code outside a component. |
| `resolveLayout.ts` | pure: `EntitySchema → ResolvedSection[]` (§6). |
| `useEntityLayout.ts` | `useEntitySchema` + memoised `resolveLayout` → `{ sections, isLoading, error }`. |
| `records.ts` | `useRecordList`, `useRecord`, `useSaveRecord`, `useDeleteRecord` for any entity type. |
| `validateField.ts` | pure: `(field, value) → message \| undefined` (§7.3). |
| `recordForm.ts` | pure form helpers: `formValues`, `changedValues`, `filledValues`, `applySaveError`. |

### `components/fields` — one component pair per data type

| File | Role |
|---|---|
| `fieldTypes.tsx` | the registry `FIELD_TYPES` and the `FieldDisplayProps` / `FieldInputProps` contracts. |
| `TextFields.tsx` | `TEXT`, `LONGTEXT`, `EMAIL`, `URL`, `PHONE`. |
| `NumberField.tsx`, `BooleanField.tsx`, `DateField.tsx`, `ReferenceField.tsx`, `CountryField.tsx`, `PicklistField.tsx` | one type each. |
| `FieldRow.tsx` | one field: label, then `Display` or `Input`; connects the input to the form. |
| `fields.module.css` | styles shared by all field components. |

### `components/entity` — the generic page

| File | Role |
|---|---|
| `EntityDetailPage.tsx` | route component; takes `entityType` as a prop. |
| `RecordDetail.tsx` | one loaded record: header, form, edit / save / delete. |
| `RecordForm.tsx` | sections inside a `FormProvider`; shared by the detail page and the create dialog. Sections are keyed by position, since two may share a label. |
| `LayoutSection.tsx` | one section: title and grid. |

`components/CreateRecordModal.tsx` is the "New {entity}" dialog for any entity type. It
uses the same `RecordForm`, empty and always editing.

---

## 4. Entity config

`ENTITIES: Record<EntityType, EntityConfig>` in `lib/entity/entityConfig.ts` is the only
place rendering code names a specific entity:

| Property | Example | Used for |
|---|---|---|
| `path` | `/companies` | routes and links; a record lives at `${path}/${id}` |
| `api` | `/api/companies` | record endpoint |
| `singular`, `plural` | `Company`, `Companies` | titles, buttons, messages |
| `icon` | `CompanyIcon` | sidebar and page header |
| `title(record)` | `record.name` | page title, reference link text |
| `subtitleKeys` | `["type", "industry"]` | line under the page title; each value goes through `displayValue`, with the field found in the schema (not the layout, which may not place it) |

The sidebar is built from `ENTITIES`, in its declaration order.

---

## 5. Schema in memory

`useEntitySchema(entityType)` is a react-query query with key `["entity-schema", type]`
and a five-minute `staleTime`. There is no separate schema store or context:
`QueryClientProvider` already gives one cached bundle per type, de-duplicated fetches,
and loading and error state.

| Event | What happens |
|---|---|
| First page of a type | one fetch; the bundle stays in memory |
| Further pages of that type | served from memory, no request |
| Stale, or window refocus | background refetch; the browser revalidates with the ETag and the server answers 304 |

The 304 handling is the browser's, not the app's: the response has an ETag and
`Cache-Control: no-cache`, so `lib/api.ts` contains no conditional-request code.

Query keys across the app:

| Key | Content |
|---|---|
| `["entity-schema", type]` | schema bundle |
| `["records", type]` | list |
| `["records", type, id]` | one record |
| `["entity-layouts"]` | every stored layout, for the settings area (`settings.md`) |

Layout writes in the settings area invalidate `["entity-schema", type]`, so a record page
picks up an edited or newly chosen default layout on its next fetch.

**Login and logout clear the query cache** (`features/auth/auth.ts`): the bundle is
resolved by the server for the current user and tenant, and the cache is keyed by query,
not by user. Nothing else in the frontend changes for roles.

---

## 6. From layout to sections

The backend sends exactly one layout (or none). `resolveLayout` joins it to the field
dictionary:

- each placement becomes its `SchemaField`; a placement with no dictionary entry is
  skipped (the backend already guarantees there are none);
- a section left with no fields is dropped;
- when `layout` is null, the result is one section, "Details", with every field, so a
  page always renders.

`LayoutSection` draws a CSS grid of one or two columns. Fields fill **row by row**: left
to right, then down. Below 900px every section is one column.

---

## 7. Fields and forms

### 7.1 The registry

```ts
type FieldType = {
  Display: ComponentType<{ field: SchemaField; value: unknown }>;
  Input: ComponentType<{ field; id; value; onChange; onBlur; hasError }>;
};
const FIELD_TYPES: Record<FieldDataType, FieldType>;
```

| `dataType` | Wire value | Display | Input |
|---|---|---|---|
| `TEXT` | string | plain text | text input |
| `LONGTEXT` | string | text with line breaks | textarea |
| `EMAIL` | string | `mailto:` link | email input |
| `URL` | string | link, new tab; `https://` added when no scheme | url input |
| `PHONE` | string | `tel:` link | tel input |
| `NUMBER` | number | `toLocaleString()` | text input with decimal keypad |
| `BOOLEAN` | boolean | "Yes" / "No" | checkbox |
| `DATE` | `"YYYY-MM-DD"` | locale date, built as a local date | date input |
| `REFERENCE` | record id | link to the record, labelled with its title | select over the referenced entity's list |
| `COUNTRY` | ISO alpha-2 code | country name | country select |
| `PICKLIST` | option code (`"technology"`) | the option's label | select: "—" (→ `null`), the active options, and the record's current value even if inactive ("Shipping (inactive)") |

- **Inputs are plain controlled components.** `value` and `onChange` use the API's JSON
  types, with `null` for "no value". They know nothing about forms, records or entities.
- **`Display` only receives non-empty values.** `FieldRow` renders "—" for an empty one.
- **An unknown `dataType`** (backend ahead of frontend) is shown as text and is
  read-only in edit mode.
- `NumberInput` keeps what the user is typing as local text, because a half-typed number
  is not a number. It reports a number, `null`, or the raw text, which validation rejects.
- `ReferenceInput` loads the referenced entity's whole list. `AsyncSearchField` is in the
  codebase for when the API can search.
- `PicklistInput` takes its options from `field.options` in the bundle, so it needs no
  request. The `Select` treats `""` as "nothing chosen", so "—" uses the value `"-"`, which
  no code can be.

### 7.2 The form holds a record

Form values are keyed by field key and have the wire types. So:

- default values are the record's own values (`formValues`), `null` where it has none;
- an update sends only the changed fields (`changedValues`, from react-hook-form's
  `dirtyFields`) as a `PATCH`;
- a create sends only the filled fields (`filledValues`) as a `POST`.

Only fields on the resolved layout are in the form, so a field the layout hides is never
sent.

`RecordDetail` and `CreateRecordModal` each own their `useForm`; `RecordForm` only lays
it out. `RecordDetail` is rendered with `key={record.id}`, so navigating to another
record starts from a clean form, and it refills the form from the record each time
editing starts.

### 7.3 Validation

`validateField` runs per field through react-hook-form's `rules.validate` and mirrors the
backend's `RecordMapper`:

| Rule | Message |
|---|---|
| required and blank (null, or whitespace only) | "Required" |
| `NUMBER` that is not a number | "Not a number" |
| string longer than `maxLength` | "At most N characters" |
| `EMAIL` not shaped like `x@y` | "Not a valid email address" |

`false` counts as a value, so a required checkbox may be unticked. There is no picklist
rule: the select can only produce valid codes, and the server checks them anyway.

The server stays the authority. `lib/api.ts` throws an `ApiError` with `status`, `code`
and `details`. On a failed save, `applySaveError` puts each `details` entry under its
field; anything it cannot place (a field that is not on the layout, or an error that is
not about a field) goes into the error banner.

---

## 8. Adding things

| To add | Do |
|---|---|
| A field to an entity | Backend only: a `FieldDescriptor` (or a custom field) and a placement in the layout. |
| A data type | Backend: a `FieldDataType` constant. Frontend: add it to `FieldDataType` in `types.ts`, write a `{ Display, Input }` pair, add one line to `FIELD_TYPES`. |
| An entity | Backend: see `entity-layouts.md` §10. Frontend: an entry in `ENTITIES` (and an icon), two routes in `router.tsx`, a list page, and a settings card in a `SECTIONS` section (`settings.md` §3.1). |

---

## 9. Tests

`npm test` (Vitest) covers the pure modules:

| Test | Covers |
|---|---|
| `resolveLayout.test.ts` | placements joined in order; no layout → one section with every field; missing fields and empty sections dropped |
| `validateField.test.ts` | required, `false` as a value, max length, email shape, non-numeric number |
| `displayValue.test.ts` | a code shows as its label (inactive too); an unknown code as itself; other fields as text |

There are no component tests.

---

## 10. Known limitations

- **List pages are hand-written.** `CompaniesPage`, `ContactsPage` and `DealsPage` pick
  their own columns; only their data comes through `useRecordList`. `CompaniesPage` also
  loads the schema, because its Type and Industry columns hold picklist codes.
- **Field keys must not contain a dot.** A key is used as a react-hook-form field name,
  where a dot means a nested path.
- **A required field that is on no layout** cannot be filled in; the server's "Required"
  error for it appears in the banner.
- **Reference selects load the full list** of the referenced entity.
- **No optimistic locking.** Two users editing the same record: the last save wins for
  each field it touched.
- **`zod` and `@hookform/resolvers`** are still in `package.json` but no longer used.
