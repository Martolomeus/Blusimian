import { Select } from "@/components/ui";
import { displayValue } from "@/lib/entity/displayValue";
import type { FieldDisplayProps, FieldInputProps, FieldType } from "./fieldTypes";

// PICKLIST: one of the field's codes on the wire ("technology"), shown as its label.
// Edited with a select of the active options; a value the record already holds stays
// pickable even when it's inactive, so editing another field never forces a change.

// The Select treats "" as "nothing chosen", so clearing needs a value of its own.
// No code can be "-": codes are lower-case letters and digits joined by underscores.
const NONE = "-";

function PicklistInput({ field, id, value, onChange, onBlur, hasError }: FieldInputProps) {
  const current = typeof value === "string" ? value : null;
  const options = [
    { value: NONE, label: "—" },
    ...(field.options ?? [])
      .filter((option) => option.active || option.code === current)
      .map((option) => ({
        value: option.code,
        label: option.active ? option.label : `${option.label} (inactive)`,
      })),
  ];
  if (current !== null && !options.some((option) => option.value === current)) {
    options.push({ value: current, label: current });
  }

  return (
    <Select
      id={id}
      value={current ?? ""}
      onChange={(selected) => onChange(selected === NONE ? null : selected)}
      onBlur={onBlur}
      options={options}
      placeholder="Select…"
      hasError={hasError}
    />
  );
}

export const PicklistField: FieldType = {
  Display: ({ field, value }: FieldDisplayProps) => <>{displayValue(field, value)}</>,
  Input: PicklistInput,
};
