package com.myworkflow.module.process.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.process.entity.WfFormDef;
import com.myworkflow.module.process.entity.WfProcessCategory;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.service.ProcessDefService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "流程定义")
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
public class ProcessDefController {

    private final ProcessDefService processDefService;

    @ApiOperation("流程分页")
    @GetMapping("/defs")
    public R<PageResult<WfProcessDef>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status) {
        return R.ok(processDefService.page(page, size, keyword, status));
    }

    @ApiOperation("流程详情")
    @GetMapping("/defs/{id}")
    public R<WfProcessDef> detail(@PathVariable Long id) {
        return R.ok(processDefService.detail(id));
    }

    @ApiOperation("保存流程")
    @PostMapping("/defs")
    public R<WfProcessDef> save(@RequestBody WfProcessDef def) {
        return R.ok(processDefService.save(def));
    }

    @ApiOperation("发布流程")
    @PostMapping("/defs/{id}/deploy")
    public R<Void> deploy(@PathVariable Long id) {
        processDefService.deploy(id);
        return R.ok();
    }

    @ApiOperation("停用流程")
    @PostMapping("/defs/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        processDefService.disable(id);
        return R.ok();
    }

    @ApiOperation("删除流程")
    @DeleteMapping("/defs/{id}")
    public R<Void> delete(@PathVariable Long id) {
        processDefService.delete(id);
        return R.ok();
    }

    @ApiOperation("已发布流程列表")
    @GetMapping("/defs/published")
    public R<List<WfProcessDef>> published() {
        return R.ok(processDefService.publishedList());
    }

    @ApiOperation("分类列表")
    @GetMapping("/categories")
    public R<List<WfProcessCategory>> categories() {
        return R.ok(processDefService.categories());
    }

    @ApiOperation("保存分类")
    @PostMapping("/categories")
    public R<Void> saveCategory(@RequestBody WfProcessCategory category) {
        processDefService.saveCategory(category);
        return R.ok();
    }

    @ApiOperation("表单分页")
    @GetMapping("/forms")
    public R<PageResult<WfFormDef>> forms(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(processDefService.formPage(page, size, keyword));
    }

    @ApiOperation("表单详情")
    @GetMapping("/forms/{id}")
    public R<WfFormDef> formDetail(@PathVariable Long id) {
        return R.ok(processDefService.formDetail(id));
    }

    @ApiOperation("保存表单")
    @PostMapping("/forms")
    public R<WfFormDef> saveForm(@RequestBody WfFormDef form) {
        return R.ok(processDefService.saveForm(form));
    }
}
