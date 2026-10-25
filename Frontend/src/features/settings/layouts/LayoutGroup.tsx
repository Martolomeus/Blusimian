import { useState } from "react";
import { Link, useNavigate } from "react-router";
import clsx from "clsx";
import { Button, ConfirmDialog } from "@/components/ui";
import type { EntityConfig } from "@/lib/entity/entityConfig";
import { CopyIcon, PlusIcon, StarIcon, StarOutlineIcon, TrashIcon } from "@/lib/icons";
import { MAX_LAYOUTS_PER_ENTITY, useDeleteLayout, useSetDefaultLayout } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { layoutPath, newLayoutPath } from "./paths";
import styles from "./layouts.module.css";

type LayoutGroupProps = {
  entity: EntityConfig;
  /** This entity's layouts, in any order. */
  layouts: EntityLayout[];
};

function count(n: number, noun: string): string {
  return `${n} ${noun}${n === 1 ? "" : "s"}`;
}

function summary(layout: EntityLayout): string {
  const fields = layout.sections.reduce((total, section) => total + section.fields.length, 0);
  return `${count(layout.sections.length, "section")} · ${count(fields, "field")}`;
}

// One entity's layouts on the settings list: default first, then by label, each row
// opening the editor. New layout and Duplicate open the editor on a layout that isn't
// stored yet. Each mutation disables only its own control while it runs, and its error
// shows under the group header until the next action.
export function LayoutGroup({ entity, layouts }: LayoutGroupProps) {
  const navigate = useNavigate();
  const setDefault = useSetDefaultLayout();
  const remove = useDeleteLayout();
  const [error, setError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<EntityLayout | null>(null);

  const rows = [...layouts].sort(
    (a, b) => Number(b.isDefault) - Number(a.isDefault) || a.label.localeCompare(b.label),
  );
  const atLimit = layouts.length >= MAX_LAYOUTS_PER_ENTITY;
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
    <section className={styles.group}>
      <header className={styles.groupHeader}>
        <entity.icon className={styles.groupIcon} size={20} />
        <h2 className={styles.groupTitle}>{entity.plural}</h2>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => navigate(newLayoutPath({ entity: entity.type }))}
          disabled={atLimit}
          title={atLimit ? limitTitle : undefined}
        >
          <PlusIcon size={16} />
          New layout
        </Button>
      </header>

      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
        </div>
      )}

      {rows.length === 0 ? (
        <p className={clsx("page-empty", styles.groupEmpty)}>No layouts yet.</p>
      ) : (
        <ul className={styles.rows}>
          {rows.map((layout) => (
            <li key={layout.id} className={styles.row} onClick={() => navigate(layoutPath(layout.id))}>
              <Link
                to={layoutPath(layout.id)}
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
                  onClick={() => navigate(newLayoutPath({ from: layout.id }))}
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
    </section>
  );
}
