package com.myworkflow.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.system.entity.SysDept;
import com.myworkflow.module.system.entity.SysMenu;
import com.myworkflow.module.system.entity.SysRole;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.entity.SysUserRole;
import com.myworkflow.module.system.entity.SysRoleMenu;
import com.myworkflow.module.system.mapper.SysDeptMapper;
import com.myworkflow.module.system.mapper.SysRoleMapper;
import com.myworkflow.module.system.mapper.SysRoleMenuMapper;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.myworkflow.module.system.mapper.SysUserRoleMapper;
import com.myworkflow.module.system.service.MenuService;
import com.myworkflow.security.RequiresPerm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Api(tags = "系统管理")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final MenuService menuService;
    private final PasswordEncoder passwordEncoder;

    @ApiOperation("用户分页")
    @GetMapping("/users")
    public R<PageResult<SysUser>> users(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long deptId) {
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>()
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getRealName, keyword))
                        .orderByDesc(SysUser::getCreateTime));
        p.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @ApiOperation("保存用户")
    @PostMapping("/users")
    public R<Void> saveUser(@RequestBody SysUser user) {
        if (user.getId() == null) {
            if (!StringUtils.hasText(user.getPassword())) {
                user.setPassword(passwordEncoder.encode("admin123"));
            } else {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            if (user.getStatus() == null) user.setStatus(1);
            if (user.getAdminFlag() == null) user.setAdminFlag(0);
            userMapper.insert(user);
        } else {
            SysUser db = userMapper.selectById(user.getId());
            if (db == null) throw new BizException("用户不存在");
            db.setRealName(user.getRealName());
            db.setEmail(user.getEmail());
            db.setMobile(user.getMobile());
            db.setDeptId(user.getDeptId());
            db.setStatus(user.getStatus());
            if (StringUtils.hasText(user.getPassword())) {
                db.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            userMapper.updateById(db);
        }
        return R.ok();
    }

    @ApiOperation("分配角色")
    @PostMapping("/users/{userId}/roles")
    public R<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setTenantId(userMapper.selectById(userId).getTenantId());
                userRoleMapper.insert(ur);
            }
        }
        return R.ok();
    }

    @ApiOperation("用户角色ID列表")
    @GetMapping("/users/{userId}/roles")
    public R<List<Long>> userRoles(@PathVariable Long userId) {
        List<SysUserRole> list = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        return R.ok(list.stream().map(SysUserRole::getRoleId).collect(Collectors.toList()));
    }

    @ApiOperation("部门树")
    @GetMapping("/depts/tree")
    public R<List<Map<String, Object>>> deptTree() {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSortNo));
        return R.ok(buildDeptTree(all, 0L));
    }

    @ApiOperation("保存部门")
    @PostMapping("/depts")
    public R<Void> saveDept(@RequestBody SysDept dept) {
        if (dept.getParentId() == null) dept.setParentId(0L);
        if (dept.getId() == null) {
            if (dept.getStatus() == null) dept.setStatus(1);
            deptMapper.insert(dept);
        } else {
            deptMapper.updateById(dept);
        }
        return R.ok();
    }

    @ApiOperation("删除部门")
    @DeleteMapping("/depts/{id}")
    public R<Void> deleteDept(@PathVariable Long id) {
        Long cnt = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (cnt > 0) throw new BizException("请先删除子部门");
        deptMapper.deleteById(id);
        return R.ok();
    }

    @ApiOperation("角色列表")
    @GetMapping("/roles")
    public R<List<SysRole>> roles() {
        return R.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortNo)));
    }

    @ApiOperation("保存角色")
    @RequiresPerm("sys:role")
    @PostMapping("/roles")
    public R<Void> saveRole(@RequestBody SysRole role) {
        if (!StringUtils.hasText(role.getDataScope())) {
            role.setDataScope("ALL");
        }
        String scope = role.getDataScope().trim().toUpperCase();
        if ("SELF".equals(scope) || "DEPT".equals(scope)) {
            role.setDataScope(scope);
        } else {
            role.setDataScope("ALL");
        }
        if (role.getId() == null) {
            if (role.getStatus() == null) role.setStatus(1);
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        return R.ok();
    }

    @ApiOperation("删除角色")
    @RequiresPerm("sys:role")
    @DeleteMapping("/roles/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        return R.ok();
    }

    @ApiOperation("角色菜单ID")
    @GetMapping("/roles/{id}/menus")
    public R<List<Long>> roleMenus(@PathVariable Long id) {
        return R.ok(menuService.roleMenuIds(id));
    }

    @ApiOperation("保存角色菜单")
    @RequiresPerm("sys:role")
    @PostMapping("/roles/{id}/menus")
    public R<Void> saveRoleMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        menuService.saveRoleMenus(id, menuIds);
        return R.ok();
    }

    @ApiOperation("菜单树")
    @GetMapping("/menus/tree")
    public R<List<SysMenu>> menuTree() {
        return R.ok(menuService.treeAll());
    }

    @ApiOperation("保存菜单")
    @RequiresPerm("sys:menu")
    @PostMapping("/menus")
    public R<SysMenu> saveMenu(@RequestBody SysMenu menu) {
        return R.ok(menuService.save(menu));
    }

    @ApiOperation("删除菜单")
    @RequiresPerm("sys:menu")
    @DeleteMapping("/menus/{id}")
    public R<Void> deleteMenu(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    @ApiOperation("用户精简列表（选人）")
    @GetMapping("/users/simple")
    public R<List<Map<String, Object>>> simpleUsers(@RequestParam(required = false) String keyword) {
        List<SysUser> list = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .last("LIMIT 50"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("deptId", u.getDeptId());
            result.add(m);
        }
        return R.ok(result);
    }

    private List<Map<String, Object>> buildDeptTree(List<SysDept> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (SysDept d : all) {
            if (Objects.equals(d.getParentId(), parentId)) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", d.getId());
                node.put("label", d.getDeptName());
                node.put("deptName", d.getDeptName());
                node.put("deptCode", d.getDeptCode());
                node.put("parentId", d.getParentId());
                node.put("sortNo", d.getSortNo());
                node.put("status", d.getStatus());
                node.put("children", buildDeptTree(all, d.getId()));
                tree.add(node);
            }
        }
        return tree;
    }
}
