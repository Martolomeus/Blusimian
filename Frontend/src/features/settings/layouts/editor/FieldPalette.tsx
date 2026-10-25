import { useId, useState, type PointerEventHandler } from "react";
import { useDraggable, useDroppable } from "@dnd-kit/core";
import clsx from "clsx";
import { Button, Select } from "@/components/ui";
import type { SchemaField } from "@/lib/entity/types";
import { PlusIcon } from "@/lib/icons";
import { placeField, sectionTitle, unplacedFields, type DraftAction, type LayoutDraft } from "@/lib/layouts/layoutDraft";
import { fieldDndId, PALETTE_DND_ID } from "./dndIds";
import { FieldSummary } from "./FieldChip";
import styles from "../layouts.module.css";

type FieldPaletteProps = {
  /** The draft as shown, including a drag in progress. */
  view: LayoutDraft;
  /** The entity's field dictionary, in its order. */
  fields: SchemaField[];
  dispatch: (action: DraftAction) => void;
};

// The fields not on the layout, in dictionary order, with required ones first (they
// only show up here when created after the layout was saved, and block saving). ＋ puts
// a field at the end of the section picked in "Add to"; a row can also be dragged onto
// the canvas.
export function FieldPalette({ view, fields, dispatch }: FieldPaletteProps) {
  const { setNodeRef } = useDroppable({ id: PALETTE_DND_ID });
  const targetId = useId();
  // null follows the last section; a picked one that gets deleted falls back to it too.
  const [pickedUid, setPickedUid] = useState<string | null>(null);
  const target = view.sections.find((section) => section.uid === pickedUid) ?? view.sections.at(-1);

  const unplaced = unplacedFields(view, fields);
  const missing = unplaced.filter((field) => field.required);
  const rows = [...missing, ...unplaced.filter((field) => !field.required)];

  return (
    <aside ref={setNodeRef} className={styles.palette}>
      <h2 className={styles.paletteTitle}>Fields</h2>
      <div className={styles.paletteTarget}>
        <label htmlFor={targetId}>Add to</label>
        <div className={styles.paletteTargetSelect}>
          <Select
            id={targetId}
            value={target?.uid ?? ""}
            onChange={setPickedUid}
            options={view.sections.map((section) => ({ value: section.uid, label: sectionTitle(section) }))}
          />
        </div>
      </div>

      {missing.length > 0 && (
        <p className={styles.paletteWarning}>Required fields must be on the layout before it can be saved.</p>
      )}

      {rows.length === 0 ? (
        <p className={styles.paletteEmpty}>All fields are on this layout.</p>
      ) : (
        <ul className={styles.paletteList}>
          {rows.map((field) => (
            <PaletteItem
              key={field.key}
              field={field}
              targetName={target ? sectionTitle(target) : undefined}
              onAdd={target ? () => dispatch(placeField(field.key, target.uid)) : undefined}
            />
          ))}
        </ul>
      )}
    </aside>
  );
}

type PaletteItemProps = {
  field: SchemaField;
  targetName: string | undefined;
  onAdd: (() => void) | undefined;
};

// Draggable by pointer only: keyboard users add with ＋ and then move the chip. So only
// the pointer listener goes on the row -- its key listener would start a keyboard drag
// from Enter on the ＋ button inside it.
function PaletteItem({ field, targetName, onAdd }: PaletteItemProps) {
  const { setNodeRef, listeners, isDragging } = useDraggable({ id: fieldDndId(field.key) });

  return (
    <li
      ref={setNodeRef}
      className={clsx(styles.paletteItem, field.required && styles.paletteItemRequired, isDragging && styles.dragSource)}
      onPointerDown={listeners?.onPointerDown as PointerEventHandler | undefined}
    >
      <Button
        variant="ghost"
        size="sm"
        className={styles.iconButton}
        aria-label={`Add ${field.label}`}
        title={targetName ? `Add to ${targetName}` : undefined}
        disabled={!onAdd}
        onClick={onAdd}
      >
        <PlusIcon size={16} />
      </Button>
      <FieldSummary fieldKey={field.key} field={field} />
    </li>
  );
}
