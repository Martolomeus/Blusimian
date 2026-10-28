import { describe, expect, it } from "vitest";
import { layoutPath, newLayoutPath, newLayoutSource } from "./paths";

describe("layout paths", () => {
  it("builds a stored layout's path", () => {
    expect(layoutPath("CONTACT", "abc")).toBe("/settings/contacts/layouts/abc");
  });

  it("builds a blank layout's path", () => {
    expect(newLayoutPath("DEAL")).toBe("/settings/deals/layouts/new");
  });

  it("builds a copy's path, encoding the id", () => {
    expect(newLayoutPath("COMPANY", "a b&c")).toBe("/settings/companies/layouts/new?from=a+b%26c");
  });

  it("reads the source back from a copy's query", () => {
    const query = newLayoutPath("COMPANY", "a b&c").split("?")[1];
    expect(newLayoutSource(new URLSearchParams(query))).toBe("a b&c");
  });

  it("gives null for a blank layout", () => {
    expect(newLayoutSource(new URLSearchParams())).toBeNull();
  });
});
