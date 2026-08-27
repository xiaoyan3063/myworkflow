package com.myworkflow.module.ticket.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.ticket.entity.TkField;
import com.myworkflow.module.ticket.entity.TkFormUi;
import com.myworkflow.module.ticket.entity.TkDetailUi;
import com.myworkflow.module.ticket.entity.TkListUi;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.service.TicketService;
import com.myworkflow.security.RequiresPerm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "工单类型")
@RestController
@RequestMapping("/ticket/types")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketService ticketService;

    @ApiOperation("类型分页")
    @GetMapping
    public R<PageResult<TkType>> page(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(ticketService.typePage(page, size, keyword));
    }

    @ApiOperation("启用中的类型")
    @GetMapping("/enabled")
    public R<List<TkType>> enabled() {
        return R.ok(ticketService.typeList());
    }

    @ApiOperation("按编码查类型")
    @GetMapping("/code/{typeCode}")
    public R<TkType> byCode(@PathVariable String typeCode) {
        return R.ok(ticketService.typeByCode(typeCode));
    }

    @ApiOperation("类型详情")
    @GetMapping("/{id}")
    public R<TkType> detail(@PathVariable Long id) {
        return R.ok(ticketService.typeDetail(id));
    }

    @ApiOperation("保存类型")
    @RequiresPerm("ticket:type:save")
    @PostMapping
    public R<TkType> save(@RequestBody TkType type) {
        return R.ok(ticketService.saveType(type));
    }

    @ApiOperation("删除类型")
    @RequiresPerm("ticket:type:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ticketService.deleteType(id);
        return R.ok();
    }

    @ApiOperation("字段列表")
    @GetMapping("/{id}/fields")
    public R<List<TkField>> fields(@PathVariable Long id) {
        return R.ok(ticketService.listFields(id));
    }

    @ApiOperation("绑定流程中归属审批节点的字段（创建工单时隐藏）")
    @GetMapping("/{id}/node-fields")
    public R<List<String>> nodeFields(@PathVariable Long id) {
        return R.ok(ticketService.typeNodeFields(id));
    }

    @ApiOperation("保存字段")
    @RequiresPerm("ticket:type:save")
    @PostMapping("/{id}/fields")
    public R<TkField> saveField(@PathVariable Long id, @RequestBody TkField field) {
        field.setTypeId(id);
        return R.ok(ticketService.saveField(field));
    }

    @ApiOperation("删除字段")
    @RequiresPerm("ticket:type:save")
    @DeleteMapping("/fields/{fieldId}")
    public R<Void> deleteField(@PathVariable Long fieldId) {
        ticketService.deleteField(fieldId);
        return R.ok();
    }

    @ApiOperation("表单设计 schema")
    @GetMapping("/{id}/form-ui")
    public R<TkFormUi> formUi(@PathVariable Long id,
                              @RequestParam(defaultValue = "false") boolean published,
                              @RequestParam(required = false) Integer version) {
        return R.ok(ticketService.getFormUi(id, published, version));
    }

    @ApiOperation("保存表单设计（草稿）")
    @RequiresPerm("ticket:type:save")
    @PutMapping("/{id}/form-ui")
    public R<TkFormUi> saveFormUi(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(ticketService.saveFormUi(id, body));
    }

    @ApiOperation("发布表单设计")
    @RequiresPerm("ticket:type:save")
    @PostMapping("/{id}/form-ui/publish")
    public R<TkFormUi> publishFormUi(@PathVariable Long id) {
        return R.ok(ticketService.publishFormUi(id));
    }

    @ApiOperation("列表配置 schema")
    @GetMapping("/{id}/list-ui")
    public R<TkListUi> listUi(@PathVariable Long id,
                              @RequestParam(defaultValue = "false") boolean published,
                              @RequestParam(required = false) Integer version) {
        return R.ok(ticketService.getListUi(id, published, version));
    }

    @ApiOperation("保存列表配置（草稿）")
    @RequiresPerm("ticket:type:save")
    @PutMapping("/{id}/list-ui")
    public R<TkListUi> saveListUi(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(ticketService.saveListUi(id, body));
    }

    @ApiOperation("发布列表配置")
    @RequiresPerm("ticket:type:save")
    @PostMapping("/{id}/list-ui/publish")
    public R<TkListUi> publishListUi(@PathVariable Long id) {
        return R.ok(ticketService.publishListUi(id));
    }

    @ApiOperation("详情配置 schema")
    @GetMapping("/{id}/detail-ui")
    public R<TkDetailUi> detailUi(@PathVariable Long id,
                                  @RequestParam(defaultValue = "false") boolean published,
                                  @RequestParam(required = false) Integer version) {
        return R.ok(ticketService.getDetailUi(id, published, version));
    }

    @ApiOperation("保存详情配置（草稿）")
    @RequiresPerm("ticket:type:save")
    @PutMapping("/{id}/detail-ui")
    public R<TkDetailUi> saveDetailUi(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(ticketService.saveDetailUi(id, body));
    }

    @ApiOperation("发布详情配置")
    @RequiresPerm("ticket:type:save")
    @PostMapping("/{id}/detail-ui/publish")
    public R<TkDetailUi> publishDetailUi(@PathVariable Long id) {
        return R.ok(ticketService.publishDetailUi(id));
    }
}
