import { useQuery } from "@tanstack/react-query";
import { get } from "@/lib/api";

// Mirrors the GET /api/countries response (Jackson serializes the IsoCountry
// entity getters → camelCase).
export interface CountryOption {
  code: string;
  name: string;
  sortOrder: number;
}

// Countries are global reference data that never change within a session, so we
// fetch once and keep them fresh forever (staleTime: Infinity).
export function useCountryOptions() {
  const { data, isLoading } = useQuery({
    queryKey: ["countries"],
    queryFn: () => get<CountryOption[]>("/api/countries"),
    staleTime: Infinity,
  });
  return { options: data ?? [], isLoading };
}

/** Resolve a country code to its display name, falling back to the code. */
export function countryName(code: string | undefined | null, options: CountryOption[]): string {
  if (!code) return "";
  return options.find((o) => o.code === code)?.name ?? code;
}
