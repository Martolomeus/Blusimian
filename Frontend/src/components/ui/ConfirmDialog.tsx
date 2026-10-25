import type React from "react";
import { Button } from "./Button";
import { Modal } from "./Modal";
import styles from "./ConfirmDialog.module.css";

interface Props {
  open: boolean;
  title: string;
  message: React.ReactNode;
  confirmLabel: string;
  /** "danger" for anything that destroys work. */
  variant?: "primary" | "danger";
  /** While the confirmed action runs: both buttons disabled, and the dialog stays open. */
  isPending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

// "Are you sure?" as a small modal, instead of window.confirm. Cancel has the focus when
// it opens, so Enter never confirms by accident; Escape and the backdrop cancel too.
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  variant = "primary",
  isPending = false,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Modal
      open={open}
      onClose={() => {
        if (!isPending) onCancel();
      }}
      title={title}
      size="sm"
      footer={
        <div className={styles.footer}>
          <Button variant="secondary" onClick={onCancel} disabled={isPending} autoFocus>
            Cancel
          </Button>
          <Button variant={variant} onClick={onConfirm} disabled={isPending}>
            {confirmLabel}
          </Button>
        </div>
      }
    >
      <p className={styles.message}>{message}</p>
    </Modal>
  );
}
