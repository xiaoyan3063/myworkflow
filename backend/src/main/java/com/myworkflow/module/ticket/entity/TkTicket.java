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
@TableName(value = "tk_ticket", autoResultMap = true)
public class TkTicket extends BaseEntity {
    private Long typeId;
    private String ticketNo;
    private String title;
    /** DRAFT / IN_APPROVAL / APPROVED / REJECTED / CANCELLED */
    private String status;
    private Long starterId;
    private String starterName;
    private String processInstId;
    /** 明细工单所属主单；为空时是普通主单 */
    private Long parentTicketId;
    /** 对应 tk_type_relation，区分同一主单下的多种明细 */
    private Long typeRelationId;
    /** 创建时写入的已发布表单 schema 版本，在途工单沿用 */
    private Integer schemaVersion;
    @TableField(value = "form_data", typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> formData;

    @TableField(exist = false)
    private String typeName;
    @TableField(exist = false)
    private String typeCode;
    @TableField(exist = false)
    private String processKey;
    @TableField(exist = false)
    private String currentApprover;
}
