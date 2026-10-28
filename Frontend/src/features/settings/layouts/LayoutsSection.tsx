import { useState } from "react";
import { Link, useNavigate } from "react-router";
import clsx from "clsx";
import { SettingsSection } from "@/components/settings/SettingsSection";
import { Button, ConfirmDialog } from "@/components/ui";
import type { EntityConfig } from "@/lib/entity/entityConfig";
import { CopyIcon, PlusIcon, StarIcon, StarOutlineIcon, TrashIcon } from "@/lib/icons";
import { MAX_LAYOUTS_PER_ENTITY, useDeleteLayout, useLayoutList, useSetDefaultLayout } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { settingsSegment } from "../paths";
import { layoutPath, newLayoutPath } from "./paths";
import styles from "./layouts.module.css";

function count(n: number, noun: string): string {
  return `${n} ${noun}${n === 1 ? "" : "s"}`;
}

function summary(layout: EntityLayout): string {
  const fields = layout.sections.reduce((total, section) => total + section.fields.length, 0);
  return `${count(layout.sections.length, "section")} · ${count(fields, "field")}`;
}

// The Layouts section of an entity's settings page: default first, then by label, each
// row opening the editor. New layout and Duplicate open the editor on a layout that isn't
// stored yet. Each mutation disables only its own control while it runs, and its error
// shows at the top of the section until the next action.
export function LayoutsSection({ entity }: { entity: EntityConfig }) {
  const navigate = useNavigate();
  const { data: layouts, isLoading, error: loadError } = useLayoutList(entity.type);
  const setDefault = useSetDefaultLayout();
  const remove = useDeleteLayout();
  const [error, setError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<EntityLayout | null>(null);

  const rows = [...(layouts ?? [])].sort(
    (a, b) => Number(b.isDefault) - Number(a.isDefault) || a.label.localeCompare(b.label),
  );
  const atLimit = rows.length >= MAX_LAYOUTS_PER_ENTITY;
  const limitTitle = `A ${entity.singular.toLowerCase()} can have at most ${MAX_LAYOUTS_PER_ENTITY} layouts. Delete one to add another.`;

  function handleSetDefault(layout: EntityLayout) {
    setError(null);
    setDefault.mutate(layout, { onError: (err) => setError(err.message) });
  }

  function handleDelete() {
    if (!toDelete) return;
    remove.mutate(toDelete, {
      onSuccess: () => setToDelete(null),
      onError: (err) => {
        setToDelete(null);
        setError(err.message);
      },
    });
  }

  return (
    <SettingsSection
      storageKey={`${settingsSegment(entity.type)}/layouts`}
      title="Layouts"
      count={layouts?.length}
      // Until layouts can be assigned to roles; then rows gain an "Assigned to" column.
      description={`Only the default layout is shown on ${entity.singular.toLowerCase()} pages today. Other layouts are kept for when layouts can be assigned to roles.`}
      actions={
        layouts && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => navigate(newLayoutPath(entity.type))}
            disabled={atLimit}
            title={atLimit ? limitTitle : undefined}
          >
            <PlusIcon size={16} />
            New layout
          </Button>
        )
      }
    >
      {isLoading && <p className="page-empty">Loading…</p>}
      {loadError && <p className="page-error">{loadError.message}</p>}

      {error && (
        <div className={clsx(styles.errorBanner, styles.listError)} role="alert">
          {error}
        </div>
      )}

      {!layouts ? null : rows.length === 0 ? (
        <p className={clsx("page-empty", styles.empty)}>No layouts yet.</p>
      ) : (
        <ul className={styles.rows}>
          {rows.map((layout) => (
            <li key={layout.id} className={styles.row} onClick={() => navigate(layoutPath(entity.type, layout.id))}>
              <Link
                to={layoutPath(entity.type, layout.id)}
                className={clsx("record-name-link", styles.rowLabel)}
                onClick={(e) => e.stopPropagation()}
              >
                {layout.label}
              </Link>
              <span>
                {layout.isDefault && (
                  <span className={clsx(styles.badge, styles.badgeDefault)}>
                    <StarIcon size={12} />
                    Default
                  </span>
                )}
              </span>
              <span className={styles.rowMeta}>{summary(layout)}</span>
              {/* Clicks here never open the editor, even on a disabled button. */}
              <div className={styles.rowActions} onClick={(e) => e.stopPropagation()}>
                {!layout.isDefault && (
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Set ${layout.label} as default`}
                    title="Set as default"
                    onClick={() => handleSetDefault(layout)}
                    disabled={setDefault.isPending && setDefault.variables?.id === layout.id}
                  >
                    <StarOutlineIcon size={18} />
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Duplicate ${layout.label}`}
                  title={atLimit ? limitTitle : "Duplicate"}
                  onClick={() => navigate(newLayoutPath(entity.type, layout.id))}
                  disabled={atLimit}
                >
                  <CopyIcon size={18} />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Delete ${layout.label}`}
                  title={
                    layout.isDefault
                      ? "The default layout can't be deleted. Make another layout the default first."
                      : "Delete"
                  }
                  onClick={() => {
                    setError(null);
                    setToDelete(layout);
                  }}
                  disabled={layout.isDefault}
                >
                  <TrashIcon size={18} />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="Delete layout"
        message={`Delete layout ‘${toDelete?.label}’? This can't be undone.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={remove.isPending}
        onConfirm={handleDelete}
        onCancel={() => setToDelete(null)}
      />
    </SettingsSection>
  );
}
