package com.myworkflow.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private Long parentId;
    /** DIR / MENU / BUTTON */
    private String menuType;
    private String menuName;
    private String path;
    private String icon;
    private String perm;
    private Integer visible;
    private Integer sortNo;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private List<SysMenu> children;
}
