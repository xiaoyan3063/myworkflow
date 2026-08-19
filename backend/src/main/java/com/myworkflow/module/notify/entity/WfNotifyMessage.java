package com.myworkflow.module.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_notify_message")
public class WfNotifyMessage extends BaseEntity {
    private Long userId;
    private String title;
    private String content;
    private String msgType;
    private String bizId;
    private Integer readFlag;
}
