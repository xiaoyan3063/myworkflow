package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {
    private Long id;
    private Long tenantId;
    private Long roleId;
    private Long menuId;
}
