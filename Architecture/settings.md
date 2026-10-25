# Settings & Layout Editor — Architecture Reference

**Status:** Implemented & verified · **Scope:** the settings area, and managing entity layouts from it (`COMPANY`, `CONTACT`, `DEAL`)
**Last updated:** 2026-10-25 · **Stack:** as `frontend-entity-pages.md`, plus `@dnd-kit/core` 6.3.1, `@dnd-kit/sortable` 10.0.0, `@dnd-kit/utilities` 3.2.2
**Code:** `Frontend/src/features/settings`, `Frontend/src/lib/layouts`, `Frontend/src/components/settings`

This is the as-built reference for the settings area and the layout editor. The
backend write path they use is in `Architecture/entity-layouts.md` §5.6; how record
pages render a layout is in `Architecture/frontend-entity-pages.md`.

---

## 1. Purpose

Layouts are stored data. The settings area is where an admin manages them: list every
layout of every entity, create, duplicate and delete them, choose each entity's default,
and arrange a layout's sections and fields in an editor.

Only the default layout is shown on record pages until layouts can be assigned to roles.
Other layouts can be built and kept, and the layouts page says so.

---

## 2. Information architecture

```
Sidebar
 ├─ Contacts / Companies / Deals     (from ENTITIES)
 │   (.sidebar-nav grows)
 └─ Settings            /settings                       ← pinned to the bottom

/settings                  Settings overview (card grid)
/settings/layouts          Entity layouts (list, grouped by entity)
/settings/layouts/:id      Layout editor, on a stored layout
/settings/layouts/new      Layout editor, on a layout not stored yet:
    ?entity=deal             blank
    ?from=<id>               a copy of that layout
```

- The sidebar's Settings link sits in `.sidebar-footer`, after `.sidebar-nav`. Because
  `.sidebar-nav` is `flex: 1`, the footer lands at the bottom with no spacer. It uses the
  same link class as the entity links, so hover, active and the collapsed / expanded
  behaviour are identical. It is active on any `/settings` path.
- Every settings sub-page starts with a back link (`.page-back`): `← Settings` on the
  layouts page, `← Entity layouts` in the editor.
- The three routes are lazy pages in `router.tsx`. `@dnd-kit` is only in the editor's
  chunk.

---

## 3. Settings shell

### 3.1 Overview

`features/settings/SettingsPage.tsx`: a title, a muted intro (`.page-intro`) and a grid
(`.settings-grid`, `repeat(auto-fill, minmax(260px, 1fr))`) of `SettingsCard`s. The list
of sections is `SECTIONS` in that file, its only user.

**Adding a settings section** = one entry in `SECTIONS` + its routes in `router.tsx`.

### 3.2 `SettingsCard`

`components/settings/SettingsCard.tsx` — `{ title, description, to, icon? }`. The only
generic settings component. It renders a single `<Link>`, so the card is one tab stop,
the whole surface is clickable, and middle-click opens a new tab. The description is
clamped to two lines. It knows nothing about which settings exist.

There is no generic settings framework (shared page shell, settings search, nested
navigation). Those get built once a second settings page exists.

### 3.3 Shared pieces

| Piece | Where | Used for |
|---|---|---|
| `ConfirmDialog` | `components/ui/ConfirmDialog.tsx` | deleting a layout; discarding unsaved changes. Wraps `Modal` (`sm`): title, message, Cancel (focused on open) and a confirm button (`primary` or `danger`). `isPending` disables both and keeps it open. `RecordDetail`'s `window.confirm` can move to it. |
| Non-entity icons | `lib/icons.tsx` | gear, layout, drag handle, star (filled and outline), copy, trash, close, plus, chevron. Inline MUI paths, like `lib/entity-icons.tsx`, which stays about entities. |
| `.badge` | `features/settings/layouts/layouts.module.css` | "Default", data-type and similar pills |

Icon-only buttons are `Button variant="ghost" size="sm"` with an icon child, an
`aria-label` and a `title`. The section column count is the existing `Select`.

---

## 4. Data: `lib/layouts`

No JSX, same rule as `lib/entity`.

| File | Role |
|---|---|
| `types.ts` | `EntityLayout` (the stored tree, with row ids) and the request bodies. |
| `layouts.ts` | `useLayoutList`, `useCreateLayout`, `useUpdateLayout`, `useSetDefaultLayout`, `useDeleteLayout`; `MAX_LAYOUTS_PER_ENTITY`. |
| `layoutDraft.ts` | the editor's model: `LayoutDraft`, `reconcile`, `fromLayout`, `blankDraft`, `copyDraft`, `toRequest`, the reducer and its action creators, the refusals, and the selectors (§6.2). |

**One read.** `useLayoutList()` (query key `["entity-layouts"]`) is every layout of every
entity as a full tree. Both the list page and the editor read from it; there is no
get-by-id.

**Writes** put their result straight into the list cache (so the editor of a
just-created layout finds it before the refetch), then invalidate `["entity-layouts"]`
and `["entity-schema", entityType]`. The schema bundle's ETag follows its content, so
record pages pick up an edited or newly chosen default on their next fetch, and the
refetch is a 304 when nothing changed.

**Creating** happens in the editor, not on click. New layout and Duplicate open
`/settings/layouts/new` (§6) on a draft that exists only in the page; nothing is stored
until Save, which posts it with `useCreateLayout()` (a plain `POST` of a
`CreateLayoutRequest`). Leaving before that leaves nothing behind. The draft starts as:

| Action | Starting draft |
|---|---|
| New layout | `blankDraft`: label `New layout`; one section "Details", 2 columns, holding every required field in dictionary order |
| Duplicate | `copyDraft`: label `Copy of <label>`; the source's sections, reconciled with the dictionary |

Both are valid as they are, so an untouched draft can be saved. Reconciling the copy
matters: the stored source can still name an archived custom field, which the server
would reject.

---

## 5. Entity layouts page (`/settings/layouts`)

`LayoutSettingsPage` groups the list by entity, in `ENTITIES` (sidebar) order, one
`LayoutGroup` each, all on one page. Within a group the default comes first, then by
label.

- **Row:** label (a link), `★ Default` badge, `N sections · M fields` (counted from the
  stored tree), actions. The whole row opens the editor, as list tables do; the actions
  stop propagation, also on disabled buttons.
- **Actions:** ☆ set as default (not on the default row), ⧉ duplicate (opens the editor
  on a copy, stored only on Save), 🗑 delete through `ConfirmDialog` (disabled on the
  default row, with the reason as its title). "New layout" in the group header opens the
  editor on a blank layout the same way.
- **Limit:** at 20 layouts the group's "New layout" and every Duplicate are disabled,
  titled "A company can have at most 20 layouts. Delete one to add another." The server
  checks again on Save; its 409 shows in the editor's error banner.
- Set default and delete each disable only their own control while they run. An error
  shows under the group header and is cleared by the next action.
- The page intro says that only the default is shown on record pages today. When role
  assignment ships, that sentence goes and rows gain an "Assigned to" column.

---

## 6. Layout editor (`/settings/layouts/:id` and `/settings/layouts/new`)

### 6.1 Shape

```
LayoutEditorPage (:id)         reads :id, finds the layout in the list; loading / not found / error
  └─ LayoutEditorWithFields    loads the entity's schema bundle (the field dictionary); Save = PUT
       └─ LayoutEditor         (below)
NewLayoutPage (new)            reads ?entity / ?from; for a copy, finds the source in the list
  └─ NewLayoutWithFields       loads the schema bundle; starts from blankDraft / copyDraft; Save = POST
       └─ LayoutEditor         owns the reducer, save, unsaved-changes guard
            ├─ header          entity icon, label input (styled as the title), Default badge, Cancel, Save
            ├─ error banner    a failed save, one line per server `details` entry
            └─ LayoutDndContext (render prop: view)
                 ├─ canvas: SortableContext of EditorSection
                 │    └─ handle, label Input, column Select, delete; SortableContext of FieldChip
                 └─ FieldPalette   unplaced fields, "Add to" Select, PaletteItem draggables
```

The two pages decide what the draft starts from and how Save stores it; they pass
`LayoutEditor` an `initial` draft, a `save(request)` that resolves to the stored layout,
and `isNew`. `LayoutEditorWithFields` is keyed by layout id and `NewLayoutWithFields` by
the query, so moving to another layout or starting point starts from a fresh draft.
Background refetches of the list or the schema don't reset the draft. Path helpers and
the `/new` query (`newLayoutPath`, `newLayoutStart`) are in `layouts/paths.ts`.

| State | Shown |
|---|---|
| list or schema loading | "Loading…" |
| id not in the list | "This layout doesn't exist (it may have been deleted)." |
| `?from` not in the list | "The layout to copy doesn't exist (it may have been deleted)." |
| no `?from`, no known `?entity` | "This link doesn't say which layout to start from." (`?entity` is case-insensitive) |
| list or schema error | `.page-error` with the message |

Below 900px (the same breakpoint as `LayoutSection`) the palette moves under the canvas
and stops being sticky, and two-column sections show one column.

### 6.2 The draft and its reducer

`fromLayout(layout, fields)` turns a stored layout into a `LayoutDraft` (a new layout
starts from `blankDraft` or `copyDraft`, §4):

```ts
type DraftSection = { uid: string; label: string; columnCount: 1 | 2; fields: string[] };
type LayoutDraft = { label: string; sections: DraftSection[] };
```

- `uid` is client-only (React key, DnD id): section ids change on every save.
- `reconcile` matches placements to the dictionary like the schema bundle does: keys
  case-insensitively, rewritten to the dictionary's spelling, and a key with no field
  (an archived custom field) dropped. Array order is display order.
- The draft holds only what the editor changes; id, entity type and default flag are
  read from the layout.

Every control dispatches to one pure reducer,
`layoutDraftReducer(draft, action, requiredKeys)`:

| Action | Effect |
|---|---|
| `renameLayout(label)` | |
| `addSection()` | appends "New section", 2 columns, empty. The action creator makes the uid, so the reducer stays pure and the page can focus the new label. Refused at 15 sections. |
| `renameSection(uid, label)`, `setColumns(uid, 1 \| 2)` | |
| `moveSection(uid, toIndex)` | |
| `removeSection(uid)` | its fields go back to the palette. Refused while it holds a required field, or when it is the only section. No confirm: Cancel undoes everything. |
| `placeField(key, sectionUid, index?)` | takes the field out of wherever it is and puts it at `index` in the resulting list (appended when absent). One action for adding from the palette, reordering and moving between sections. |
| `removeField(key)` | refused for a required field |
| `resetDraft(draft)` | replace the draft (after a save) |

The refusals (`canAddSection`, `sectionRemovalRefusal`) are exported and also decide
which buttons are disabled, so a control and the reducer can't disagree. Derived, not
stored: `unplacedFields`, `isDirty` (compares `toRequest` of the draft and of the loaded
draft, so uids and surrounding whitespace don't count), `validateDraft`, `toRequest`.

### 6.3 One control per job

Drag-and-drop arranges (pointer and keyboard); palette ＋ adds; chip ✕ removes. There
are no move buttons, no drag-to-palette removal, and the default is set only from the
list page.

- **Required fields can't come off a layout:** their ✕ is disabled, so the server's
  main rule can't be broken from the editor.
- **Field chip:** drag handle, label, `*` for required, data-type badge, ✕.
- **Palette:** the fields not on the layout, in dictionary order, required ones first.
  "Add to" picks the section ＋ appends to; it follows the last section until another
  is picked. Standard and custom fields are not told apart.
- **A required field not placed** is only possible when it was created after the
  layout was saved. It shows at the top of the palette in warning style, and Save is
  disabled.

### 6.4 Drag-and-drop

`LayoutDndContext` is one `DndContext` over canvas and palette, after dnd-kit's
multiple-containers example.

- **Lists:** sections are one `SortableContext` (`verticalListSortingStrategy`), dragged
  by their handle; each section's chips are another (`rectSortingStrategy`, so the two-
  column grid sorts correctly), dragged anywhere with the pointer; palette rows are plain
  `useDraggable`s, pointer only (keyboard users add with ＋).
- **Ids** are prefixed (`section:`, `field:`, `palette`, in `editor/dndIds.ts`), because
  a field key is arbitrary text. A field has the same id on the canvas and in the
  palette: it is the same item wherever it is.
- **Preview.** Within one list, sortable transforms show where an item lands. A field
  that changes section has to change lists mid-drag, so while dragging, the context
  keeps a *preview*: the draft as it would be after that move, made by the editor's own
  reducer. Canvas and palette render `view = preview ?? draft`. On drop, the gesture
  becomes **one** `moveSection` or `placeField` on the real draft; cancelling just drops
  the preview. All rules stay in the reducer.
- **Collision detection:** a section only ever lands among sections (`closestCenter`).
  A field takes what the pointer is in (falling back to rectangle intersection); over a
  section that has chips it aims at the closest chip. Over the palette, the field goes
  back to where it started.
- **Keyboard:** `KeyboardSensor` with a combined coordinate getter:
  `sortableKeyboardCoordinates` for sections; for fields, the nearest chip in the arrow's
  direction in any section, or an empty section. Focus a handle, Space to lift, arrows,
  Space to drop, Escape to cancel. The chip's handle is its registered activator, so keys
  pressed on ✕ never start a drag.
- **Announcements** name things by label: "Website moved to Overview, position 3 of 7."
- **`DragOverlay`** (portalled to `body`) shows the chip or a section summary under the
  pointer; the source's slot shows where it will land.
- Droppables are re-measured continuously while dragging (`MeasuringStrategy.Always`),
  because moving a field between sections shifts everything below it.

### 6.5 Validation

Most server rules can't be broken from the editor (section limit, column count, unknown
keys, a field placed twice, removing a required field). Three remain, from
`validateDraft`:

| Rule | Shown as |
|---|---|
| layout label blank | "Required" under the title |
| section label blank | "Required" under the section's label |
| required field not placed | warning in the palette |

Any issue disables Save, with what to do as its title ("Place all required fields to
save"). Label inputs cap at 255 characters. The server stays the authority: a 400
(reachable when something changed mid-edit, such as a field being archived) is shown in
the error banner, one line per `details` entry, not mapped back onto the canvas.

### 6.6 Saving and leaving

- **Save, stored layout** (`PUT`, whole tree) → the returned layout goes into the list
  cache, and the draft is reset from it, so the editor is clean again. Last save wins.
- **Save, new layout** (`POST`) → the created layout goes into the list cache, and the
  editor moves to `/settings/layouts/:id` with `replace`, so Back goes to the list rather
  than to `/new`, where Save would create a second copy. The `:id` page finds the layout in
  the cache, with no fetch. Save is enabled before any change (nothing is stored yet), and
  stays disabled from the create until the move, so a double click can't post twice. The
  subtitle reads "… layout · not saved yet".
- **Unsaved changes:** react-router's `useBlocker` shows `ConfirmDialog` "Discard unsaved
  changes?" for any in-app navigation, Cancel included, while the draft differs from where
  it started; a `beforeunload` listener covers reload and tab close. An untouched new
  layout leaves without asking: nothing is lost. The move after creating runs before React
  re-renders, while `dirty` is still true, so a `leaving` ref lets that one navigation
  through.

---

## 7. Files

| File | Role |
|---|---|
| `lib/icons.tsx` | non-entity glyphs |
| `components/settings/SettingsCard.tsx` + css | §3.2 |
| `components/ui/ConfirmDialog.tsx` + css | §3.3, exported from `components/ui` |
| `components/Sidebar.tsx`, `globals.css` | Settings link, `.sidebar-footer`, `.page-intro`, `.settings-grid`, `.visually-hidden`, warning colour tokens |
| `components/entity/RecordForm.tsx` | sections keyed by index: two sections may share a label |
| `lib/entity/useEntitySchema.ts` | also exports `entitySchemaQuery(type)`, the query options |
| `features/settings/SettingsPage.tsx` | overview and its `SECTIONS` |
| `features/settings/layouts/LayoutSettingsPage.tsx`, `LayoutGroup.tsx` | list page |
| `features/settings/layouts/layouts.module.css` | styles shared by list and editor |
| `features/settings/layouts/paths.ts` | the layout pages' URLs; reading the `/new` query |
| `features/settings/layouts/editor/LayoutEditor.tsx` | the editor: the reducer, save, guard; `EditorMessage` |
| `features/settings/layouts/editor/LayoutEditorPage.tsx`, `NewLayoutPage.tsx` | the two routes: loading, the starting draft, how Save stores it |
| `features/settings/layouts/editor/EditorSection.tsx`, `FieldChip.tsx`, `FieldPalette.tsx` | canvas and palette |
| `features/settings/layouts/editor/LayoutDndContext.tsx`, `dndIds.ts` | drag-and-drop (§6.4) |
| `lib/layouts/*` | §4 |

---

## 8. Permissions (later)

Everything here is an admin function. When auth lands: the sidebar hides Settings for
non-admins, `/settings/**` routes redirect, and — the real guard — every
`/api/entity-layouts` endpoint requires the admin role. The schema bundle stays open to
every signed-in user.

---

## 9. Tests

None yet; the area was verified by hand in the browser (pointer and keyboard drags,
validation, save, the unsaved-changes guard, set default, delete; creating from blank and
from a copy, Back after creating, bad `/new` links, a refused create). When tests are picked
up, `layoutDraft` is the place to start (Vitest, pure): `reconcile` drops unknown keys
and fixes case; `blankDraft` holds the required fields; each reducer action,
including its refusals and `placeField` moving an already-placed field.

---

## 10. Known limitations and decisions

- **Last save wins** when two admins edit one layout.
- **A new layout's draft lives in the page.** Reloading `/new` starts over from the
  link (after the browser's leave warning), as reloading a stored layout's editor does.
- **Drag precision under synthetic input.** dnd-kit reads the drop target from the last
  rendered frame; scripted drags that send every event in one frame can land one slot
  off. Real pointer and keyboard input don't.

Considered and left out for simplicity, so they aren't proposed again without a reason:

| Left out | Why it isn't needed |
|---|---|
| Optimistic locking | records are last-write-wins too; one admin, no auth |
| A new-layout dialog (name and source first) | the editor names the layout itself, and a dialog would still store it before editing |
| Draft layouts on the server, or a draft kept in browser storage | an unsaved layout is a page, like an unsaved record form |
| Get-by-id, a summary DTO, a per-entity list | one list of full trees serves both pages |
| A layout-specific error code; mapping server errors onto the canvas | the editor checks the rules first; a 400 is rare and a banner suffices |
| Unique labels, no empty sections | they protect nothing; empty sections are already hidden |
| A preview of the record page | the canvas already uses the record page's grid |
| Section ↑/↓ buttons, drag-to-palette removal, set-default in the editor | each duplicated another control |
| Palette search, marking custom fields | fields per entity are few; standard and custom are one list |
| `SegmentedControl`, `IconButton`, `Badge` primitives | `Select`, `Button` and a CSS class do the job |
