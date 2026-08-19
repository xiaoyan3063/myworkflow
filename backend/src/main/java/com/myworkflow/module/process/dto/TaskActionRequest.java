package com.myworkflow.module.process.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Data
public class TaskActionRequest {
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    private String comment;
    private Map<String, Object> variables;
    private Map<String, Object> formData;
    /** 驳回方式：TERMINATE 终止流程；ACTIVITY 回退到 rejectToActivityId 指定的节点 */
    private String rejectMode;
    /** 驳回回退的目标节点，仅 rejectMode=ACTIVITY 时生效 */
    private String rejectToActivityId;
    /** 转办目标用户 */
    private String transferUserId;
    /** 加签用户 */
    private List<String> addSignUserIds;
    /** 抄送人 */
    private List<Long> ccUserIds;
}
