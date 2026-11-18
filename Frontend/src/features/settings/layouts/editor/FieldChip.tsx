import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import clsx from "clsx";
import { Button } from "@/components/ui";
import type { FieldDataType, SchemaField } from "@/lib/entity/types";
import { CloseIcon, DragHandleIcon } from "@/lib/icons";
import { fieldDndId } from "./dndIds";
import styles from "../layouts.module.css";

const DATA_TYPE_LABELS: Record<FieldDataType, string> = {
  TEXT: "Text",
  LONGTEXT: "Long text",
  EMAIL: "Email",
  URL: "URL",
  PHONE: "Phone",
  PICKLIST: "Picklist",
  NUMBER: "Number",
  BOOLEAN: "Yes/No",
  DATE: "Date",
  REFERENCE: "Reference",
  COUNTRY: "Country",
};

type FieldSummaryProps = {
  fieldKey: string;
  /** Undefined only if the field left the dictionary mid-edit. */
  field: SchemaField | undefined;
};

/** What a field shows wherever it is -- canvas, palette, dragged: label, required mark, type. */
export function FieldSummary({ fieldKey, field }: FieldSummaryProps) {
  return (
    <>
      <span className={styles.fieldLabel}>{field?.label ?? fieldKey}</span>
      {field?.required && (
        <span className={styles.requiredMark} title="Required">
          <span aria-hidden>*</span>
          <span className="visually-hidden">(required)</span>
        </span>
      )}
      <span className={styles.badge}>{field ? (DATA_TYPE_LABELS[field.dataType] ?? field.dataType) : "Unknown"}</span>
    </>
  );
}

type FieldChipProps = FieldSummaryProps & {
  onRemove: () => void;
};

// A field placed on the canvas. The whole chip can be dragged with the pointer; the
// handle is the keyboard's way in (focus it, Space to lift). Keys pressed on the remove
// button never start a drag, because the handle is the registered activator.
export function FieldChip({ fieldKey, field, onRemove }: FieldChipProps) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: fieldDndId(fieldKey) });
  const label = field?.label ?? fieldKey;
  // Required fields can't come off a layout, so the main rule can't be broken from here.
  const required = field?.required ?? false;

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={clsx(styles.chip, isDragging && styles.chipPlaceholder)}
      {...listeners}
    >
      <button type="button" ref={setActivatorNodeRef} className={styles.handle} {...attributes} aria-label={`Move ${label}`}>
        <DragHandleIcon size={16} />
      </button>
      <FieldSummary fieldKey={fieldKey} field={field} />
      <Button
        variant="ghost"
        size="sm"
        className={styles.iconButton}
        aria-label={`Remove ${label}`}
        title={required ? "Required fields can't be removed" : "Remove from layout"}
        disabled={required}
        onClick={onRemove}
      >
        <CloseIcon size={16} />
      </Button>
    </div>
  );
}

/** The chip as it follows the pointer while dragged (in the DragOverlay). */
export function FieldChipOverlay({ fieldKey, field }: FieldSummaryProps) {
  return (
    <div className={clsx(styles.chip, styles.dragOverlay)}>
      <span className={styles.handle}>
        <DragHandleIcon size={16} />
      </span>
      <FieldSummary fieldKey={fieldKey} field={field} />
    </div>
  );
}
