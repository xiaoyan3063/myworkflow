package com.myworkflow.module.notify.controller;

import com.myworkflow.common.result.PageResult;
import com.myworkflow.common.result.R;
import com.myworkflow.module.notify.entity.WfNotifyMessage;
import com.myworkflow.module.notify.service.NotifyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "消息通知")
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @ApiOperation("我的消息")
    @GetMapping("/messages")
    public R<PageResult<WfNotifyMessage>> messages(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Integer readFlag) {
        return R.ok(notifyService.myMessages(page, size, readFlag));
    }

    @ApiOperation("未读数量")
    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        Map<String, Object> m = new HashMap<>();
        // Map 的值会自动装箱，用 int 避免落入 Long -> String 的全局序列化规则
        m.put("count", (int) notifyService.unreadCount());
        return R.ok(m);
    }

    @ApiOperation("标记已读")
    @PostMapping("/messages/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        notifyService.markRead(id);
        return R.ok();
    }
}
