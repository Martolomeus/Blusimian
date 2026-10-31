package com.blusimian.blusimian.roles.dto;

import com.blusimian.blusimian.roles.AppPermission;

/** The role document's app-wide permissions. */
public record AppPermissions(
        boolean manageUsers,
        boolean manageRoles,
        boolean manageJobTitles,
        boolean manageLayouts,
        boolean manageCustomFields
) {
    public static final AppPermissions ALL = new AppPermissions(true, true, true, true, true);
    public static final AppPermissions NONE = new AppPermissions(false, false, false, false, false);

    public static AppPermissions from(AppPermission row) {
        return new AppPermissions(row.isManageUsers(), row.isManageRoles(), row.isManageJobTitles(),
                row.isManageLayouts(), row.isManageCustomFields());
    }
}
