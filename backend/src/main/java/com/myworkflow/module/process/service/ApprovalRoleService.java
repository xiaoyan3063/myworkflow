package com.myworkflow.module.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.process.entity.WfApprovalRole;
import com.myworkflow.module.process.entity.WfApprovalRoleUser;
import com.myworkflow.module.process.mapper.WfApprovalRoleMapper;
import com.myworkflow.module.process.mapper.WfApprovalRoleUserMapper;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ApprovalRoleService {

    private final WfApprovalRoleMapper roleMapper;
    private final WfApprovalRoleUserMapper roleUserMapper;
    private final WfProcessDefMapper processDefMapper;
    private final SysUserMapper userMapper;

    public List<WfApprovalRole> list(boolean enabledOnly) {
        return roleMapper.selectList(new LambdaQueryWrapper<WfApprovalRole>()
                .eq(WfApprovalRole::getTenantId, tenantId())
                .eq(enabledOnly, WfApprovalRole::getStatus, 1)
                .orderByAsc(WfApprovalRole::getSortNo)
                .orderByAsc(WfApprovalRole::getRoleName));
    }

    @Transactional(rollbackFor = Exception.class)
    public WfApprovalRole save(WfApprovalRole input) {
        if (!StringUtils.hasText(input.getRoleCode()) || !StringUtils.hasText(input.getRoleName())) {
            throw new BizException("请填写审批角色编码和名称");
        }
        String code = input.getRoleCode().trim().toUpperCase();
        Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<WfApprovalRole>()
                .eq(WfApprovalRole::getTenantId, tenantId())
                .eq(WfApprovalRole::getRoleCode, code)
                .ne(input.getId() != null, WfApprovalRole::getId, input.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BizException("审批角色编码已存在");
        }
        if (input.getId() == null) {
            input.setTenantId(tenantId());
            input.setRoleCode(code);
            input.setStatus(input.getStatus() == null ? 1 : input.getStatus());
            input.setSortNo(input.getSortNo() == null ? 0 : input.getSortNo());
            roleMapper.insert(input);
            return input;
        }
        WfApprovalRole db = requiredRole(input.getId());
        // 已发布流程按编码引用角色，避免编辑编码导致在途和历史流程失效
        if (!Objects.equals(db.getRoleCode(), code)) {
            throw new BizException("审批角色编码被流程引用，创建后不能修改");
        }
        db.setRoleName(input.getRoleName().trim());
        db.setSortNo(input.getSortNo() == null ? 0 : input.getSortNo());
        db.setStatus(input.getStatus() == null ? 1 : input.getStatus());
        db.setRemark(input.getRemark());
        roleMapper.updateById(db);
        return db;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WfApprovalRole role = requiredRole(id);
        Long references = processDefMapper.selectCount(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getTenantId, tenantId())
                .like(WfProcessDef::getBpmnXml, role.getRoleCode()));
        if (references != null && references > 0) {
            throw new BizException("该审批角色已被流程引用，不能删除；可将状态改为停用");
        }
        roleUserMapper.delete(new LambdaQueryWrapper<WfApprovalRoleUser>()
                .eq(WfApprovalRoleUser::getTenantId, tenantId())
                .eq(WfApprovalRoleUser::getRoleId, id));
        roleMapper.deleteById(id);
    }

    public List<Map<String, Object>> members(Long roleId) {
        requiredRole(roleId);
        List<WfApprovalRoleUser> links = roleUserMapper.selectList(
                new LambdaQueryWrapper<WfApprovalRoleUser>()
                        .eq(WfApprovalRoleUser::getTenantId, tenantId())
                        .eq(WfApprovalRoleUser::getRoleId, roleId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WfApprovalRoleUser link : links) {
            SysUser user = userMapper.selectById(link.getUserId());
            if (user == null || !Objects.equals(user.getTenantId(), tenantId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            row.put("deptId", user.getDeptId());
            row.put("status", user.getStatus());
            result.add(row);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMembers(Long roleId, List<Long> userIds) {
        requiredRole(roleId);
        roleUserMapper.delete(new LambdaQueryWrapper<WfApprovalRoleUser>()
                .eq(WfApprovalRoleUser::getTenantId, tenantId())
                .eq(WfApprovalRoleUser::getRoleId, roleId));
        if (userIds == null) {
            return;
        }
        userIds.stream().filter(Objects::nonNull).distinct().forEach(userId -> {
            SysUser user = userMapper.selectById(userId);
            if (user == null || !Objects.equals(user.getTenantId(), tenantId())) {
                throw new BizException("所选用户不存在或不属于当前租户");
            }
            WfApprovalRoleUser link = new WfApprovalRoleUser();
            link.setTenantId(tenantId());
            link.setRoleId(roleId);
            link.setUserId(userId);
            roleUserMapper.insert(link);
        });
    }

    private WfApprovalRole requiredRole(Long id) {
        WfApprovalRole role = roleMapper.selectOne(new LambdaQueryWrapper<WfApprovalRole>()
                .eq(WfApprovalRole::getId, id)
                .eq(WfApprovalRole::getTenantId, tenantId()));
        if (role == null) {
            throw new BizException("审批角色不存在");
        }
        return role;
    }

    private long tenantId() {
        Long id = UserContext.currentTenantId();
        return id == null ? 0L : id;
    }
}
