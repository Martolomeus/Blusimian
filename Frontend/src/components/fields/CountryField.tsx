import { CountrySelect } from "@/components/ui";
import { countryName, useCountryOptions } from "@/lib/countries";
import type { FieldDisplayProps, FieldType } from "./fieldTypes";

// COUNTRY: an ISO 3166-1 alpha-2 code ("NL") on the wire. Shown as the country's name
// and edited with the country picker.

function CountryDisplay({ value }: FieldDisplayProps) {
  const { options } = useCountryOptions();
  return <>{countryName(String(value), options)}</>;
}

export const CountryField: FieldType = {
  Display: CountryDisplay,
  Input: ({ id, value, onChange, onBlur, hasError }) => (
    <CountrySelect
      id={id}
      value={typeof value === "string" ? value : ""}
      onChange={(code) => onChange(code === "" ? null : code)}
      onBlur={onBlur}
      placeholder="Country…"
      hasError={hasError}
    />
  ),
};
