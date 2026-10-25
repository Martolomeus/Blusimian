import React from "react";
import clsx from "clsx";
import styles from "./Button.module.css";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = "primary", size = "md", className, ...props }: Props) {
  return (
    <button
      className={clsx(styles.btn, styles[variant], styles[`size_${size}`], className)}
      {...props}
    />
  );
}
