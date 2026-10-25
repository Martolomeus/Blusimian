import type { FieldType } from "./fieldTypes";
import styles from "./fields.module.css";

// BOOLEAN: true / false on the wire. A value that was never set stays null (shown as
// "—") until the user ticks or unticks the box.
export const BooleanField: FieldType = {
  Display: ({ value }) => <>{value === true ? "Yes" : "No"}</>,
  Input: ({ id, value, onChange, onBlur }) => (
    <input
      id={id}
      type="checkbox"
      className={styles.checkbox}
      checked={value === true}
      onChange={(e) => onChange(e.target.checked)}
      onBlur={onBlur}
    />
  ),
};
