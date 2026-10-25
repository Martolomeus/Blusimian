import clsx from "clsx";
import { FieldRow } from "@/components/fields/FieldRow";
import type { ResolvedSection } from "@/lib/entity/resolveLayout";
import type { EntityRecord } from "@/lib/entity/types";
import styles from "./LayoutSection.module.css";

type LayoutSectionProps = {
  section: ResolvedSection;
  record?: EntityRecord;
  isEditing: boolean;
};

// One section of a layout: its title over its fields, in one or two columns.
export function LayoutSection({ section, record, isEditing }: LayoutSectionProps) {
  return (
    <section className={styles.section}>
      <h2 className={styles.title}>{section.label}</h2>
      <div className={clsx(styles.grid, section.columnCount === 2 && styles.twoColumns)}>
        {section.fields.map((field) => (
          <FieldRow key={field.key} field={field} record={record} isEditing={isEditing} />
        ))}
      </div>
    </section>
  );
}
