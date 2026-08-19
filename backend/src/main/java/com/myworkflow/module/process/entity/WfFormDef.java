package com.myworkflow.module.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form_def")
public class WfFormDef extends BaseEntity {
    private String formKey;
    private String formName;
    private String formSchema;
    private Integer status;
    private String remark;
}
