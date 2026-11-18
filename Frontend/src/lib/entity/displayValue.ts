import { text } from "./entityConfig";
import type { SchemaField } from "./types";

/** A record value as display text, "" when there is none. The one place a picklist code becomes its label. */
export function displayValue(field: SchemaField | undefined, value: unknown): string {
  if (field?.dataType === "PICKLIST" && typeof value === "string") {
    // An unknown code (a stale schema, an old value) shows as itself.
    return field.options?.find((option) => option.code === value)?.label ?? value;
  }
  return text(value);
}
