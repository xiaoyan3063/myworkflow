package com.myworkflow.module.process.controller;

import com.myworkflow.common.result.R;
import com.myworkflow.module.process.entity.WfApprovalRole;
import com.myworkflow.module.process.service.ApprovalRoleService;
import com.myworkflow.security.RequiresPerm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "审批角色")
@RestController
@RequestMapping("/process/approval-roles")
@RequiredArgsConstructor
@RequiresPerm("process:manage")
public class ApprovalRoleController {

    private final ApprovalRoleService service;

    @ApiOperation("审批角色列表")
    @GetMapping
    public R<List<WfApprovalRole>> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return R.ok(service.list(enabledOnly));
    }

    @ApiOperation("保存审批角色")
    @PostMapping
    public R<WfApprovalRole> save(@RequestBody WfApprovalRole role) {
        return R.ok(service.save(role));
    }

    @ApiOperation("删除审批角色")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @ApiOperation("审批角色成员")
    @GetMapping("/{id}/users")
    public R<List<Map<String, Object>>> members(@PathVariable Long id) {
        return R.ok(service.members(id));
    }

    @ApiOperation("保存审批角色成员")
    @PostMapping("/{id}/users")
    public R<Void> saveMembers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        service.saveMembers(id, userIds);
        return R.ok();
    }
}
