package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String realName;
    private String module;
    private String title;
    private String requestUri;
    private String httpMethod;
    private String operParam;
    /** 1 成功，0 失败 */
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private String ip;
    private String userAgent;
    /** WEB / OPENAPI */
    private String source;
    /** 操作类型：工单业务为具体类型名，其余为用户管理、流程设计等 */
    private String ticketTypeName;
    private LocalDateTime createTime;
}
