import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import clsx from "clsx";
import styles from "./Select.module.css";
import { type OptionItem, parseChildrenToOptions } from "./selectOptions";

export interface SelectProps {
  /** Current value. Use "" for no selection. */
  value: string;
  /** Called with the new value when the user picks an option. */
  onChange: (value: string) => void;
  /** Called when the trigger loses focus. */
  onBlur?: () => void;
  /** Option list. Either pass this or `<option>` children. */
  options?: OptionItem[];
  children?: React.ReactNode;
  placeholder?: string;
  hasError?: boolean;
  disabled?: boolean;
  required?: boolean;
  name?: string;
  id?: string;
  className?: string;
  dropdownClassName?: string;
}

export function Select({
  value,
  onChange,
  onBlur,
  options: optionsProp,
  children,
  placeholder,
  hasError,
  disabled = false,
  required,
  name,
  id,
  className,
  dropdownClassName,
}: SelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const [dropdownPos, setDropdownPos] = useState<{ top?: number; bottom?: number; left: number; width: number }>({ left: 0, width: 0 });

  const wrapperRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();

  const allParsed = useMemo(
    () => (optionsProp ? optionsProp : parseChildrenToOptions(children)),
    [optionsProp, children],
  );

  const resolvedPlaceholder = useMemo(() => {
    if (placeholder) return placeholder;
    const emptyOpt = allParsed.find((o) => o.value === "");
    return emptyOpt?.label || undefined;
  }, [placeholder, allParsed]);

  const resolvedOptions = useMemo(
    () => allParsed.filter((o) => o.value !== ""),
    [allParsed],
  );

  const selectedOption = resolvedOptions.find((o) => o.value === value);
  const displayLabel = selectedOption?.label;

  // Close on scroll (except scrolling inside the dropdown itself)
  useEffect(() => {
    if (!isOpen) return;
    function handleScroll(e: Event) {
      if (dropdownRef.current?.contains(e.target as Node)) return;
      if (dropdownRef.current) dropdownRef.current.style.display = "none";
      setIsOpen(false);
      setFocusedIndex(-1);
    }
    window.addEventListener("scroll", handleScroll, true);
    return () => window.removeEventListener("scroll", handleScroll, true);
  }, [isOpen]);

  // Close on outside click (check both wrapper and portal dropdown)
  useEffect(() => {
    if (!isOpen) return;
    function handleMouseDown(e: MouseEvent) {
      const target = e.target as Node;
      if (
        wrapperRef.current?.contains(target) ||
        dropdownRef.current?.contains(target)
      ) return;
      setIsOpen(false);
      setFocusedIndex(-1);
    }
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, [isOpen]);

  // Scroll focused item into view — only on keyboard navigation
  const isKeyboardNav = useRef(false);
  useEffect(() => {
    if (!isOpen || focusedIndex < 0 || !isKeyboardNav.current || !dropdownRef.current) return;
    isKeyboardNav.current = false;
    const dropdown = dropdownRef.current;
    const item = dropdown.children[focusedIndex] as HTMLElement | undefined;
    if (!item) return;
    const itemTop = item.offsetTop;
    const itemBottom = itemTop + item.offsetHeight;
    if (itemTop < dropdown.scrollTop) {
      dropdown.scrollTop = itemTop;
    } else if (itemBottom > dropdown.scrollTop + dropdown.clientHeight) {
      dropdown.scrollTop = itemBottom - dropdown.clientHeight;
    }
  }, [focusedIndex, isOpen]);

  const selectValue = useCallback(
    (newValue: string) => {
      onChange(newValue);
      setIsOpen(false);
      setFocusedIndex(-1);
    },
    [onChange],
  );

  function firstEnabledIndex(): number {
    const idx = resolvedOptions.findIndex((o) => !o.disabled);
    return idx >= 0 ? idx : 0;
  }

  function positionDropdown() {
    const rect = wrapperRef.current?.getBoundingClientRect();
    if (!rect) return;
    const spaceBelow = window.innerHeight - rect.bottom;
    const spaceAbove = rect.top;
    if (spaceBelow < 220 && spaceAbove > spaceBelow) {
      setDropdownPos({ bottom: window.innerHeight - rect.top + 2, left: rect.left, width: rect.width });
    } else {
      setDropdownPos({ top: rect.bottom + 2, left: rect.left, width: rect.width });
    }
  }

  function handleTriggerClick() {
    if (disabled) return;
    setIsOpen((prev) => {
      if (!prev) {
        positionDropdown();
        const idx = resolvedOptions.findIndex((o) => o.value === value);
        setFocusedIndex(idx >= 0 ? idx : firstEnabledIndex());
      }
      return !prev;
    });
  }

  function keyboardSetFocusedIndex(indexOrFn: number | ((prev: number) => number)) {
    isKeyboardNav.current = true;
    setFocusedIndex(indexOrFn);
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (disabled) return;

    switch (e.key) {
      case "ArrowDown": {
        e.preventDefault();
        if (!isOpen) {
          positionDropdown();
          setIsOpen(true);
          const idx = resolvedOptions.findIndex((o) => o.value === value);
          keyboardSetFocusedIndex(idx >= 0 ? idx : firstEnabledIndex());
        } else {
          keyboardSetFocusedIndex((prev) => {
            let next = prev + 1;
            while (next < resolvedOptions.length && resolvedOptions[next].disabled) next++;
            return next < resolvedOptions.length ? next : prev;
          });
        }
        break;
      }
      case "ArrowUp": {
        e.preventDefault();
        if (isOpen) {
          keyboardSetFocusedIndex((prev) => {
            let next = prev - 1;
            while (next >= 0 && resolvedOptions[next].disabled) next--;
            return next >= 0 ? next : prev;
          });
        }
        break;
      }
      case "Enter":
      case " ": {
        e.preventDefault();
        if (!isOpen) {
          positionDropdown();
          setIsOpen(true);
          const idx = resolvedOptions.findIndex((o) => o.value === value);
          setFocusedIndex(idx >= 0 ? idx : firstEnabledIndex());
        } else if (focusedIndex >= 0 && !resolvedOptions[focusedIndex]?.disabled) {
          selectValue(resolvedOptions[focusedIndex].value);
        }
        break;
      }
      case "Escape": {
        e.preventDefault();
        setIsOpen(false);
        setFocusedIndex(-1);
        break;
      }
      case "Tab": {
        setIsOpen(false);
        setFocusedIndex(-1);
        break;
      }
      case "Home": {
        if (isOpen) {
          e.preventDefault();
          const idx = firstEnabledIndex();
          if (idx < resolvedOptions.length) keyboardSetFocusedIndex(idx);
        }
        break;
      }
      case "End": {
        if (isOpen) {
          e.preventDefault();
          let idx = resolvedOptions.length - 1;
          while (idx >= 0 && resolvedOptions[idx].disabled) idx--;
          if (idx >= 0) keyboardSetFocusedIndex(idx);
        }
        break;
      }
      default: {
        // Type-ahead: jump to first option starting with typed character
        if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
          const char = e.key.toLowerCase();
          const startIdx = isOpen ? focusedIndex + 1 : 0;
          let idx = resolvedOptions.findIndex(
            (o, i) => i >= startIdx && !o.disabled && o.label.toLowerCase().startsWith(char),
          );
          if (idx === -1) {
            idx = resolvedOptions.findIndex(
              (o) => !o.disabled && o.label.toLowerCase().startsWith(char),
            );
          }
          if (idx >= 0) {
            if (!isOpen) setIsOpen(true);
            keyboardSetFocusedIndex(idx);
          }
        }
      }
    }
  }

  const focusedItemId = focusedIndex >= 0 ? `${listboxId}-opt-${focusedIndex}` : undefined;

  return (
    <div className={clsx(styles.wrapper, className)} ref={wrapperRef}>
      <button
        type="button"
        id={id}
        name={name}
        className={clsx(styles.trigger, hasError && styles.error)}
        disabled={disabled}
        aria-required={required || undefined}
        onClick={handleTriggerClick}
        onKeyDown={handleKeyDown}
        onBlur={onBlur}
        role="combobox"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-controls={listboxId}
        aria-activedescendant={isOpen ? focusedItemId : undefined}
      >
        <span className={clsx(styles.triggerLabel, !displayLabel && styles.placeholder)}>
          {displayLabel || resolvedPlaceholder || " "}
        </span>
        <svg
          className={clsx(styles.chevron, isOpen && styles.chevronOpen)}
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M4 6l4 4 4-4" />
        </svg>
      </button>

      {/* Dropdown — portal so it escapes overflow clipping */}
      {isOpen && createPortal(
        <div
          ref={dropdownRef}
          className={clsx(styles.dropdown, dropdownClassName)}
          role="listbox"
          id={listboxId}
          style={{
            position: "fixed",
            top: dropdownPos.top,
            bottom: dropdownPos.bottom,
            left: dropdownPos.left,
            width: dropdownPos.width,
          }}
        >
          {resolvedOptions.length === 0 ? (
            <div className={styles.dropdownEmpty}>No options</div>
          ) : (
            resolvedOptions.map((opt, idx) => (
              <div
                key={opt.value}
                id={`${listboxId}-opt-${idx}`}
                role="option"
                aria-selected={opt.disabled ? undefined : opt.value === value}
                aria-disabled={opt.disabled || undefined}
                className={clsx(
                  styles.dropdownItem,
                  idx === focusedIndex && styles.dropdownItemFocused,
                  opt.value === value && !opt.disabled && styles.dropdownItemSelected,
                  opt.disabled && styles.dropdownItemDisabled,
                )}
                onMouseDown={(e) => {
                  e.preventDefault();
                  if (!opt.disabled) selectValue(opt.value);
                }}
                onMouseEnter={() => {
                  if (!opt.disabled) setFocusedIndex(idx);
                }}
              >
                {opt.label}
              </div>
            ))
          )}
        </div>,
        document.body,
      )}
    </div>
  );
}
