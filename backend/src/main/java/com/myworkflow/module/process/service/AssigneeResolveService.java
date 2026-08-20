package com.myworkflow.module.process.service;

import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批人解析：支持 user / role / dept / starter / formField
 * 节点扩展属性 assigneeType + assigneeValue
 */
@Service
@RequiredArgsConstructor
public class AssigneeResolveService {

    private final SysUserMapper userMapper;

    /**
     * 供 BPMN 会签节点的多实例集合表达式调用，例如
     * flowable:collection="${assigneeResolveService.collect(execution,'role','MANAGER')}"
     */
    public List<String> collect(DelegateExecution execution, String assigneeType, String assigneeValue) {
        Object starter = execution.getVariable("starterId");
        Object fieldValue = "formField".equals(assigneeType) && StringUtils.hasText(assigneeValue)
                ? execution.getVariable(assigneeValue) : null;
        List<String> assignees = resolve(assigneeType, assigneeValue,
                starter == null ? null : String.valueOf(starter), fieldValue);
        if ("formField".equals(assigneeType) && assignees.isEmpty()) {
            throw new BizException("表单字段「" + assigneeValue + "」未选择审批人");
        }
        if (assignees.isEmpty() && starter != null) {
            assignees = Collections.singletonList(String.valueOf(starter));
        }
        return assignees;
    }

    public List<String> resolve(String assigneeType, String assigneeValue, String starterId) {
        return resolve(assigneeType, assigneeValue, starterId, null);
    }

    public List<String> resolve(String assigneeType, String assigneeValue,
                                String starterId, Object formFieldValue) {
        if (!StringUtils.hasText(assigneeType)) {
            assigneeType = "user";
        }
        List<String> result = new ArrayList<>();
        switch (assigneeType) {
            case "user":
                if (StringUtils.hasText(assigneeValue)) {
                    result.addAll(Arrays.asList(assigneeValue.split(",")));
                }
                break;
            case "role":
                if (StringUtils.hasText(assigneeValue)) {
                    for (String code : assigneeValue.split(",")) {
                        List<Long> ids = userMapper.selectUserIdsByRoleCode(code.trim());
                        result.addAll(ids.stream().map(String::valueOf).collect(Collectors.toList()));
                    }
                }
                break;
            case "dept":
                if (StringUtils.hasText(assigneeValue)) {
                    for (String deptId : assigneeValue.split(",")) {
                        List<Long> ids = userMapper.selectUserIdsByDeptId(Long.valueOf(deptId.trim()));
                        result.addAll(ids.stream().map(String::valueOf).collect(Collectors.toList()));
                    }
                }
                break;
            case "starter":
                if (StringUtils.hasText(starterId)) {
                    result.add(starterId);
                }
                break;
            case "formField":
                addFieldUsers(result, formFieldValue);
                break;
            default:
                if (StringUtils.hasText(assigneeValue)) {
                    result.addAll(Arrays.asList(assigneeValue.split(",")));
                }
        }
        return result.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    /** 人员字段既支持单个用户 ID，也支持多选产生的集合、数组或逗号分隔字符串。 */
    private void addFieldUsers(List<String> result, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection) {
            ((Collection<?>) value).forEach(item -> {
                if (item != null) result.add(String.valueOf(item));
            });
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                Object item = Array.get(value, i);
                if (item != null) result.add(String.valueOf(item));
            }
            return;
        }
        result.addAll(Arrays.asList(String.valueOf(value).split(",")));
    }

    public void ensureNotEmpty(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            throw new BizException("未解析到审批人，请检查节点配置");
        }
    }
}
