import { useState } from "react";
import clsx from "clsx";
import type { FieldInputProps, FieldType } from "./fieldTypes";
import styles from "./fields.module.css";

// NUMBER: a JSON number on the wire. The input keeps what the user is typing as its
// own text, because a half-typed number ("12." or "-") isn't a number yet. It reports
// a real number whenever the text is one, null when it's empty, and otherwise the raw
// text -- which validateField rejects as "Not a number".
function NumberInput({ id, value, onChange, onBlur, hasError }: FieldInputProps) {
  const [draft, setDraft] = useState(value == null ? "" : String(value));

  function handleChange(text: string) {
    setDraft(text);
    const trimmed = text.trim().replace(",", ".");
    if (trimmed === "") {
      onChange(null);
    } else {
      const number = Number(trimmed);
      onChange(Number.isFinite(number) ? number : text);
    }
  }

  return (
    <input
      id={id}
      type="text"
      inputMode="decimal"
      className={clsx(styles.input, hasError && styles.inputError)}
      value={draft}
      onChange={(e) => handleChange(e.target.value)}
      onBlur={onBlur}
    />
  );
}

export const NumberField: FieldType = {
  Display: ({ value }) => (
    <>{typeof value === "number" ? value.toLocaleString() : String(value)}</>
  ),
  Input: NumberInput,
};
