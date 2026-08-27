package com.myworkflow.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.system.entity.SysMenu;
import com.myworkflow.module.system.entity.SysRole;
import com.myworkflow.module.system.entity.SysRoleMenu;
import com.myworkflow.module.system.mapper.SysMenuMapper;
import com.myworkflow.module.system.mapper.SysRoleMapper;
import com.myworkflow.module.system.mapper.SysRoleMenuMapper;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.mapper.TkTypeMapper;
import com.myworkflow.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    public static final long TICKET_DIR_ID = 30L;
    public static final long ADMIN_ROLE_ID = 1L;

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMapper roleMapper;
    private final TkTypeMapper typeMapper;
    private final PermissionService permissionService;

    public List<SysMenu> treeAll() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortNo).orderByAsc(SysMenu::getId));
        return permissionService.buildTree(all, 0L);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysMenu save(SysMenu menu) {
        if (!StringUtils.hasText(menu.getMenuName()) || !StringUtils.hasText(menu.getMenuType())) {
            throw new BizException("请填写名称和类型");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getSortNo() == null) {
            menu.setSortNo(0);
        }
        if (menu.getId() == null) {
            menuMapper.insert(menu);
            grantToAdmin(menu.getId());
        } else {
            menuMapper.updateById(menu);
        }
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long kids = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (kids != null && kids > 0) {
            throw new BizException("请先删除子菜单");
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    public List<Long> roleMenuIds(Long roleId) {
        List<SysRoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        List<Long> ids = new ArrayList<>();
        for (SysRoleMenu rm : list) {
            ids.add(rm.getMenuId());
        }
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null) {
            return;
        }
        SysRole role = roleMapper.selectById(roleId);
        Long tenantId = role == null || role.getTenantId() == null ? 0L : role.getTenantId();
        Set<Long> seen = new HashSet<>();
        for (Long menuId : menuIds) {
            if (menuId == null || !seen.add(menuId)) {
                continue;
            }
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            rm.setTenantId(tenantId);
            roleMenuMapper.insert(rm);
        }
    }

    /** 类型保存后挂到「工单」目录，路径 /tickets/{typeCode} */
    @Transactional(rollbackFor = Exception.class)
    public void syncTypeMenu(TkType type) {
        if (type == null || !StringUtils.hasText(type.getTypeCode())) {
            return;
        }
        ensureTicketDir();
        String path = "/tickets/" + type.getTypeCode().trim();
        SysMenu exist = menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path)
                .last("LIMIT 1"));
        if (exist == null) {
            SysMenu m = new SysMenu();
            m.setParentId(TICKET_DIR_ID);
            m.setMenuType("MENU");
            m.setMenuName(StringUtils.hasText(type.getTypeName()) ? type.getTypeName() : type.getTypeCode());
            m.setPath(path);
            m.setIcon("Document");
            m.setPerm("ticket:list");
            m.setVisible(type.getStatus() != null && type.getStatus() == 0 ? 0 : 1);
            m.setSortNo(20);
            m.setStatus(1);
            m.setRemark("ticket-type:" + type.getTypeCode());
            menuMapper.insert(m);
            grantToAdmin(m.getId());
        } else {
            exist.setMenuName(StringUtils.hasText(type.getTypeName()) ? type.getTypeName() : exist.getMenuName());
            exist.setVisible(type.getStatus() != null && type.getStatus() == 0 ? 0 : 1);
            menuMapper.updateById(exist);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeTypeMenu(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return;
        }
        String path = "/tickets/" + typeCode.trim();
        SysMenu exist = menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path)
                .last("LIMIT 1"));
        if (exist != null) {
            menuMapper.deleteById(exist.getId());
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, exist.getId()));
        }
    }

    public void syncAllTypeMenus() {
        List<TkType> types = typeMapper.selectList(new LambdaQueryWrapper<TkType>().eq(TkType::getStatus, 1));
        for (TkType t : types) {
            try {
                syncTypeMenu(t);
            } catch (Exception e) {
                log.warn("同步工单类型菜单失败 {}: {}", t.getTypeCode(), e.getMessage());
            }
        }
    }

    public void ensureBaseMenus() {
        Long cnt = menuMapper.selectCount(null);
        if (cnt == null || cnt == 0) {
            seed();
        }
        ensureTicketDir();
        ensureSystemLogMenu();
        syncAllTypeMenus();
        grantAllToAdminIfEmpty();
        ensureSalesRole();
    }

    private void ensureTicketDir() {
        if (menuMapper.selectById(TICKET_DIR_ID) == null) {
            insert(TICKET_DIR_ID, 0L, "DIR", "工单", null, "Document", null, 30);
        }
    }

    private void ensureSystemLogMenu() {
        SysMenu system = menuMapper.selectById(40L);
        if (system != null && !"系统管理".equals(system.getMenuName())) {
            system.setMenuName("系统管理");
            menuMapper.updateById(system);
        }
        if (menuMapper.selectById(46L) == null) {
            insert(46L, 40L, "MENU", "系统日志", "/system-logs", "Document", "sys:log", 5);
        }
        grantToAdmin(46L);
    }

    private void grantToAdmin(Long menuId) {
        Long exists = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, ADMIN_ROLE_ID)
                .eq(SysRoleMenu::getMenuId, menuId));
        if (exists != null && exists > 0) {
            return;
        }
        if (roleMapper.selectById(ADMIN_ROLE_ID) == null) {
            return;
        }
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(ADMIN_ROLE_ID);
        rm.setMenuId(menuId);
        rm.setTenantId(1L);
        roleMenuMapper.insert(rm);
    }

    private void grantAllToAdminIfEmpty() {
        Long n = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, ADMIN_ROLE_ID));
        if (n != null && n > 0) {
            return;
        }
        List<SysMenu> all = menuMapper.selectList(null);
        for (SysMenu m : all) {
            grantToAdmin(m.getId());
        }
        grantApprovalToRole(2L);
        grantApprovalToRole(3L);
    }

    private void grantApprovalToRole(Long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            return;
        }
        Long n = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (n != null && n > 0) {
            return;
        }
        List<Long> ids = Arrays.asList(1L, 10L, 11L, 12L, 13L, 14L, 15L, 45L);
        for (Long id : ids) {
            if (menuMapper.selectById(id) == null) {
                continue;
            }
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(id);
            rm.setTenantId(1L);
            roleMenuMapper.insert(rm);
        }
    }

    private void ensureSalesRole() {
        Long n = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "SALES"));
        if (n != null && n > 0) {
            return;
        }
        SysRole sales = new SysRole();
        sales.setTenantId(1L);
        sales.setRoleCode("SALES");
        sales.setRoleName("销售");
        sales.setSortNo(10);
        sales.setStatus(1);
        sales.setDataScope("SELF");
        sales.setRemark("示例：只能看自己的工单；再勾选某种工单菜单即可");
        roleMapper.insert(sales);
    }

    private void seed() {
        insert(1L, 0L, "MENU", "工作台", "/dashboard", "Odometer", null, 1);
        insert(10L, 0L, "DIR", "审批中心", null, "Checked", null, 10);
        insert(11L, 10L, "MENU", "我的待办", "/todo", null, null, 1);
        insert(12L, 10L, "MENU", "我的已办", "/done", null, null, 2);
        insert(13L, 10L, "MENU", "我发起的", "/started", null, null, 3);
        insert(14L, 10L, "MENU", "抄送我的", "/cc", null, null, 4);
        insert(15L, 10L, "MENU", "发起审批", "/start", null, null, 5);
        insert(20L, 0L, "DIR", "流程设计", null, "SetUp", "process:manage", 20);
        insert(21L, 20L, "MENU", "流程管理", "/process", null, "process:manage", 1);
        insert(22L, 20L, "MENU", "表单管理", "/forms", null, "process:manage", 2);
        insert(TICKET_DIR_ID, 0L, "DIR", "工单", null, "Document", null, 30);
        insert(31L, TICKET_DIR_ID, "MENU", "工单类型", "/ticket-types", null, "ticket:type", 1);
        insert(32L, TICKET_DIR_ID, "MENU", "工单总表", "/tickets", null, "ticket:list", 2);
        insert(33L, TICKET_DIR_ID, "BUTTON", "新建工单", null, null, "ticket:create", 81);
        insert(34L, TICKET_DIR_ID, "BUTTON", "编辑工单", null, null, "ticket:update", 82);
        insert(35L, TICKET_DIR_ID, "BUTTON", "提交审批", null, null, "ticket:submit", 83);
        insert(36L, TICKET_DIR_ID, "BUTTON", "删除草稿", null, null, "ticket:delete", 84);
        insert(37L, TICKET_DIR_ID, "BUTTON", "保存类型/设计", null, null, "ticket:type:save", 85);
        insert(38L, TICKET_DIR_ID, "BUTTON", "删除类型", null, null, "ticket:type:delete", 86);
        insert(40L, 0L, "DIR", "系统管理", null, "OfficeBuilding", null, 40);
        insert(41L, 40L, "MENU", "用户管理", "/users", null, "sys:user", 1);
        insert(42L, 40L, "MENU", "部门管理", "/depts", null, "sys:dept", 2);
        insert(43L, 40L, "MENU", "角色管理", "/roles", null, "sys:role", 3);
        insert(44L, 40L, "MENU", "菜单管理", "/menus", null, "sys:menu", 4);
        insert(46L, 40L, "MENU", "系统日志", "/system-logs", "Document", "sys:log", 5);
        insert(45L, 0L, "MENU", "消息中心", "/messages", "Bell", null, 90);
        log.info("已写入默认菜单");
    }

    private void insert(Long id, Long parentId, String type, String name, String path, String icon, String perm, int sort) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setTenantId(1L);
        m.setParentId(parentId);
        m.setMenuType(type);
        m.setMenuName(name);
        m.setPath(path);
        m.setIcon(icon);
        m.setPerm(perm);
        m.setVisible(1);
        m.setSortNo(sort);
        m.setStatus(1);
        m.setDeleted(0);
        menuMapper.insert(m);
    }
}
