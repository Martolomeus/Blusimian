import { useState } from "react";
import { useNavigate } from "react-router";
import { useForm } from "react-hook-form";
import { EntityHeader } from "@/components/EntityHeader";
import { Button } from "@/components/ui";
import type { EntityConfig } from "@/lib/entity/entityConfig";
import { displayValue } from "@/lib/entity/displayValue";
import { applySaveError, changedValues, formValues } from "@/lib/entity/recordForm";
import { useDeleteRecord, useSaveRecord } from "@/lib/entity/records";
import { useEntitySchema } from "@/lib/entity/useEntitySchema";
import type { ResolvedSection } from "@/lib/entity/resolveLayout";
import type { EntityRecord, RecordValues } from "@/lib/entity/types";
import { RecordForm } from "./RecordForm";
import styles from "./RecordDetail.module.css";

type RecordDetailProps = {
  config: EntityConfig;
  sections: ResolvedSection[];
  record: EntityRecord;
};

// One loaded record: header, the layout's sections, and inline editing. Owns the form
// and the edit / save / delete flow. Render it with key={record.id}, so moving to
// another record starts from a clean form.
export function RecordDetail({ config, sections, record }: RecordDetailProps) {
  const navigate = useNavigate();
  const form = useForm<RecordValues>({ defaultValues: formValues(sections, record) });
  const save = useSaveRecord(config.type, record.id);
  const remove = useDeleteRecord(config.type);
  // Already cached: the sections came from it. The subtitle's keys needn't be on the layout.
  const { data: schema } = useEntitySchema(config.type);
  const [isEditing, setIsEditing] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const { dirtyFields, isSubmitting } = form.formState;

  function startEdit() {
    // The record may have been refetched since the form was filled.
    form.reset(formValues(sections, record));
    setSaveError(null);
    setIsEditing(true);
  }

  function cancelEdit() {
    form.reset();
    setSaveError(null);
    setIsEditing(false);
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setSaveError(null);
    const changes = changedValues(values, dirtyFields);
    if (Object.keys(changes).length === 0) {
      setIsEditing(false);
      return;
    }
    try {
      await save.mutateAsync(changes);
      setIsEditing(false);
    } catch (err) {
      setSaveError(applySaveError(err, sections, form));
    }
  });

  async function handleDelete() {
    if (!confirm(`Delete this ${config.singular.toLowerCase()}?`)) return;
    try {
      await remove.mutateAsync(record.id);
      navigate(config.path);
    } catch (err) {
      alert(err instanceof Error ? err.message : `Failed to delete ${config.singular.toLowerCase()}`);
    }
  }

  return (
    <>
      <EntityHeader
        icon={config.icon}
        title={config.title(record)}
        subtitle={config.subtitleKeys.map((key) =>
          displayValue(schema?.fields.find((field) => field.key === key), record[key]),
        )}
        backLink={{ to: config.path, label: config.plural }}
        isEditing={isEditing}
        onEdit={startEdit}
        onDelete={handleDelete}
      />

      <div className={styles.body}>
        <div className={styles.leftCol}>
          <RecordForm form={form} sections={sections} record={record} isEditing={isEditing} onSubmit={onSubmit}>
            {isEditing && (
              <div className={styles.editBar}>
                {saveError && <div className={styles.errorBanner}>{saveError}</div>}
                <div className={styles.actions}>
                  <Button type="submit" variant="primary" size="sm" disabled={isSubmitting}>
                    {isSubmitting ? "Saving..." : "Save"}
                  </Button>
                  <Button type="button" variant="secondary" size="sm" onClick={cancelEdit} disabled={isSubmitting}>
                    Cancel
                  </Button>
                </div>
              </div>
            )}
          </RecordForm>
        </div>
      </div>
    </>
  );
}
