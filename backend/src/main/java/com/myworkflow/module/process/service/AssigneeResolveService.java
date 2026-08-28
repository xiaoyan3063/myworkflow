package com.myworkflow.module.process.service;

import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.process.entity.WfApprovalRole;
import com.myworkflow.module.process.entity.WfApprovalRoleUser;
import com.myworkflow.module.process.mapper.WfApprovalRoleMapper;
import com.myworkflow.module.process.mapper.WfApprovalRoleUserMapper;
import com.myworkflow.module.system.entity.SysDept;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysDeptMapper;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批人解析：支持 user / approvalRole / dept / starter / formField；
 * role 仅兼容改造前已发布的系统角色节点。
 * 节点扩展属性 assigneeType + assigneeValue
 */
@Service
@RequiredArgsConstructor
public class AssigneeResolveService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final WfApprovalRoleMapper approvalRoleMapper;
    private final WfApprovalRoleUserMapper approvalRoleUserMapper;

    /**
     * 供 BPMN 会签节点的多实例集合表达式调用，例如
     * flowable:collection="${assigneeResolveService.collect(execution,'role','MANAGER')}"
     */
    public List<String> collect(DelegateExecution execution, String assigneeType, String assigneeValue) {
        return collect(execution, assigneeType, assigneeValue, "ALL", "");
    }

    public List<String> collect(DelegateExecution execution, String assigneeType, String assigneeValue,
                                String deptScope, String fixedDeptId) {
        Object starter = execution.getVariable("starterId");
        Object fieldValue = "formField".equals(assigneeType) && StringUtils.hasText(assigneeValue)
                ? execution.getVariable(assigneeValue) : null;
        List<String> assignees = resolve(assigneeType, assigneeValue,
                starter == null ? null : String.valueOf(starter), fieldValue, deptScope, fixedDeptId);
        if ("formField".equals(assigneeType) && assignees.isEmpty()) {
            throw new BizException("表单字段「" + assigneeValue + "」未选择审批人");
        }
        if (assignees.isEmpty()) {
            throw new BizException("未找到下一审批节点的审批人，请联系系统管理员维护审批角色人员");
        }
        return assignees;
    }

    public List<String> resolve(String assigneeType, String assigneeValue, String starterId) {
        return resolve(assigneeType, assigneeValue, starterId, null);
    }

    public List<String> resolve(String assigneeType, String assigneeValue,
                                String starterId, Object formFieldValue) {
        return resolve(assigneeType, assigneeValue, starterId, formFieldValue, "ALL", "");
    }

    public List<String> resolve(String assigneeType, String assigneeValue,
                                String starterId, Object formFieldValue,
                                String deptScope, String fixedDeptId) {
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
            case "approvalRole":
                if (StringUtils.hasText(assigneeValue)) {
                    for (String code : assigneeValue.split(",")) {
                        List<Long> ids = approvalRoleUsers(code.trim(), starterId, deptScope, fixedDeptId);
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

    private List<Long> approvalRoleUsers(String roleCode, String starterId,
                                         String deptScope, String fixedDeptId) {
        SysUser starter = null;
        try {
            starter = StringUtils.hasText(starterId) ? userMapper.selectById(Long.valueOf(starterId)) : null;
        } catch (NumberFormatException ignored) {
            // 非系统用户发起时只能依赖全局唯一的角色编码
        }
        LambdaQueryWrapper<WfApprovalRole> query = new LambdaQueryWrapper<WfApprovalRole>()
                .eq(WfApprovalRole::getRoleCode, roleCode)
                .eq(starter != null && starter.getTenantId() != null,
                        WfApprovalRole::getTenantId, starter == null ? null : starter.getTenantId())
                .last("LIMIT 1");
        WfApprovalRole role = approvalRoleMapper.selectOne(query);
        if (role == null) {
            return Collections.emptyList();
        }
        if (role.getStatus() == null || role.getStatus() != 1) {
            return Collections.emptyList();
        }
        List<WfApprovalRoleUser> links = approvalRoleUserMapper.selectList(
                new LambdaQueryWrapper<WfApprovalRoleUser>()
                        .eq(WfApprovalRoleUser::getTenantId, role.getTenantId())
                        .eq(WfApprovalRoleUser::getRoleId, role.getId()));
        Set<Long> allowedDeptIds = allowedDeptIds(starterId, deptScope, fixedDeptId);
        List<Long> result = new ArrayList<>();
        for (WfApprovalRoleUser link : links) {
            SysUser user = userMapper.selectById(link.getUserId());
            if (user == null || !Objects.equals(user.getTenantId(), role.getTenantId())
                    || user.getStatus() == null || user.getStatus() != 1) {
                continue;
            }
            if (allowedDeptIds == null || allowedDeptIds.contains(user.getDeptId())) {
                result.add(user.getId());
            }
        }
        return result;
    }

    /** null 表示不限制部门；空集合表示配置有效但没有可匹配部门。 */
    private Set<Long> allowedDeptIds(String starterId, String deptScope, String fixedDeptId) {
        if (!StringUtils.hasText(deptScope) || "ALL".equals(deptScope)) {
            return null;
        }
        if ("FIXED_DEPT".equals(deptScope)) {
            Set<Long> ids = new HashSet<>();
            if (StringUtils.hasText(fixedDeptId)) {
                for (String value : fixedDeptId.split(",")) {
                    if (StringUtils.hasText(value)) {
                        ids.add(Long.valueOf(value.trim()));
                    }
                }
            }
            return ids;
        }
        SysUser starter = null;
        try {
            starter = StringUtils.hasText(starterId) ? userMapper.selectById(Long.valueOf(starterId)) : null;
        } catch (NumberFormatException ignored) {
            // 发起人不是系统用户时无法按组织匹配
        }
        if (starter == null || starter.getDeptId() == null) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        if ("SAME_DEPT".equals(deptScope) || "SAME_AND_PARENT".equals(deptScope)) {
            ids.add(starter.getDeptId());
        }
        if ("PARENT_DEPTS".equals(deptScope) || "SAME_AND_PARENT".equals(deptScope)) {
            Long current = starter.getDeptId();
            Set<Long> visited = new HashSet<>();
            while (current != null && current != 0L && visited.add(current)) {
                SysDept dept = deptMapper.selectById(current);
                if (dept == null || dept.getParentId() == null || dept.getParentId() == 0L) {
                    break;
                }
                current = dept.getParentId();
                ids.add(current);
            }
        }
        return ids;
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
