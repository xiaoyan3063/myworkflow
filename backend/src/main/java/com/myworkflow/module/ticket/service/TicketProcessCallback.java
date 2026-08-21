package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.module.process.service.ProcessFinishListener;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用 businessKey = ticket_no 回写工单。纯审批发起没有工单，只打日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketProcessCallback implements ProcessFinishListener {

    private final TkTicketMapper ticketMapper;

    @Override
    public void onProcessFinished(String processInstId, String businessKey, String processStatus) {
        if (!StringUtils.hasText(businessKey)) {
            log.info("skip ticket writeback: empty businessKey, inst={}", processInstId);
            return;
        }
        TkTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<TkTicket>()
                .eq(TkTicket::getTicketNo, businessKey)
                .last("LIMIT 1"));
        if (ticket == null) {
            log.info("no ticket for businessKey={}, skip writeback (pure approval). inst={}",
                    businessKey, processInstId);
            return;
        }
        if (StringUtils.hasText(ticket.getProcessInstId())
                && !ticket.getProcessInstId().equals(processInstId)) {
            log.info("ticket {} bound to inst {}, ignore finish of {}",
                    ticket.getTicketNo(), ticket.getProcessInstId(), processInstId);
            return;
        }
        if ("COMPLETED".equals(processStatus)) {
            ticket.setStatus(TicketService.STATUS_APPROVED);
        } else if ("REJECTED".equals(processStatus)) {
            ticket.setStatus(TicketService.STATUS_REJECTED);
        } else {
            log.info("ignore process status {} for ticket {}", processStatus, ticket.getTicketNo());
            return;
        }
        ticketMapper.updateById(ticket);
        log.info("ticket {} status -> {} by process {}", ticket.getTicketNo(), ticket.getStatus(), processInstId);
    }
}
