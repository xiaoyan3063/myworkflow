package com.myworkflow.module.ticket.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.myworkflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_ticket_file")
public class TkTicketFile extends BaseEntity {
    private Long ticketId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    @JsonIgnore
    private String storagePath;
}
