import { describe, expect, it } from "vitest";
import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";
import {
  entityFromSettingsSegment,
  entitySettingsPath,
  newRolePath,
  newRoleSource,
  rolePath,
  settingsSegment,
  userPath,
} from "./paths";

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

  it("keeps roles and users apart from entity segments", () => {
    expect(entityFromSettingsSegment("roles")).toBeNull();
    expect(entityFromSettingsSegment("users")).toBeNull();
  });

  it("builds the role and user paths", () => {
    expect(rolePath("1")).toBe("/settings/roles/1");
    expect(userPath("1")).toBe("/settings/users/1");
    expect(newRolePath()).toBe("/settings/roles/new");
  });

  it("round-trips the role to copy through the URL", () => {
    const search = newRolePath("abc").split("?")[1];
    expect(newRoleSource(new URLSearchParams(search))).toBe("abc");
    expect(newRoleSource(new URLSearchParams())).toBeNull();
  });
});
