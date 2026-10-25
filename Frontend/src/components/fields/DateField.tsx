import clsx from "clsx";
import type { FieldType } from "./fieldTypes";
import styles from "./fields.module.css";

// DATE: an ISO "YYYY-MM-DD" string on the wire, which is also what a date input reads
// and writes.

// Built from its parts rather than new Date(iso): that would parse the string as UTC
// midnight and show the previous day west of Greenwich.
function formatDate(iso: string): string {
  const [year, month, day] = iso.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleDateString();
}

export const DateField: FieldType = {
  Display: ({ value }) => <>{formatDate(String(value))}</>,
  Input: ({ id, value, onChange, onBlur, hasError }) => (
    <input
      id={id}
      type="date"
      className={clsx(styles.input, hasError && styles.inputError)}
      value={typeof value === "string" ? value : ""}
      onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
      onBlur={onBlur}
    />
  ),
};
