package com.myworkflow.module.process.job;

import com.myworkflow.module.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 超时催办：扫描已过 dueDate 的待办任务并发送站内信
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutJob {

    private final TaskService taskService;
    private final NotifyService notifyService;

    @Scheduled(cron = "0 0/10 * * * ?")
    public void remindOverdue() {
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .taskDueBefore(new Date())
                .listPage(0, 100);
        for (Task t : tasks) {
            Set<String> users = new HashSet<>();
            if (StringUtils.hasText(t.getAssignee())) {
                users.add(t.getAssignee());
            }
            taskService.getIdentityLinksForTask(t.getId()).forEach(l -> {
                if (StringUtils.hasText(l.getUserId())) users.add(l.getUserId());
            });
            for (String uid : users) {
                try {
                    notifyService.send(Long.valueOf(uid), "超时催办",
                            "任务「" + t.getName() + "」已超时，请尽快处理",
                            "TIMEOUT", t.getId());
                } catch (Exception e) {
                    log.warn("催办失败 taskId={}", t.getId());
                }
            }
        }
        if (!tasks.isEmpty()) {
            log.info("超时催办扫描完成，处理 {} 条", tasks.size());
        }
    }
}
