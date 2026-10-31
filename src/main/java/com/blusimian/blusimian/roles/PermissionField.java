package com.blusimian.blusimian.roles;

/** One field as a permission table sees it: its API key and its two columns. */
record PermissionField(String key, boolean required, String readColumn, String editColumn) {

    static PermissionField of(String key, boolean required, String columnPrefix) {
        return new PermissionField(key, required, readColumn(columnPrefix), editColumn(columnPrefix));
    }

    static String readColumn(String columnPrefix) {
        return columnPrefix + "_read";
    }

    static String editColumn(String columnPrefix) {
        return columnPrefix + "_edit";
    }
}
