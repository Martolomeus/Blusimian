import type { UseFormReturn } from "react-hook-form";
import { ApiError } from "@/lib/api";
import type { ResolvedSection } from "./resolveLayout";
import type { EntityRecord, RecordValues } from "./types";

// Helpers for a form that edits a record through a layout. The form holds a record:
// its values are keyed by field key and have the API's own JSON types, with null for
// "no value". So defaults come straight from the record and a save sends form values
// as they are.

/** The form's values: one entry per field on the layout, null where the record has none. */
export function formValues(sections: ResolvedSection[], record?: EntityRecord): RecordValues {
  const values: RecordValues = {};
  for (const section of sections) {
    for (const field of section.fields) {
      values[field.key] = record?.[field.key] ?? null;
    }
  }
  return values;
}

/** Only the values the user changed -- the body of a merge-patch update. */
export function changedValues(values: RecordValues, dirtyFields: Record<string, unknown>): RecordValues {
  const changed: RecordValues = {};
  for (const key of Object.keys(dirtyFields)) {
    if (dirtyFields[key]) {
      changed[key] = values[key];
    }
  }
  return changed;
}

/** Only the values that are set -- the body of a create. */
export function filledValues(values: RecordValues): RecordValues {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value != null));
}

// Shows a failed save on the form. Each field error from the server lands under its
// field; anything that can't (a field that isn't on this layout, or an error that
// isn't about a field at all) is returned as the message for the form's error banner.
export function applySaveError(
  error: unknown,
  sections: ResolvedSection[],
  form: UseFormReturn<RecordValues>,
): string {
  if (!(error instanceof ApiError) || error.details.length === 0) {
    return error instanceof Error ? error.message : "Failed to save";
  }
  const fields = sections.flatMap((section) => section.fields);
  const elsewhere: string[] = [];
  for (const { field: key, reason } of error.details) {
    // the server matches keys case-insensitively and echoes the one it was sent
    const field = fields.find((f) => f.key.toLowerCase() === key.toLowerCase());
    if (field) {
      form.setError(field.key, { message: reason });
    } else {
      elsewhere.push(`${key}: ${reason}`);
    }
  }
  return elsewhere.length > 0 ? elsewhere.join("; ") : "Some fields need another look.";
}
