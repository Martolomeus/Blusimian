import { useId, useState, type ReactNode } from "react";
import { ChevronRightIcon } from "@/lib/icons";
import styles from "./SettingsSection.module.css";

type SettingsSectionProps = {
  /** Unique app-wide and stable; it names the collapsed state in storage. "overview/crm", "contacts/layouts". */
  storageKey: string;
  title: string;
  /** Shown as a muted pill after the title, also while collapsed. Omit while unknown (loading). */
  count?: number;
  /** A muted line at the top of the body; hidden with it. */
  description?: string;
  /** Controls at the right of the header, e.g. "New layout". Always visible, also while collapsed. */
  actions?: ReactNode;
  children: ReactNode;
};

const STORAGE_PREFIX = "blusimian:settings-collapsed:";

// Storage can be missing or blocked (private window); then sections just aren't remembered.
function readCollapsed(key: string): boolean {
  try {
    return localStorage.getItem(STORAGE_PREFIX + key) === "1";
  } catch {
    return false;
  }
}

function writeCollapsed(key: string, collapsed: boolean): void {
  try {
    if (collapsed) localStorage.setItem(STORAGE_PREFIX + key, "1");
    else localStorage.removeItem(STORAGE_PREFIX + key);
  } catch {
    // Not remembered; the toggle still works.
  }
}

// A collapsible block of a settings page: a disclosure button in the heading, actions
// beside it. The body is hidden, not unmounted, so its state survives collapsing.
export function SettingsSection({ storageKey, title, count, description, actions, children }: SettingsSectionProps) {
  const headingId = useId();
  const bodyId = useId();
  const [open, setOpen] = useState(() => !readCollapsed(storageKey));

  function toggle() {
    writeCollapsed(storageKey, open);
    setOpen(!open);
  }

  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <header className={styles.header}>
        <h2 id={headingId} className={styles.heading}>
          <button type="button" className={styles.toggle} aria-expanded={open} aria-controls={bodyId} onClick={toggle}>
            <ChevronRightIcon className={styles.chevron} size={20} />
            <span>{title}</span>
            {count !== undefined && <span className={styles.count}>{count}</span>}
          </button>
        </h2>
        {actions && <div className={styles.actions}>{actions}</div>}
      </header>
      <div id={bodyId} className={styles.body} hidden={!open}>
        {description && <p className={styles.description}>{description}</p>}
        {children}
      </div>
    </section>
  );
}
