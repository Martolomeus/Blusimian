import { useEffect, useId, useRef } from "react";
import { rectSortingStrategy, SortableContext, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import clsx from "clsx";
import { Button, Input, Select } from "@/components/ui";
import type { SchemaField } from "@/lib/entity/types";
import { DragHandleIcon, TrashIcon } from "@/lib/icons";
import {
  MAX_LABEL_LENGTH,
  removeField,
  removeSection,
  renameSection,
  sectionTitle,
  setColumns,
  type DraftAction,
  type DraftSection,
} from "@/lib/layouts/layoutDraft";
import { fieldDndId, sectionDndId } from "./dndIds";
import { FieldChip } from "./FieldChip";
import styles from "../layouts.module.css";

const COLUMN_OPTIONS = [
  { value: "1", label: "1 column" },
  { value: "2", label: "2 columns" },
];

type EditorSectionProps = {
  section: DraftSection;
  fieldsByKey: Map<string, SchemaField>;
  /** Why the section can't be deleted, or null when it can. */
  removeRefusal: string | null;
  labelInvalid: boolean;
  /** Focus and select the label on mount: the section was just added. */
  autoFocus: boolean;
  dispatch: (action: DraftAction) => void;
};

// One section on the canvas: a sortable card, dragged by its handle, holding its fields
// as chips in the same one- or two-column grid the record page uses, so the canvas reads
// like the page it produces. Its fields are their own sortable list; the card itself is
// where a field lands when dropped on it outside any chip.
export function EditorSection({
  section,
  fieldsByKey,
  removeRefusal,
  labelInvalid,
  autoFocus,
  dispatch,
}: EditorSectionProps) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: sectionDndId(section.uid) });
  const labelRef = useRef<HTMLInputElement>(null);
  const columnsId = useId();
  const title = sectionTitle(section);

  useEffect(() => {
    if (autoFocus) {
      labelRef.current?.focus();
      labelRef.current?.select();
    }
  }, [autoFocus]);

  return (
    <section
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={clsx(styles.section, isDragging && styles.dragSource)}
    >
      <div className={styles.sectionHeader}>
        <button
          type="button"
          ref={setActivatorNodeRef}
          className={styles.handle}
          {...attributes}
          {...listeners}
          aria-label={`Move section ${title}`}
        >
          <DragHandleIcon size={18} />
        </button>
        <div className={styles.sectionLabel}>
          <Input
            ref={labelRef}
            value={section.label}
            maxLength={MAX_LABEL_LENGTH}
            aria-label="Section name"
            aria-invalid={labelInvalid || undefined}
            hasError={labelInvalid}
            onChange={(e) => dispatch(renameSection(section.uid, e.target.value))}
          />
          {labelInvalid && <p className={styles.inputError}>Required</p>}
        </div>
        <div className={styles.columnSelect}>
          <label htmlFor={columnsId} className="visually-hidden">
            Columns
          </label>
          <Select
            id={columnsId}
            value={String(section.columnCount)}
            options={COLUMN_OPTIONS}
            onChange={(value) => dispatch(setColumns(section.uid, value === "1" ? 1 : 2))}
          />
        </div>
        <Button
          variant="ghost"
          size="sm"
          className={styles.sectionDelete}
          aria-label={`Delete section ${title}`}
          title={removeRefusal ?? "Delete section"}
          disabled={removeRefusal !== null}
          onClick={() => dispatch(removeSection(section.uid))}
        >
          <TrashIcon size={18} />
        </Button>
      </div>

      <SortableContext items={section.fields.map(fieldDndId)} strategy={rectSortingStrategy}>
        {section.fields.length === 0 ? (
          <div className={styles.dropZone}>Drop a field here or use ＋ in the field list</div>
        ) : (
          <div className={clsx(styles.chipGrid, section.columnCount === 2 && styles.twoColumns)}>
            {section.fields.map((key) => (
              <FieldChip
                key={key}
                fieldKey={key}
                field={fieldsByKey.get(key)}
                onRemove={() => dispatch(removeField(key))}
              />
            ))}
          </div>
        )}
      </SortableContext>
    </section>
  );
}
