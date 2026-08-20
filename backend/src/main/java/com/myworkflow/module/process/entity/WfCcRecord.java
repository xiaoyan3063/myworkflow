package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_cc_record")
public class WfCcRecord extends BaseEntity {
    private String processInstId;
    private String taskId;
    private Long userId;
    private String title;
    private Integer readFlag;
    /** 抄送单据当前停留节点的处理人，实时查 Flowable 得出，不落库 */
    @TableField(exist = false)
    private String currentApprover;
}
