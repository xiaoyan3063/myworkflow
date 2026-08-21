package com.myworkflow.module.ticket.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.service.TicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Api(tags = "工单草稿")
@RestController
@RequestMapping("/ticket/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @ApiOperation("工单分页")
    @GetMapping
    public R<PageResult<TkTicket>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) Long typeId,
                                        @RequestParam(required = false) String keyword) {
        return R.ok(ticketService.ticketPage(page, size, typeId, keyword));
    }

    @ApiOperation("工单详情")
    @GetMapping("/{id}")
    public R<TkTicket> detail(@PathVariable Long id) {
        return R.ok(ticketService.ticketDetail(id));
    }

    @ApiOperation("新建草稿")
    @PostMapping
    public R<TkTicket> create(@RequestBody TkTicket ticket) {
        return R.ok(ticketService.createDraft(ticket));
    }

    @ApiOperation("更新草稿")
    @PutMapping("/{id}")
    public R<TkTicket> update(@PathVariable Long id, @RequestBody TkTicket ticket) {
        return R.ok(ticketService.updateDraft(id, ticket));
    }

    @ApiOperation("删除草稿")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ticketService.deleteDraft(id);
        return R.ok();
    }
}
