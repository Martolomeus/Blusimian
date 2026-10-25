import type { ComponentType } from "react";
import { CompanyIcon, ContactIcon, DealIcon, type IconProps } from "@/lib/entity-icons";
import type { EntityRecord, EntityType } from "./types";

// The per-entity facts the schema endpoint doesn't serve: where the entity lives in
// the app and the API, what it's called, and which fields make up a record's title.
// This is the only place rendering code names a specific entity -- everything else is
// driven by the schema.
export type EntityConfig = {
  type: EntityType;
  /** Route base, e.g. "/companies". A record lives at `${path}/${id}`. */
  path: string;
  /** Record endpoint, e.g. "/api/companies". */
  api: string;
  singular: string;
  plural: string;
  icon: ComponentType<IconProps>;
  /** The record's display name: page title, link text. */
  title: (record: EntityRecord) => string;
  /** Field keys shown under the title on the detail page. */
  subtitleKeys: string[];
};

/** A record value as display text; "" when there is none. */
export function text(value: unknown): string {
  return value == null ? "" : String(value);
}

// In sidebar order.
export const ENTITIES: Record<EntityType, EntityConfig> = {
  CONTACT: {
    type: "CONTACT",
    path: "/contacts",
    api: "/api/contacts",
    singular: "Contact",
    plural: "Contacts",
    icon: ContactIcon,
    title: (r) => `${text(r.firstName)} ${text(r.lastName)}`.trim(),
    subtitleKeys: ["email", "phone"],
  },
  COMPANY: {
    type: "COMPANY",
    path: "/companies",
    api: "/api/companies",
    singular: "Company",
    plural: "Companies",
    icon: CompanyIcon,
    title: (r) => text(r.name),
    subtitleKeys: ["type", "industry"],
  },
  DEAL: {
    type: "DEAL",
    path: "/deals",
    api: "/api/deals",
    singular: "Deal",
    plural: "Deals",
    icon: DealIcon,
    title: (r) => text(r.dealName),
    subtitleKeys: [],
  },
};
