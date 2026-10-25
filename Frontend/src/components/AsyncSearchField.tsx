import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Input } from "@/components/ui";
import styles from "./AsyncSearchField.module.css";

// Debounced async-search combobox: type to search, pick one option. Ported for
// future use (linking related records, e.g. contact↔company) — not wired into
// any page yet.

export type Option = { id: string; label: string };

export type AsyncSearchFieldProps = {
  id?: string;
  value: string | null;
  onChange: (id: string | null) => void;
  fetchOptions: (query: string) => Promise<Option[]>;
  initialLabel?: string;
  placeholder?: string;
  disabled?: boolean;
  error?: string;
};

export function AsyncSearchField({
  id,
  value,
  onChange,
  fetchOptions,
  initialLabel,
  placeholder = "Search…",
  disabled = false,
  error,
}: AsyncSearchFieldProps) {
  const [inputText, setInputText] = useState<string>(initialLabel ?? "");
  const [isTyping, setIsTyping] = useState(false);

  const [options, setOptions] = useState<Option[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  const [dropdownPos, setDropdownPos] = useState({ top: 0, left: 0, width: 0 });

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const selectedLabelRef = useRef<string>(initialLabel ?? "");

  useEffect(() => {
    if (isTyping) return;
    if (value === null) {
      setInputText("");
      selectedLabelRef.current = "";
    } else {
      const label = initialLabel ?? selectedLabelRef.current;
      setInputText(label);
    }
  }, [value, initialLabel, isTyping]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      const target = e.target as Node;
      if (
        wrapperRef.current?.contains(target) ||
        dropdownRef.current?.contains(target)
      ) return;
      closeDropdown();
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Close on scroll (except inside dropdown)
  useEffect(() => {
    if (!isOpen) return;
    function handleScroll(e: Event) {
      if (dropdownRef.current?.contains(e.target as Node)) return;
      if (dropdownRef.current) dropdownRef.current.style.display = "none";
      closeDropdown();
    }
    window.addEventListener("scroll", handleScroll, true);
    return () => window.removeEventListener("scroll", handleScroll, true);
  }, [isOpen]);

  function closeDropdown() {
    setIsOpen(false);
    setIsSearching(false);
    if (isTyping) {
      setInputText(selectedLabelRef.current);
      setIsTyping(false);
    }
  }

  const runSearch = useCallback(
    (query: string) => {
      if (query.length < 1) {
        setOptions([]);
        setIsOpen(false);
        setIsSearching(false);
        return;
      }
      setIsSearching(true);
      const rect = wrapperRef.current?.getBoundingClientRect();
      if (rect) {
        setDropdownPos({ top: rect.bottom + 2, left: rect.left, width: rect.width });
      }
      setIsOpen(true);
      fetchOptions(query)
        .then((results) => {
          setOptions(results);
          setIsSearching(false);
        })
        .catch(() => {
          setOptions([]);
          setIsSearching(false);
        });
    },
    [fetchOptions]
  );

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const query = e.target.value;
    setInputText(query);
    setIsTyping(true);

    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (query.length === 0) {
      onChange(null);
      selectedLabelRef.current = "";
      setOptions([]);
      setIsOpen(false);
      setIsSearching(false);
      return;
    }

    debounceRef.current = setTimeout(() => {
      runSearch(query);
    }, 300);
  }

  function handleSelect(option: Option) {
    selectedLabelRef.current = option.label;
    setInputText(option.label);
    setIsTyping(false);
    setIsOpen(false);
    setOptions([]);
    onChange(option.id);
  }

  function handleClear(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    selectedLabelRef.current = "";
    setInputText("");
    setIsTyping(false);
    setIsOpen(false);
    setOptions([]);
    onChange(null);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Escape") {
      closeDropdown();
    }
  }

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <div className={styles.inputWrapper}>
        <Input
          id={id}
          type="text"
          value={inputText}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          hasError={!!error}
          autoComplete="off"
          style={{ paddingRight: value !== null && !disabled ? "2rem" : undefined }}
        />
        {value !== null && !disabled && (
          <button
            type="button"
            aria-label="Clear selection"
            className={styles.clearBtn}
            onClick={handleClear}
          >
            &times;
          </button>
        )}
      </div>

      {isOpen && createPortal(
        <div
          ref={dropdownRef}
          className={styles.dropdown}
          role="listbox"
          style={{
            position: "fixed",
            top: dropdownPos.top,
            left: dropdownPos.left,
            width: dropdownPos.width,
          }}
        >
          {isSearching && <div className={styles.dropdownMeta}>Searching…</div>}
          {!isSearching && options.length === 0 && (
            <div className={styles.dropdownMeta}>No results</div>
          )}
          {!isSearching &&
            options.map((option) => (
              <div
                key={option.id}
                role="option"
                aria-selected={option.id === value}
                className={styles.dropdownItem}
                onMouseDown={(e) => {
                  e.preventDefault();
                  handleSelect(option);
                }}
              >
                {option.label}
              </div>
            ))}
        </div>,
        document.body,
      )}

      {error && <p className={styles.error}>{error}</p>}
    </div>
  );
}
