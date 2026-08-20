package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance_ext")
public class WfProcessInstanceExt extends BaseEntity {
    private String processInstId;
    private Long processDefId;
    private String processKey;
    private String businessKey;
    private String businessType;
    private String title;
    private Long starterId;
    private String starterName;
    /** RUNNING / COMPLETED / REJECTED / CANCELLED */
    private String status;
    private String formData;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 当前停留节点的处理人，实时查 Flowable 得出，不落库 */
    @TableField(exist = false)
    private String currentApprover;
}
