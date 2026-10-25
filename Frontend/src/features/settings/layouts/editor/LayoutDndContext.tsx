import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import {
  closestCenter,
  closestCorners,
  DndContext,
  DragOverlay,
  getFirstCollision,
  KeyboardCode,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  pointerWithin,
  rectIntersection,
  useSensor,
  useSensors,
  type Active,
  type Announcements,
  type CollisionDetection,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
  type DroppableContainer,
  type KeyboardCoordinateGetter,
  type Over,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import type { SchemaField } from "@/lib/entity/types";
import { DragHandleIcon } from "@/lib/icons";
import {
  layoutDraftReducer,
  moveSection,
  placeField,
  sectionTitle,
  type DraftAction,
  type DraftSection,
  type LayoutDraft,
} from "@/lib/layouts/layoutDraft";
import { fieldDndId, parseDndId, type DndTarget } from "./dndIds";
import { FieldChipOverlay } from "./FieldChip";
import styles from "../layouts.module.css";

type LayoutDndContextProps = {
  draft: LayoutDraft;
  fieldsByKey: Map<string, SchemaField>;
  required: ReadonlySet<string>;
  dispatch: (action: DraftAction) => void;
  /** Renders the canvas and the palette from `view`: the draft, plus a drag in progress. */
  children: (view: LayoutDraft) => ReactNode;
};

// The drag-and-drop layer over the editor, after dnd-kit's multiple-containers example.
//
// Sections sort in one list; each section's fields are another; palette rows are plain
// draggables. Within one list, dnd-kit's sortable transforms show where an item will
// land. A field moving to another section (or in from the palette) has to change lists
// mid-drag, so while dragging, this keeps a preview: the draft as it would be after that
// move, made by the editor's own reducer. Canvas and palette render the preview, so the
// field shows up in the section it's over and the sortable takes over from there.
//
// The preview is never the draft. On drop the whole gesture becomes one action on the
// real draft -- moveSection or placeField -- and the reducer applies its own rules to
// it; cancelling just drops the preview. Nothing here decides what is allowed.
export function LayoutDndContext({ draft, fieldsByKey, required, dispatch, children }: LayoutDndContextProps) {
  const [activeId, setActiveId] = useState<UniqueIdentifier | null>(null);
  const [preview, setPreview] = useState<LayoutDraft | null>(null);
  const view = preview ?? draft;

  // dnd-kit calls the keyboard getter and the announcements outside render, after the
  // sensor was set up; they read the view through this ref so it is never stale.
  const viewRef = useRef(view);
  useEffect(() => {
    viewRef.current = view;
  });

  // From the multiple-containers example: right after a field changes section the layout
  // shifts and the pointer may briefly be over nothing; then the field itself is the best
  // "over", rather than the target it just left.
  const lastOverId = useRef<UniqueIdentifier | null>(null);
  const recentlyMovedSection = useRef(false);
  useEffect(() => {
    requestAnimationFrame(() => {
      recentlyMovedSection.current = false;
    });
  }, [preview]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: (event, args) =>
        parseDndId(args.active).kind === "section"
          ? sortableKeyboardCoordinates(event, args)
          : fieldKeyboardCoordinates(event, args, viewRef.current),
    }),
  );

  const collisionDetection: CollisionDetection = (args) => {
    // A section only ever lands among sections.
    if (parseDndId(args.active.id).kind === "section") {
      return closestCenter({
        ...args,
        droppableContainers: args.droppableContainers.filter((c) => parseDndId(c.id).kind === "section"),
      });
    }

    const pointerHits = pointerWithin(args);
    const hits = pointerHits.length > 0 ? pointerHits : rectIntersection(args);
    let overId = getFirstCollision(hits, "id");
    if (overId != null) {
      // Over a section that has fields: aim at the closest of them, so the field lands
      // between chips rather than at the end.
      const target = parseDndId(overId);
      const section = target.kind === "section" ? view.sections.find((s) => s.uid === target.uid) : undefined;
      if (section && section.fields.length > 0) {
        const chips = new Set(section.fields.map(fieldDndId));
        overId =
          closestCenter({
            ...args,
            droppableContainers: args.droppableContainers.filter((c) => chips.has(String(c.id))),
          })[0]?.id ?? overId;
      }
      lastOverId.current = overId;
      return [{ id: overId }];
    }
    if (recentlyMovedSection.current) {
      lastOverId.current = args.active.id;
    }
    return lastOverId.current != null ? [{ id: lastOverId.current }] : [];
  };

  function handleDragStart({ active }: DragStartEvent) {
    setActiveId(active.id);
    setPreview(null);
    lastOverId.current = null;
  }

  function handleDragOver({ active, over }: DragOverEvent) {
    const dragged = parseDndId(active.id);
    if (dragged.kind !== "field" || !over) return;
    const target = parseDndId(over.id);
    if (target.kind === "palette") {
      // No drag-to-remove: over the palette, the field is back where it started.
      setPreview(null);
      return;
    }
    const current = preview ?? draft;
    const toSection = sectionOf(current, target);
    if (!toSection || toSection.fields.includes(dragged.key)) return; // same list: the sortable shows it

    let index = toSection.fields.length;
    if (target.kind === "field") {
      index = toSection.fields.indexOf(target.key) + (isAfter(active, over) ? 1 : 0);
    }
    recentlyMovedSection.current = true;
    setPreview(layoutDraftReducer(current, placeField(dragged.key, toSection.uid, index), required));
  }

  function handleDragEnd({ active, over }: DragEndEvent) {
    const current = preview ?? draft;
    endDrag();
    if (!over) return;
    const dragged = parseDndId(active.id);
    const target = parseDndId(over.id);

    if (dragged.kind === "section") {
      const toIndex = target.kind === "section" ? draft.sections.findIndex((s) => s.uid === target.uid) : -1;
      if (toIndex >= 0) dispatch(moveSection(dragged.uid, toIndex));
      return;
    }
    if (dragged.kind === "field") {
      const drop = dropPosition(current, dragged.key, target);
      if (drop) dispatch(placeField(dragged.key, drop.section.uid, drop.index));
    }
  }

  function endDrag() {
    setActiveId(null);
    setPreview(null);
  }

  const announcements = layoutAnnouncements(() => viewRef.current, fieldsByKey);
  const dragged = activeId != null ? parseDndId(activeId) : null;
  const draggedSection =
    dragged?.kind === "section" ? view.sections.find((section) => section.uid === dragged.uid) : undefined;

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={collisionDetection}
      // Re-measure while dragging: moving a field between sections shifts everything below.
      measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
      accessibility={{ announcements, screenReaderInstructions: { draggable: INSTRUCTIONS } }}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
      onDragCancel={endDrag}
    >
      {children(view)}
      {createPortal(
        <DragOverlay>
          {dragged?.kind === "field" && <FieldChipOverlay fieldKey={dragged.key} field={fieldsByKey.get(dragged.key)} />}
          {draggedSection && (
            <div className={styles.sectionOverlay}>
              <DragHandleIcon size={18} />
              <span className={styles.fieldLabel}>{sectionTitle(draggedSection)}</span>
              <span className={styles.badge}>
                {draggedSection.fields.length} field{draggedSection.fields.length === 1 ? "" : "s"}
              </span>
            </div>
          )}
        </DragOverlay>,
        document.body,
      )}
    </DndContext>
  );
}

/** The section a drop target is in: the section itself, or the one holding the field. */
function sectionOf(view: LayoutDraft, target: DndTarget): DraftSection | undefined {
  if (target.kind === "section") return view.sections.find((section) => section.uid === target.uid);
  if (target.kind === "field") return view.sections.find((section) => section.fields.includes(target.key));
  return undefined;
}

/**
 * Where a dragged field lands when dropped on `target`, in a view that already shows any
 * move between sections. Over a chip it takes that chip's place, the way the sortable
 * shows it; over the section itself it stays where the preview put it, or goes last.
 */
function dropPosition(view: LayoutDraft, key: string, target: DndTarget) {
  const section = sectionOf(view, target);
  if (!section) return null;
  const index =
    target.kind === "field"
      ? section.fields.indexOf(target.key)
      : section.fields.includes(key)
        ? section.fields.indexOf(key)
        : section.fields.length;
  return { section, index };
}

/** Whether the dragged item is past the middle of `over`, reading the grid row by row. */
function isAfter(active: Active, over: Over): boolean {
  const rect = active.rect.current.translated;
  if (!rect) return false;
  const centerX = rect.left + rect.width / 2;
  const centerY = rect.top + rect.height / 2;
  if (centerY > over.rect.top + over.rect.height) return true;
  if (centerY < over.rect.top) return false;
  return centerX > over.rect.left + over.rect.width / 2;
}

const DIRECTIONS: string[] = [KeyboardCode.Down, KeyboardCode.Right, KeyboardCode.Up, KeyboardCode.Left];

/**
 * Arrow keys for a field, after the multiple-containers example: go to the nearest chip
 * in that direction, in any section, or to an empty section. A section that has chips is
 * never the target itself (the chips are), and neither is the palette.
 */
function fieldKeyboardCoordinates(
  event: KeyboardEvent,
  { context: { active, collisionRect, droppableRects, droppableContainers, over } }: Parameters<KeyboardCoordinateGetter>[1],
  view: LayoutDraft,
) {
  if (!DIRECTIONS.includes(event.code)) return undefined;
  event.preventDefault();
  if (!active || !collisionRect) return undefined;

  const candidates: DroppableContainer[] = [];
  droppableContainers.getEnabled().forEach((entry) => {
    const rect = droppableRects.get(entry.id);
    if (!rect) return;
    const target = parseDndId(entry.id);
    if (target.kind === "palette") return;
    if (target.kind === "section" && view.sections.find((s) => s.uid === target.uid)?.fields.length) return;

    switch (event.code) {
      case KeyboardCode.Down:
        if (collisionRect.top < rect.top) candidates.push(entry);
        break;
      case KeyboardCode.Up:
        if (collisionRect.top > rect.top) candidates.push(entry);
        break;
      case KeyboardCode.Left:
        if (collisionRect.left >= rect.left + rect.width) candidates.push(entry);
        break;
      case KeyboardCode.Right:
        if (collisionRect.left + collisionRect.width <= rect.left) candidates.push(entry);
        break;
    }
  });

  const collisions = closestCorners({
    active,
    collisionRect,
    droppableRects,
    droppableContainers: candidates,
    pointerCoordinates: null,
  });
  let closestId = getFirstCollision(collisions, "id");
  if (closestId === over?.id && collisions.length > 1) {
    closestId = collisions[1].id;
  }
  const rect = closestId != null ? droppableRects.get(closestId) : undefined;
  return rect ? { x: rect.left, y: rect.top } : undefined;
}

const INSTRUCTIONS =
  "To pick up a field or a section, focus its handle and press Space or Enter. " +
  "Use the arrow keys to move it, Space or Enter to drop it, or Escape to cancel.";

/** Live-region messages that name fields and sections by their labels, not their ids. */
function layoutAnnouncements(getView: () => LayoutDraft, fieldsByKey: Map<string, SchemaField>): Announcements {
  function name(id: UniqueIdentifier): string {
    const item = parseDndId(id);
    if (item.kind === "field") return fieldsByKey.get(item.key)?.label ?? item.key;
    if (item.kind === "section") {
      const section = getView().sections.find((s) => s.uid === item.uid);
      return `Section ${section ? sectionTitle(section) : ""}`.trim();
    }
    return "the field list";
  }

  /** "Overview, position 3 of 7", or null when it wouldn't land anywhere. */
  function place(active: Active, over: Over | null): string | null {
    if (!over) return null;
    const view = getView();
    const dragged = parseDndId(active.id);
    const target = parseDndId(over.id);
    if (dragged.kind === "section") {
      const index = target.kind === "section" ? view.sections.findIndex((s) => s.uid === target.uid) : -1;
      return index < 0 ? null : `position ${index + 1} of ${view.sections.length}`;
    }
    if (dragged.kind !== "field") return null;
    const drop = dropPosition(view, dragged.key, target);
    if (!drop) return null;
    const total = drop.section.fields.length + (drop.section.fields.includes(dragged.key) ? 0 : 1);
    return `${sectionTitle(drop.section)}, position ${drop.index + 1} of ${total}`;
  }

  return {
    onDragStart: ({ active }) => `Picked up ${name(active.id)}.`,
    onDragOver: ({ active, over }) => {
      const where = place(active, over);
      return where ? `${name(active.id)} is over ${where}.` : `${name(active.id)} is not over a place it can go.`;
    },
    onDragEnd: ({ active, over }) => {
      const where = place(active, over);
      return where ? `${name(active.id)} moved to ${where}.` : `${name(active.id)} was dropped. Nothing changed.`;
    },
    onDragCancel: ({ active }) => `Moving ${name(active.id)} was cancelled.`,
  };
}
