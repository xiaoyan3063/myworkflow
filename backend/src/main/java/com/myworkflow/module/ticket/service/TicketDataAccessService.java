package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批身份只决定任务归属；这里按用户角色的数据范围决定能否读取、修改和审批工单数据。
 */
@Service
@RequiredArgsConstructor
public class TicketDataAccessService {

    private final TkTicketMapper ticketMapper;
    private final PermissionService permissionService;

    public boolean hasDataAccess(TkTicket ticket) {
        if (ticket == null) {
            return false;
        }
        if (permissionService.allScope()) {
            return true;
        }
        Long currentUserId = UserContext.currentUserId();
        Long starterId = ticket.getStarterId();
        if (currentUserId == null || starterId == null) {
            return false;
        }
        if (permissionService.deptScope()) {
            List<Long> ids = permissionService.scopeUserIds();
            return ids != null && ids.contains(starterId);
        }
        return currentUserId.equals(starterId);
    }

    public boolean hasDataAccess(String processInstanceId) {
        return hasDataAccess(ticketByProcess(processInstanceId));
    }

    public void assertDataAccess(String processInstanceId) {
        if (!hasDataAccess(processInstanceId)) {
            throw denied();
        }
    }

    public void assertTicketDataAccess(Long ticketId) {
        if (ticketId == null) {
            return;
        }
        TkTicket ticket = ticketMapper.selectById(ticketId);
        if (!hasDataAccess(ticket)) {
            throw denied();
        }
    }

    public TkTicket ticketByProcess(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        return ticketMapper.selectOne(new LambdaQueryWrapper<TkTicket>()
                .eq(TkTicket::getProcessInstId, processInstanceId)
                .last("LIMIT 1"));
    }

    public BizException denied() {
        return new BizException(403,
                "您是当前审批人，但用户角色的数据权限不包含该工单，暂不能查看字段或办理；授权后请刷新页面");
    }
}
