package com.myworkflow.module.process.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.process.dto.StartProcessRequest;
import com.myworkflow.module.process.dto.TaskActionRequest;
import com.myworkflow.module.process.entity.WfCcRecord;
import com.myworkflow.module.process.entity.WfProcessInstanceExt;
import com.myworkflow.module.process.service.ProcessRuntimeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Api(tags = "流程运行/任务")
@RestController
@RequestMapping("/runtime")
@RequiredArgsConstructor
public class ProcessRuntimeController {

    private final ProcessRuntimeService runtimeService;

    @ApiOperation("发起流程")
    @PostMapping("/start")
    public R<Map<String, Object>> start(@Valid @RequestBody StartProcessRequest req) {
        return R.ok(runtimeService.start(req));
    }

    @ApiOperation("待办列表")
    @GetMapping("/todo")
    public R<PageResult<Map<String, Object>>> todo(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword) {
        return R.ok(runtimeService.todoList(page, size, keyword));
    }

    @ApiOperation("已办列表")
    @GetMapping("/done")
    public R<PageResult<Map<String, Object>>> done(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return R.ok(runtimeService.doneList(page, size));
    }

    @ApiOperation("我发起的")
    @GetMapping("/started")
    public R<PageResult<WfProcessInstanceExt>> started(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return R.ok(runtimeService.myStarted(page, size));
    }

    @ApiOperation("抄送我的")
    @GetMapping("/cc")
    public R<PageResult<WfCcRecord>> cc(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return R.ok(runtimeService.myCc(page, size));
    }

    @ApiOperation("任务详情")
    @GetMapping("/tasks/{taskId}")
    public R<Map<String, Object>> taskDetail(@PathVariable String taskId) {
        return R.ok(runtimeService.taskDetail(taskId));
    }

    @ApiOperation("我在该实例上的待办任务")
    @GetMapping("/instances/{processInstanceId}/my-task")
    public R<Map<String, Object>> myTask(@PathVariable String processInstanceId) {
        return R.ok(runtimeService.myActiveTask(processInstanceId));
    }

    @ApiOperation("同意")
    @PostMapping("/approve")
    public R<Void> approve(@Valid @RequestBody TaskActionRequest req) {
        runtimeService.approve(req);
        return R.ok();
    }

    @ApiOperation("可回退节点")
    @GetMapping("/tasks/{taskId}/reject-targets")
    public R<List<Map<String, Object>>> rejectTargets(@PathVariable String taskId) {
        return R.ok(runtimeService.rejectTargets(taskId));
    }

    @ApiOperation("驳回")
    @PostMapping("/reject")
    public R<Void> reject(@Valid @RequestBody TaskActionRequest req) {
        runtimeService.reject(req);
        return R.ok();
    }

    @ApiOperation("转办")
    @PostMapping("/transfer")
    public R<Void> transfer(@Valid @RequestBody TaskActionRequest req) {
        runtimeService.transfer(req);
        return R.ok();
    }

    @ApiOperation("前加签")
    @PostMapping("/add-sign")
    public R<Void> addSign(@Valid @RequestBody TaskActionRequest req) {
        runtimeService.addSign(req);
        return R.ok();
    }

    @ApiOperation("流程实例详情")
    @GetMapping("/instances/{processInstanceId}")
    public R<Map<String, Object>> instanceDetail(@PathVariable String processInstanceId) {
        return R.ok(runtimeService.instanceDetail(processInstanceId));
    }

    @ApiOperation("审批轨迹")
    @GetMapping("/timeline/{processInstanceId}")
    public R<Map<String, Object>> timeline(@PathVariable String processInstanceId) {
        return R.ok(runtimeService.timeline(processInstanceId));
    }
}
