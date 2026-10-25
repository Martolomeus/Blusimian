import clsx from "clsx";
import type { FieldDisplayProps, FieldInputProps, FieldType } from "./fieldTypes";
import styles from "./fields.module.css";

// The text-like types: TEXT, LONGTEXT, EMAIL, URL, PHONE. All hold a string and all
// but LONGTEXT edit it in the same single-line input; they differ in how the value is
// shown (plain, or as a mailto / web / tel link) and in the keyboard the input asks for.

type TextInputProps = FieldInputProps & { type?: "text" | "email" | "url" | "tel" };

function TextInput({ field, id, value, onChange, onBlur, hasError, type = "text" }: TextInputProps) {
  return (
    <input
      id={id}
      type={type}
      className={clsx(styles.input, hasError && styles.inputError)}
      value={typeof value === "string" ? value : ""}
      maxLength={field.maxLength ?? undefined}
      onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
      onBlur={onBlur}
    />
  );
}

function TextDisplay({ value }: FieldDisplayProps) {
  return <>{String(value)}</>;
}

export const TextField: FieldType = { Display: TextDisplay, Input: TextInput };

export const LongTextField: FieldType = {
  Display: ({ value }) => <span className={styles.longText}>{String(value)}</span>,
  Input: ({ field, id, value, onChange, onBlur, hasError }) => (
    <textarea
      id={id}
      className={clsx(styles.input, styles.textarea, hasError && styles.inputError)}
      value={typeof value === "string" ? value : ""}
      maxLength={field.maxLength ?? undefined}
      onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
      onBlur={onBlur}
    />
  ),
};

export const EmailField: FieldType = {
  Display: ({ value }) => (
    <a href={`mailto:${value}`} className={styles.link}>{String(value)}</a>
  ),
  Input: (props) => <TextInput {...props} type="email" />,
};

export const PhoneField: FieldType = {
  Display: ({ value }) => (
    <a href={`tel:${value}`} className={styles.link}>{String(value)}</a>
  ),
  Input: (props) => <TextInput {...props} type="tel" />,
};

export const UrlField: FieldType = {
  Display: ({ value }) => {
    // People type "acme.com"; without a scheme the browser would treat it as a path.
    const url = String(value);
    const href = /^https?:\/\//i.test(url) ? url : `https://${url}`;
    return (
      <a href={href} target="_blank" rel="noreferrer" className={styles.link}>{url}</a>
    );
  },
  Input: (props) => <TextInput {...props} type="url" />,
};
