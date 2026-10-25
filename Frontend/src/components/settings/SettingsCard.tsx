import type { ComponentType } from "react";
import { Link } from "react-router";
import type { IconProps } from "@/lib/entity-icons";
import { ChevronRightIcon } from "@/lib/icons";
import styles from "./SettingsCard.module.css";

export type SettingsCardProps = {
  title: string;
  description: string;
  /** Route the card opens. The whole card is the link. */
  to: string;
  icon?: ComponentType<IconProps>;
};

// One section on the settings overview. A single link, so the card is one tab stop, the
// whole surface is clickable, and middle-click opens a new tab. It knows nothing about
// which settings exist.
export function SettingsCard({ title, description, to, icon: Icon }: SettingsCardProps) {
  return (
    <Link to={to} className={styles.card}>
      <span className={styles.titleRow}>
        {Icon && <Icon className={styles.icon} size={20} />}
        <span className={styles.title}>{title}</span>
        <ChevronRightIcon className={styles.chevron} size={20} />
      </span>
      <span className={styles.description}>{description}</span>
    </Link>
  );
}
