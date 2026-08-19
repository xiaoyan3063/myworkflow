package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String realName;
    private String email;
    private String mobile;
    private Long deptId;
    /** 头像 */
    private String avatar;
    /** 1正常 0停用 */
    private Integer status;
    private Integer adminFlag;
}
