import type { SchemaField } from "./types";

// Client-side check of one field's value against what the schema says about it.
// Mirrors the backend's RecordMapper.apply rule for rule (required, type, max length,
// email format), so the user hears about a problem before the request is sent. The
// server remains the authority: its own field errors are shown the same way.
//
// Returns the message to show, or undefined when the value is fine.
export function validateField(field: SchemaField, value: unknown): string | undefined {
  const blank = value == null || (typeof value === "string" && value.trim() === "");
  if (blank) {
    return field.required ? "Required" : undefined;
  }
  if (field.dataType === "NUMBER" && typeof value !== "number") {
    return "Not a number";
  }
  if (typeof value === "string") {
    if (field.maxLength != null && value.length > field.maxLength) {
      return `At most ${field.maxLength} characters`;
    }
    if (field.dataType === "EMAIL" && !/^[^@\s]+@[^@\s]+$/.test(value)) {
      return "Not a valid email address";
    }
  }
  return undefined;
}
