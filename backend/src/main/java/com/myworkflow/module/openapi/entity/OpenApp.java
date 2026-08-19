package com.myworkflow.module.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_app")
public class OpenApp extends BaseEntity {
    private String appName;
    private String appKey;
    private String appSecret;
    private Integer status;
    private String callbackUrl;
    private String remark;
}
