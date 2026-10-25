import React from "react";

// Shared option model + `<option>`-children parsing.

export type OptionItem = { value: string; label: string; disabled?: boolean };

export function extractText(node: React.ReactNode): string {
  if (typeof node === "string") return node;
  if (typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(extractText).join("");
  if (React.isValidElement(node)) {
    const props = node.props as { children?: React.ReactNode };
    return extractText(props.children);
  }
  return "";
}

export function parseChildrenToOptions(children: React.ReactNode): OptionItem[] {
  const items: OptionItem[] = [];
  React.Children.forEach(children, (child) => {
    if (!React.isValidElement(child)) return;
    if (child.type === "optgroup") {
      const groupProps = child.props as React.OptgroupHTMLAttributes<HTMLOptGroupElement>;
      items.push(...parseChildrenToOptions(groupProps.children as React.ReactNode));
      return;
    }
    if (child.type === "option") {
      const props = child.props as React.OptionHTMLAttributes<HTMLOptionElement>;
      const value = String(props.value ?? "");
      const label = extractText(props.children) || String(props.value ?? "");
      items.push({ value, label, disabled: props.disabled });
    }
  });
  return items;
}
