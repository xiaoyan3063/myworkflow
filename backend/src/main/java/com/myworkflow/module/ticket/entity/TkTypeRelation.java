package com.myworkflow.module.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_type_relation")
public class TkTypeRelation extends BaseEntity {
    private Long parentTypeId;
    private Long childTypeId;
    private String relationCode;
    private String relationName;
    /** 1：删除主单时级联终止并删除子单；0：子单保留并解除关联 */
    private Integer cascadeDelete;
    /** 明细条数下限，0 表示不限。节点配置里填了大于 0 的值时以节点为准 */
    private Integer minRows;
    /** 明细条数上限，0 表示不限 */
    private Integer maxRows;
    /** 1：主单发起提交时也校验下限；0：只在审批节点同意时校验 */
    private Integer checkMinOnStart;
    private Integer sortNo;
    private Integer status;

    @TableField(exist = false)
    private String childTypeCode;
    @TableField(exist = false)
    private String childTypeName;
    @TableField(exist = false)
    private String childProcessKey;
}
