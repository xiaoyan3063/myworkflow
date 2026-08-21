package com.myworkflow.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private Long tenantId;
    private Long deptId;
    private Boolean admin;
    private List<String> roles;
    private List<String> perms;
    private Object menus;
}
