import { describe, expect, it } from "vitest";
import { resolveLayout } from "./resolveLayout";
import type { EntitySchema, SchemaField } from "./types";

function field(key: string): SchemaField {
  return { key, label: key, dataType: "TEXT", required: false, maxLength: null, referenceTo: null, options: null };
}

const fields = [field("name"), field("website"), field("phone")];

function schema(layout: EntitySchema["layout"]): EntitySchema {
  return { entityType: "COMPANY", fields, layout };
}

describe("resolveLayout", () => {
  it("joins each placement to its field, keeping section and field order", () => {
    const sections = resolveLayout(
      schema({
        id: "1",
        label: "Default",
        sections: [
          { label: "Contact", columnCount: 1, fields: [{ key: "phone" }] },
          { label: "Overview", columnCount: 2, fields: [{ key: "website" }, { key: "name" }] },
        ],
      }),
    );

    expect(sections.map((s) => s.label)).toEqual(["Contact", "Overview"]);
    expect(sections[1].columnCount).toBe(2);
    expect(sections[1].fields).toEqual([field("website"), field("name")]);
  });

  it("puts every field in one section when the entity has no layout", () => {
    expect(resolveLayout(schema(null))).toEqual([{ label: "Details", columnCount: 1, fields }]);
  });

  it("skips a placement that has no field, and a section left empty by that", () => {
    const sections = resolveLayout(
      schema({
        id: "1",
        label: "Default",
        sections: [
          { label: "Overview", columnCount: 2, fields: [{ key: "name" }, { key: "gone" }] },
          { label: "Ghosts", columnCount: 1, fields: [{ key: "gone" }] },
        ],
      }),
    );

    expect(sections).toEqual([{ label: "Overview", columnCount: 2, fields: [field("name")] }]);
  });
});
