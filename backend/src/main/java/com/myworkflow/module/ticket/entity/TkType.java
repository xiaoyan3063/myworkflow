package com.myworkflow.module.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_type")
public class TkType extends BaseEntity {
    private String typeCode;
    private String typeName;
    /** 绑定的已发布流程标识，本步只存不发起 */
    private String processKey;
    private Integer status;
    private String remark;
}
