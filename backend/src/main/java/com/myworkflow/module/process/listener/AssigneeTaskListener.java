package com.myworkflow.module.process.listener;

import com.myworkflow.common.exception.BizException;
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
    private Expression deptScope;
    private Expression fixedDeptId;

    public void setAssigneeType(Expression assigneeType) {
        this.assigneeType = assigneeType;
    }

    public void setAssigneeValue(Expression assigneeValue) {
        this.assigneeValue = assigneeValue;
    }

    public void setDeptScope(Expression deptScope) {
        this.deptScope = deptScope;
    }

    public void setFixedDeptId(Expression fixedDeptId) {
        this.fixedDeptId = fixedDeptId;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String type = assigneeType == null ? null : (String) assigneeType.getValue(delegateTask);
        String value = assigneeValue == null ? null : (String) assigneeValue.getValue(delegateTask);
        String scope = deptScope == null ? "ALL" : String.valueOf(deptScope.getValue(delegateTask));
        String deptId = fixedDeptId == null ? "" : String.valueOf(fixedDeptId.getValue(delegateTask));
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
        Object formFieldValue = "formField".equals(type) && value != null
                ? delegateTask.getVariable(value) : null;
        List<String> assignees = assigneeResolveService.resolve(
                type, value, starterId, formFieldValue, scope, deptId);

        // 多实例会签：每个实例已有 assignee 变量
        Object miAssignee = delegateTask.getVariable("assignee");
        if (miAssignee != null) {
            delegateTask.setAssignee(String.valueOf(miAssignee));
            return;
        }

        if (assignees.isEmpty()) {
            if ("formField".equals(type)) {
                throw new BizException("表单字段「" + value + "」未选择审批人");
            }
            throw new BizException("未找到下一审批节点「" + delegateTask.getName()
                    + "」的审批人，请联系系统管理员维护审批角色人员");
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
