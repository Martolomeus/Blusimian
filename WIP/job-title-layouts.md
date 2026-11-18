# Job Titles & Layout Assignment — Spec

**Status:** Proposal, nothing implemented · **Scope:** a tenant-defined list of job titles, a job title on each user, and assigning entity layouts per job title
**Date:** 2026-10-31 · **Builds on (as-built):** `Architecture/entity-layouts.md`, `Architecture/multitenancy.md`, `Architecture/settings.md`
**Depends on:** `Architecture/roles-and-permissions.md` (built). This spec adds to its Users pages, `PUT /api/users/{id}` and `manage_job_titles`.
**Code:** new package `jobTitles`; `users`, `entityLayouts`, `entitySchema`, `tenants`, `V1__init.sql`; `Frontend/src/features/settings`, new `Frontend/src/lib/jobTitles`

> **This replaces role-based layout assignment.** `entity-layouts.md` and `settings.md`
> planned to assign layouts to roles. Instead, a user has a **job title**, picked from a
> list each tenant defines, and each job title can name one layout per entity. A user
> sees the layout assigned to their job title, or the entity's default when there is
> none.
>
> Roles decide what a user **may do**. Job titles decide how the screen **is arranged**.
> The two never reference each other.
>
> The schema bundle's contract doesn't change: it still returns exactly one layout, chosen
> by the server. Record pages need no change.

---

## 1. Decisions at a glance

| # | Decision | Section |
|---|---|---|
| D1 | Layouts are assigned by job title. The role-based plan is dropped. | §2 |
| D2 | Job titles get their own table, `app.job_titles`, not a generic picklist table. | §3 |
| D3 | `users.job_title_id` is nullable, with a composite FK. No title means the default layouts. | §4.2 |
| D4 | `app.job_title_layouts` holds one row per title and entity. No row means that entity's default. | §4.3 |
| D5 | The foreign key to the layout includes `entity_type`, so a company slot can't hold a contact layout. | §4.3 |
| D6 | The caller's title is read fresh on each resolve, never from the session. `resolve` takes the user id. A separate `defaultLayout` serves code with no caller. | §5 |
| D7 | A title in use can't be deleted (409). A deleted layout's assignments cascade, and its users fall back to the default. | §4.4 |
| D8 | One write: `PUT /api/job-titles/{id}` takes the name and all assignments, in one transaction. | §7 |
| D9 | Layouts are assigned from the job title's page. The Layouts section shows "Assigned to" read-only. One control per job. | §8 |
| D10 | A new tenant has no job titles. Everyone sees the default layouts until an admin sets some up. | §4.5 |

---

## 2. Why job title rather than role

The earlier plan (`entity-layouts.md` §5.4) took "the caller's role". That worked when a user was expected to have one role. With the permissions spec a user can hold several, which breaks the plan: a user with Sales and Support could match two company layouts, and the bundle must return one. Role-based assignment would need a precedence rule, and an admin who could reason about it.

| | By role | By job title |
|---|---|---|
| Values per user | Many | **One**, so exactly one layout with no tie-break |
| Meaning | Security | Presentation, the existing rule: "a layout is presentation, not permission" |
| Change what someone sees | Changes what they may do too | Touches only presentation |
| Who the admin thinks of | A permission set | "Our account managers" |

**What this costs.** Two people with the same title can't have different layouts, and a tenant with very fine-grained titles must map each one, or those people get the default. Both are acceptable: an unmapped title is harmless, and a per-user override is additive if it's ever needed (§10).

---

## 3. A dedicated table, not a generic picklist

The brief left this open. A generic `picklists` + `picklist_values` pair looks reusable, but a job title isn't only a list of labels. Other rows point at it.

| Need | `app.job_titles` | Generic `picklist_values` |
|---|---|---|
| `users.job_title_id` can only hold a job title | The FK guarantees it | The FK proves only "some picklist value". A user's title could be an Industry value, unless every referencing table also carries a discriminator in its FK |
| Layout assignments hang off the value | The FK guarantees it | The same gap |
| Rename without touching users | Yes, references are by id | Yes |
| Unique within the list | `(tenant_id, name)` | `(tenant_id, picklist_id, label)` |
| Serves custom picklist fields | No | **Built separately.** Picklists are `app.picklist_values`, keyed by entity type and field key with no parent table, not the `config` column (`Architecture/picklists.md`). A slot can't carry an FK (`custom-objects-architecture.md` §14) |
| Serves `Company.type`, `industry` | No | They're picklists now, in `app.picklist_values` (`picklists.md` §8). Job titles still keep their own table: other rows point at them (`picklists.md` §2.2) |

So a generic table's one real, FK-backed consumer would be job titles, and they'd lose integrity in exchange. This is the "one true lookup table" trade, and the codebase has consistently chosen the opposite (`ck_cfd_type_matches_slot`, composite FKs: "unrepresentable, not merely discouraged").

Picklists have since been built with their own design (`Architecture/picklists.md`), and that spec reaches the same rule: anything other rows point at gets its own table.

---

## 4. Data model

Same recipe as everything else (`multitenancy.md` §17), in `V1__init.sql`. After editing V1, run `make db-reset`.

```
app.job_titles ──< app.users            (a user has at most one title)
      │
      └──< app.job_title_layouts >── app.entity_layouts
             one per title and entity
```

### 4.1 `app.job_titles`

```sql
create table app.job_titles
(
    id         uuid         not null default gen_random_uuid(),
    tenant_id  uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    name       varchar(100) collate app.case_insensitive not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),

    constraint pk_job_titles primary key (tenant_id, id),
    constraint uq_job_titles_name unique (tenant_id, name)
);
```

**Goes before `app.users` in V1**, because `users` references it. It needs only `app.tenants`.

Listed by name, so there's no `sort_order` and no reorder UI. An archive column isn't needed: a title that's no longer used can simply be deleted once nobody holds it (§4.4).

### 4.2 `app.users`

```sql
    job_title_id uuid,   -- NULL: no title, so the default layouts

    constraint fk_users_job_title_id foreign key (tenant_id, job_title_id)
        references app.job_titles (tenant_id, id) on delete restrict
```

```sql
create index idx_users_job_title on app.users (tenant_id, job_title_id) where job_title_id is not null;
```

The index serves the in-use count and the FK check when a title is deleted. With MATCH SIMPLE, a null `job_title_id` skips the FK. `app.find_login_user` doesn't change.

`User` maps it as a plain `UUID jobTitleId`, the convention `Deal.customerId` uses.

### 4.3 `app.job_title_layouts`

```sql
create table app.job_title_layouts
(
    id           uuid        not null default gen_random_uuid(),
    tenant_id    uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    job_title_id uuid        not null,
    entity_type  varchar(50) not null,
    layout_id    uuid        not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),

    constraint pk_job_title_layouts primary key (tenant_id, id),
    constraint uq_job_title_layouts unique (tenant_id, job_title_id, entity_type),
    constraint fk_job_title_layouts_job_title_id foreign key (tenant_id, job_title_id)
        references app.job_titles (tenant_id, id) on delete cascade,
    -- entity_type is in the key so a company row can only name a company layout.
    constraint fk_job_title_layouts_layout_id foreign key (tenant_id, layout_id, entity_type)
        references app.entity_layouts (tenant_id, id, entity_type) on delete cascade
);

create index idx_job_title_layouts_layout on app.job_title_layouts (tenant_id, layout_id);
```

This needs one new constraint on `app.entity_layouts`, which looks redundant, so it gets a comment saying why it's there:

```sql
    -- Target of fk_job_title_layouts_layout_id, not a real uniqueness rule: id is already unique.
    constraint uq_entity_layouts_type unique (tenant_id, id, entity_type)
```

- **No row means the default.** So the default stays a fallback, not a value, and changing an entity's default moves every unmapped title at once.
- **`entity_type` needs no CHECK.** The FK already limits it to values in `entity_layouts`, so a new entity type has one fewer place to update.
- **Assigning the layout that is currently the default is allowed.** It pins that title to it: if the default changes later, that title stays. Choosing "Use the default" stores no row.

### 4.4 Deleting things

| Delete | Result | Why |
|---|---|---|
| A job title with users | **409** "3 users have this job title." | A user's title would silently vanish. Same rule as a held role |
| A job title with none | Deleted, its assignments cascade | They're config owned by the title |
| An assigned layout | Deleted, its assignments cascade, its users see the default | The default always exists, so it's safe, and the dialog names who's affected (§8.4) |
| The default layout | 409, as today | Unchanged |

The title rule differs from the layout rule on purpose. A title is an attribute on people that someone has to replace by hand. A layout assignment has a safe fallback.

### 4.5 Provisioning

A new tenant gets no job titles, and `users.job_title_id` starts null. Every user sees the default layouts, exactly as today. There's no sensible default list, and an empty list is a working state.

`DemoTenantSeeder` adds, for Acme only: an *Account Manager* title, a compact company layout assigned to it, and a second user `carol` / `carol` holding that title and the Standard user role. Then `alice` and `carol` show different company pages by hand, the same way the demo tenants make isolation visible. The seeder's import of `roles` is dev-only, like its existing import of `entityLayouts`.

---

## 5. Resolution

`EntityLayoutService.resolve` is the seam `entity-layouts.md` §5.4 set aside. It gains the user:

```java
/** The layout for this user: their job title's, else the entity's default. */
@Transactional(readOnly = true)
public Optional<EntityLayoutResponse> resolve(UUID userId, EntityType entityType) {
    return users.jobTitleIdOf(userId)
            .flatMap(title -> assignments.findByJobTitleIdAndEntityType(title, entityType))
            .flatMap(assignment -> repository.findById(assignment.getLayoutId()))
            .or(() -> repository.findByEntityTypeAndIsDefaultTrue(entityType))
            .map(EntityLayoutResponse::from);
}

/** The entity's default layout: for code with no caller. */
@Transactional(readOnly = true)
public Optional<EntityLayoutResponse> defaultLayout(EntityType entityType) {
    return repository.findByEntityTypeAndIsDefaultTrue(entityType).map(EntityLayoutResponse::from);
}
```

- **At most three selects, each by key.** The bundle is fetched once per entity type and revalidated, so this is cheap.
- **The title is read fresh** rather than kept on `TenantUserPrincipal`. Sessions persist across restarts, so a principal's copy would keep showing the old layout until the next login.
- **The user comes from the controller, not from a static lookup:** `EntitySchemaController` takes `@AuthenticationPrincipal TenantPrincipal principal` and calls `service.getSchema(principal.userId(), entityType)`. Services stay free of the security context, as `AuthController.me` already keeps it at the edge.
- **`defaultLayout` is needed** because `DemoTenantSeeder` runs with no user. It also keeps the isolation test's intent clear.

| Call site of `resolve` today | Becomes |
|---|---|
| `EntitySchemaService.java:69` | `resolve(userId, entityType)` |
| `DemoTenantSeeder.java:73` | `defaultLayout(EntityType.COMPANY)` |
| `TenantIsolationTest.java:204, 210, 211` | `defaultLayout(EntityType.COMPANY)` |
| `EntitySchemaServiceTest.java:70, 109` | `resolve(USER_ID, EntityType.COMPANY)` |

### Caching

Nothing in `EntitySchemaController` needs to change:

- **ETag:** derived from content, so two users with different layouts get different ETags. A second user on the same browser sends the first's `If-None-Match`, it doesn't match, and they get a 200.
- **`Cache-Control: no-cache, private`** already assumes the bundle varies by caller.
- **`entitySchemaQuery` has a 5-minute `staleTime`.** Writes on the admin's own browser invalidate `["entity-schema"]` (§8.5). Anyone else signed in sees the change within 5 minutes, the same delay as a layout edit today.

---

## 6. Backend

### 6.1 Package and dependency direction

New package `com.blusimian.blusimian.jobTitles`, with `dto/` and `exception/`.

```
users         ──► platform                               (a plain UUID jobTitleId; no new import)
jobTitles     ──► entityModel users platform             ← NEW
entityLayouts ──► entityModel jobTitles users platform   ← gains two edges
entitySchema  ──► entityModel entityLayouts              (unchanged)
```

All one-way. `jobTitles` never reads layouts: whether a layout fits is the FK's job, translated in the service, as `DealService` does for a customer. `users` never reads titles: an unknown title is the same kind of FK translation.

| File | Role |
|---|---|
| `JobTitle`, `JobTitleRepository` | The row. `JobTitle.create(name)`, `rename`. |
| `JobTitleLayout`, `JobTitleLayoutRepository` | One assignment: `jobTitleId`, `entityType`, `layoutId`, all plain values. `findByJobTitleIdAndEntityType`, `findByJobTitleId`, `findAll`. |
| `JobTitleService`, `JobTitleController` | §6.2, §7. |
| `exception/JobTitleNotFoundException` | → 404. |
| `exception/JobTitleNameTakenException`, `JobTitleInUseException` | → 409. |
| `exception/InvalidLayoutAssignmentException` | → 400, from `fk_job_title_layouts_layout_id`. |
| `users/exception/InvalidJobTitleException` | → 400, from `fk_users_job_title_id`. |

### 6.2 `JobTitleService`

| Method | Rules |
|---|---|
| `list()` | Titles by name, with `userCount` and `layouts`. Three queries (titles, one grouped user count, all assignments), so no N+1. |
| `create(name)` | Stripped; `uq_job_titles_name` → 409. |
| `replace(id, request)` | Renames, then applies the assignments (below). 404 if missing. |
| `delete(id)` | 409 with the count if held; the FK is the backstop for a race. |

**Applying assignments updates rows in place.** For each `EntityType`: a row exists and a layout is given → `setLayoutId`; a row exists and none is given → delete; no row and a layout is given → insert. Deleting all and reinserting would run into the trap from `entity-layouts.md` §5.6: Hibernate flushes inserts before deletes, and the insert would break `uq_job_title_layouts`.

`replace` translates `DataIntegrityViolationException` by constraint name, after `saveAndFlush`, as `UserService.create` does.

### 6.3 `users`

`UpdateUserRequest` from the permissions spec gains the title. `null` clears it, and the whole-body `PUT` is why that's possible with a plain record:

```java
public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String displayName,
        UUID jobTitleId
) {
}
```

`User.changeJobTitle(UUID)`, `UserResponse` gains `jobTitleId`, and `UserService` gains:

```java
@Transactional(readOnly = true)
public Optional<UUID> jobTitleIdOf(UUID userId) {
    return repository.findById(userId).map(User::getJobTitleId);
}

long countByJobTitleId(UUID jobTitleId);   // on UserRepository
```

---

## 7. HTTP API

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/job-titles` | 200 `[JobTitleResponse]`, by name | — |
| POST | `/api/job-titles` | 201 `JobTitleResponse` | 400; 409 name taken |
| PUT | `/api/job-titles/{id}` | 200 `JobTitleResponse` | 400; 404; 409 name taken |
| DELETE | `/api/job-titles/{id}` | 204 | 404; 409 in use |
| PUT | `/api/users/{id}` | 200 `UserResponse` | 400 unknown title; 404 |

There's no get-by-id. The list is small and both pages read from it, the same reasoning as `settings.md` §4.

```jsonc
// GET /api/job-titles
[
  {
    "id": "…",
    "name": "Account Manager",
    "userCount": 4,
    "layouts": { "COMPANY": "5c8e1f04-…" }     // an entity missing here uses its default
  }
]

// POST /api/job-titles
{ "name": "Account Manager" }

// PUT /api/job-titles/{id}: the whole document. A missing or null entity uses its default.
{ "name": "Account Manager", "layouts": { "COMPANY": "5c8e1f04-…", "DEAL": null } }
```

`PUT` here treats a missing entity as "use the default", which differs from the role document, where a missing field is left alone. A role document can lag behind custom fields created while it's open. This one has a fixed set of keys, so a missing one can only mean "none".

| Rule | Status | `field` / message |
|---|---|---|
| name not blank, at most 100 | 400 | `name`: "Required" / "At most 100 characters" |
| name unique, case-insensitive | 409 | "A job title named 'Support' already exists" |
| layout exists and belongs to the entity | 400 | "That layout doesn't belong to this entity" |
| title unused, on delete | 409 | "3 users have this job title" |

The schema endpoint's contract is unchanged.

---

## 8. Settings UI

### 8.1 Information architecture

```
/settings                 ▾ General: [Roles] [Users] [Job titles]   ← one new card
/settings/job-titles      Job titles list
/settings/job-titles/:id  Job title page
```

`job-titles` is a static segment and no entity's plural. `lib/icons.tsx` gains a `BadgeIcon`.

```ts
{ title: "Job titles", description: "Your organisation's job titles and the layouts they show.", to: JOB_TITLES_PATH, icon: BadgeIcon },
```

### 8.2 Job titles list

A title, an intro, a New job title button, and a `.list-table`: Name (link), Users, then one column per entity in `ENTITIES` order, showing the assigned layout's label or a muted `Default`.

- **New job title** opens a `Modal` with a name input. Saving stores it and goes to its page. A dialog suits this one, unlike layouts: the name is all a title needs, and a title with no assignments behaves like no title.
- **Delete** uses `ConfirmDialog`, and is disabled while `userCount > 0`, with the reason as its title.
- The empty state explains itself: "No job titles yet. Without one, everyone sees the default layouts."

### 8.3 Job title page

```
← Job titles
[Account Manager            ] (title-sized input)                  Cancel   [Save]
4 users

▾ Layouts
   People with this job title see these layouts.
   Contacts    [Use the default (Default)      ▾]
   Companies   [Sales (compact)                ▾]
   Deals       [Use the default (Default)      ▾]
```

One `Select` per entity: *Use the default (\<its label\>)* first, then every layout of that entity by label. One Save writes the name and all three in one `PUT`. It uses `useUnsavedChangesGuard` from the permissions spec. A plain `useState` draft is enough; there's no reducer to justify here.

### 8.4 Layouts section

Three changes to `LayoutsSection.tsx`:

| Where | Change |
|---|---|
| Section `description` | "People see the layout assigned to their job title, or the default if they have none." |
| Row | A muted "Assigned to" list after the summary: `Account Manager, Support`. Derived on the page from `useJobTitles()`; the layout DTOs don't change. |
| Delete dialog | When assigned: "Delete layout ‘Sales’? Account Manager and Support will go back to the default layout. This can't be undone." |

It's read-only. To change an assignment, you go to the job title (D9).

### 8.5 Users pages

- **Users list:** a Job title column, after Name.
- **User page:** a Job title `Select` in General, with *None* first, saved by the same Save as the display name.

`lib/jobTitles/jobTitles.ts`: `useJobTitles`, `useCreateJobTitle`, `useUpdateJobTitle`, `useDeleteJobTitle`. After a title or user save, invalidate `["entity-schema"]`, so an admin who changes their own title sees it at once. A layout delete also invalidates `["job-titles"]`.

---

## 9. Architectural integrity analysis

Each DDL claim here was run; §11.1 says how and what wasn't covered.

### 9.1 What holds up

**The contract doesn't move.** `resolve` was built as this seam, and the bundle still carries exactly one layout. `EntitySchemaResponse`, its ETag and every record page stay as they are. The change is contained in one method and its callers.

**Single-valued beats multi-valued here.** The hardest problem with role-based assignment, precedence, doesn't exist.

**Bad states are unrepresentable, not validated.** Tested against the real schema:

| Attempt | Stopped by |
|---|---|
| A contact layout in a company slot | `fk_job_title_layouts_layout_id` (composite with `entity_type`) |
| Two layouts for one title and entity | `uq_job_title_layouts` |
| Another tenant's title on a user | `fk_users_job_title_id` |
| Another tenant's layout on a title | `fk_job_title_layouts_layout_id` |
| Changing an assigned layout's entity type | The same FK |
| Deleting a title in use | `fk_users_job_title_id` |

**The fallback is total.** Every path ends at the default: no title, no row, or a layout deleted mid-request. And every entity always has a default (`entity-layouts.md` §5.6), so `layout` stays non-null for a provisioned tenant.

### 9.2 Where job titles and roles meet

They share no table and no key, which is the point. They still meet in the schema bundle, once enforcement exists:

| Situation | Outcome | Needs work? |
|---|---|---|
| A layout places a field the user's role can't read | The bundle's `fields` loses it, and `EntitySchemaService` already drops placements with no field (`entity-layouts.md` §5.3) | No. Works as built |
| Every field in a section is hidden that way | An empty section, which the read path already skips | No |
| A role with Create meets a required field on a layout | Every layout places it, and the role always holds Read and Edit on it (permissions spec §3.8), so the create form can always be completed | No |
| A role without Create has a required field closed | The field drops out of the bundle like any other, but `RecordMapper` must stop demanding it on update (permissions spec §6.4) | Yes, at enforcement |
| A title's layout is on an entity the user can't read | The layout is served, and records return 403 | No. A layout carries no record data |
| Two users share a layout but not roles | The same arrangement with different visible fields, so different ETags | No. The ETag follows content |

So the layer ordering is: **job title picks the arrangement, then roles remove what isn't allowed.** That's why `entity-layouts.md` §1's rule that a layout is never permission is worth keeping.

The first row will be common, because every permission defaults to `false`: a new optional custom field placed on a layout stays hidden from everyone but Administrator until a role is granted it. An admin who adds a field to a layout and sees it missing for a colleague needs to look at the role, not the layout.

### 9.3 Risks that remain

| Risk | Severity | Note |
|---|---|---|
| **`uq_entity_layouts_type` looks removable.** It restates what the primary key already implies, so a future clean-up could drop it, and the FK with it. | Low | The migration would fail loudly. The comment in §4.3 says why it exists. |
| **Custom objects will reshape `job_title_layouts`.** `entity_type` is one of the columns `custom-objects-architecture.md` §4 means to replace with `standard_object`, `custom_object_id` and `object_key`. | Medium, later | Add this table to that spec's Phase 0 list. The composite FK target moves to `(tenant_id, id, object_key)`; the shape holds. |
| **Same title, same layout.** No per-person exception. | Low | §10: additive when wanted. |
| **Assignments reach other sessions within 5 minutes,** not immediately. | Low | The same as a layout edit today. |
| **Anyone signed in can change titles and assignments** until enforcement ships. | Medium | Same as layouts today. `manage_job_titles` is ready for it. |
| **A layout saved before a required custom field existed** is short of it, and is now served to real users, not only kept (`entity-layouts.md` §9). | Low | The rule is unchanged, but more non-default layouts will now be live. The layout list could flag them. |

---

## 10. Considered and left out

| Left out | Why |
|---|---|
| A generic picklist table | §3 |
| A per-user layout override | No need yet. A `user_layout_overrides` table checked first in `resolve` is additive. |
| Assigning from the layout row as well | Two controls for one job. `settings.md` §6.3's rule. |
| `sort_order` on titles | Alphabetical serves a picklist. |
| Archiving titles | Delete covers it; nothing needs the history. |
| Replace-with on delete ("move these users to…") | Worth having eventually. For now the Users list shows who holds it. |
| A title on `TenantUserPrincipal` or `/api/auth/me` | Stale for the session (§5), and nothing needs it. |
| Default job titles at provisioning | No universal list exists. |
| A preview-as-title on the layouts page | Not asked for. Log in as a demo user. |
| A limit on titles | Protects nothing. |

---

## 11. Verification

### 11.1 Already run (2026-10-31)

Part of the same run as the permissions spec (§9.1 there, including its limits: PGlite on Postgres 17.5, no ICU, no Java compiled): V1 applied with `job_titles` ahead of `users`, then exercised as `blusimian_app` under RLS.

- every row of the table in §9.1, each rejected by the constraint named;
- the same title name in two tenants is allowed, and a duplicate within one isn't;
- the matching assignment is accepted, and resolves through the user's title with a join;
- a layout delete cascades its assignments; a title delete cascades its assignments;
- startup checks C3–C5 pass on the new tables.

### 11.2 Tests to add

| Test | Kind | Covers |
|---|---|---|
| `EntityLayoutResolveTest` | unit, mocks | Title with a row → that layout. Title without → default. No title → default. No default → empty. |
| `EntitySchemaServiceTest`, updated | unit | Passes the user through; bundle otherwise unchanged. |
| `JobTitleServiceTest` | unit, mocks | `replace` updates, inserts and deletes per entity, and never deletes then reinserts one key. |
| `TenantIsolationTest`, extended | integration | Another tenant's title on a user is a 400. Another tenant's layout on a title is a 400. Titles list per tenant. Two users in one tenant, different titles, get different bundles and ETags through MockMvc. |
| Case-insensitive names | integration | `Support` and `SUPPORT` clash (not covered by §11.1). |

### 11.3 By hand

1. A fresh tenant: Job titles is empty and explains itself, and record pages show the default layouts.
2. Create a title, assign a company layout, and give it to `carol`. Log in as `carol`: Companies shows that layout. Log in as `alice`: the default.
3. As `carol`, Contacts shows the default (no row for that entity).
4. The company layout's row says "Assigned to Account Manager".
5. Change the assignment to "Use the default": `carol` sees the default within 5 minutes, or at once on a page reload.
6. Delete a title with users: disabled. Clear it from the user: enabled, and it deletes.
7. Delete an assigned layout: the dialog names the title, and `carol` falls back to the default.
8. Rename a title: every user keeps it. `Support` and `SUPPORT` clash with a clear error.
9. Change the entity's default: unmapped titles follow it, pinned ones don't.
10. Log out and in as another user on the same browser: no layout carries over.

---

## 12. Build order

1. **Schema.** `job_titles`, `users.job_title_id`, `uq_entity_layouts_type`, `job_title_layouts`; `make db-reset`.
2. **`jobTitles` slice.** Entities, service, controller, tests.
3. **`users`.** `jobTitleId`, the extended `PUT`, `jobTitleIdOf`, `InvalidJobTitleException`.
4. **Resolution.** `resolve(userId, entityType)`, `defaultLayout`, `EntitySchemaController` and `EntitySchemaService`; update the five test call sites. At this point the feature works over HTTP.
5. **Demo data.** Acme's title, layout and `carol`.
6. **Frontend.** `lib/jobTitles`, the two pages, the General card, the Users changes, `LayoutsSection`.
7. `./mvnw test`, `npm run build`, `npm test`, the §11.3 checklist.
8. **Docs:** §13, then delete this file.

---

## 13. Docs and comments to update

Everything that still promises role-based assignment:

| Where | Change |
|---|---|
| `Architecture/entity-layouts.md` | Status line. §2: `job_title_layouts`, `uq_entity_layouts_type`. §5.4: rewritten around this spec. §5.5: "by role later" → by job title. §9: role assignment comes off "Not built". §4: the dependency graph |
| `Architecture/settings.md` | §1 and §5: the "only the default layout is shown" text and its `Assigned to` note. §8: the admin rule becomes `manage_layouts`. The job title pages |
| `Architecture/multitenancy.md` | §5.7: the new tables and the `users` FK. §18.3: no longer "role-based layout assignment" |
| `WIP/custom-objects-architecture.md` | Phase 0's list gains `job_title_layouts` |
| `EntityLayoutService.java:55-61` | `resolve` Javadoc |
| `EntitySchemaController.java:37` | "per caller (role, later tenant)" |
| `LayoutsSection.tsx:62-63` | The comment and the description |
| `V1__init.sql`, the `app.entity_layouts` header | "assignment rules (per profile / record type) are a future concern" |

---

## 14. Extension recipes (after this lands)

| To add | Do |
|---|---|
| A new entity | Nothing here: the columns and rows are generic. `ENTITIES` drives the page's `Select`s |
| Custom object layouts | §9.3: with `custom-objects-architecture.md` Phase 0 |
| A per-user override | A table keyed by user and entity, tried first in `resolve` |
| Replace-with on title delete | A body on `DELETE`, moving users in the same transaction |
| More attributes on a title | Columns on `job_titles`. The dedicated table is what makes this possible (§3) |
