import { useId } from "react";
import { useController } from "react-hook-form";
import { validateField } from "@/lib/entity/validateField";
import type { EntityRecord, SchemaField } from "@/lib/entity/types";
import { FIELD_TYPES } from "./fieldTypes";
import styles from "./FieldRow.module.css";

type FieldRowProps = {
  field: SchemaField;
  /** The record being shown. Absent on a create form, which is always editing. */
  record?: EntityRecord;
  isEditing: boolean;
};

// One field of a record: its label, then its value (view) or its input (edit). The
// one place the field type registry is consulted, and the one place an input is
// connected to the surrounding form -- so it must be rendered inside a FormProvider.
export function FieldRow({ field, record, isEditing }: FieldRowProps) {
  const id = useId();
  const { field: input, fieldState } = useController({
    name: field.key,
    rules: { validate: (value) => validateField(field, value) },
  });

  // A data type this frontend doesn't know yet (the backend is ahead of it) is shown
  // as text and can't be edited: sending it back as a string could be the wrong type.
  const type = FIELD_TYPES[field.dataType];
  const { Display, Input } = type ?? FIELD_TYPES.TEXT;
  const showInput = isEditing && type !== undefined;

  const value = record?.[field.key];
  const isEmpty = value == null || value === "";

  return (
    <div className={styles.row}>
      <label className={styles.label} htmlFor={showInput ? id : undefined}>
        {field.label}
        {field.required && showInput && <span className={styles.required}>*</span>}
      </label>
      <div className={styles.value}>
        {showInput ? (
          <>
            <Input
              field={field}
              id={id}
              value={input.value}
              onChange={input.onChange}
              onBlur={input.onBlur}
              hasError={!!fieldState.error}
            />
            {fieldState.error && <p className={styles.error}>{fieldState.error.message}</p>}
          </>
        ) : isEmpty ? (
          "—"
        ) : (
          <Display field={field} value={value} />
        )}
      </div>
    </div>
  );
}
