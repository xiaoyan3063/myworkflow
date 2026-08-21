package com.myworkflow.module.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_field")
public class TkField extends BaseEntity {
    private Long typeId;
    private String fieldKey;
    private String title;
    /** input / textarea / number / select / user / users */
    private String fieldType;
    private Integer required;
    private Integer listVisible;
    private Integer sortNo;
    /** 下拉选项 JSON 数组字符串 */
    private String optionsJson;
    private String remark;
}
