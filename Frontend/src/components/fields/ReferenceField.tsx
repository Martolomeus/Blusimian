import { Link } from "react-router";
import { Select } from "@/components/ui";
import { ENTITIES } from "@/lib/entity/entityConfig";
import { useRecord, useRecordList } from "@/lib/entity/records";
import type { FieldDisplayProps, FieldInputProps, FieldType } from "./fieldTypes";
import styles from "./fields.module.css";

// REFERENCE: the id of another record on the wire; field.referenceTo says of which
// entity type. Shown as a link to that record, labelled with its title, and edited by
// picking a record from that entity's list.

function ReferenceDisplay({ field, value }: FieldDisplayProps) {
  const id = String(value);
  const target = ENTITIES[field.referenceTo!];
  const { data: record, isLoading } = useRecord(target.type, id);

  if (isLoading) {
    return <span className={styles.muted}>…</span>;
  }
  return (
    <Link to={`${target.path}/${id}`} className={styles.link}>
      {record ? target.title(record) : id}
    </Link>
  );
}

// Loads the referenced entity's whole list, which is fine while lists are small. Once
// the API can search, swap the Select for AsyncSearchField.
function ReferenceInput({ field, id, value, onChange, onBlur, hasError }: FieldInputProps) {
  const target = ENTITIES[field.referenceTo!];
  const { data: records, isLoading } = useRecordList(target.type);

  const options = [
    { value: "", label: "None" },
    ...(records ?? []).map((record) => ({ value: record.id, label: target.title(record) })),
  ];

  return (
    <Select
      id={id}
      value={typeof value === "string" ? value : ""}
      onChange={(selected) => onChange(selected === "" ? null : selected)}
      onBlur={onBlur}
      options={options}
      placeholder={`Select ${target.singular.toLowerCase()}…`}
      hasError={hasError}
      disabled={isLoading}
    />
  );
}

export const ReferenceField: FieldType = { Display: ReferenceDisplay, Input: ReferenceInput };
