// TypeScript mirror of /api/roles and /api/role-assignments. Fields go by key: labels and
// `required` come from the schema bundle, so the document holds values only.

import type { EntityType } from "@/lib/entity/types";

export type FieldAccess = { read: boolean; edit: boolean };

export type EntityPermissions = {
  canRead: boolean;
  canCreate: boolean;
  canEdit: boolean;
  canDelete: boolean;
  /** By field key: every field the entity has now. */
  fields: Record<string, FieldAccess>;
};

export type AppPermissions = {
  manageUsers: boolean;
  manageRoles: boolean;
  manageJobTitles: boolean;
  manageLayouts: boolean;
  manageCustomFields: boolean;
};

/** A row of the roles list. */
export type RoleSummary = {
  id: string;
  name: string;
  isAdmin: boolean;
  userCount: number;
};

export type Role = {
  id: string;
  name: string;
  isAdmin: boolean;
  app: AppPermissions;
  entities: Record<EntityType, EntityPermissions>;
  createdAt: string;
  updatedAt: string;
};

/**
 * Body of POST and PUT /api/roles. A field left out is closed on a POST, and keeps its
 * stored bits on a PUT.
 */
export type RoleRequest = Pick<Role, "name" | "app" | "entities">;

export type RoleAssignment = { userId: string; roleId: string };
