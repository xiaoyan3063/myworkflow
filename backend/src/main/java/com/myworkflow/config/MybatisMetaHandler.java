package com.myworkflow.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.myworkflow.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MybatisMetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        Long userId = UserContext.currentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
        Long tenantId = UserContext.currentTenantId();
        if (getFieldValByName("tenantId", metaObject) == null && tenantId != null) {
            strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        }
        if (getFieldValByName("deleted", metaObject) == null) {
            strictInsertFill(metaObject, "deleted", Integer.class, 0);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContext.currentUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }
}
