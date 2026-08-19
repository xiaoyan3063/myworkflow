package com.myworkflow.config;

import com.myworkflow.module.openapi.entity.OpenApp;
import com.myworkflow.module.openapi.mapper.OpenAppMapper;
import com.myworkflow.module.process.entity.WfFormDef;
import com.myworkflow.module.process.entity.WfProcessCategory;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.mapper.WfFormDefMapper;
import com.myworkflow.module.process.mapper.WfProcessCategoryMapper;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.service.ProcessDefService;
import com.myworkflow.module.system.entity.*;
import com.myworkflow.module.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final SysTenantMapper tenantMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final WfProcessCategoryMapper categoryMapper;
    private final WfFormDefMapper formDefMapper;
    private final WfProcessDefMapper processDefMapper;
    private final ProcessDefService processDefService;
    private final OpenAppMapper openAppMapper;

    @Override
    public void run(String... args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);

        if (tenantMapper.selectCount(null) > 0) {
            return;
        }
        log.info("初始化演示数据...");

        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setTenantId(0L);
        tenant.setTenantCode("default");
        tenant.setTenantName("默认租户");
        tenant.setStatus(1);
        tenant.setDeleted(0);
        tenantMapper.insert(tenant);

        SysDept dept = new SysDept();
        dept.setId(1L);
        dept.setTenantId(1L);
        dept.setParentId(0L);
        dept.setDeptName("总部");
        dept.setDeptCode("HQ");
        dept.setSortNo(1);
        dept.setStatus(1);
        dept.setDeleted(0);
        deptMapper.insert(dept);

        SysDept dept2 = new SysDept();
        dept2.setId(2L);
        dept2.setTenantId(1L);
        dept2.setParentId(1L);
        dept2.setDeptName("销售部");
        dept2.setDeptCode("SALES");
        dept2.setSortNo(2);
        dept2.setStatus(1);
        dept2.setDeleted(0);
        deptMapper.insert(dept2);

        SysRole adminRole = new SysRole();
        adminRole.setId(1L);
        adminRole.setTenantId(1L);
        adminRole.setRoleCode("ADMIN");
        adminRole.setRoleName("系统管理员");
        adminRole.setSortNo(1);
        adminRole.setStatus(1);
        adminRole.setDeleted(0);
        roleMapper.insert(adminRole);

        SysRole managerRole = new SysRole();
        managerRole.setId(2L);
        managerRole.setTenantId(1L);
        managerRole.setRoleCode("MANAGER");
        managerRole.setRoleName("部门经理");
        managerRole.setSortNo(2);
        managerRole.setStatus(1);
        managerRole.setDeleted(0);
        roleMapper.insert(managerRole);

        SysRole empRole = new SysRole();
        empRole.setId(3L);
        empRole.setTenantId(1L);
        empRole.setRoleCode("EMPLOYEE");
        empRole.setRoleName("普通员工");
        empRole.setSortNo(3);
        empRole.setStatus(1);
        empRole.setDeleted(0);
        roleMapper.insert(empRole);

        SysUser admin = buildUser(1L, "admin", "系统管理员", 1L, 1);
        SysUser manager = buildUser(2L, "manager", "张经理", 2L, 0);
        SysUser emp = buildUser(3L, "zhangsan", "张三", 2L, 0);
        userMapper.insert(admin);
        userMapper.insert(manager);
        userMapper.insert(emp);

        bindRole(1L, 1L, 1L);
        bindRole(2L, 2L, 2L);
        bindRole(3L, 3L, 3L);

        WfProcessCategory cat = new WfProcessCategory();
        cat.setId(1L);
        cat.setTenantId(1L);
        cat.setCategoryName("通用审批");
        cat.setSortNo(1);
        cat.setDeleted(0);
        categoryMapper.insert(cat);

        WfFormDef form = new WfFormDef();
        form.setId(1L);
        form.setTenantId(1L);
        form.setFormKey("leave_form");
        form.setFormName("请假申请单");
        form.setFormSchema("[{\"type\":\"input\",\"field\":\"title\",\"title\":\"标题\",\"value\":\"\",\"props\":{\"placeholder\":\"请输入标题\"}},{\"type\":\"select\",\"field\":\"leaveType\",\"title\":\"请假类型\",\"options\":[{\"label\":\"年假\",\"value\":\"annual\"},{\"label\":\"事假\",\"value\":\"personal\"},{\"label\":\"病假\",\"value\":\"sick\"}]},{\"type\":\"number\",\"field\":\"days\",\"title\":\"天数\",\"value\":1},{\"type\":\"textarea\",\"field\":\"reason\",\"title\":\"事由\",\"props\":{\"rows\":3}}]");
        form.setStatus(1);
        form.setDeleted(0);
        formDefMapper.insert(form);

        OpenApp app = new OpenApp();
        app.setId(1L);
        app.setTenantId(1L);
        app.setAppName("CRM演示应用");
        app.setAppKey("crm_demo_key");
        app.setAppSecret("crm_demo_secret_change_me");
        app.setStatus(1);
        app.setCallbackUrl("");
        app.setDeleted(0);
        openAppMapper.insert(app);

        try {
            String bpmn = StreamUtils.copyToString(
                    new ClassPathResource("processes/leave_approve.bpmn20.xml").getInputStream(),
                    StandardCharsets.UTF_8);
            WfProcessDef def = new WfProcessDef();
            def.setId(1L);
            def.setTenantId(1L);
            def.setProcessKey("leave_approve");
            def.setProcessName("请假审批");
            def.setCategoryId(1L);
            def.setFormId(1L);
            def.setIcon("Document");
            def.setDescription("演示：经理 -> 管理员 两级审批");
            def.setVersion(1);
            def.setStatus(0);
            def.setBpmnXml(bpmn);
            def.setDeleted(0);
            processDefMapper.insert(def);
            processDefService.deploy(1L);
        } catch (Exception e) {
            log.warn("演示流程部署失败: {}", e.getMessage());
        }

        log.info("演示数据初始化完成。默认账号 admin / admin123");
    }

    private SysUser buildUser(Long id, String username, String realName, Long deptId, int adminFlag) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setTenantId(1L);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("admin123"));
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setStatus(1);
        u.setAdminFlag(adminFlag);
        u.setDeleted(0);
        u.setEmail(username + "@example.com");
        return u;
    }

    private void bindRole(Long id, Long userId, Long roleId) {
        SysUserRole ur = new SysUserRole();
        ur.setId(id);
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        ur.setTenantId(1L);
        userRoleMapper.insert(ur);
    }
}
