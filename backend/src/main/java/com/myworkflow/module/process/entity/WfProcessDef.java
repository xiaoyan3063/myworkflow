package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_def")
public class WfProcessDef extends BaseEntity {
    private String processKey;
    private String processName;
    private Long categoryId;
    private Long formId;
    private String icon;
    private String description;
    private Integer version;
    /** 0草稿 1已发布 2停用 */
    private Integer status;
    private String bpmnXml;
    private String flowableDeployId;
    private String flowableDefId;
}
