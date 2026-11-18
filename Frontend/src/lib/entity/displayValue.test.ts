import { describe, expect, it } from "vitest";
import { displayValue } from "./displayValue";
import type { SchemaField } from "./types";

const industry: SchemaField = {
  key: "industry",
  label: "Industry",
  dataType: "PICKLIST",
  required: false,
  maxLength: 100,
  referenceTo: null,
  options: [
    { code: "technology", label: "Technology", active: true },
    { code: "shipping", label: "Shipping", active: false },
  ],
};

describe("displayValue", () => {
  it("shows a picklist code as its label, inactive ones too", () => {
    expect(displayValue(industry, "technology")).toBe("Technology");
    expect(displayValue(industry, "shipping")).toBe("Shipping");
  });

  it("shows an unknown code as itself", () => {
    expect(displayValue(industry, "software")).toBe("software");
  });

  it("shows nothing for no value", () => {
    expect(displayValue(industry, null)).toBe("");
  });

  it("shows other fields, and fields it doesn't know, as text", () => {
    expect(displayValue({ ...industry, dataType: "TEXT", options: null }, "technology")).toBe("technology");
    expect(displayValue(undefined, 42)).toBe("42");
  });
});
