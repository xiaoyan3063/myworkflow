package com.myworkflow.module.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import com.myworkflow.common.mybatis.JsonbMapTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "tk_list_ui", autoResultMap = true)
public class TkListUi extends BaseEntity {
    private Long typeId;
    private Integer version;
    @TableField(value = "schema", typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> schema;
}
