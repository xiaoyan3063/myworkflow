package com.myworkflow.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.system.entity.SysLoginLog;
import com.myworkflow.module.system.entity.SysOperLog;
import com.myworkflow.module.system.mapper.SysLoginLogMapper;
import com.myworkflow.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(SysLoginLog item) {
        try {
            item.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(item);
        } catch (Exception e) {
            log.warn("写入登录日志失败: {}", e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOperation(SysOperLog item) {
        try {
            item.setCreateTime(LocalDateTime.now());
            operLogMapper.insert(item);
        } catch (Exception e) {
            log.warn("写入操作日志失败: {}", e.getMessage());
        }
    }

    public PageResult<SysLoginLog> loginLogs(long page, long size, String username, Integer status) {
        Page<SysLoginLog> p = loginLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysLoginLog>()
                        .eq(SysLoginLog::getTenantId, tenantId())
                        .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                        .eq(status != null, SysLoginLog::getStatus, status)
                        .orderByDesc(SysLoginLog::getCreateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public PageResult<SysOperLog> operationLogs(long page, long size, String username,
                                                 String module, Integer status) {
        Page<SysOperLog> p = operLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysOperLog>()
                        .eq(SysOperLog::getTenantId, tenantId())
                        .and(StringUtils.hasText(username), w -> w
                                .like(SysOperLog::getUsername, username)
                                .or().like(SysOperLog::getRealName, username))
                        .eq(StringUtils.hasText(module), SysOperLog::getModule, module)
                        .eq(status != null, SysOperLog::getStatus, status)
                        .orderByDesc(SysOperLog::getCreateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLoginLogs(List<Long> ids) {
        assertAdmin();
        if (ids != null && !ids.isEmpty()) {
            loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                    .eq(SysLoginLog::getTenantId, tenantId()).in(SysLoginLog::getId, ids));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOperationLogs(List<Long> ids) {
        assertAdmin();
        if (ids != null && !ids.isEmpty()) {
            operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                    .eq(SysOperLog::getTenantId, tenantId()).in(SysOperLog::getId, ids));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cleanupBefore(LocalDateTime before) {
        assertAdmin();
        if (before == null) {
            throw new BizException("请选择清理截止时间");
        }
        loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getTenantId, tenantId()).lt(SysLoginLog::getCreateTime, before));
        operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getTenantId, tenantId()).lt(SysOperLog::getCreateTime, before));
    }

    private long tenantId() {
        Long id = UserContext.currentTenantId();
        return id == null ? 0L : id;
    }

    private void assertAdmin() {
        if (UserContext.get() == null || !UserContext.get().isAdmin()) {
            throw new BizException(403, "仅系统管理员可删除日志");
        }
    }
}
