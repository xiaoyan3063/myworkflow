package com.myworkflow.module.system.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.system.entity.SysLoginLog;
import com.myworkflow.module.system.entity.SysOperLog;
import com.myworkflow.module.system.service.SystemLogService;
import com.myworkflow.security.RequiresPerm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Api(tags = "系统日志")
@RestController
@RequestMapping("/system/logs")
@RequiredArgsConstructor
@RequiresPerm("sys:log")
public class SystemLogController {

    private final SystemLogService logService;

    @ApiOperation("登录日志分页")
    @GetMapping("/login")
    public R<PageResult<SysLoginLog>> loginLogs(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(logService.loginLogs(page, size, username, status));
    }

    @ApiOperation("操作日志分页")
    @GetMapping("/operation")
    public R<PageResult<SysOperLog>> operationLogs(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String username,
                                                    @RequestParam(required = false) String module,
                                                    @RequestParam(required = false) Integer status) {
        return R.ok(logService.operationLogs(page, size, username, module, status));
    }

    @ApiOperation("删除登录日志")
    @DeleteMapping("/login")
    public R<Void> deleteLoginLogs(@RequestBody IdsRequest req) {
        logService.deleteLoginLogs(req.getIds());
        return R.ok();
    }

    @ApiOperation("删除操作日志")
    @DeleteMapping("/operation")
    public R<Void> deleteOperationLogs(@RequestBody IdsRequest req) {
        logService.deleteOperationLogs(req.getIds());
        return R.ok();
    }

    @ApiOperation("清理截止时间以前的日志")
    @DeleteMapping("/before")
    public R<Void> cleanupBefore(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        logService.cleanupBefore(before);
        return R.ok();
    }

    @Data
    public static class IdsRequest {
        private List<Long> ids;
    }
}
