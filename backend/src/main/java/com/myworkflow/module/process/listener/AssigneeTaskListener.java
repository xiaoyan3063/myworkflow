package com.myworkflow.module.process.listener;

import com.myworkflow.module.process.service.AssigneeResolveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户任务创建时解析审批人。
 * 在 BPMN 中配置：
 * flowable:taskListener event="create" delegateExpression="${assigneeTaskListener}"
 * 扩展属性：assigneeType / assigneeValue
 * 会签：多实例 collection=${assigneeList} elementVariable=assignee
 */
@Slf4j
@Component("assigneeTaskListener")
@RequiredArgsConstructor
public class AssigneeTaskListener implements TaskListener {

    private final AssigneeResolveService assigneeResolveService;

    private Expression assigneeType;
    private Expression assigneeValue;

    public void setAssigneeType(Expression assigneeType) {
        this.assigneeType = assigneeType;
    }

    public void setAssigneeValue(Expression assigneeValue) {
        this.assigneeValue = assigneeValue;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String type = assigneeType == null ? null : (String) assigneeType.getValue(delegateTask);
        String value = assigneeValue == null ? null : (String) assigneeValue.getValue(delegateTask);
        // 也可从扩展属性读取
        if (type == null) {
            type = (String) delegateTask.getVariableLocal("assigneeType");
        }
        if (value == null) {
            value = (String) delegateTask.getVariableLocal("assigneeValue");
        }
        if (type == null) {
            Object extType = delegateTask.getVariable("nodeAssigneeType_" + delegateTask.getTaskDefinitionKey());
            if (extType != null) type = String.valueOf(extType);
        }
        if (value == null) {
            Object extVal = delegateTask.getVariable("nodeAssigneeValue_" + delegateTask.getTaskDefinitionKey());
            if (extVal != null) value = String.valueOf(extVal);
        }

        String starterId = String.valueOf(delegateTask.getVariable("starterId"));
        List<String> assignees = assigneeResolveService.resolve(type, value, starterId);

        // 多实例会签：每个实例已有 assignee 变量
        Object miAssignee = delegateTask.getVariable("assignee");
        if (miAssignee != null) {
            delegateTask.setAssignee(String.valueOf(miAssignee));
            return;
        }

        if (assignees.isEmpty()) {
            log.warn("任务 {} 未配置审批人，回退到发起人", delegateTask.getName());
            delegateTask.setAssignee(starterId);
            return;
        }
        if (assignees.size() == 1) {
            delegateTask.setAssignee(assignees.get(0));
        } else {
            for (String u : assignees) {
                delegateTask.addCandidateUser(u);
            }
        }
    }
}
