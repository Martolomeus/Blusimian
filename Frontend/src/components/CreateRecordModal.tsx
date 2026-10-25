import { useState } from "react";
import { useForm } from "react-hook-form";
import { Modal, Button } from "@/components/ui";
import { RecordForm } from "@/components/entity/RecordForm";
import { ENTITIES } from "@/lib/entity/entityConfig";
import { applySaveError, filledValues, formValues } from "@/lib/entity/recordForm";
import { useSaveRecord } from "@/lib/entity/records";
import type { ResolvedSection } from "@/lib/entity/resolveLayout";
import { useEntityLayout } from "@/lib/entity/useEntityLayout";
import type { EntityType, RecordValues } from "@/lib/entity/types";
import styles from "./CreateRecordModal.module.css";

const FORM_ID = "create-record-form";

export interface CreateRecordModalProps {
  entityType: EntityType;
  open: boolean;
  onClose: () => void;
  /** Called with the new record's id after a successful create. */
  onSuccess: (id: string) => void;
}

// "New {entity}" dialog for any entity type: the same layout-driven form as the detail
// page, empty and always editing. The form lives in CreateRecordDialog, which only
// mounts while the modal is open and the layout is loaded -- so every open starts from
// a clean form.
export function CreateRecordModal({ entityType, open, onClose, onSuccess }: CreateRecordModalProps) {
  const { sections, error } = useEntityLayout(entityType);

  if (!open) return null;
  if (!sections) {
    return (
      <Modal open onClose={onClose} title={`New ${ENTITIES[entityType].singular}`} size="lg">
        <p className={error ? "page-error" : "page-empty"}>{error ? error.message : "Loading…"}</p>
      </Modal>
    );
  }
  return (
    <CreateRecordDialog entityType={entityType} sections={sections} onClose={onClose} onSuccess={onSuccess} />
  );
}

type CreateRecordDialogProps = Omit<CreateRecordModalProps, "open"> & { sections: ResolvedSection[] };

function CreateRecordDialog({ entityType, sections, onClose, onSuccess }: CreateRecordDialogProps) {
  const config = ENTITIES[entityType];
  const form = useForm<RecordValues>({ defaultValues: formValues(sections) });
  const save = useSaveRecord(entityType);
  const [error, setError] = useState<string | null>(null);
  const { isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const created = await save.mutateAsync(filledValues(values));
      onSuccess(created.id);
    } catch (err) {
      setError(applySaveError(err, sections, form));
    }
  });

  return (
    <Modal
      open
      onClose={onClose}
      title={`New ${config.singular}`}
      size="lg"
      footer={
        <>
          {error && <p className={styles.error}>{error}</p>}
          <div className={styles.footer}>
            <Button variant="secondary" type="button" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" form={FORM_ID} disabled={isSubmitting}>
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          </div>
        </>
      }
    >
      <RecordForm id={FORM_ID} form={form} sections={sections} isEditing onSubmit={onSubmit} />
    </Modal>
  );
}

export default CreateRecordModal;
