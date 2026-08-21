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
import com.myworkflow.security.JwtTokenProvider;
import com.myworkflow.security.PermissionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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

    @ApiOperation("登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername())
                .eq(req.getTenantId() != null, SysUser::getTenantId, req.getTenantId())
                .last("LIMIT 1"));
        if (user == null || user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("用户不存在或已停用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        boolean admin = user.getAdminFlag() != null && user.getAdminFlag() == 1;
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername(), user.getTenantId(), admin);
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<SysMenu> allMenus = permissionService.menusForUser(user.getId(), admin);
        return R.ok(LoginResponse.builder()
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
                .build());
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
