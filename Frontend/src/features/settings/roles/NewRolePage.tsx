import { useSearchParams } from "react-router";
import { ApiError } from "@/lib/api";
import { blankDraft, copyDraft, type EntityFields } from "@/lib/roles/roleDraft";
import { useCreateRole, useEntityFields, useRole } from "@/lib/roles/roles";
import type { Role } from "@/lib/roles/types";
import { newRoleSource } from "../paths";
import { RoleEditor, RoleMessage } from "./RoleEditor";

// /settings/roles/new, or ?from=<id> for a copy: the editor on a role that isn't stored
// yet. Nothing is written until Save, which creates the role and moves to its own page.
export default function NewRolePage() {
  const [params] = useSearchParams();
  const from = newRoleSource(params);
  const { fields, isLoading, error } = useEntityFields();

  if (isLoading) return <RoleMessage>Loading…</RoleMessage>;
  if (error || !fields) return <RoleMessage error>{error ? error.message : "Couldn't load the fields."}</RoleMessage>;

  // Keyed by query, so a different starting point starts a fresh draft.
  const key = params.toString();
  if (!from) return <NewRole key={key} fields={fields} />;
  return <CopiedRole key={key} from={from} fields={fields} />;
}

function CopiedRole({ from, fields }: { from: string; fields: EntityFields }) {
  const { data: source, isLoading, error } = useRole(from);

  if (isLoading) return <RoleMessage>Loading…</RoleMessage>;
  if (error instanceof ApiError && error.status === 404) {
    return <RoleMessage>The role to copy doesn't exist (it may have been deleted).</RoleMessage>;
  }
  if (error || !source) return <RoleMessage error>{error ? error.message : "Couldn't load the role."}</RoleMessage>;
  // Its stored bits don't track custom fields, so a copy would silently lack them.
  if (source.isAdmin) return <RoleMessage>The Administrator role can't be duplicated.</RoleMessage>;
  return <NewRole fields={fields} source={source} />;
}

function NewRole({ fields, source }: { fields: EntityFields; source?: Role }) {
  const create = useCreateRole();

  return (
    <RoleEditor
      fields={fields}
      initial={source ? copyDraft(source, fields) : blankDraft(fields)}
      isNew
      save={create.mutateAsync}
      // Once created, the editor is on its way to the role's own page: no second post.
      isSaving={create.isPending || create.isSuccess}
    />
  );
}
