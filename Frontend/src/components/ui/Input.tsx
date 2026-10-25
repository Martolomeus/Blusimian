import React from "react";
import clsx from "clsx";
import styles from "./Input.module.css";

interface Props extends React.InputHTMLAttributes<HTMLInputElement> {
  hasError?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, Props>(
  function Input({ hasError, className, ...props }, ref) {
    return (
      <input
        ref={ref}
        className={clsx(styles.input, hasError && styles.error, className)}
        {...props}
      />
    );
  }
);
