package com.buct.adminbackend.security;

/**
 * 权限码常量。Spring Security 中 authority 为 {@code PERM_} + code。
 */
public final class PermissionCodes {

    public static final String AUTHORITY_PREFIX = "PERM_";

    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_EDIT = "USER_EDIT";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_BAN = "USER_BAN";

    public static final String REVIEW_VIEW = "REVIEW_VIEW";
    public static final String REVIEW_ACTION = "REVIEW_ACTION";

    public static final String ARTIFACT_VIEW = "ARTIFACT_VIEW";
    public static final String ARTIFACT_EDIT = "ARTIFACT_EDIT";
    public static final String ARTIFACT_DELETE = "ARTIFACT_DELETE";
    public static final String ARTIFACT_IMPORT_EXPORT = "ARTIFACT_IMPORT_EXPORT";

    public static final String LOG_VIEW = "LOG_VIEW";
    public static final String STATS_VIEW = "STATS_VIEW";

    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
    public static final String PERMISSION_ASSIGN = "PERMISSION_ASSIGN";

    public static final String BACKUP_MANAGE = "BACKUP_MANAGE";
    public static final String ADMIN_MANAGE = "ADMIN_MANAGE";

    private PermissionCodes() {
    }

    public static String authority(String code) {
        return AUTHORITY_PREFIX + code;
    }
}
