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
import com.myworkflow.module.process.mapper.WfDoneTaskMapper;
import com.myworkflow.module.process.mapper.WfFormDefMapper;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.mapper.WfProcessInstanceExtMapper;
import com.myworkflow.module.process.util.BpmnEnhanceUtil;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.entity.TkTypeRelation;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.module.ticket.mapper.TkTypeMapper;
import com.myworkflow.module.ticket.mapper.TkTypeRelationMapper;
import com.myworkflow.module.ticket.service.TicketDataAccessService;
import com.myworkflow.module.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeService {

    /** 审批动作写在评论的 type 上，轨迹据此判断这一步是通过还是驳回 */
    static final String ACTION_APPROVE = "APPROVE";
    static final String ACTION_REJECT = "REJECT";
    static final String ACTION_TRANSFER = "TRANSFER";
    static final String ACTION_RESUBMIT = "RESUBMIT";
    static final String ACTION_ADD_SIGN = "ADD_SIGN";
    static final String ACTION_ADD_SIGN_APPROVE = "ADD_SIGN_APPROVE";
    static final String CATEGORY_ADD_SIGN = "ADD_SIGN";
    static final String ADD_SIGN_TASK_KEY_SUFFIX = "__add_sign";

    private static final Map<String, String> ACTION_LABELS;

    static {
        Map<String, String> labels = new HashMap<>();
        labels.put(ACTION_APPROVE, "通过");
        labels.put(ACTION_REJECT, "拒绝");
        labels.put(ACTION_TRANSFER, "转办");
        labels.put(ACTION_RESUBMIT, "重新提交");
        labels.put(ACTION_ADD_SIGN, "加签");
        labels.put(ACTION_ADD_SIGN_APPROVE, "加签完成");
        labels.put("PENDING", "待审批");
        labels.put("CANCELLED", "已取消");
        ACTION_LABELS = Collections.unmodifiableMap(labels);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WfProcessDefMapper processDefMapper;
    private final WfProcessInstanceExtMapper instanceExtMapper;
    private final WfCcRecordMapper ccRecordMapper;
    private final WfFormDefMapper formDefMapper;
    private final WfDoneTaskMapper doneTaskMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final SysUserMapper userMapper;
    private final NotifyService notifyService;
    private final TicketDataAccessService ticketDataAccessService;
    private final TkTicketMapper ticketMapper;
    private final TkTypeMapper ticketTypeMapper;
    private final TkTypeRelationMapper typeRelationMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ProcessFinishListener> finishListeners;

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

    /**
     * 我的已办按单据聚合，一张单据只出现一行。
     * 同一个人在一张单据上可能处理过多个节点（驳回后又处理重新提交就是典型情况），
     * 展示的是他最后处理的那个节点。
     */
    public PageResult<Map<String, Object>> doneList(long page, long size) {
        String userId = String.valueOf(UserContext.currentUserId());
        long total = doneTaskMapper.countDoneInstances(userId);
        // 总数已经单独统计过，让分页插件只负责拼方言分页
        Page<String> pageParam = new Page<>(page, size, false);
        List<String> instanceIds = doneTaskMapper.selectDoneInstanceIds(pageParam, userId);
        if (instanceIds.isEmpty()) {
            return PageResult.of(total, Collections.emptyList());
        }

        // 每个实例保留该用户最后处理的一条任务
        Map<String, HistoricTaskInstance> latest = new HashMap<>();
        Map<String, Integer> handledCount = new HashMap<>();
        for (HistoricTaskInstance t : historyService.createHistoricTaskInstanceQuery()
                .processInstanceIdIn(instanceIds)
                .taskAssignee(userId)
                .finished()
                .list()) {
            handledCount.merge(t.getProcessInstanceId(), 1, Integer::sum);
            latest.merge(t.getProcessInstanceId(), t,
                    (a, b) -> a.getEndTime().after(b.getEndTime()) ? a : b);
        }

        Map<String, String> approvers = currentApprovers(instanceIds);
        List<Map<String, Object>> records = new ArrayList<>();
        for (String instanceId : instanceIds) {
            HistoricTaskInstance t = latest.get(instanceId);
            if (t == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getId());
            m.put("taskName", t.getName());
            m.put("processInstanceId", instanceId);
            m.put("startTime", t.getCreateTime());
            m.put("endTime", t.getEndTime());
            m.put("handledCount", handledCount.getOrDefault(instanceId, 1));
            m.put("currentApprover", approvers.get(instanceId));
            WfProcessInstanceExt ext = findExt(instanceId);
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
        Map<String, String> approvers = currentApprovers(p.getRecords().stream()
                .map(WfProcessInstanceExt::getProcessInstId).collect(Collectors.toList()));
        p.getRecords().forEach(r -> r.setCurrentApprover(approvers.get(r.getProcessInstId())));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    /**
     * 批量查出这些实例当前停在谁手上。逐条查会把列表页打成 N+1，
     * 所以一次性把待办捞出来再按实例归并。工单列表复用同一套逻辑。
     */
    public Map<String, String> currentApprovers(Collection<String> processInstanceIds) {
        List<String> ids = processInstanceIds.stream()
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Task> tasks = taskService.createTaskQuery().processInstanceIdIn(ids).list();
        Map<String, Set<String>> byInstance = new LinkedHashMap<>();
        Set<String> allUserIds = new HashSet<>();
        for (Task t : tasks) {
            Set<String> users = byInstance.computeIfAbsent(t.getProcessInstanceId(), k -> new LinkedHashSet<>());
            if (StringUtils.hasText(t.getAssignee())) {
                users.add(t.getAssignee());
            } else {
                // 未认领的任务只有候选人，取候选人列表展示；配的是候选组就翻成组里的人
                taskService.getIdentityLinksForTask(t.getId()).forEach(link -> {
                    if (StringUtils.hasText(link.getUserId())) {
                        users.add(link.getUserId());
                    } else if (StringUtils.hasText(link.getGroupId())) {
                        users.addAll(usersOfGroup(link.getGroupId()));
                    }
                });
            }
            allUserIds.addAll(users);
        }

        Map<String, String> names = userNames(allUserIds);
        Map<String, String> result = new HashMap<>();
        byInstance.forEach((instanceId, users) -> result.put(instanceId,
                users.stream().map(u -> names.getOrDefault(u, u)).collect(Collectors.joining("、"))));
        return result;
    }

    private Map<String, String> userNames(Set<String> userIds) {
        List<Long> numeric = new ArrayList<>();
        for (String id : userIds) {
            try {
                numeric.add(Long.valueOf(id));
            } catch (NumberFormatException ignored) {
                // assignee 也可能是外部系统的账号标识，保持原样展示
            }
        }
        if (numeric.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> names = new HashMap<>();
        for (SysUser u : userMapper.selectBatchIds(numeric)) {
            names.put(String.valueOf(u.getId()),
                    StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
        }
        return names;
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(TaskActionRequest req) {
        Task task = getTask(req.getTaskId());
        assertCanHandle(task);
        assertTicketDataAccess(task.getProcessInstanceId());
        if (isAddSignTask(task)) {
            completeAddSignTask(task, req.getComment());
            return;
        }
        validateTaskRequiredFields(task);
        validateChildRequiredFields(task);
        validateChildRowCounts(task);
        validateChildTicketsClosed(task);
        claimIfNeeded(task);
        boolean resubmitTask = BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID
                .equals(task.getTaskDefinitionKey());
        // 即使没填意见也要留痕，审批轨迹靠 type 判断这一步是通过还是驳回
        addActionComment(task, resubmitTask ? ACTION_RESUBMIT : ACTION_APPROVE, req.getComment());
        Map<String, Object> vars = req.getVariables() == null ? new HashMap<>() : new HashMap<>(req.getVariables());
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
            WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
            if (ext != null) {
                WfProcessDef def = processDefMapper.selectById(ext.getProcessDefId());
                if (def != null) {
                    fillMissingFormFields(def, vars);
                }
                try {
                    ext.setFormData(objectMapper.writeValueAsString(req.getFormData()));
                    instanceExtMapper.updateById(ext);
                } catch (Exception e) {
                    throw new BizException("表单数据保存失败");
                }
            }
        }
        // 重新提交只是修改申请，不等同于审批通过
        vars.put("approved", !resubmitTask);
        vars.put("lastComment", req.getComment());
        taskService.complete(task.getId(), vars);
        saveCc(req, task);
        refreshInstanceStatus(task.getProcessInstanceId());
        notifyNext(task.getProcessInstanceId());
    }

    /**
     * 按流程设计图反向遍历当前节点的入向连线，只返回拓扑上的上游用户任务。
     * 不使用历史记录：历史里可能包含前一次驳回后再次经过的节点，也可能包含当前设计图
     * 并非当前节点上游的并行任务，都不应该因此成为可回退目标。
     */
    public List<Map<String, Object>> rejectTargets(String taskId) {
        Task task = getTask(taskId);
        Map<String, String> assigneeTypes = readAssigneeTypes(task.getProcessInstanceId());
        org.flowable.bpmn.model.Process process = repositoryService
                .getBpmnModel(task.getProcessDefinitionId()).getMainProcess();
        List<Map<String, Object>> targets = new ArrayList<>();
        FlowElement current = process.getFlowElement(task.getTaskDefinitionKey(), true);
        if (current instanceof FlowNode) {
            Deque<FlowNode> queue = new ArrayDeque<>();
            queue.add((FlowNode) current);
            Set<String> visited = new HashSet<>();
            visited.add(current.getId());

            while (!queue.isEmpty()) {
                FlowNode node = queue.removeFirst();
                for (SequenceFlow incoming : node.getIncomingFlows()) {
                    FlowElement source = incoming.getSourceFlowElement();
                    if (source == null && StringUtils.hasText(incoming.getSourceRef())) {
                        source = process.getFlowElement(incoming.getSourceRef(), true);
                    }
                    if (!(source instanceof FlowNode) || !visited.add(source.getId())) {
                        continue;
                    }
                    if (source instanceof UserTask
                            && !BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID.equals(source.getId())) {
                        Map<String, Object> target = new HashMap<>();
                        target.put("activityId", source.getId());
                        target.put("activityName", StringUtils.hasText(source.getName())
                                ? source.getName() : source.getId());
                        target.put("starterNode", "starter".equals(assigneeTypes.get(source.getId())));
                        targets.add(target);
                    }
                    queue.addLast((FlowNode) source);
                }
            }
        }

        // 开始事件本身不能承接待办，用发布时注入的系统任务代表「退回发起人」。
        if (process.getFlowElement(BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID, true) != null) {
            Map<String, Object> starterTarget = new HashMap<>();
            starterTarget.put("activityId", BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID);
            starterTarget.put("activityName", "发起人重新提交");
            starterTarget.put("starterNode", true);
            starterTarget.put("systemNode", true);
            targets.add(starterTarget);
        }
        return targets;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(TaskActionRequest req) {
        if (!StringUtils.hasText(req.getComment())) {
            throw new BizException("驳回意见不能为空");
        }
        Task task = getTask(req.getTaskId());
        assertCanHandle(task);
        assertTicketDataAccess(task.getProcessInstanceId());
        if (isAddSignTask(task)) {
            throw new BizException("加签任务只允许完成加签");
        }
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
        addActionComment(task, ACTION_REJECT, req.getComment());
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), req.getComment());
        WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
        if (ext != null) {
            ext.setStatus("REJECTED");
            ext.setEndTime(LocalDateTime.now());
            instanceExtMapper.updateById(ext);
            notifyService.send(ext.getStarterId(), "审批驳回",
                    "您的申请「" + ext.getTitle() + "」已被驳回并终止", "REJECT", ext.getProcessInstId());
            fireProcessFinished(task.getProcessInstanceId(), ext.getBusinessKey(), "REJECTED");
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
        addActionComment(task, ACTION_REJECT, req.getComment());
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
        if (BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID.equals(activityId)) {
            return BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_NAME;
        }
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
        assertCanHandle(task);
        assertTicketDataAccess(task.getProcessInstanceId());
        if (isAddSignTask(task)) {
            throw new BizException("加签任务不能转办");
        }
        if (!StringUtils.hasText(req.getTransferUserId())) {
            throw new BizException("请指定转办人");
        }
        // 先留痕再改派，否则轨迹上只剩转办后的人，看不出是谁转出去的
        addActionComment(task, ACTION_TRANSFER, req.getComment());
        taskService.setAssignee(task.getId(), req.getTransferUserId());
        notifyService.send(Long.valueOf(req.getTransferUserId()), "任务转办",
                "您有一个新的转办待办：" + Optional.ofNullable(findExt(task.getProcessInstanceId()))
                        .map(WfProcessInstanceExt::getTitle).orElse(task.getName()),
                "TRANSFER", task.getId());
    }

    /**
     * 前加签：暂时移走原任务的办理人，为所选用户创建关联同一流程实例的子任务。
     * 全部加签人完成后，再把原任务恢复给发起加签的审批人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(TaskActionRequest req) {
        Task parent = getTask(req.getTaskId());
        assertCanHandle(parent);
        assertTicketDataAccess(parent.getProcessInstanceId());
        if (isAddSignTask(parent)) {
            throw new BizException("加签任务不能继续加签");
        }
        List<String> userIds = req.getAddSignUserIds() == null
                ? Collections.emptyList()
                : req.getAddSignUserIds().stream().filter(StringUtils::hasText)
                .map(String::trim).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            throw new BizException("请选择加签人");
        }
        if (userIds.size() > 50) {
            throw new BizException("一次最多选择 50 名加签人");
        }
        if (activeAddSignChildren(parent.getId(), parent.getProcessInstanceId()) > 0) {
            throw new BizException("当前任务已有未完成的加签任务");
        }

        Long tenantId = UserContext.currentTenantId();
        String initiator = String.valueOf(UserContext.currentUserId());
        for (String userId : userIds) {
            if (initiator.equals(userId)) {
                throw new BizException("不能将自己选为加签人");
            }
            SysUser user;
            try {
                user = userMapper.selectById(Long.valueOf(userId));
            } catch (NumberFormatException e) {
                throw new BizException("加签用户不存在");
            }
            if (user == null || user.getStatus() == null || user.getStatus() != 1
                    || !Objects.equals(user.getTenantId(), tenantId)) {
                throw new BizException("加签用户不存在、已停用或不属于当前租户");
            }
        }

        claimIfNeeded(parent);
        // 清掉候选人，否则加签期间同组的其他候选人仍能看到并处理原任务
        for (IdentityLink link : taskService.getIdentityLinksForTask(parent.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (StringUtils.hasText(link.getUserId())) {
                taskService.deleteCandidateUser(parent.getId(), link.getUserId());
            } else if (StringUtils.hasText(link.getGroupId())) {
                taskService.deleteCandidateGroup(parent.getId(), link.getGroupId());
            }
        }
        addActionComment(parent, ACTION_ADD_SIGN, req.getComment());

        // 上面每次改动都会推高任务的 REV_，必须重新查出最新实体再写回，否则触发乐观锁异常
        Task suspended = getTask(parent.getId());
        suspended.setOwner(initiator);
        suspended.setAssignee(null);
        taskService.saveTask(suspended);

        WfProcessInstanceExt ext = findExt(parent.getProcessInstanceId());
        for (String userId : userIds) {
            Task child = taskService.newTask();
            child.setName(parent.getName() + "（加签）");
            child.setDescription("由 " + displayName(initiator) + " 发起加签");
            child.setParentTaskId(parent.getId());
            child.setAssignee(userId);
            child.setOwner(initiator);
            child.setCategory(CATEGORY_ADD_SIGN);
            child.setTenantId(parent.getTenantId());
            TaskEntity entity = (TaskEntity) child;
            entity.setProcessInstanceId(parent.getProcessInstanceId());
            entity.setProcessDefinitionId(parent.getProcessDefinitionId());
            entity.setTaskDefinitionKey(parent.getTaskDefinitionKey() + ADD_SIGN_TASK_KEY_SUFFIX);
            taskService.saveTask(child);
            notifyService.send(Long.valueOf(userId), "审批加签",
                    "您有一个新的加签任务：" + (ext == null ? parent.getName() : ext.getTitle()),
                    "ADD_SIGN", child.getId());
        }
    }

    private void completeAddSignTask(Task task, String comment) {
        String parentId = task.getParentTaskId();
        if (!StringUtils.hasText(parentId)) {
            throw new BizException("加签任务缺少原审批任务");
        }
        addActionComment(task, ACTION_ADD_SIGN_APPROVE, comment);
        taskService.complete(task.getId());
        if (activeAddSignChildren(parentId, task.getProcessInstanceId()) > 0) {
            return;
        }
        Task parent = taskService.createTaskQuery().taskId(parentId).singleResult();
        if (parent == null || !StringUtils.hasText(parent.getOwner())) {
            throw new BizException("原审批任务不存在，无法恢复");
        }
        String approver = parent.getOwner();
        parent.setOwner(null);
        parent.setAssignee(approver);
        taskService.saveTask(parent);
        WfProcessInstanceExt ext = findExt(parent.getProcessInstanceId());
        notifyService.send(Long.valueOf(approver), "加签已完成",
                "加签人已完成审批：" + (ext == null ? parent.getName() : ext.getTitle()),
                "ADD_SIGN_DONE", parent.getId());
    }

    private boolean isAddSignTask(Task task) {
        return task != null && CATEGORY_ADD_SIGN.equals(task.getCategory())
                && StringUtils.hasText(task.getParentTaskId());
    }

    private long activeAddSignChildren(String parentTaskId, String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).active().list().stream()
                .filter(this::isAddSignTask)
                .filter(task -> parentTaskId.equals(task.getParentTaskId()))
                .count();
    }

    private String displayName(String userId) {
        try {
            SysUser user = userMapper.selectById(Long.valueOf(userId));
            return user == null ? userId
                    : (StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        } catch (NumberFormatException e) {
            return userId;
        }
    }

    /**
     * 当前用户在该实例上的待办任务，没有则返回 null。
     * 工单详情据此决定要不要显示审批按钮，省得审批人绕回待办列表。
     */
    public Map<String, Object> myActiveTask(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return null;
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateOrAssigned(String.valueOf(userId))
                .active()
                .orderByTaskCreateTime().asc()
                .list();
        if (tasks.isEmpty()) {
            return null;
        }
        Task task = tasks.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getId());
        m.put("taskName", task.getName());
        m.put("processInstanceId", processInstanceId);
        m.put("resubmitTask", BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID.equals(task.getTaskDefinitionKey()));
        m.put("addSignTask", isAddSignTask(task));
        if (isAddSignTask(task)) {
            m.put("writableFields", Collections.emptyList());
            m.put("requiredFields", Collections.emptyList());
            m.put("detailConfigs", Collections.emptyList());
            m.put("childValidationMode", "NONE");
            m.put("childValidationRelationIds", Collections.emptyList());
        } else {
            m.putAll(taskFieldConfig(task));
            m.putAll(BpmnEnhanceUtil.readTaskDetailConfig(
                    processModelXml(task.getProcessDefinitionId()), task.getTaskDefinitionKey()));
        }
        return m;
    }

    /**
     * 返回当前用户在实例上的任务及字段权限。调用方可据此过滤提交字段；
     * taskCandidateOrAssigned 保证转办后原处理人立即失去编辑权限。
     */
    public Map<String, Object> myTaskFieldConfig(String processInstanceId) {
        Map<String, Object> task = myActiveTask(processInstanceId);
        if (task == null) {
            throw new BizException(403, "当前用户不是该节点处理人");
        }
        return task;
    }

    /** 工单字段保存后同步流程变量和实例快照，保证后续网关和审批人表达式读取新值。 */
    public void syncFormData(String processInstanceId, Map<String, Object> formData) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BizException("工单尚未进入审批");
        }
        Map<String, Object> safe = formData == null ? new HashMap<>() : new HashMap<>(formData);
        runtimeService.setVariables(processInstanceId, safe);
        WfProcessInstanceExt ext = findExt(processInstanceId);
        if (ext != null) {
            try {
                ext.setFormData(objectMapper.writeValueAsString(safe));
                instanceExtMapper.updateById(ext);
            } catch (Exception e) {
                throw new BizException("流程表单数据同步失败");
            }
        }
    }

    /** 当前用户是不是正被退回、需要改单重提的发起人 */
    public boolean hasResubmitTask(String processInstanceId) {
        Map<String, Object> task = myActiveTask(processInstanceId);
        return task != null && Boolean.TRUE.equals(task.get("resubmitTask"));
    }

    /** 删除被退回、等待发起人重新提交的工单时，同步终止活动流程，避免留下孤立待办。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelForTicketDeletion(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return;
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (instance != null) {
            runtimeService.deleteProcessInstance(processInstanceId, "发起人删除重新提交工单");
        }
        WfProcessInstanceExt ext = findExt(processInstanceId);
        if (ext != null) {
            ext.setStatus("CANCELLED");
            ext.setEndTime(LocalDateTime.now());
            instanceExtMapper.updateById(ext);
        }
    }

    /** 处理过或当前待办的人都算参与人，业务单据的可见性判断会用到 */
    public boolean isInvolved(String processInstanceId, Long userId) {
        if (!StringUtils.hasText(processInstanceId) || userId == null) {
            return false;
        }
        String uid = String.valueOf(userId);
        long handled = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskInvolvedUser(uid)
                .count();
        if (handled > 0) {
            return true;
        }
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateOrAssigned(uid)
                .active()
                .count() > 0;
    }

    /**
     * 节点字段的可见性：字段跟着它归属的节点走。
     * 归属节点已经到达（含正在处理）的字段才展示，尚未走到的节点字段先不显示，
     * 避免发起人和前置审批人看到后续环节才录入的内容。
     */
    public Map<String, List<String>> fieldVisibility(String processInstanceId) {
        Map<String, List<String>> result = new HashMap<>();
        result.put("nodeFields", new ArrayList<>());
        result.put("hiddenFields", new ArrayList<>());
        if (!StringUtils.hasText(processInstanceId)) {
            return result;
        }
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (instance == null) {
            return result;
        }
        Map<String, List<String>> byActivity =
                BpmnEnhanceUtil.readTaskFieldsByActivity(processModelXml(instance.getProcessDefinitionId()));
        Set<String> reached = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId).list().stream()
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());

        Set<String> all = new LinkedHashSet<>();
        Set<String> visible = new LinkedHashSet<>();
        byActivity.forEach((activityId, fields) -> {
            all.addAll(fields);
            if (reached.contains(activityId)) {
                visible.addAll(fields);
            }
        });
        List<String> hidden = all.stream().filter(f -> !visible.contains(f)).collect(Collectors.toList());
        result.put("nodeFields", new ArrayList<>(all));
        result.put("hiddenFields", hidden);
        return result;
    }

    public Map<String, List<String>> detailVisibility(String processInstanceId) {
        Map<String, List<String>> result = new HashMap<>();
        result.put("nodeRelationIds", new ArrayList<>());
        result.put("hiddenRelationIds", new ArrayList<>());
        if (!StringUtils.hasText(processInstanceId)) return result;
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (instance == null) return result;
        Map<String, List<String>> byActivity = BpmnEnhanceUtil.readTaskDetailRelationsByActivity(
                processModelXml(instance.getProcessDefinitionId()));
        Set<String> reached = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId).list().stream()
                .map(HistoricActivityInstance::getActivityId).collect(Collectors.toSet());
        Set<String> all = new LinkedHashSet<>();
        Set<String> visible = new LinkedHashSet<>();
        byActivity.forEach((activityId, ids) -> {
            all.addAll(ids);
            if (reached.contains(activityId)) visible.addAll(ids);
        });
        result.put("nodeRelationIds", new ArrayList<>(all));
        result.put("hiddenRelationIds",
                all.stream().filter(id -> !visible.contains(id)).collect(Collectors.toList()));
        return result;
    }

    public List<String> designNodeRelations(String bpmnXml) {
        Set<String> ids = new LinkedHashSet<>();
        BpmnEnhanceUtil.readTaskDetailRelationsByActivity(bpmnXml).values().forEach(ids::addAll);
        return new ArrayList<>(ids);
    }

    /** 流程还没发起时，所有节点字段都属于后续环节，创建工单时一律不显示。 */
    public List<String> designNodeFields(String bpmnXml) {
        Set<String> fields = new LinkedHashSet<>();
        BpmnEnhanceUtil.readTaskFieldsByActivity(bpmnXml).values().forEach(fields::addAll);
        return new ArrayList<>(fields);
    }

    private String processModelXml(String processDefinitionId) {
        try (InputStream input = repositoryService.getProcessModel(processDefinitionId);
             Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception e) {
            log.warn("读取流程模型失败 defId={}: {}", processDefinitionId, e.getMessage());
            return "";
        }
    }

    private Map<String, List<String>> taskFieldConfig(Task task) {
        // 必须读取该任务实际运行的已部署版本。若读 wf_process_def 当前设计稿，
        // 只保存但尚未发布的修改也会错误地影响在途实例。
        return BpmnEnhanceUtil.readTaskFieldConfig(
                processModelXml(task.getProcessDefinitionId()), task.getTaskDefinitionKey());
    }

    private void validateTaskRequiredFields(Task task) {
        List<String> required = taskFieldConfig(task).get("requiredFields");
        if (required == null || required.isEmpty()) {
            return;
        }
        // 流程变量是节点保存后最先更新的一份，实例快照只作兜底
        Map<String, Object> data = new HashMap<>();
        WfProcessInstanceExt ext = findExt(task.getProcessInstanceId());
        if (ext != null && StringUtils.hasText(ext.getFormData())) {
            try {
                data = objectMapper.readValue(ext.getFormData(), Map.class);
            } catch (Exception e) {
                throw new BizException("流程表单数据无法解析");
            }
        }
        data.putAll(runtimeService.getVariables(task.getProcessInstanceId()));
        final Map<String, Object> formData = data;
        List<String> missing = required.stream()
                .filter(field -> isBlankValue(formData.get(field)))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BizException("请填写本节点必填字段：" + String.join("、", missing));
        }
    }

    /**
     * 主流程节点可选择不校验、校验全部明细类型或校验设计器勾选的明细类型。
     * 按配置顺序逐类执行 EXISTS 查询，发现第一类未关闭就立即返回。
     */
    private void validateChildTicketsClosed(Task task) {
        Map<String, Object> config = BpmnEnhanceUtil.readTaskDetailConfig(
                processModelXml(task.getProcessDefinitionId()), task.getTaskDefinitionKey());
        String mode = String.valueOf(config.getOrDefault("childValidationMode", "NONE"));
        if ("NONE".equals(mode)) return;

        TkTicket parent = ticketDataAccessService.ticketByProcess(task.getProcessInstanceId());
        if (parent == null) return;
        ticketMapper.selectForUpdate(parent.getId());
        List<TkTypeRelation> relations;
        if ("ALL".equals(mode)) {
            relations = typeRelationMapper.selectList(new LambdaQueryWrapper<TkTypeRelation>()
                    .eq(TkTypeRelation::getParentTypeId, parent.getTypeId())
                    .eq(TkTypeRelation::getStatus, 1)
                    .orderByAsc(TkTypeRelation::getSortNo)
                    .orderByAsc(TkTypeRelation::getId));
        } else {
            List<Long> ids = new ArrayList<>();
            Object configured = config.get("childValidationRelationIds");
            if (configured instanceof Iterable) {
                for (Object id : (Iterable<?>) configured) {
                    try {
                        ids.add(Long.valueOf(String.valueOf(id)));
                    } catch (NumberFormatException ignored) {
                        // 发布后关系被删或配置损坏时跳过无效 ID
                    }
                }
            }
            if (ids.isEmpty()) return;
            Map<Long, TkTypeRelation> indexed = typeRelationMapper.selectBatchIds(ids).stream()
                    .filter(r -> parent.getTypeId().equals(r.getParentTypeId()) && Integer.valueOf(1).equals(r.getStatus()))
                    .collect(Collectors.toMap(TkTypeRelation::getId, r -> r));
            relations = ids.stream().map(indexed::get).filter(Objects::nonNull).collect(Collectors.toList());
        }

        for (TkTypeRelation relation : relations) {
            Long unfinished = ticketMapper.selectCount(new LambdaQueryWrapper<TkTicket>()
                    .eq(TkTicket::getParentTicketId, parent.getId())
                    .eq(TkTicket::getTypeRelationId, relation.getId())
                    .in(TkTicket::getStatus, TicketService.STATUS_DRAFT, TicketService.STATUS_IN_APPROVAL));
            if (unfinished != null && unfinished > 0) {
                String name = relation.getRelationName();
                if (!StringUtils.hasText(name)) {
                    TkType childType = ticketTypeMapper.selectById(relation.getChildTypeId());
                    name = childType == null ? "明细工单" : childType.getTypeName();
                }
                throw new BizException(name + "未全部关闭");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateChildRequiredFields(Task task) {
        Map<String, Object> config = BpmnEnhanceUtil.readTaskDetailConfig(
                processModelXml(task.getProcessDefinitionId()), task.getTaskDefinitionKey());
        Object rawConfigs = config.get("detailConfigs");
        if (!(rawConfigs instanceof Iterable)) return;
        TkTicket parent = ticketDataAccessService.ticketByProcess(task.getProcessInstanceId());
        if (parent == null) return;
        for (Object raw : (Iterable<?>) rawConfigs) {
            if (!(raw instanceof Map)) continue;
            Map<String, Object> detail = (Map<String, Object>) raw;
            if (!Boolean.TRUE.equals(detail.get("visible"))) continue;
            List<String> required = new ArrayList<>();
            Object rawRequired = detail.get("requiredFields");
            if (rawRequired instanceof Iterable) {
                for (Object field : (Iterable<?>) rawRequired) required.add(String.valueOf(field));
            }
            if (required.isEmpty()) continue;
            Long relationId;
            try {
                relationId = Long.valueOf(String.valueOf(detail.get("relationId")));
            } catch (Exception e) {
                continue;
            }
            TkTypeRelation relation = typeRelationMapper.selectById(relationId);
            if (relation == null || !parent.getTypeId().equals(relation.getParentTypeId())) continue;
            List<TkTicket> children = ticketMapper.selectList(new LambdaQueryWrapper<TkTicket>()
                    .eq(TkTicket::getParentTicketId, parent.getId())
                    .eq(TkTicket::getTypeRelationId, relationId));
            for (TkTicket child : children) {
                Map<String, Object> data = child.getFormData() == null
                        ? Collections.emptyMap() : child.getFormData();
                Optional<String> missing = required.stream().filter(f -> isBlankValue(data.get(f))).findFirst();
                if (missing.isPresent()) {
                    String name = StringUtils.hasText(relation.getRelationName())
                            ? relation.getRelationName() : "明细工单";
                    throw new BizException(name + "存在未填写的必填字段：" + missing.get());
                }
            }
        }
    }

    /**
     * 明细条数下限：节点填了大于 0 的值就以节点为准，否则用明细关系上的默认值。
     * 上限在新增明细时就已经拦住，这里只需要补下限。
     */
    @SuppressWarnings("unchecked")
    private void validateChildRowCounts(Task task) {
        Map<String, Object> config = BpmnEnhanceUtil.readTaskDetailConfig(
                processModelXml(task.getProcessDefinitionId()), task.getTaskDefinitionKey());
        Object rawConfigs = config.get("detailConfigs");
        if (!(rawConfigs instanceof Iterable)) return;
        TkTicket parent = ticketDataAccessService.ticketByProcess(task.getProcessInstanceId());
        if (parent == null) return;
        for (Object raw : (Iterable<?>) rawConfigs) {
            if (!(raw instanceof Map)) continue;
            Map<String, Object> detail = (Map<String, Object>) raw;
            if (!Boolean.TRUE.equals(detail.get("visible"))) continue;
            Long relationId;
            try {
                relationId = Long.valueOf(String.valueOf(detail.get("relationId")));
            } catch (Exception e) {
                continue;
            }
            TkTypeRelation relation = typeRelationMapper.selectById(relationId);
            if (relation == null || !parent.getTypeId().equals(relation.getParentTypeId())) continue;
            int min = intValue(detail.get("minRows"));
            if (min <= 0) min = relation.getMinRows() == null ? 0 : relation.getMinRows();
            if (min <= 0) continue;
            Long count = ticketMapper.selectCount(new LambdaQueryWrapper<TkTicket>()
                    .eq(TkTicket::getParentTicketId, parent.getId())
                    .eq(TkTicket::getTypeRelationId, relationId)
                    .ne(TkTicket::getStatus, TicketService.STATUS_CANCELLED));
            if (count == null || count < min) {
                String name = StringUtils.hasText(relation.getRelationName())
                        ? relation.getRelationName() : "明细工单";
                throw new BizException(name + "至少需要 " + min + " 条");
            }
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) return true;
        if (value instanceof String) return !StringUtils.hasText((String) value);
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        return false;
    }

    public Map<String, Object> taskDetail(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        Map<String, Object> m = new HashMap<>();
        if (task != null) {
            m.putAll(toTaskMap(task));
            m.put("variables", runtimeService.getVariables(task.getProcessInstanceId()));
            boolean resubmitTask = BpmnEnhanceUtil.SYSTEM_RESUBMIT_ACTIVITY_ID
                    .equals(task.getTaskDefinitionKey());
            m.put("resubmitTask", resubmitTask);
            if (resubmitTask) {
                appendFormSchema(m, findExt(task.getProcessInstanceId()));
            }
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
        applyTicketDataAccess(m, (String) m.get("processInstanceId"));
        return m;
    }

    private void applyTicketDataAccess(Map<String, Object> detail, String processInstanceId) {
        TkTicket ticket = ticketDataAccessService.ticketByProcess(processInstanceId);
        if (ticket == null) {
            detail.put("dataAccess", true);
            return;
        }
        boolean allowed = ticketDataAccessService.hasDataAccess(ticket);
        detail.put("dataAccess", allowed);
        if (!allowed) {
            detail.put("variables", Collections.emptyMap());
            detail.put("formData", "{}");
            detail.put("accessMessage",
                    "您是当前审批人，但用户角色的数据权限不包含该工单，字段已隐藏且暂不能办理；授权后请刷新页面");
        }
    }

    private void appendFormSchema(Map<String, Object> detail, WfProcessInstanceExt ext) {
        if (ext == null || ext.getProcessDefId() == null) {
            detail.put("formSchema", Collections.emptyList());
            return;
        }
        WfProcessDef def = processDefMapper.selectById(ext.getProcessDefId());
        WfFormDef form = def == null || def.getFormId() == null
                ? null : formDefMapper.selectById(def.getFormId());
        if (form == null || !StringUtils.hasText(form.getFormSchema())) {
            detail.put("formSchema", Collections.emptyList());
            return;
        }
        try {
            detail.put("formSchema", objectMapper.readValue(form.getFormSchema(), List.class));
        } catch (Exception e) {
            detail.put("formSchema", Collections.emptyList());
        }
    }

    /**
     * 流程实例详情，供已办结的单据查看审批轨迹。
     * 与 taskDetail 的区别：不依赖是否还有活动任务，流程跑完了照样能查。
     */
    public Map<String, Object> instanceDetail(String processInstanceId) {
        WfProcessInstanceExt ext = findExt(processInstanceId);
        if (ext == null) {
            throw new BizException("流程实例不存在");
        }
        assertCanView(ext);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("processInstanceId", ext.getProcessInstId());
        m.put("title", ext.getTitle());
        m.put("processKey", ext.getProcessKey());
        m.put("businessKey", ext.getBusinessKey());
        m.put("businessType", ext.getBusinessType());
        m.put("starterName", ext.getStarterName());
        m.put("status", ext.getStatus());
        m.put("startTime", ext.getStartTime() == null ? null : ext.getStartTime().format(TIME_FORMAT));
        m.put("endTime", ext.getEndTime() == null ? null : ext.getEndTime().format(TIME_FORMAT));
        m.put("formData", ext.getFormData());
        m.put("currentApprover", currentApprovers(
                Collections.singletonList(processInstanceId)).get(processInstanceId));
        appendFormSchema(m, ext);
        applyTicketDataAccess(m, processInstanceId);
        return m;
    }

    /** 审批记录只对相关人可见：管理员、发起人、参与过的审批人、被抄送人 */
    private void assertCanView(WfProcessInstanceExt ext) {
        UserContext ctx = UserContext.get();
        if (ctx != null && ctx.isAdmin()) {
            return;
        }
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            throw new BizException("无权查看该审批记录");
        }
        if (userId.equals(ext.getStarterId())) {
            return;
        }
        long handled = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(ext.getProcessInstId())
                .taskInvolvedUser(String.valueOf(userId))
                .count();
        if (handled > 0) {
            return;
        }
        Long cc = ccRecordMapper.selectCount(new LambdaQueryWrapper<WfCcRecord>()
                .eq(WfCcRecord::getProcessInstId, ext.getProcessInstId())
                .eq(WfCcRecord::getUserId, userId));
        if (cc == null || cc == 0) {
            throw new BizException("无权查看该审批记录");
        }
    }

    /**
     * 审批轨迹。以历史任务为单位而不是历史活动：历史任务一定带 assignee 和起止时间，
     * 驳回、回退这类没有正常 complete 的任务也能拿到处理人。
     * 相邻且同节点的任务合并成一段（会签的多个人聚在一起），
     * 回退后再次经过同一节点时不相邻，会自然形成新的一段。
     */
    public Map<String, Object> timeline(String processInstanceId) {
        WfProcessInstanceExt ext = findExt(processInstanceId);
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .orderByTaskId().asc()
                .list();

        Map<String, List<Comment>> commentsByTask = taskService
                .getProcessInstanceComments(processInstanceId).stream()
                .filter(c -> StringUtils.hasText(c.getTaskId()))
                .collect(Collectors.groupingBy(Comment::getTaskId));

        Set<String> userIds = new HashSet<>();
        tasks.forEach(t -> {
            if (StringUtils.hasText(t.getAssignee())) userIds.add(t.getAssignee());
        });
        commentsByTask.values().forEach(list -> list.forEach(c -> {
            if (StringUtils.hasText(c.getUserId())) userIds.add(c.getUserId());
        }));
        // 未结束的节点要看运行时任务：历史表里的 assignee 停留在任务创建那一刻，转办、认领都不会更新
        Map<String, Task> liveTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId).list().stream()
                .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
        liveTasks.values().forEach(t -> {
            if (StringUtils.hasText(t.getAssignee())) userIds.add(t.getAssignee());
        });
        Map<String, List<String>> candidatesByTask = candidateUsers(tasks, liveTasks);
        candidatesByTask.values().forEach(userIds::addAll);
        Map<String, String> names = userNames(userIds);

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> group = null;
        for (HistoricTaskInstance t : tasks) {
            if (group == null || !Objects.equals(group.get("activityId"), t.getTaskDefinitionKey())) {
                group = new LinkedHashMap<>();
                group.put("activityId", t.getTaskDefinitionKey());
                group.put("activityName", StringUtils.hasText(t.getName())
                        ? t.getName() : t.getTaskDefinitionKey());
                group.put("handlers", new ArrayList<Map<String, Object>>());
                nodes.add(group);
            }
            appendHandler(group, t, commentsByTask.get(t.getId()), names,
                    candidatesByTask.get(t.getId()), liveTasks.get(t.getId()));
        }
        nodes.forEach(this::summarizeNode);
        // 前加签先于原审批人办理，但子任务创建更晚；按开始时间分组会排到原节点后面，这里挪回前面
        nodes = reorderAddSignAhead(nodes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("starterName", ext == null ? null : ext.getStarterName());
        result.put("startTime", ext == null || ext.getStartTime() == null
                ? null : ext.getStartTime().format(TIME_FORMAT));
        result.put("status", ext == null ? null : ext.getStatus());
        result.put("finished", ext != null && !"RUNNING".equals(ext.getStatus()));
        result.put("endTime", ext == null || ext.getEndTime() == null
                ? null : ext.getEndTime().format(TIME_FORMAT));
        result.put("nodes", nodes);
        return result;
    }

    /**
     * 把加签节点挪到对应原审批节点前面。只匹配时间线上最近的上一个同节点，
     * 避免驳回后再经过同一节点时，把后一次加签插到第一次审批前面。
     */
    private List<Map<String, Object>> reorderAddSignAhead(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> result = new ArrayList<>(nodes);
        for (int i = 0; i < result.size(); i++) {
            String activityId = (String) result.get(i).get("activityId");
            if (!StringUtils.hasText(activityId) || !activityId.endsWith(ADD_SIGN_TASK_KEY_SUFFIX)) {
                continue;
            }
            String parentId = activityId.substring(0, activityId.length() - ADD_SIGN_TASK_KEY_SUFFIX.length());
            int parentIndex = -1;
            for (int j = i - 1; j >= 0; j--) {
                if (parentId.equals(result.get(j).get("activityId"))) {
                    parentIndex = j;
                    break;
                }
            }
            if (parentIndex < 0) {
                continue;
            }
            result.add(parentIndex, result.remove(i));
            i = parentIndex;
        }
        return result;
    }

    /**
     * 多人审批的节点在有人认领前只有候选人、没有 assignee，
     * 轨迹上要把这些候选人列出来，否则待审批那一步显示不出是谁该处理。
     */
    private Map<String, List<String>> candidateUsers(List<HistoricTaskInstance> tasks,
                                                     Map<String, Task> liveTasks) {
        Map<String, List<String>> result = new HashMap<>();
        for (HistoricTaskInstance t : tasks) {
            Task live = liveTasks.get(t.getId());
            List<String> users = new ArrayList<>();
            List<String> groups = new ArrayList<>();
            if (live != null) {
                if (StringUtils.hasText(live.getAssignee())) {
                    continue;
                }
                taskService.getIdentityLinksForTask(t.getId()).forEach(link -> {
                    if (StringUtils.hasText(link.getUserId())) users.add(link.getUserId());
                    else if (StringUtils.hasText(link.getGroupId())) groups.add(link.getGroupId());
                });
            } else {
                if (StringUtils.hasText(t.getAssignee())) {
                    continue;
                }
                for (HistoricIdentityLink link : historyService.getHistoricIdentityLinksForTask(t.getId())) {
                    if (StringUtils.hasText(link.getUserId())) users.add(link.getUserId());
                    else if (StringUtils.hasText(link.getGroupId())) groups.add(link.getGroupId());
                }
            }

            List<String> candidates = users.stream().distinct().collect(Collectors.toList());
            if (candidates.isEmpty()) {
                // 节点配的是候选组，身份连接上只有组编码
                candidates = groups.stream().distinct()
                        .flatMap(g -> usersOfGroup(g).stream())
                        .distinct()
                        .collect(Collectors.toList());
            }
            if (!candidates.isEmpty()) {
                result.put(t.getId(), candidates);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void appendHandler(Map<String, Object> group, HistoricTaskInstance task,
                               List<Comment> comments, Map<String, String> names,
                               List<String> candidates, Task live) {
        // 一个任务可能有多条留痕（如先加签后审批），取最新的一条代表这一步的结果。
        // Flowable 返回的评论按时间倒序，不能靠列表顺序判断新旧
        Comment acted = comments == null ? null : comments.stream()
                .filter(c -> ACTION_LABELS.containsKey(c.getType()) || StringUtils.hasText(c.getFullMessage()))
                .max(Comparator.comparing(Comment::getTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        String userId;
        if (task.getEndTime() == null) {
            // 待办看运行时，转办改派后要显示新的处理人而不是留痕里的转出人
            userId = live != null ? live.getAssignee() : task.getAssignee();
        } else {
            // 已办以留痕为准，历史表的 assignee 可能还是任务创建时的值
            userId = acted != null && StringUtils.hasText(acted.getUserId())
                    ? acted.getUserId() : task.getAssignee();
        }
        String action = resolveAction(task, acted);

        String displayName;
        if (StringUtils.hasText(userId)) {
            displayName = names.getOrDefault(userId, userId);
        } else if (candidates != null && !candidates.isEmpty()) {
            // 多人待办在有人认领前没有 assignee，列出候选人才能看出该谁处理
            displayName = candidates.stream()
                    .map(c -> names.getOrDefault(c, c))
                    .collect(Collectors.joining("、"));
        } else {
            displayName = task.getEndTime() == null ? "待认领" : "系统";
        }

        Map<String, Object> handler = new LinkedHashMap<>();
        handler.put("userId", userId);
        handler.put("candidateCount", candidates == null ? 0 : candidates.size());
        handler.put("name", displayName);
        handler.put("action", action);
        handler.put("actionText", ACTION_LABELS.getOrDefault(action, "处理中"));
        handler.put("comment", acted == null ? null : stripLegacyPrefix(acted.getFullMessage()));
        handler.put("time", formatDate(task.getEndTime() != null ? task.getEndTime()
                : acted == null ? null : acted.getTime()));
        handler.put("startTime", formatDate(task.getStartTime()));
        ((List<Map<String, Object>>) group.get("handlers")).add(handler);

        if (task.getStartTime() != null) {
            group.merge("rawStart", task.getStartTime(), (a, b) ->
                    ((Date) a).before((Date) b) ? a : b);
        }
        if (task.getEndTime() != null) {
            group.merge("rawEnd", task.getEndTime(), (a, b) ->
                    ((Date) a).after((Date) b) ? a : b);
        }
    }

    /** 动作优先看评论上的 type；老数据没有 type，退回到消息前缀识别 */
    private String resolveAction(HistoricTaskInstance task, Comment comment) {
        // 还没结束的任务一律算待审批：转办只是换了个人，这一步仍未完成
        if (task.getEndTime() == null) {
            return "PENDING";
        }
        if (comment != null && ACTION_LABELS.containsKey(comment.getType())) {
            return comment.getType();
        }
        String message = comment == null ? "" : Optional.ofNullable(comment.getFullMessage()).orElse("");
        if (message.startsWith("驳回")) return ACTION_REJECT;
        if (message.startsWith("转办")) return ACTION_TRANSFER;
        if (message.startsWith("重新提交")) return ACTION_RESUBMIT;
        if (message.startsWith("同意")) return ACTION_APPROVE;
        // 已结束但没有任何操作记录：多半是流程被终止时一并清掉的并行待办
        return StringUtils.hasText(task.getDeleteReason()) ? "CANCELLED" : ACTION_APPROVE;
    }

    private String stripLegacyPrefix(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        int idx = message.indexOf('：');
        if (idx > 0 && idx < 12) {
            String head = message.substring(0, idx);
            if (head.startsWith("驳回") || head.startsWith("同意")
                    || head.startsWith("转办") || head.startsWith("重新提交")) {
                return message.substring(idx + 1);
            }
        }
        return message;
    }

    @SuppressWarnings("unchecked")
    private void summarizeNode(Map<String, Object> node) {
        List<Map<String, Object>> handlers = (List<Map<String, Object>>) node.get("handlers");
        boolean rejected = handlers.stream().anyMatch(h -> ACTION_REJECT.equals(h.get("action")));
        boolean pending = handlers.stream().anyMatch(h -> "PENDING".equals(h.get("action")));

        String status = rejected ? "REJECTED" : pending ? "PENDING" : "APPROVED";
        node.put("status", status);
        node.put("statusText", rejected ? "拒绝" : pending ? "审批中" : "通过");

        Date start = (Date) node.remove("rawStart");
        Date end = (Date) node.remove("rawEnd");
        node.put("startTime", formatDate(start));
        node.put("endTime", formatDate(end));
        node.put("durationText", pending || start == null || end == null
                ? null : durationText(end.getTime() - start.getTime()));
    }

    private String durationText(long millis) {
        long minutes = millis / 60000;
        if (minutes < 1) {
            return "小于1分钟";
        }
        long days = minutes / (60 * 24);
        long hours = minutes % (60 * 24) / 60;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (mins > 0) sb.append(mins).append("分钟");
        return sb.toString();
    }

    private String formatDate(Date date) {
        return date == null ? null
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    public PageResult<WfCcRecord> myCc(long page, long size) {
        Page<WfCcRecord> p = ccRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfCcRecord>()
                        .eq(WfCcRecord::getUserId, UserContext.currentUserId())
                        .orderByDesc(WfCcRecord::getCreateTime));
        Map<String, String> approvers = currentApprovers(p.getRecords().stream()
                .map(WfCcRecord::getProcessInstId).collect(Collectors.toList()));
        p.getRecords().forEach(r -> r.setCurrentApprover(approvers.get(r.getProcessInstId())));
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

    /**
     * Flowable 的评论作者取自 Authentication 上下文，不设置的话 userId 会是空的，
     * 审批轨迹就认不出这一步是谁办的。所有留痕都要走这里。
     */
    private void addActionComment(Task task, String type, String message) {
        String previous = Authentication.getAuthenticatedUserId();
        Authentication.setAuthenticatedUserId(String.valueOf(UserContext.currentUserId()));
        try {
            taskService.addComment(task.getId(), task.getProcessInstanceId(),
                    type, message == null ? "" : message);
        } finally {
            Authentication.setAuthenticatedUserId(previous);
        }
    }

    /** 节点直接配候选组时身份连接上只有组编码，要翻成具体的人才能展示 */
    private List<String> usersOfGroup(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            return Collections.emptyList();
        }
        return userMapper.selectUserIdsByRoleCode(groupCode).stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 办理前把任务落到当前用户名下。多人候选的任务在有人处理前没有 assignee，
     * 不落名的话历史记录里查不到是谁办的，「我的已办」也会漏掉这条。
     */
    private void claimIfNeeded(Task task) {
        String userId = String.valueOf(UserContext.currentUserId());
        assertCanHandle(task);
        // Flowable 记录历史任务在 create 监听器之前，监听器里设置的 assignee 不会同步到历史表，
        // 历史里留下的是任务创建那一刻的值。不回写的话「我的已办」会漏单、轨迹会显示错的处理人。
        // 运行时已经是本人时 setAssignee 会被引擎跳过，所以先置空再设一次强制落库。
        taskService.setAssignee(task.getId(), null);
        taskService.setAssignee(task.getId(), userId);
    }

    private void assertCanHandle(Task task) {
        String userId = String.valueOf(UserContext.currentUserId());
        long count = taskService.createTaskQuery()
                .taskId(task.getId())
                .taskCandidateOrAssigned(userId)
                .active()
                .count();
        if (count == 0) {
            throw new BizException(403, "无权处理该任务");
        }
    }

    private void assertTicketDataAccess(String processInstanceId) {
        TkTicket ticket = ticketDataAccessService.ticketByProcess(processInstanceId);
        if (ticket != null && !ticketDataAccessService.hasDataAccess(ticket)) {
            throw ticketDataAccessService.denied();
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
        m.put("addSignTask", isAddSignTask(t));
        WfProcessInstanceExt ext = findExt(t.getProcessInstanceId());
        if (ext != null) {
            m.put("title", ext.getTitle());
            m.put("starterName", ext.getStarterName());
            m.put("businessKey", ext.getBusinessKey());
            m.put("formData", ext.getFormData());
            m.put("processDefId", ext.getProcessDefId());
            m.put("businessType", ext.getBusinessType());
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
            fireProcessFinished(processInstanceId, ext.getBusinessKey(), "COMPLETED");
        }
    }

    private void fireProcessFinished(String processInstId, String businessKey, String processStatus) {
        for (ProcessFinishListener listener : finishListeners) {
            try {
                listener.onProcessFinished(processInstId, businessKey, processStatus);
            } catch (Exception e) {
                log.warn("process finish listener failed, inst={}, key={}: {}",
                        processInstId, businessKey, e.getMessage());
            }
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
