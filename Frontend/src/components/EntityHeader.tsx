import type React from "react";
import type { ComponentType } from "react";
import { Link } from "react-router";
import { Button } from "@/components/ui";
import styles from "./EntityHeader.module.css";

type IconProps = { className?: string; size?: number };

export type EntityHeaderProps = {
  /** Entity glyph (from lib/entity-icons). */
  icon: ComponentType<IconProps>;
  /** Main heading — company name, contact full name, etc. */
  title: string;
  /** Secondary line: a few key values, rendered with · separators. Falsy
   *  entries should be filtered out by the caller. */
  subtitle?: React.ReactNode[];
  backLink: { to: string; label: string };
  /** When true, the Edit button is hidden (the inline editor's Save/Cancel bar
   *  is showing instead). */
  isEditing: boolean;
  onEdit: () => void;
  onDelete: () => void;
  /** Optional override for the action buttons. When provided it replaces the
   *  default Edit/Delete pair — the escape hatch for entities that grow extra
   *  actions. */
  actions?: React.ReactNode;
};

// Presentational page header shared by entity detail pages. Owns no state: it
// renders the data it's given and calls back on Edit/Delete. The page keeps the
// form/edit state and the delete side effects.
export function EntityHeader({
  icon: Icon,
  title,
  subtitle,
  backLink,
  isEditing,
  onEdit,
  onDelete,
  actions,
}: EntityHeaderProps) {
  const subtitleItems = subtitle?.filter(Boolean) ?? [];

  return (
    <div className={styles.header}>
      <Link to={backLink.to} className={styles.backLink}>← Back to {backLink.label}</Link>

      <div className={styles.titleRow}>
        <Icon className={styles.titleIcon} size={28} />
        <h1 className={styles.title}>{title}</h1>

        <div className={styles.actions}>
          {actions ?? (
            <>
              {!isEditing && (
                <Button variant="secondary" size="sm" onClick={onEdit}>Edit</Button>
              )}
              <Button variant="danger" size="sm" onClick={onDelete}>Delete</Button>
            </>
          )}
        </div>
      </div>

      {subtitleItems.length > 0 && (
        <div className={styles.subtitle}>
          {subtitleItems.map((item, i) => (
            <span key={i} className={styles.subtitleItem}>{item}</span>
          ))}
        </div>
      )}
    </div>
  );
}

export default EntityHeader;
