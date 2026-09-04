package com.myworkflow.module.ticket.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.service.TicketService;
import com.myworkflow.security.RequiresPerm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@Api(tags = "工单")
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

    @ApiOperation("按类型编码分页（仅 schema.filters 允许的筛选字段）")
    @GetMapping("/by-type/{typeCode}")
    public R<PageResult<TkTicket>> pageByType(@PathVariable String typeCode,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam Map<String, String> params) {
        params.remove("page");
        params.remove("size");
        return R.ok(ticketService.ticketPageByType(typeCode, page, size, params));
    }

    @ApiOperation("工单详情")
    @GetMapping("/by-process/{processInstanceId}")
    public R<TkTicket> byProcessInstance(@PathVariable String processInstanceId) {
        return R.ok(ticketService.ticketByProcessInstance(processInstanceId));
    }

    @ApiOperation("工单详情")
    @GetMapping("/{id}")
    public R<TkTicket> detail(@PathVariable Long id) {
        return R.ok(ticketService.ticketDetail(id));
    }

    @ApiOperation("新建草稿")
    @RequiresPerm("ticket:create")
    @PostMapping
    public R<TkTicket> create(@RequestBody TkTicket ticket) {
        return R.ok(ticketService.createDraft(ticket));
    }

    @ApiOperation("主工单下可见的明细分组")
    @GetMapping("/{id}/children")
    public R<List<Map<String, Object>>> children(@PathVariable Long id) {
        return R.ok(ticketService.childGroups(id));
    }

    @ApiOperation("在主工单下新增明细草稿")
    @PostMapping("/{id}/children/{relationId}")
    public R<TkTicket> createChild(@PathVariable Long id, @PathVariable Long relationId,
                                    @RequestBody Map<String, Object> formData) {
        return R.ok(ticketService.createChildDraft(id, relationId, formData));
    }

    @ApiOperation("保存主工单下的明细")
    @PutMapping("/{id}/children/{childId}")
    public R<TkTicket> updateChild(@PathVariable Long id, @PathVariable Long childId,
                                    @RequestBody Map<String, Object> formData) {
        return R.ok(ticketService.updateChild(id, childId, formData));
    }

    @ApiOperation("删除主工单下的草稿明细")
    @DeleteMapping("/{id}/children/{childId}")
    public R<Void> deleteChild(@PathVariable Long id, @PathVariable Long childId) {
        ticketService.deleteChild(id, childId);
        return R.ok();
    }

    @ApiOperation("手动提交明细并启动子流程")
    @PostMapping("/{id}/children/{childId}/submit")
    public R<TkTicket> submitChild(@PathVariable Long id, @PathVariable Long childId) {
        return R.ok(ticketService.submitChild(id, childId));
    }

    @ApiOperation("更新草稿")
    @RequiresPerm("ticket:update")
    @PutMapping("/{id}")
    public R<TkTicket> update(@PathVariable Long id, @RequestBody TkTicket ticket) {
        return R.ok(ticketService.updateDraft(id, ticket));
    }

    @ApiOperation("字段可见性与当前节点可编辑字段")
    @GetMapping("/{id}/field-access")
    public R<Map<String, Object>> fieldAccess(@PathVariable Long id) {
        return R.ok(ticketService.fieldAccess(id));
    }

    @ApiOperation("当前审批人保存本节点可填写字段")
    @PatchMapping("/{id}/approval-fields")
    public R<TkTicket> saveApprovalFields(@PathVariable Long id,
                                          @RequestBody Map<String, Object> formData) {
        return R.ok(ticketService.saveApprovalFields(id, formData));
    }

    @ApiOperation("删除草稿或等待发起人重新提交的工单")
    @RequiresPerm("ticket:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ticketService.deleteDraft(id);
        return R.ok();
    }

    @ApiOperation("提交审批")
    @RequiresPerm("ticket:submit")
    @PostMapping("/{id}/submit")
    public R<TkTicket> submit(@PathVariable Long id) {
        return R.ok(ticketService.submit(id));
    }
}
