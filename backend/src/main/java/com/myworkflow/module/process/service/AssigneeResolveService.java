package com.myworkflow.module.process.service;

import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批人解析：支持 user / role / dept / starter / starterLeader
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
        List<String> assignees = resolve(assigneeType, assigneeValue, starter == null ? null : String.valueOf(starter));
        if (assignees.isEmpty() && starter != null) {
            assignees = Collections.singletonList(String.valueOf(starter));
        }
        return assignees;
    }

    public List<String> resolve(String assigneeType, String assigneeValue, String starterId) {
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
            default:
                if (StringUtils.hasText(assigneeValue)) {
                    result.addAll(Arrays.asList(assigneeValue.split(",")));
                }
        }
        return result.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    public void ensureNotEmpty(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            throw new BizException("未解析到审批人，请检查节点配置");
        }
    }
}
