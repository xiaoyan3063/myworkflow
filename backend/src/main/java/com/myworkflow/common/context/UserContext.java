package com.myworkflow.common.context;

import lombok.Data;

@Data
public class UserContext {

    private Long userId;
    private String username;
    private String realName;
    private Long tenantId;
    private Long deptId;
    private boolean admin;
    private String dataScope;
    private java.util.Set<String> perms;
    /** DEPT 范围下允许看到的发起人 ID（本部门及下级，不含上级/总部） */
    private java.util.List<Long> scopeUserIds;

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        UserContext ctx = get();
        return ctx == null ? null : ctx.userId;
    }

    public static Long currentTenantId() {
        UserContext ctx = get();
        return ctx == null ? null : ctx.tenantId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
