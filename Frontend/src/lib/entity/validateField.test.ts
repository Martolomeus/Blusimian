import { describe, expect, it } from "vitest";
import { validateField } from "./validateField";
import type { SchemaField } from "./types";

function field(overrides: Partial<SchemaField> = {}): SchemaField {
  return { key: "f", label: "F", dataType: "TEXT", required: false, maxLength: null, referenceTo: null, ...overrides };
}

describe("validateField", () => {
  it("accepts an empty optional field", () => {
    expect(validateField(field(), null)).toBeUndefined();
    expect(validateField(field(), "")).toBeUndefined();
  });

  it("requires a non-blank value for a required field", () => {
    const required = field({ required: true });
    expect(validateField(required, null)).toBe("Required");
    expect(validateField(required, "   ")).toBe("Required");
    expect(validateField(required, "Acme")).toBeUndefined();
  });

  it("counts false as a value: a required checkbox may be unticked", () => {
    expect(validateField(field({ dataType: "BOOLEAN", required: true }), false)).toBeUndefined();
  });

  it("enforces the field's max length", () => {
    const capped = field({ maxLength: 3 });
    expect(validateField(capped, "abc")).toBeUndefined();
    expect(validateField(capped, "abcd")).toBe("At most 3 characters");
  });

  it("checks the shape of an email address", () => {
    const email = field({ dataType: "EMAIL" });
    expect(validateField(email, "ada@example.test")).toBeUndefined();
    expect(validateField(email, "not-an-email")).toBe("Not a valid email address");
  });

  it("rejects text in a number field", () => {
    const number = field({ dataType: "NUMBER" });
    expect(validateField(number, 12.5)).toBeUndefined();
    expect(validateField(number, "12abc")).toBe("Not a number");
  });
});
