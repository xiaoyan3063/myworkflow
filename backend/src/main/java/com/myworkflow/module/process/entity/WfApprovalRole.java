package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_role")
public class WfApprovalRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private Integer sortNo;
    private Integer status;
    private String remark;
}
