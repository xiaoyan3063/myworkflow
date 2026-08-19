package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private Long parentId;
    private String deptName;
    private String deptCode;
    private Integer sortNo;
    private Integer status;
    private String leaderId;
}
