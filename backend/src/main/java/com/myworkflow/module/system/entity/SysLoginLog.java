package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String realName;
    /** 1 成功，0 失败 */
    private Integer status;
    private String message;
    private String ip;
    /** WEB（网页端）/ APP / OTHER，由客户端声明或 User-Agent 推断 */
    private String clientType;
    private String userAgent;
    private LocalDateTime createTime;
}
