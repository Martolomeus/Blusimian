import type React from "react";
import { FormProvider, type UseFormReturn } from "react-hook-form";
import type { ResolvedSection } from "@/lib/entity/resolveLayout";
import type { EntityRecord, RecordValues } from "@/lib/entity/types";
import { LayoutSection } from "./LayoutSection";

type RecordFormProps = {
  /** From useForm, with formValues(sections, record) as its default values. */
  form: UseFormReturn<RecordValues>;
  sections: ResolvedSection[];
  /** The record being shown. Absent when creating one. */
  record?: EntityRecord;
  isEditing: boolean;
  onSubmit: React.FormEventHandler<HTMLFormElement>;
  /** Lets a submit button outside the form (a modal footer) target it. */
  id?: string;
  /** Rendered inside the form, after the sections -- e.g. a Save / Cancel bar. */
  children?: React.ReactNode;
};

// A record drawn through a layout: every section, every field, as values or as inputs.
// Shared by the detail page (view + inline edit) and the create modal (always editing).
// The caller owns the form and what a submit does; this only lays it out.
export function RecordForm({ form, sections, record, isEditing, onSubmit, id, children }: RecordFormProps) {
  return (
    <FormProvider {...form}>
      <form id={id} onSubmit={onSubmit} noValidate>
        {/* Keyed by position: two sections may share a label. */}
        {sections.map((section, index) => (
          <LayoutSection key={index} section={section} record={record} isEditing={isEditing} />
        ))}
        {children}
      </form>
    </FormProvider>
  );
}
