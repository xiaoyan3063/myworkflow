package com.myworkflow.module.process.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StartProcessRequest {
    private Long processDefId;
    private String processKey;
    private String businessKey;
    private String businessType;
    private String title;
    private Map<String, Object> formData;
    private String starterId;
    private String starterName;
}
