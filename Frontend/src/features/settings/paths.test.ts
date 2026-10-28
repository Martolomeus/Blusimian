import { describe, expect, it } from "vitest";
import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";
import { entityFromSettingsSegment, entitySettingsPath, settingsSegment } from "./paths";

describe("settings paths", () => {
  it.each(Object.keys(ENTITIES) as EntityType[])("round-trips %s through its segment", (type) => {
    expect(entityFromSettingsSegment(settingsSegment(type))?.type).toBe(type);
  });

  it("matches the segment case-insensitively", () => {
    expect(entityFromSettingsSegment("CONTACTS")?.type).toBe("CONTACT");
  });

  it("gives null for an unknown, empty or missing segment", () => {
    expect(entityFromSettingsSegment("layouts")).toBeNull();
    expect(entityFromSettingsSegment("")).toBeNull();
    expect(entityFromSettingsSegment(undefined)).toBeNull();
  });

  it("builds the entity settings path", () => {
    expect(entitySettingsPath("CONTACT")).toBe("/settings/contacts");
  });
});
