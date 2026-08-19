package com.myworkflow.module.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.notify.service.NotifyService;
import com.myworkflow.module.process.dto.StartProcessRequest;
import com.myworkflow.module.process.dto.TaskActionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.myworkflow.module.process.entity.WfCcRecord;
import com.myworkflow.module.process.entity.WfFormDef;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.entity.WfProcessInstanceExt;
import com.myworkflow.module.process.mapper.WfCcRecordMapper;
import com.myworkflow.module.process.mapper.WfFormDefMapper;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.mapper.WfProcessInstanceExtMapper;
import com.myworkflow.module.process.util.BpmnEnhanceUtil;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeService {

    private final WfProcessDefMapper processDefMapper;
    private final WfProcessInstanceExtMapper instanceExtMapper;
    private final WfCcRecordMapper ccRecordMapper;
    private final WfFormDefMapper formDefMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final SysUserMapper userMapper;
    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> start(StartProcessRequest req) {
        WfProcessDef def = null;
        if (req.getProcessDefId() != null) {
            def = processDefMapper.selectById(req.getProcessDefId());
        } else if (StringUtils.hasText(req.getProcessKey())) {
            def = processDefMapper.selectOne(new LambdaQueryWrapper<WfProcessDef>()
                    .eq(WfProcessDef::getProcessKey, req.getProcessKey())
                    .eq(WfProcessDef::getStatus, 1)
                    .last("LIMIT 1"));
        }
        if (def == null || def.getStatus() == null || def.getStatus() != 1) {
            throw new BizException("流程未发布或不存在");
        }
        String starterId = StringUtils.hasText(req.getStarterId())
                ? req.getStarterId()
                : String.valueOf(UserContext.currentUserId());
        String starterName = req.getStarterName();
        if (!StringUtils.hasText(starterName)) {
            SysUser u = userMapper.selectById(Long.valueOf(starterId));
            starterName = u == null ? starterId : u.getRealName();
        }

        Map<String, Object> vars = new HashMap<>();
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
        }
        fillMissingFormFields(def, vars);
        vars.put("starterId", starterId);
        vars.put("starterName", starterName);
        vars.put("title", req.getTitle());
        vars.put("businessKey", req.getBusinessKey());
        vars.put("businessType", req.getBusinessType());

        ProcessInstance pi = runtimeService.startProcessInstanceById(
                def.getFlowableDefId(), req.getBusinessKey(), vars);

        WfProcessInstanceExt ext = new WfProcessInstanceExt();
        ext.setProcessInstId(pi.getId());
        ext.setProcessDefId(def.getId());
        ext.setProcessKey(def.getProcessKey());
        ext.setBusinessKey(req.getBusinessKey());
        ext.setBusinessType(req.getBusinessType());
        ext.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle() : def.getProcessName());
        ext.setStarterId(Long.valueOf(starterId));
        ext.setStarterName(starterName);
        ext.setStatus("RUNNING");
        ext.setStartTime(LocalDateTime.now());
        try {
            ext.setFormData(objectMapper.writeValueAsString(req.getFormData()));
        } catch (Exception e) {
            ext.setFormData("{}");
        }
        instanceExtMapper.insert(ext);

        // 通知首批待办人
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(pi.getId()).list();
        for (Task t : tasks) {
            notifyAssignees(t, ext.getTitle());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("processInstanceId", pi.getId());
        result.put("businessKey", req.getBusinessKey());
        return result;
    }

    public PageResult<Map<String, Object>> todoList(long page, long size, String keyword) {
        String userId = String.valueOf(UserContext.currentUserId());
        long total = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .active()
                .count();
        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .active()
                .orderByTaskCreateTime().desc()
                .listPage((int) ((page - 1) * size), (int) size);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> m = toTaskMap(t);
            if (StringUtils.hasText(keyword)) {
                Object title = m.get("title");
                if (title == null || !title.toString().contains(keyword)) {
                    continue;
                }
            }
            records.add(m);
        }
        return PageResult.of(total, records);
    }

    public PageResult<Map<String, Object>> doneList(long page, long size) {
        String userId = String.valueOf(UserContext.currentUserId());
        long total = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .finished()
                .count();
        List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .listPage((int) ((page - 1) * size), (int) size);
        List<Map<String, Object>> records = new ArrayList<>();
        for (HistoricTaskInstance t : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("processInstanceId", t.getProcessInstanceId());
            m.put("startTime", t.getCreateTime());
            m.put("endTime", t.getEndTime());
            WfProcessInstanceExt ext = findExt(t.getProcessInstanceId());
            if (ext != null) {
                m.put("title", ext.getTitle());
                m.put("starterName", ext.getStarterName());
                m.put("status", ext.getStatus());
            }
            records.add(m);
        }
        return PageResult.of(total, records);
    }

    public PageResult<WfProcessInstanceExt> myStarted(long page, long size) {
        Page<WfProcessInstanceExt> p = instanceExtMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfProcessInstanceExt>()
                        .eq(WfProcessInstanceExt::getStarterId, UserContext.currentUserId())
                        .orderByDesc(WfProcessInstanceExt::getStartTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(TaskActionRequest req) {
        Task task = getTask(req.getTaskId());
        claimIfNeeded(task);
        if (StringUtils.hasText(req.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), req.getComment());
        }
        Map<String, Object> vars = req.getVariables() == null ? new HashMap<>() : new HashMap<>(req.getVariables());
        vars.put("approved", true);
        vars.put("lastComment", req.getComment());
        taskService.complete(task.getId(), vars);
        saveCc(req, task);
        refreshInstanceStatus(task.getProcessInstanceId());
        notifyNext(task.getProcessInstanceId());
    }

    /**
     * 当前任务可以驳回回退到哪些节点：本实例中已经走完的用户任务，按发生顺序排列。
     * 配成「发起人本人」的节点会被标记出来，前端把它显示为「退回发起人」。
     */
    public List<Map<String, Object>> rejectTargets(String taskId) {
        Task task = getTask(taskId);
        Map<String, String> assigneeTypes = readAssigneeTypes(task.getProcessInstanceId());

        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<Map<String, Object>> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (HistoricActivityInstance a : acts) {
            // 同一节点可能因为会签或多次回退产生多条历史，只保留一条
            if (a.getActivityId().equals(task.getTaskDefinitionKey()) || !seen.add(a.getActivityId())) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("activityId", a.getActivityId());
            m.put("activityName", StringUtils.hasText(a.getActivityName()) ? a.getActivityName() : a.getActivityId());
            m.put("starterNode", "starter".equals(assigneeTypes.get(a.getActivityId())));
            targets.add(m);
        }
        return targets;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(TaskActionRequest req) {
        Task task = getTask(req.getTaskId());
        claimIfNeeded(task);
        // 带了目标节点就按回退处理，否则终止；显式传 rejectMode 优先
        boolean toActivity = "ACTIVITY".equalsIgnoreCase(req.getRejectMode())
                || (!StringUtils.hasText(req.getRejectMode()) && StringUtils.hasText(req.getRejectToActivityId()));
        if (toActivity) {
            rejectToActivity(task, req);
        } else {
            rejectAndTerminate(task, req);
        }
    }

    private void rejectAndTerminate(Task task, TaskActionRequest req) {
        String reason = StringUtils.hasText(req.getComment()) ? req.getComment() : "驳回";
        if (StringUtils.hasText(req.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), "驳回（终止流程）：" + req.getComment());
        }
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), reason);
        WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
        if (ext != null) {
            ext.setStatus("REJECTED");
            ext.setEndTime(LocalDateTime.now());
            instanceExtMapper.updateById(ext);
            notifyService.send(ext.getStarterId(), "审批驳回",
                    "您的申请「" + ext.getTitle() + "」已被驳回并终止", "REJECT", ext.getProcessInstId());
        }
    }

    /**
     * 回退：把执行流从当前节点挪回目标节点，流程实例继续存活。
     * 目标节点的 create 监听器会重新解析审批人，所以退回发起人节点时
     * 发起人会重新收到待办，改完表单再提交即可继续往下走。
     */
    private void rejectToActivity(Task task, TaskActionRequest req) {
        String target = req.getRejectToActivityId();
        if (!StringUtils.hasText(target)) {
            throw new BizException("请选择要回退到的节点");
        }
        boolean reachable = rejectTargets(task.getId()).stream()
                .anyMatch(t -> target.equals(t.get("activityId")));
        if (!reachable) {
            throw new BizException("该节点不在当前流程已走过的路径上，无法回退");
        }

        String targetName = activityName(task.getProcessInstanceId(), target);
        if (StringUtils.hasText(req.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(),
                    "驳回至【" + targetName + "】：" + req.getComment());
        }
        // 回退后目标节点的分支条件会重新求值，这两个变量要先归位
        runtimeService.setVariable(task.getProcessInstanceId(), "approved", false);
        runtimeService.setVariable(task.getProcessInstanceId(), "lastComment", req.getComment());
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), target)
                    .changeState();
        } catch (Exception e) {
            log.error("回退失败, taskId={}, from={}, to={}", task.getId(), task.getTaskDefinitionKey(), target, e);
            throw new BizException("回退失败：" + e.getMessage());
        }

        WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
        if (ext != null) {
            notifyService.send(ext.getStarterId(), "审批驳回",
                    "您的申请「" + ext.getTitle() + "」已被驳回到【" + targetName + "】", "REJECT", ext.getProcessInstId());
        }
        notifyNext(task.getProcessInstanceId());
    }

    private String activityName(String processInstanceId, String activityId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .list().stream()
                .map(HistoricActivityInstance::getActivityName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(activityId);
    }

    /** 审批人来源存在设计器原稿里，运行时反查流程定义才能拿到 */
    private Map<String, String> readAssigneeTypes(String processInstanceId) {
        WfProcessInstanceExt ext = findExt(processInstanceId);
        if (ext == null || ext.getProcessDefId() == null) {
            return Collections.emptyMap();
        }
        WfProcessDef def = processDefMapper.selectById(ext.getProcessDefId());
        return def == null ? Collections.emptyMap() : BpmnEnhanceUtil.readAssigneeTypes(def.getBpmnXml());
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(TaskActionRequest req) {
        Task task = getTask(req.getTaskId());
        if (!StringUtils.hasText(req.getTransferUserId())) {
            throw new BizException("请指定转办人");
        }
        taskService.setAssignee(task.getId(), req.getTransferUserId());
        if (StringUtils.hasText(req.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), "转办：" + req.getComment());
        }
        notifyService.send(Long.valueOf(req.getTransferUserId()), "任务转办",
                "您有一个新的转办待办：" + Optional.ofNullable(findExt(task.getProcessInstanceId()))
                        .map(WfProcessInstanceExt::getTitle).orElse(task.getName()),
                "TRANSFER", task.getId());
    }

    public Map<String, Object> taskDetail(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        Map<String, Object> m = new HashMap<>();
        if (task != null) {
            m.putAll(toTaskMap(task));
            m.put("variables", runtimeService.getVariables(task.getProcessInstanceId()));
        } else {
            HistoricTaskInstance ht = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
            if (ht == null) throw new BizException("任务不存在");
            m.put("taskId", ht.getId());
            m.put("taskName", ht.getName());
            m.put("processInstanceId", ht.getProcessInstanceId());
            WfProcessInstanceExt ext = findExt(ht.getProcessInstanceId());
            if (ext != null) {
                m.put("title", ext.getTitle());
                m.put("formData", ext.getFormData());
                m.put("status", ext.getStatus());
            }
        }
        m.put("comments", taskService.getProcessInstanceComments(
                (String) m.get("processInstanceId")));
        return m;
    }

    public List<Map<String, Object>> timeline(String processInstanceId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        List<Map<String, Object>> list = new ArrayList<>();
        for (HistoricActivityInstance a : acts) {
            if (!"userTask".equals(a.getActivityType()) && !"startEvent".equals(a.getActivityType())
                    && !"endEvent".equals(a.getActivityType())) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("activityId", a.getActivityId());
            m.put("activityName", a.getActivityName());
            m.put("activityType", a.getActivityType());
            m.put("assignee", a.getAssignee());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            if (StringUtils.hasText(a.getAssignee())) {
                try {
                    SysUser u = userMapper.selectById(Long.valueOf(a.getAssignee()));
                    if (u != null) m.put("assigneeName", u.getRealName());
                } catch (Exception ignored) {
                }
            }
            list.add(m);
        }
        return list;
    }

    public PageResult<WfCcRecord> myCc(long page, long size) {
        Page<WfCcRecord> p = ccRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfCcRecord>()
                        .eq(WfCcRecord::getUserId, UserContext.currentUserId())
                        .orderByDesc(WfCcRecord::getCreateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    /**
     * 分支条件里引用的表单字段如果没提交，JUEL 求值会抛 PropertyNotFound 直接让发起失败。
     * 这里按表单定义把缺失字段补成类型默认值，保证条件永远可求值。
     */
    private void fillMissingFormFields(WfProcessDef def, Map<String, Object> vars) {
        if (def.getFormId() == null) return;
        WfFormDef form = formDefMapper.selectById(def.getFormId());
        if (form == null || !StringUtils.hasText(form.getFormSchema())) return;
        try {
            JsonNode schema = objectMapper.readTree(form.getFormSchema());
            for (JsonNode field : schema) {
                String name = field.path("field").asText(null);
                if (!StringUtils.hasText(name) || vars.containsKey(name)) continue;
                vars.put(name, "number".equals(field.path("type").asText()) ? 0 : "");
            }
        } catch (Exception e) {
            log.warn("解析表单 {} 失败，跳过默认值填充", def.getFormId(), e);
        }
    }

    private Task getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new BizException("任务不存在或已处理");
        return task;
    }

    private void claimIfNeeded(Task task) {
        String userId = String.valueOf(UserContext.currentUserId());
        if (!StringUtils.hasText(task.getAssignee())) {
            taskService.claim(task.getId(), userId);
        } else if (!userId.equals(task.getAssignee())) {
            // 候选人也允许办理
            long cnt = taskService.createTaskQuery().taskId(task.getId()).taskCandidateOrAssigned(userId).count();
            if (cnt == 0) throw new BizException("无权处理该任务");
            taskService.setAssignee(task.getId(), userId);
        }
    }

    private Map<String, Object> toTaskMap(Task t) {
        Map<String, Object> m = new HashMap<>();
        m.put("taskId", t.getId());
        m.put("taskName", t.getName());
        m.put("assignee", t.getAssignee());
        m.put("createTime", t.getCreateTime());
        m.put("dueDate", t.getDueDate());
        m.put("processInstanceId", t.getProcessInstanceId());
        m.put("processDefinitionId", t.getProcessDefinitionId());
        WfProcessInstanceExt ext = findExt(t.getProcessInstanceId());
        if (ext != null) {
            m.put("title", ext.getTitle());
            m.put("starterName", ext.getStarterName());
            m.put("businessKey", ext.getBusinessKey());
            m.put("formData", ext.getFormData());
            m.put("processDefId", ext.getProcessDefId());
        }
        return m;
    }

    private WfProcessInstanceExt findExt(String processInstanceId) {
        return instanceExtMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getProcessInstId, processInstanceId)
                .last("LIMIT 1"));
    }

    private void refreshInstanceStatus(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        WfProcessInstanceExt ext = findExt(processInstanceId);
        if (ext == null) return;
        if (pi == null) {
            ext.setStatus("COMPLETED");
            ext.setEndTime(LocalDateTime.now());
            instanceExtMapper.updateById(ext);
            notifyService.send(ext.getStarterId(), "审批完成",
                    "您的申请「" + ext.getTitle() + "」已审批通过", "COMPLETE", processInstanceId);
        }
    }

    private void notifyNext(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        WfProcessInstanceExt ext = findExt(processInstanceId);
        String title = ext == null ? "待办任务" : ext.getTitle();
        for (Task t : tasks) {
            notifyAssignees(t, title);
        }
    }

    private void notifyAssignees(Task t, String title) {
        Set<String> users = new HashSet<>();
        if (StringUtils.hasText(t.getAssignee())) {
            users.add(t.getAssignee());
        }
        taskService.getIdentityLinksForTask(t.getId()).forEach(link -> {
            if (StringUtils.hasText(link.getUserId())) {
                users.add(link.getUserId());
            }
        });
        for (String uid : users) {
            try {
                notifyService.send(Long.valueOf(uid), "待办提醒",
                        "您有新的审批待办：「" + title + "」- " + t.getName(),
                        "TODO", t.getId());
            } catch (Exception e) {
                log.warn("通知失败 userId={}", uid);
            }
        }
    }

    private void saveCc(TaskActionRequest req, Task task) {
        if (req.getCcUserIds() == null || req.getCcUserIds().isEmpty()) return;
        WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
        for (Long uid : req.getCcUserIds()) {
            WfCcRecord cc = new WfCcRecord();
            cc.setProcessInstId(task.getProcessInstanceId());
            cc.setTaskId(task.getId());
            cc.setUserId(uid);
            cc.setTitle(ext == null ? task.getName() : ext.getTitle());
            cc.setReadFlag(0);
            ccRecordMapper.insert(cc);
            notifyService.send(uid, "抄送通知",
                    "您收到一条抄送：「" + cc.getTitle() + "」", "CC", task.getProcessInstanceId());
        }
    }
}
