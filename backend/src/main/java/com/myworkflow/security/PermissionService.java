package com.myworkflow.security;

import com.myworkflow.common.context.UserContext;
import com.myworkflow.module.system.entity.SysDept;
import com.myworkflow.module.system.entity.SysMenu;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysDeptMapper;
import com.myworkflow.module.system.mapper.SysMenuMapper;
import com.myworkflow.module.system.mapper.SysRoleMenuMapper;
import com.myworkflow.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_SELF = "SELF";

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    public void fillContext(UserContext ctx) {
        if (ctx == null) {
            return;
        }
        if (ctx.getUserId() != null) {
            SysUser user = userMapper.selectById(ctx.getUserId());
            if (user != null) {
                ctx.setDeptId(user.getDeptId());
                if (!StringUtils.hasText(ctx.getUsername())) {
                    ctx.setUsername(user.getUsername());
                }
                ctx.setRealName(user.getRealName());
            }
        }
        if (ctx.isAdmin()) {
            ctx.setDataScope(SCOPE_ALL);
            ctx.setPerms(allPerms());
            return;
        }
        ctx.setDataScope(resolveDataScope(ctx.getUserId()));
        if (SCOPE_DEPT.equals(ctx.getDataScope())) {
            ctx.setScopeUserIds(userIdsInDeptScope(ctx));
        }
        Set<String> perms = new HashSet<>();
        List<SysMenu> menus = ctx.getUserId() == null
                ? Collections.<SysMenu>emptyList()
                : menuMapper.selectByUserId(ctx.getUserId());
        for (SysMenu m : menus) {
            if (StringUtils.hasText(m.getPerm())) {
                perms.add(m.getPerm());
            }
        }
        ctx.setPerms(perms);
    }

    public boolean hasPerm(String perm) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            return false;
        }
        if (ctx.isAdmin()) {
            return true;
        }
        if (!StringUtils.hasText(perm)) {
            return true;
        }
        return ctx.getPerms() != null && ctx.getPerms().contains(perm);
    }

    public boolean allScope() {
        UserContext ctx = UserContext.get();
        return ctx == null || ctx.isAdmin() || SCOPE_ALL.equalsIgnoreCase(ctx.getDataScope());
    }

    public boolean selfScope() {
        UserContext ctx = UserContext.get();
        return ctx != null && !ctx.isAdmin() && SCOPE_SELF.equalsIgnoreCase(ctx.getDataScope());
    }

    public boolean deptScope() {
        UserContext ctx = UserContext.get();
        return ctx != null && !ctx.isAdmin() && SCOPE_DEPT.equalsIgnoreCase(ctx.getDataScope());
    }

    public List<Long> scopeUserIds() {
        UserContext ctx = UserContext.get();
        if (ctx == null || ctx.getScopeUserIds() == null) {
            return Collections.emptyList();
        }
        return ctx.getScopeUserIds();
    }

    /**
     * 本部门及下级部门的用户。不含上级（例如销售部经理看不到总部发起的单）。
     * 没有部门时退化为仅自己。
     */
    public List<Long> userIdsInDeptScope(UserContext ctx) {
        List<Long> ids = new ArrayList<>();
        if (ctx == null || ctx.getUserId() == null) {
            return ids;
        }
        ids.add(ctx.getUserId());
        Long deptId = ctx.getDeptId();
        if (deptId == null) {
            return ids;
        }
        Set<Long> deptIds = descendantDeptIds(deptId);
        deptIds.add(deptId);
        List<SysUser> users = userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .in(SysUser::getDeptId, deptIds)
                .eq(SysUser::getStatus, 1));
        Set<Long> seen = new HashSet<>(ids);
        for (SysUser u : users) {
            if (u.getId() != null && seen.add(u.getId())) {
                ids.add(u.getId());
            }
        }
        return ids;
    }

    /** 当前部门的下级，不含父级总部 */
    public Set<Long> descendantDeptIds(Long deptId) {
        Set<Long> out = new HashSet<>();
        if (deptId == null) {
            return out;
        }
        List<SysDept> all = deptMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getStatus, 1));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (SysDept d : all) {
                Long pid = d.getParentId();
                Long id = d.getId();
                if (id == null || out.contains(id) || id.equals(deptId)) {
                    continue;
                }
                if (deptId.equals(pid) || (pid != null && out.contains(pid))) {
                    out.add(id);
                    grew = true;
                }
            }
        }
        return out;
    }

    public List<SysMenu> menusForUser(Long userId, boolean admin) {
        if (admin) {
            return menuMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSortNo)
                    .orderByAsc(SysMenu::getId));
        }
        return userId == null ? Collections.<SysMenu>emptyList() : menuMapper.selectByUserId(userId);
    }

    public List<SysMenu> sidebarTree(Long userId, boolean admin) {
        List<SysMenu> all = menusForUser(userId, admin);
        List<SysMenu> visible = new ArrayList<>();
        for (SysMenu m : all) {
            if (m.getVisible() != null && m.getVisible() == 0) {
                continue;
            }
            if ("BUTTON".equals(m.getMenuType())) {
                continue;
            }
            visible.add(m);
        }
        return buildTree(visible, 0L);
    }

    public List<String> permsOf(List<SysMenu> menus) {
        List<String> perms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SysMenu m : menus) {
            if (StringUtils.hasText(m.getPerm()) && seen.add(m.getPerm())) {
                perms.add(m.getPerm());
            }
        }
        return perms;
    }

    public List<SysMenu> buildTree(List<SysMenu> all, Long parentId) {
        List<SysMenu> tree = new ArrayList<>();
        for (SysMenu m : all) {
            Long pid = m.getParentId() == null ? 0L : m.getParentId();
            if (pid.equals(parentId)) {
                m.setChildren(buildTree(all, m.getId()));
                tree.add(m);
            }
        }
        return tree;
    }

    private String resolveDataScope(Long userId) {
        if (userId == null) {
            return SCOPE_SELF;
        }
        List<String> scopes = roleMenuMapper.selectDataScopesByUserId(userId);
        if (scopes == null || scopes.isEmpty()) {
            return SCOPE_SELF;
        }
        for (String s : scopes) {
            if (!StringUtils.hasText(s) || SCOPE_ALL.equalsIgnoreCase(s)) {
                return SCOPE_ALL;
            }
        }
        for (String s : scopes) {
            if (SCOPE_DEPT.equalsIgnoreCase(s)) {
                return SCOPE_DEPT;
            }
        }
        return SCOPE_SELF;
    }

    private Set<String> allPerms() {
        Set<String> perms = new HashSet<>();
        List<SysMenu> all = menuMapper.selectList(null);
        for (SysMenu m : all) {
            if (StringUtils.hasText(m.getPerm())) {
                perms.add(m.getPerm());
            }
        }
        return perms;
    }
}
