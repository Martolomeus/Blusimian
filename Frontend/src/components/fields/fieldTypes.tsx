import type { ComponentType } from "react";
import type { FieldDataType, SchemaField } from "@/lib/entity/types";
import { EmailField, LongTextField, PhoneField, TextField, UrlField } from "./TextFields";
import { NumberField } from "./NumberField";
import { BooleanField } from "./BooleanField";
import { DateField } from "./DateField";
import { ReferenceField } from "./ReferenceField";
import { CountryField } from "./CountryField";

// The field type registry: how each data type is shown and edited. This is the only
// place data types are enumerated -- FieldRow looks a field's type up here and never
// branches on it. Supporting a new type is one component pair plus one line below.

/** Shows a value that is known not to be empty (FieldRow renders the "—" itself). */
export type FieldDisplayProps = {
  field: SchemaField;
  value: unknown;
};

// A plain controlled input. `value` and `onChange` speak the API's own JSON types
// (string, number, boolean) with null for "no value"; mapping that to and from a DOM
// control is the component's whole job. It knows nothing about forms or records.
export type FieldInputProps = {
  field: SchemaField;
  id: string;
  value: unknown;
  onChange: (value: unknown) => void;
  onBlur: () => void;
  hasError: boolean;
};

export type FieldType = {
  Display: ComponentType<FieldDisplayProps>;
  Input: ComponentType<FieldInputProps>;
};

export const FIELD_TYPES: Record<FieldDataType, FieldType> = {
  TEXT: TextField,
  LONGTEXT: LongTextField,
  EMAIL: EmailField,
  URL: UrlField,
  PHONE: PhoneField,
  NUMBER: NumberField,
  BOOLEAN: BooleanField,
  DATE: DateField,
  REFERENCE: ReferenceField,
  COUNTRY: CountryField,
};
