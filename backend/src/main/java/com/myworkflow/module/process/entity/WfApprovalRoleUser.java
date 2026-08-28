package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("wf_approval_role_user")
public class WfApprovalRoleUser implements Serializable {

    private Long id;
    private Long tenantId;
    private Long roleId;
    private Long userId;
}
