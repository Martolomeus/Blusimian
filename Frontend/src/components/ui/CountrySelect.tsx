import { Select, type SelectProps } from "./Select";
import { useCountryOptions } from "@/lib/countries";

export type CountrySelectProps = Omit<SelectProps, "options">;

// A Select preloaded with the ISO country list from /api/countries.
export function CountrySelect(props: CountrySelectProps) {
  const { options, isLoading } = useCountryOptions();

  return (
    <Select
      {...props}
      disabled={props.disabled || isLoading}
      options={options.map((o) => ({ value: o.code, label: o.name }))}
    />
  );
}
