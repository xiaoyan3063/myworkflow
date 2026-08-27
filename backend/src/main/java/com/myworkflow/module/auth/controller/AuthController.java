package com.myworkflow.module.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.R;
import com.myworkflow.module.auth.dto.LoginRequest;
import com.myworkflow.module.auth.dto.LoginResponse;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.entity.SysMenu;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.myworkflow.module.system.entity.SysLoginLog;
import com.myworkflow.module.system.service.OperationLogAspect;
import com.myworkflow.module.system.service.SystemLogService;
import com.myworkflow.security.JwtTokenProvider;
import com.myworkflow.security.PermissionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PermissionService permissionService;
    private final SystemLogService systemLogService;

    @ApiOperation("登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername())
                .eq(req.getTenantId() != null, SysUser::getTenantId, req.getTenantId())
                .last("LIMIT 1"));
        if (user == null || user.getStatus() != null && user.getStatus() == 0) {
            recordLogin(req, user, request, 0, "用户不存在或已停用");
            throw new BizException("用户不存在或已停用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordLogin(req, user, request, 0, "用户名或密码错误");
            throw new BizException("用户名或密码错误");
        }
        boolean admin = user.getAdminFlag() != null && user.getAdminFlag() == 1;
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername(), user.getTenantId(), admin);
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<SysMenu> allMenus = permissionService.menusForUser(user.getId(), admin);
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .tenantId(user.getTenantId())
                .deptId(user.getDeptId())
                .admin(admin)
                .roles(roles)
                .perms(permissionService.permsOf(allMenus))
                .menus(permissionService.sidebarTree(user.getId(), admin))
                .build();
        recordLogin(req, user, request, 1, "登录成功");
        return R.ok(response);
    }

    private void recordLogin(LoginRequest req, SysUser user, HttpServletRequest request,
                             int status, String message) {
        SysLoginLog item = new SysLoginLog();
        item.setTenantId(user != null && user.getTenantId() != null
                ? user.getTenantId() : (req.getTenantId() == null ? 0L : req.getTenantId()));
        item.setUserId(user == null ? null : user.getId());
        item.setUsername(req.getUsername());
        item.setRealName(user == null ? null : user.getRealName());
        item.setStatus(status);
        item.setMessage(message);
        item.setIp(OperationLogAspect.clientIp(request));
        String agent = request.getHeader("User-Agent");
        item.setUserAgent(agent != null && agent.length() > 512 ? agent.substring(0, 512) : agent);
        item.setClientType(clientType(request.getHeader("X-Client-Type"), agent));
        systemLogService.recordLogin(item);
    }

    /**
     * 客户端类型。前端登录时可显式带 X-Client-Type；没带就按 User-Agent 猜：
     * 移动端 SDK 的 UA 不含 Mozilla，浏览器一定含。
     */
    private String clientType(String declared, String agent) {
        if (StringUtils.hasText(declared)) {
            String v = declared.trim().toUpperCase();
            if ("WEB".equals(v) || "APP".equals(v)) {
                return v;
            }
        }
        if (!StringUtils.hasText(agent)) {
            return "OTHER";
        }
        String ua = agent.toLowerCase();
        if (ua.contains("okhttp") || ua.contains("cfnetwork") || ua.contains("dart")
                || ua.contains("flutter") || ua.contains("uni-app")) {
            return "APP";
        }
        return ua.contains("mozilla") ? "WEB" : "OTHER";
    }

    @ApiOperation("当前用户信息")
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Long userId = UserContext.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getId());
        map.put("username", user.getUsername());
        map.put("realName", user.getRealName());
        map.put("tenantId", user.getTenantId());
        map.put("deptId", user.getDeptId());
        map.put("email", user.getEmail());
        map.put("mobile", user.getMobile());
        map.put("avatar", user.getAvatar());
        boolean admin = user.getAdminFlag() != null && user.getAdminFlag() == 1;
        List<SysMenu> allMenus = permissionService.menusForUser(user.getId(), admin);
        map.put("admin", admin);
        map.put("roles", userMapper.selectRoleCodesByUserId(user.getId()));
        map.put("perms", permissionService.permsOf(allMenus));
        map.put("menus", permissionService.sidebarTree(user.getId(), admin));
        map.put("dataScope", admin ? "ALL" : (UserContext.get() == null ? "SELF" : UserContext.get().getDataScope()));
        return R.ok(map);
    }
}
