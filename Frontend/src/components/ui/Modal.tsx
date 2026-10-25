import React, { useEffect } from "react";
import clsx from "clsx";
import styles from "./Modal.module.css";

type Size = "sm" | "md" | "lg" | "xl";

interface Props {
  open: boolean;
  onClose: () => void;
  title?: string;
  size?: Size;
  children: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}

export function Modal({ open, onClose, title, size = "md", children, footer, className }: Props) {
  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className={styles.overlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className={clsx(styles.container, styles[size], className)} role="dialog" aria-modal="true">
        {title != null && (
          <div className={styles.header}>
            <h2 className={styles.title}>{title}</h2>
            <button type="button" className={styles.closeBtn} aria-label="Close" onClick={onClose}>
              &times;
            </button>
          </div>
        )}

        <div className={styles.body}>{children}</div>

        {footer != null && <div className={styles.footer}>{footer}</div>}
      </div>
    </div>
  );
}
