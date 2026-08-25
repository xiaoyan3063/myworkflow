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
    /** 绑定的已发布流程标识 */
    private String processKey;
    /** 工单号前缀，空则用 typeCode */
    private String noPrefix;
    /** 日期段，默认 yyyyMMdd */
    private String noDatePattern;
    /** 流水位数，默认 4 */
    private Integer noSeqLen;
    private Integer status;
    private String remark;
}
