# Settings Sections & Per-Entity Settings — Spec

**Status:** Proposal, nothing implemented · **Scope:** the settings area's information architecture; layouts move from one generic page into per-entity settings pages
**Date:** 2026-10-25 · **Builds on (as-built):** `Architecture/settings.md`, `Architecture/entity-layouts.md`
**Code:** `Frontend/src/features/settings`, `Frontend/src/components/settings`, `Frontend/src/router.tsx`

> The settings overview is one card grid today, with one card: *Entity layouts*. This
> spec groups settings into **collapsible sections**, each holding its own content (a
> card grid on the overview). There are two sections to start with: **CRM**, with
> *Contact settings* and *Company settings*, and **Sales**, with *Deal settings*. Each
> card opens that entity's settings page, which is itself a stack of collapsible
> sections. Its first
> section, **Layouts**, lists that entity's layouts exactly as the *Entity layouts*
> page does now. The *Entity layouts* page goes away. The layout editor stays as it is;
> only where it links back to changes.

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | A settings page is a stack of `SettingsSection`s: collapsible, with a title, an optional count, description and header actions, and any content. On the overview the content is a card grid; on an entity page it's whatever the section needs. | §4 |
| D2 | **One** generic entity settings page, `/settings/:entity`, driven by `ENTITIES`. No page per entity. | §6 |
| D3 | The URL segment is the entity's own route segment (`/contacts` → `/settings/contacts`). The overview section (CRM, Sales) is **not** in the URL. | §3 |
| D4 | Layouts become the **Layouts** section of the entity page. `LayoutGroup` becomes `LayoutsSection`; its header moves into `SettingsSection`. `LayoutSettingsPage` and `/settings/layouts` are removed. | §7 |
| D5 | Editor URLs nest under the entity: `/settings/contacts/layouts/:id`, `/settings/contacts/layouts/new[?from=<id>]`. `?entity=` is gone; the path says it. The editor UI doesn't change; its back link, Cancel and post-create move point at the entity page. | §8 |
| D6 | A layout is found only under its own entity's URL. Any other entity's URL shows "doesn't exist". | §8.2 |
| D7 | Collapsed state is per viewer, per section, in `localStorage`. Every section starts expanded, and if storage isn't available they all just stay expanded. | §4.3 |
| D8 | No backend change. The single `/api/entity-layouts` list stays; each entity page filters it, as `LayoutSettingsPage` does today. | — |
| D9 | The overview has two sections: **CRM** (Contact, Company) and **Sales** (Deal). | §5 |
| D10 | Card and page titles come from `ENTITIES`, so the entity is *Company* everywhere (*Company settings*). | §5 |

---

## 2. Before and after

```
Before                                    After
/settings                                 /settings
  [Entity layouts]                          ▾ CRM
                                              [Contact settings] [Company settings]
/settings/layouts                           ▾ Sales
  Contacts  (layout rows)                     [Deal settings]
  Companies (layout rows)
  Deals     (layout rows)                 /settings/contacts            (/companies, /deals alike)
                                            ▾ Layouts                     (contact layout rows)

                                          /settings/contacts/layouts/:id     editor (unchanged)
/settings/layouts/:id         editor      /settings/contacts/layouts/new     editor, blank
/settings/layouts/new?entity=contact      /settings/contacts/layouts/new?from=<id>   editor, copy
/settings/layouts/new?from=<id>
```

---

## 3. Information architecture and URLs

```
Sidebar
 └─ Settings              /settings                                  (unchanged; active on any /settings path)

/settings                              Settings overview: sections of cards
/settings/:entity                      Entity settings: sections (today: Layouts)
/settings/:entity/layouts              → redirects to /settings/:entity
/settings/:entity/layouts/:id          Layout editor, on a stored layout of that entity
/settings/:entity/layouts/new          Layout editor, blank layout of that entity
    ?from=<id>                           a copy of that layout (must be of that entity)
```

`:entity` is the entity's route segment: `contacts`, `companies`, `deals`, matched
case-insensitively. It's the same segment as the record pages, so `/contacts` and
`/settings/contacts` belong together.

**Why the section isn't in the URL.** CRM and Sales are headings on the overview, not
pages: nothing would live at `/settings/crm`. Leaving them out also keeps an entity's URL
the same if it moves to another section later.

**Route precedence.** React Router ranks static segments above dynamic ones, so a future
`/settings/users` beats `/settings/:entity`. A new static settings page just must not take
an entity's plural.

**Removed:** `/settings/layouts`, `/settings/layouts/:id` and `/settings/layouts/new`. There
are no redirects: this is the dev phase and nobody has bookmarks. `/settings/layouts` now
matches `/settings/:entity` and shows the unknown-entity message (§6.3).

### Back links

Each page keeps the single `.page-back` link it has now. There's no breadcrumb trail.

| Page | Back link |
|---|---|
| `/settings/:entity` | `← Settings` |
| editor, any state (incl. loading / not found) | `← Contact settings` (the entity page) |
| unknown `:entity`, on any route | `← Settings` |

---

## 4. `SettingsSection` — the collapsible section

`components/settings/SettingsSection.tsx` + `SettingsSection.module.css`. It's the second
generic settings component, next to `SettingsCard`, and like `SettingsCard` it knows
nothing about which settings exist.

### 4.1 API

```ts
type SettingsSectionProps = {
  /** Unique app-wide and stable; it names the collapsed state in storage. "overview/crm", "contacts/layouts". */
  storageKey: string;
  title: string;
  /** Shown as a muted pill after the title, also while collapsed. Omit while unknown (loading). */
  count?: number;
  /** A muted line at the top of the body; hidden with it. */
  description?: string;
  /** Controls at the right of the header, e.g. "New layout". Always visible, also while collapsed. */
  actions?: ReactNode;
  children: ReactNode;
};
```

### 4.2 Markup and accessibility

The WAI-ARIA disclosure pattern:

```tsx
<section className={styles.section} aria-labelledby={headingId}>
  <header className={styles.header}>
    <h2 id={headingId} className={styles.heading}>
      <button type="button" className={styles.toggle} aria-expanded={open} aria-controls={bodyId} onClick={toggle}>
        <ChevronRightIcon className={styles.chevron} size={20} />   {/* rotated 90° when open */}
        <span>{title}</span>
        {count !== undefined && <span className={styles.count}>{count}</span>}
      </button>
    </h2>
    {actions && <div className={styles.actions}>{actions}</div>}
  </header>
  <div id={bodyId} className={styles.body} hidden={!open}>
    {description && <p className={styles.description}>{description}</p>}
    {children}
  </div>
</section>
```

- Ids come from `useId()`.
- **The toggle is a real `<button>` inside the `<h2>`.** Screen readers announce "CRM,
  heading level 2, button, expanded"; Enter and Space toggle it natively. It is
  `flex: 1`, so the whole header up to the actions is the click target.
- **Actions sit outside the button**, never inside it, so you never get a button nested
  in a button. That's also why it isn't `<details>/<summary>` (§13).
- **The body is `hidden`, not unmounted.** State inside a collapsed section (an error
  banner, a pending mutation, the delete dialog) survives collapsing, and queries keep
  running. Hidden content leaves the tab order and the accessibility tree.
- Focus stays on the toggle when it's clicked, so collapsing never strands focus.

### 4.3 Collapsed state

- A section starts expanded. Collapsing writes `localStorage["blusimian:settings-collapsed:<storageKey>"] = "1"`;
  expanding removes the entry. It's read once, in the `useState` initialiser.
- Every read and write sits in `try/catch` and fails silently. With no storage (private
  window, blocked site data) sections start expanded and can still be toggled; the state
  just isn't remembered.
- Each page has its own collapsed state: collapsing *Layouts* on Contact settings doesn't
  collapse it on Company settings.
- An entry for a section that no longer exists is never read again, which is harmless.
- The two helpers (`readCollapsed`, `writeCollapsed`) live in `SettingsSection.tsx`,
  their only user. This is the app's first use of browser storage, and it's used for a
  per-viewer convenience only.

### 4.4 Styles

The header is today's `LayoutGroup` header (`.groupHeader`), moved here, so the entity
page looks like the current layouts page:

| Class | Rules |
|---|---|
| `.section + .section` | `margin-top: var(--space-14)` (today's `.group` spacing) |
| `.header` | flex, `align-items: center`, `gap: var(--space-4)`, `padding-bottom: var(--space-4)`, `border-bottom: 2px solid var(--color-table-header-border)` |
| `.heading` | `flex: 1`, `min-width: 0`, no margin |
| `.toggle` | button reset; `display: flex`, `width: 100%`, `align-items: center`, `gap: var(--space-3)`; `font-size: var(--font-size-lg)`, `font-weight: var(--font-weight-semibold)`, `color: var(--color-text)`; `cursor: pointer`; `:focus-visible` outline as `SettingsCard` (`2px solid var(--color-sidebar-active)`, offset 2px) |
| `.chevron` | muted; `transition: transform var(--transition-fast)`; `[aria-expanded="true"] .chevron { transform: rotate(90deg) }`; no transition under `prefers-reduced-motion: reduce`; turns `--color-text` on toggle hover |
| `.count` | muted pill, the look of `layouts.module.css` `.badge` |
| `.body` | `padding-top: var(--space-8)` |
| `.description` | as `.page-intro`: muted, `max-width: 720px`, `margin-bottom: var(--space-8)` |

There's no height animation: `hidden` can't animate, and the expand is instant (§13). A
collapsed section is just its header and its rule, so a stack of collapsed sections reads
as a list.

---

## 5. Settings overview (`/settings`)

`features/settings/SettingsPage.tsx`. The title and intro stay; the single grid becomes
one `SettingsSection` per entry in `SECTIONS`, each holding a `.settings-grid` of
`SettingsCard`s. `SettingsCard` and `.settings-grid` don't change.

```ts
type OverviewSection = { key: string; title: string; description?: string; cards: SettingsCardProps[] };

function entitySettingsCard(type: EntityType): SettingsCardProps {
  const entity = ENTITIES[type];
  return {
    title: `${entity.singular} settings`,
    description: `Layouts for ${entity.singular.toLowerCase()} records.`,
    to: entitySettingsPath(type),
    icon: entity.icon,
  };
}

// In overview order; an entity's card in the section it belongs to, in sidebar order
// within it. A section is listed once it has a card.
const SECTIONS: OverviewSection[] = [
  { key: "crm", title: "CRM", cards: [entitySettingsCard("CONTACT"), entitySettingsCard("COMPANY")] },
  { key: "sales", title: "Sales", cards: [entitySettingsCard("DEAL")] },
];
```

Every entity has a card in exactly one section. Nothing checks this: three entities
are easy to see at a glance, and a missing card shows up on the overview.

Each section renders as
`<SettingsSection storageKey={`overview/${key}`} title={title} description={description}>`.
There's no count and no actions on overview sections.

When the entity page gains a section, the card description says so:
"Fields and layouts for contact records."

```
Settings
Configure how Blusimian works for your organisation.

▾ CRM
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌──────────────────────────┐ ┌──────────────────────────┐
│ [icon] Contact settings › │ │ [icon] Company settings › │
│ Layouts for contact       │ │ Layouts for company       │
│ records.                  │ │ records.                  │
└──────────────────────────┘ └──────────────────────────┘

▾ Sales
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌──────────────────────────┐
│ [icon] Deal settings    › │
│ Layouts for deal records. │
└──────────────────────────┘
```

The grid is `auto-fill`, so a section with one card keeps the card at its usual width.

---

## 6. Entity settings page (`/settings/:entity`)

`features/settings/entity/EntitySettingsPage.tsx`, a lazy route. It's one component for
every entity.

### 6.1 Shape

```
EntitySettingsPage            reads :entity → EntityConfig, or the unknown-entity message
  ├─ ← Settings
  ├─ header                   entity icon (28px, as the editor header) + "Contact settings"; intro
  └─ sections, in order
       └─ LayoutsSection (entity)            §7
```

- Title: `${singular} settings`. Intro (`.page-intro`): "Configure how contacts work for
  your organisation." (`plural`, lower-cased).
- The page itself fetches nothing. Each section loads its own data and shows its own
  loading and error states, so a slow or failing section doesn't block the others.

### 6.2 Sections on this page

The sections are written out in `EntitySettingsPage`, with no registry. Today it's just
one:

```tsx
<LayoutsSection entity={entity} />
```

**Adding a section** means writing a component that renders a `SettingsSection` with
`storageKey={`${segment}/<name>`}` and adding it to this list. Then update the entity
card's description (§5).

### 6.3 Unknown entity

If `:entity` matches no entity, the page shows `← Settings` and
`<p className="page-empty">This settings page doesn't exist.</p>`. The editor routes show
the same message under an unknown `:entity`.

```
← Settings
[contact icon] Contact settings
Configure how contacts work for your organisation.

▾ Layouts (3)                                                          [+ New layout]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Only the default layout is shown on contact pages today. Other layouts are kept for
when layouts can be assigned to roles.
  Default               ★ Default    2 sections · 10 fields                     ⧉  🗑
  Field sales                        3 sections · 12 fields                  ☆  ⧉  🗑
  Sales (compact)                    1 section · 5 fields                    ☆  ⧉  🗑
──────────────────────────────────────────────────────────────────────────────────────

collapsed:
▸ Layouts (3)                                                          [+ New layout]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 7. Layouts section

`features/settings/layouts/LayoutsSection.tsx` replaces `LayoutGroup.tsx` (rename it, and
move the header out). Each row behaves **exactly** as it does today
(`Architecture/settings.md` §5): row order, columns, actions, the limit, per-control
pending state, the error banner, and `ConfirmDialog`.

```tsx
export function LayoutsSection({ entity }: { entity: EntityConfig }) {
  const { data: all, isLoading, error } = useLayoutList();
  const layouts = all?.filter((layout) => layout.entityType === entity.type);
  // … today's LayoutGroup state and handlers, unchanged …
  return (
    <SettingsSection
      storageKey={`${settingsSegment(entity.type)}/layouts`}
      title="Layouts"
      count={layouts?.length}
      description={`Only the default layout is shown on ${entity.singular.toLowerCase()} pages today. Other layouts are kept for when layouts can be assigned to roles.`}
      actions={layouts && <Button …>New layout</Button>}   // today's button, unchanged
    >
      {isLoading && <p className="page-empty">Loading…</p>}
      {error && <p className="page-error">{error.message}</p>}
      {/* today's error banner, empty state, rows and ConfirmDialog */}
    </SettingsSection>
  );
}
```

What changes compared with `LayoutGroup`:

| Was | Becomes |
|---|---|
| `<section className={styles.group}>` with its own header (entity icon, plural, New layout) | `SettingsSection` titled "Layouts"; the icon moves to the page header; New layout becomes `actions` |
| received `layouts` from the page | reads `useLayoutList()` and filters it itself |
| page intro about default-only | the section's `description`, naming the entity |
| `navigate(newLayoutPath({ entity }))`, `newLayoutPath({ from })`, `layoutPath(id)` | `newLayoutPath(entity.type)`, `newLayoutPath(entity.type, layout.id)`, `layoutPath(entity.type, id)` (§9) |
| — | New layout appears once the list has loaded, as it does today (the group wasn't rendered before that) |

- The empty state stays as "No layouts yet." It's still reachable only if the seed is
  missing, since the default layout can't be deleted.
- The role-assignment note in `Architecture/settings.md` §5 still applies: when roles
  ship, the description sentence goes away and rows gain an "Assigned to" column.

**CSS.** In `layouts.module.css`, delete `.group`, `.groupHeader`, `.groupIcon`,
`.groupTitle` and `.groupHeader + .errorBanner`; `SettingsSection` owns the header now.
Give `.errorBanner` a bottom margin inside the section (`margin-bottom: var(--space-4)`).
The `.row*` classes stay.

---

## 8. Layout editor

The editor (header, canvas, palette, drag-and-drop, validation, save, unsaved-changes
guard) doesn't change. The only visible change is the back link's text and target.

### 8.1 Navigation targets

| Where | Was | Becomes |
|---|---|---|
| back link (`LayoutEditor`, `EditorMessage`) | `← Entity layouts` → `/settings/layouts` | `← Contact settings` → `/settings/contacts` |
| Cancel | `navigate(LAYOUTS_PATH)` | `navigate(entitySettingsPath(entityType))` |
| after creating | `navigate(layoutPath(saved.id), { replace: true })` | `navigate(layoutPath(entityType, saved.id), { replace: true })` |

The unsaved-changes guard (`useBlocker`, `beforeunload`, the `leaving` ref) works the same
way. Cancel and the back link to the entity page are in-app navigation, so they trigger
it as they do now. After creating a layout, Back still lands on the page you came from:
now that's the entity page.

`EditorMessage` takes the entity type for its back link:
`EditorMessage({ entityType, error, children })`. Without an `entityType` (unknown
`:entity`), it links to `← Settings`.

### 8.2 Route pages

Both pages first resolve `:entity` with `entityFromSettingsSegment`. If it matches
nothing, they show the unknown-entity message (§6.3).

**`LayoutEditorPage`** (`/settings/:entity/layouts/:id`): finds the layout with
`l.id === id && l.entityType === entity.type`. A layout of another entity is
"This layout doesn't exist (it may have been deleted)." (D6). That URL is only ever
typed by hand, and one rule is simpler than a redirect.

**`NewLayoutPage`** (`/settings/:entity/layouts/new`):

| Query | Starts from |
|---|---|
| none | `blankDraft` of the path's entity. There's no wait for the list, as today with `?entity=`. |
| `?from=<id>`, a layout of this entity | `copyDraft` of it |
| `?from=<id>`, missing or of another entity | "The layout to copy doesn't exist (it may have been deleted)." |

The message "This link doesn't say which layout to start from." goes away: the path
always names the entity. `NewLayoutWithFields` stays keyed by `params.toString()`, now
together with the entity (`${entity.type}?${params}`), so switching entity or source
starts a fresh draft.

### 8.3 States (updated table from `Architecture/settings.md` §6.1)

| State | Shown |
|---|---|
| unknown `:entity` | "This settings page doesn't exist." (`← Settings`) |
| list or schema loading | "Loading…" |
| `:id` not in the list, or of another entity | "This layout doesn't exist (it may have been deleted)." |
| `?from` not in the list, or of another entity | "The layout to copy doesn't exist (it may have been deleted)." |
| list or schema error | `.page-error` with the message |

---

## 9. Paths

### `features/settings/paths.ts` (new)

```ts
export const SETTINGS_PATH = "/settings";

/** The entity's segment under /settings: its route segment, "contacts". */
export function settingsSegment(type: EntityType): string;          // ENTITIES[type].path.slice(1)

/** /settings/contacts */
export function entitySettingsPath(type: EntityType): string;

/** The entity a /settings/:entity segment names (case-insensitive), or null. */
export function entityFromSettingsSegment(segment: string | undefined): EntityConfig | null;
```

### `features/settings/layouts/paths.ts` (rewritten)

```ts
/** /settings/contacts/layouts/<id> */
export function layoutPath(type: EntityType, id: string): string;

/** A blank layout of the entity, or a copy of one of its layouts. /settings/contacts/layouts/new[?from=<id>] */
export function newLayoutPath(type: EntityType, from?: string): string;

/** The ?from id of a /new URL, or null for a blank layout. */
export function newLayoutSource(params: URLSearchParams): string | null;
```

`LAYOUTS_PATH`, `NewLayoutStart` and `newLayoutStart` are removed. Nothing outside
`features/settings` builds a settings URL except the sidebar's `/settings`.

---

## 10. Routing (`router.tsx`)

```ts
{ path: "settings", ...lazyPage(() => import("./features/settings/SettingsPage")) },
{ path: "settings/:entity", ...lazyPage(() => import("./features/settings/entity/EntitySettingsPage")) },
{ path: "settings/:entity/layouts", element: <Navigate to=".." relative="path" replace /> },
{ path: "settings/:entity/layouts/new", ...lazyPage(() => import("./features/settings/layouts/editor/NewLayoutPage")) },
{ path: "settings/:entity/layouts/:id", ...lazyPage(() => import("./features/settings/layouts/editor/LayoutEditorPage")) },
```

The `settings/layouts*` entries are removed. Every page is still its own lazy chunk, and
`@dnd-kit` stays in the editor's chunk only. `LayoutsSection` is in the entity page's
chunk.

---

## 11. Files

| File | Change |
|---|---|
| `components/settings/SettingsSection.tsx` + `.module.css` | **new** — §4 |
| `features/settings/paths.ts` | **new** — §9 |
| `features/settings/paths.test.ts`, `layouts/paths.test.ts` | **new** — §14 |
| `features/settings/entity/EntitySettingsPage.tsx` | **new** — §6 |
| `features/settings/layouts/LayoutsSection.tsx` | **new**, from `LayoutGroup.tsx` — §7 |
| `features/settings/SettingsPage.tsx` | `SECTIONS` becomes sections of cards — §5 |
| `features/settings/layouts/paths.ts` | rewritten — §9 |
| `features/settings/layouts/editor/LayoutEditor.tsx` | back link, Cancel, post-create move; `EditorMessage` takes `entityType` — §8.1 |
| `features/settings/layouts/editor/LayoutEditorPage.tsx`, `NewLayoutPage.tsx` | entity from the path — §8.2 |
| `features/settings/layouts/layouts.module.css` | group header classes removed — §7 |
| `router.tsx` | §10 |
| `features/settings/layouts/LayoutSettingsPage.tsx` | **deleted** |
| `features/settings/layouts/LayoutGroup.tsx` | **deleted** (became `LayoutsSection.tsx`) |
| `components/settings/SettingsCard.tsx`, `Sidebar.tsx`, `globals.css`, `lib/layouts/*` | unchanged |
| backend | unchanged |

---

## 12. Settled questions

| Question | Answer |
|---|---|
| "Account" or "Company"? | **Company**, everywhere. Titles come from `ENTITIES`, so it's *Company settings*, matching the sidebar, the record pages and `/companies`. |
| Where do deal layouts go? | A second overview section, **Sales**, holding *Deal settings*. Without it, removing the generic page would leave deal layouts with no place in the UI. |

---

## 13. Considered and left out

| Left out | Why |
|---|---|
| `/settings/crm/contacts` URLs | CRM isn't a page; an entity's URL would change if it moved section (D3) |
| Redirects from `/settings/layouts/*` | dev phase, no users or bookmarks |
| A breadcrumb trail | two levels deep; one back link per page, as today |
| `<details>` / `<summary>` | header actions (New layout) can't go inside `<summary>`, and remembering state needs the toggle handled in code anyway |
| Animated expand / collapse | `hidden` can't animate; instant is fine and keeps the body out of the tab order for free |
| Expand all / collapse all, a `defaultOpen` prop | a handful of sections, all open by default |
| Collapsed state on the server or shared between pages | it's a per-viewer convenience; there are no users yet |
| A section registry on the entity page | one section today; a list of components is clearer than config |
| A page per entity (`ContactSettingsPage`, …) | they'd be identical; `ENTITIES` already says everything that differs |
| A per-entity layouts endpoint | the list is at most 60 small trees and is already cached; the filter is one line |
| Settings search, a settings side-nav | still few pages; revisit when the overview has several sections |

---

## 14. Verification

### Unit (Vitest, pure — as `lib/entity/*.test.ts`)

- `paths.test.ts`: for every `ENTITIES` entry,
  `entityFromSettingsSegment(settingsSegment(type))?.type === type`; the segment is
  matched case-insensitively (`CONTACTS`); unknown, empty and `undefined` give `null`;
  `entitySettingsPath("CONTACT") === "/settings/contacts"`.
- `layouts/paths.test.ts`: `layoutPath`; `newLayoutPath` with and without `from`
  (the id is URL-encoded); `newLayoutSource` round-trips `newLayoutPath`'s query and gives
  `null` with no `from`.

### By hand, in the browser

1. `/settings` shows CRM (Contact and Company cards) and then Sales (the Deal card), both
   expanded. Cards wrap at narrow widths, and middle-click opens a new tab.
2. Collapse CRM: its cards are hidden, the chevron points right, `aria-expanded="false"`,
   and Sales stays expanded. Reload: CRM is still collapsed. Expand, reload: expanded.
3. Keyboard: Tab reaches the toggle, it has a visible focus ring, Enter and Space toggle
   it, and a collapsed section's cards are skipped by Tab.
4. A card opens `/settings/contacts`; `← Settings` returns.
5. Contact settings shows only contact layouts: default first, then by label, with the
   count in the header. Set default, duplicate, delete (`ConfirmDialog`), the 20-layout
   limit and the per-group error banner all behave as before.
6. Collapsing Layouts on Contact settings leaves it expanded on Company settings.
   New layout stays usable while the section is collapsed.
7. A row opens `/settings/contacts/layouts/:id`. The editor is unchanged apart from
   `← Contact settings`.
8. Edit, then Cancel or Back: "Discard unsaved changes?". Confirming lands on
   `/settings/contacts`.
9. New layout opens `/settings/contacts/layouts/new`. Save replaces it with `/:id`, and
   Back goes to `/settings/contacts`. Duplicate opens `/new?from=<id>` and saves the
   same way.
10. `/settings/companies/layouts/<a contact layout id>` shows "This layout doesn't
    exist…" with `← Company settings`. The same goes for `/new?from=<that id>`.
11. `/settings/foo` and `/settings/layouts` show "This settings page doesn't exist." with
    `← Settings`. `/settings/contacts/layouts` redirects to `/settings/contacts`.
12. In a private window with site data blocked, sections start expanded, toggle, and
    nothing appears in the console.
13. Editing the default layout still shows on `/contacts/:id` after Save (the schema
    bundle is invalidated, as before).

---

## 15. Build order

1. `SettingsSection` (+ storage helpers) and `features/settings/paths.ts` with tests.
2. Rewrite `layouts/paths.ts`; point `LayoutEditor`, `EditorMessage`, `LayoutEditorPage`
   and `NewLayoutPage` at the entity (§8).
3. `LayoutsSection` from `LayoutGroup`; `EntitySettingsPage`; CSS clean-up.
4. `router.tsx`: new routes in, `settings/layouts*` out; delete `LayoutSettingsPage` and
   `LayoutGroup`.
5. `SettingsPage` sections (§5).
6. `npm run build` (type check), `npm test`, then the browser checklist (§14).
7. Docs: fold this into `Architecture/settings.md`. That means §2 (IA), §3 (shell: add
   `SettingsSection`, and drop "no nested navigation" from §3.2), §5 (the Entity layouts
   page becomes *Entity settings page → Layouts section*), §6.1 and §6.6 (routes, back
   link, post-create move), §7 (files) and §9 (verified by hand). Also add "give it a
   settings card in a `SECTIONS` section" to the new-entity recipe in
   `Architecture/frontend-entity-pages.md` §8. Then delete this file.

---

## 16. Extension recipes (after this lands)

| To add | Do |
|---|---|
| A section on the overview (e.g. *Users & access*) | one entry in `SECTIONS`, its cards, their routes (static, so they outrank `:entity`) |
| A card in an existing section | one `SettingsCardProps` in that section's `cards` |
| Moving an entity to another section | move its `entitySettingsCard(...)` between `cards` lists; its URL doesn't change |
| A section on every entity page (e.g. *Fields*, for custom fields) | a component rendering `SettingsSection` with `storageKey={`${segment}/fields`}`, listed in `EntitySettingsPage`; update the card description |
| A new standard entity | its `ENTITIES` entry (as today) + `entitySettingsCard(...)` in the section it belongs to; routes and pages are generic |
| Custom objects (`WIP/custom-objects-architecture.md`) | `entityFromSettingsSegment` also consults the object catalog; an *Objects* overview section lists them. The entity page and Layouts section need no change, since they're driven by the config they're given |
