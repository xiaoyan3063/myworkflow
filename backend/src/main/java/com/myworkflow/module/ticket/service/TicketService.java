package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.process.dto.StartProcessRequest;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.service.ProcessRuntimeService;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.myworkflow.module.system.service.MenuService;
import com.myworkflow.module.ticket.entity.TkDetailUi;
import com.myworkflow.module.ticket.entity.TkField;
import com.myworkflow.module.ticket.entity.TkFormUi;
import com.myworkflow.module.ticket.entity.TkListUi;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.mapper.TkDetailUiMapper;
import com.myworkflow.module.ticket.mapper.TkFieldMapper;
import com.myworkflow.module.ticket.mapper.TkFormUiMapper;
import com.myworkflow.module.ticket.mapper.TkListUiMapper;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.module.ticket.mapper.TkTypeMapper;
import com.myworkflow.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_IN_APPROVAL = "IN_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    private static final Set<String> FIELD_TYPES = new HashSet<String>(Arrays.asList(
            "input", "textarea", "number", "select", "date", "user", "users", "file"));

    private final TkTypeMapper typeMapper;
    private final TkFieldMapper fieldMapper;
    private final TkFormUiMapper formUiMapper;
    private final TkListUiMapper listUiMapper;
    private final TkDetailUiMapper detailUiMapper;
    private final TkTicketMapper ticketMapper;
    private final SysUserMapper userMapper;
    private final WfProcessDefMapper processDefMapper;
    private final ProcessRuntimeService processRuntimeService;
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final TicketDataAccessService ticketDataAccessService;
    private final TicketUiService ticketUiService;
    private final TicketFileService ticketFileService;

    public PageResult<TkType> typePage(long page, long size, String keyword) {
        Page<TkType> p = typeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TkType>()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(TkType::getTypeName, keyword)
                                .or()
                                .like(TkType::getTypeCode, keyword))
                        .orderByDesc(TkType::getUpdateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public List<TkType> typeList() {
        return typeMapper.selectList(new LambdaQueryWrapper<TkType>()
                .eq(TkType::getStatus, 1)
                .orderByAsc(TkType::getTypeCode));
    }

    public TkType typeDetail(Long id) {
        TkType type = typeMapper.selectById(id);
        if (type == null) {
            throw new BizException("工单类型不存在");
        }
        return type;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkType saveType(TkType type) {
        if (!StringUtils.hasText(type.getTypeCode()) || !StringUtils.hasText(type.getTypeName())) {
            throw new BizException("请填写类型编码和名称");
        }
        type.setTypeCode(type.getTypeCode().trim());
        Long exists = typeMapper.selectCount(new LambdaQueryWrapper<TkType>()
                .eq(TkType::getTypeCode, type.getTypeCode())
                .ne(type.getId() != null, TkType::getId, type.getId()));
        if (exists != null && exists > 0) {
            throw new BizException("类型编码已存在");
        }
        if (StringUtils.hasText(type.getProcessKey())) {
            assertPublishedProcess(type.getProcessKey().trim());
            type.setProcessKey(type.getProcessKey().trim());
        }
        if (type.getNoSeqLen() == null || type.getNoSeqLen() < 1) {
            type.setNoSeqLen(4);
        }
        if (type.getNoSeqLen() > 8) {
            type.setNoSeqLen(8);
        }
        if (!StringUtils.hasText(type.getNoDatePattern())) {
            type.setNoDatePattern("yyyyMMdd");
        }
        if (type.getStatus() == null) {
            type.setStatus(1);
        }
        if (type.getId() == null) {
            typeMapper.insert(type);
            ticketUiService.initForType(type.getId());
        } else {
            typeMapper.updateById(type);
        }
        menuService.syncTypeMenu(type);
        return type;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        Long tickets = ticketMapper.selectCount(new LambdaQueryWrapper<TkTicket>().eq(TkTicket::getTypeId, id));
        if (tickets != null && tickets > 0) {
            throw new BizException("该类型下已有工单，不能删除");
        }
        TkType type = typeMapper.selectById(id);
        String code = type == null ? null : type.getTypeCode();
        typeMapper.deleteById(id);
        fieldMapper.delete(new LambdaQueryWrapper<TkField>().eq(TkField::getTypeId, id));
        formUiMapper.delete(new LambdaQueryWrapper<TkFormUi>().eq(TkFormUi::getTypeId, id));
        listUiMapper.delete(new LambdaQueryWrapper<TkListUi>().eq(TkListUi::getTypeId, id));
        detailUiMapper.delete(new LambdaQueryWrapper<TkDetailUi>().eq(TkDetailUi::getTypeId, id));
        menuService.removeTypeMenu(code);
    }

    public TkType typeByCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            throw new BizException("缺少工单类型编码");
        }
        TkType type = typeMapper.selectOne(new LambdaQueryWrapper<TkType>()
                .eq(TkType::getTypeCode, typeCode.trim())
                .last("LIMIT 1"));
        if (type == null) {
            throw new BizException("工单类型不存在：" + typeCode);
        }
        return type;
    }

    public List<TkField> listFields(Long typeId) {
        typeDetail(typeId);
        return fieldMapper.selectList(new LambdaQueryWrapper<TkField>()
                .eq(TkField::getTypeId, typeId)
                .orderByAsc(TkField::getSortNo)
                .orderByAsc(TkField::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public TkField saveField(TkField field) {
        if (field.getTypeId() == null) {
            throw new BizException("缺少工单类型");
        }
        typeDetail(field.getTypeId());
        if (!StringUtils.hasText(field.getFieldKey()) || !StringUtils.hasText(field.getTitle())) {
            throw new BizException("请填写字段名和标题");
        }
        field.setFieldKey(field.getFieldKey().trim());
        if (!StringUtils.hasText(field.getFieldType())) {
            field.setFieldType("input");
        }
        if (!FIELD_TYPES.contains(field.getFieldType())) {
            throw new BizException("不支持的字段类型：" + field.getFieldType());
        }
        Long dup = fieldMapper.selectCount(new LambdaQueryWrapper<TkField>()
                .eq(TkField::getTypeId, field.getTypeId())
                .eq(TkField::getFieldKey, field.getFieldKey())
                .ne(field.getId() != null, TkField::getId, field.getId()));
        if (dup != null && dup > 0) {
            throw new BizException("同一类型下字段名不能重复");
        }
        if (field.getRequired() == null) {
            field.setRequired(0);
        }
        if (field.getListVisible() == null) {
            field.setListVisible(1);
        }
        if (field.getSortNo() == null) {
            field.setSortNo(0);
        }
        if (field.getId() == null) {
            fieldMapper.insert(field);
        } else {
            fieldMapper.updateById(field);
        }
        return field;
    }

    public void deleteField(Long id) {
        fieldMapper.deleteById(id);
    }

    public TkFormUi getFormUi(Long typeId, boolean published, Integer version) {
        typeDetail(typeId);
        return ticketUiService.getFormUi(typeId, published, version);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkFormUi saveFormUi(Long typeId, Map<String, Object> body) {
        typeDetail(typeId);
        return ticketUiService.saveFormUi(typeId, body);
    }

    public TkFormUi publishFormUi(Long typeId) {
        typeDetail(typeId);
        return ticketUiService.publishForm(typeId);
    }

    public TkListUi getListUi(Long typeId, boolean published, Integer version) {
        typeDetail(typeId);
        return ticketUiService.getListUi(typeId, published, version);
    }

    public TkListUi getListUi(Long typeId) {
        return getListUi(typeId, false, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkListUi saveListUi(Long typeId, Map<String, Object> body) {
        typeDetail(typeId);
        return ticketUiService.saveListUi(typeId, body, listFields(typeId));
    }

    public TkListUi publishListUi(Long typeId) {
        typeDetail(typeId);
        return ticketUiService.publishList(typeId);
    }

    public TkDetailUi getDetailUi(Long typeId, boolean published, Integer version) {
        typeDetail(typeId);
        return ticketUiService.getDetailUi(typeId, published, version);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkDetailUi saveDetailUi(Long typeId, Map<String, Object> body) {
        typeDetail(typeId);
        return ticketUiService.saveDetailUi(typeId, body, listFields(typeId));
    }

    public TkDetailUi publishDetailUi(Long typeId) {
        typeDetail(typeId);
        return ticketUiService.publishDetail(typeId);
    }

    /**
     * 按类型编码分页。筛选字段必须出现在 list schema.filters 里，json 字段走 form_data->>'key'。
     */
    public PageResult<TkTicket> ticketPageByType(String typeCode, long page, long size, Map<String, String> params) {
        TkType type = typeByCode(typeCode);
        Map<String, Object> schema = getListUi(type.getId(), true, null).getSchema();
        Map<String, Map<String, Object>> allowed = TicketListSchema.filterIndex(schema);

        LambdaQueryWrapper<TkTicket> w = new LambdaQueryWrapper<TkTicket>()
                .eq(TkTicket::getTypeId, type.getId());
        applyDataScope(w);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String rawKey = e.getKey();
                if (!TicketListSchema.isSafeField(rawKey) || !StringUtils.hasText(e.getValue())) {
                    continue;
                }
                Map<String, Object> spec = allowed.get(rawKey);
                if (spec == null) {
                    continue;
                }
                applyFilter(w, spec, e.getValue().trim());
            }
        }
        w.orderByDesc(TkTicket::getCreateTime);
        Page<TkTicket> p = ticketMapper.selectPage(new Page<>(page, size), w);
        for (TkTicket t : p.getRecords()) {
            t.setTypeName(type.getTypeName());
            t.setTypeCode(type.getTypeCode());
            t.setProcessKey(type.getProcessKey());
        }
        fillCurrentApprovers(p.getRecords());
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    private void applyFilter(LambdaQueryWrapper<TkTicket> w, Map<String, Object> spec, String value) {
        String field = String.valueOf(spec.get("field"));
        String op = spec.get("op") == null ? "eq" : String.valueOf(spec.get("op"));
        String from = spec.get("from") == null ? TicketListSchema.FROM_MAIN : String.valueOf(spec.get("from"));
        if (TicketListSchema.FROM_JSON.equals(from)) {
            if (!TicketListSchema.isSafeField(field)) {
                return;
            }
            String expr = "form_data->>'" + field + "'";
            applyOp(w, expr, op, value, true);
            return;
        }
        String col = TicketListSchema.mainColumn(field);
        if (col == null) {
            return;
        }
        if ("ticket_no".equals(col)) {
            applyMainLikeOrEq(w, "ticket_no", TkTicket::getTicketNo, op, value);
        } else if ("title".equals(col)) {
            applyMainLikeOrEq(w, "title", TkTicket::getTitle, op, value);
        } else if ("status".equals(col)) {
            w.eq(TkTicket::getStatus, value);
        } else if ("create_time".equals(col)) {
            applyOp(w, "create_time", op, value, false);
        }
    }

    private void applyMainLikeOrEq(LambdaQueryWrapper<TkTicket> w, String col,
                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<TkTicket, ?> getter,
                                   String op, String value) {
        if ("like".equals(op)) {
            w.like(getter, value);
        } else if ("eq".equals(op)) {
            w.eq(getter, value);
        } else {
            applyOp(w, col, op, value, false);
        }
    }

    private void applyOp(LambdaQueryWrapper<TkTicket> w, String expr, String op, String value, boolean jsonText) {
        String left = jsonText ? "(" + expr + ")" : expr;
        if ("like".equals(op)) {
            w.apply(expr + " LIKE {0}", "%" + value + "%");
        } else if ("gt".equals(op)) {
            w.apply(left + " > {0}", value);
        } else if ("gte".equals(op)) {
            w.apply(left + " >= {0}", value);
        } else if ("lt".equals(op)) {
            w.apply(left + " < {0}", value);
        } else if ("lte".equals(op)) {
            w.apply(left + " <= {0}", value);
        } else {
            w.apply(expr + " = {0}", value);
        }
    }

    public PageResult<TkTicket> ticketPage(long page, long size, Long typeId, String keyword) {
        LambdaQueryWrapper<TkTicket> w = new LambdaQueryWrapper<TkTicket>()
                .eq(typeId != null, TkTicket::getTypeId, typeId)
                .and(StringUtils.hasText(keyword), qw -> qw
                        .like(TkTicket::getTitle, keyword)
                        .or()
                        .like(TkTicket::getTicketNo, keyword))
                .orderByDesc(TkTicket::getCreateTime);
        applyDataScope(w);
        Page<TkTicket> p = ticketMapper.selectPage(new Page<>(page, size), w);
        Map<Long, TkType> types = new HashMap<>();
        for (TkTicket t : p.getRecords()) {
            TkType type = types.computeIfAbsent(t.getTypeId(), id -> typeMapper.selectById(id));
            if (type != null) {
                t.setTypeName(type.getTypeName());
                t.setTypeCode(type.getTypeCode());
                t.setProcessKey(type.getProcessKey());
            }
        }
        fillCurrentApprovers(p.getRecords());
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    private void fillCurrentApprovers(List<TkTicket> tickets) {
        List<String> instIds = new ArrayList<>();
        for (TkTicket t : tickets) {
            if (StringUtils.hasText(t.getProcessInstId()) && STATUS_IN_APPROVAL.equals(t.getStatus())) {
                instIds.add(t.getProcessInstId());
            }
        }
        Map<String, String> approvers = processRuntimeService.currentApprovers(instIds);
        for (TkTicket t : tickets) {
            if (StringUtils.hasText(t.getProcessInstId())) {
                t.setCurrentApprover(approvers.get(t.getProcessInstId()));
            }
        }
    }

    public TkTicket ticketDetail(Long id) {
        TkTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BizException("工单不存在");
        }
        assertTicketScope(ticket);
        boolean dataAccess = ticketDataAccessService.hasDataAccess(ticket);
        TkType type = typeMapper.selectById(ticket.getTypeId());
        if (type != null) {
            ticket.setTypeName(type.getTypeName());
            ticket.setTypeCode(type.getTypeCode());
            ticket.setProcessKey(type.getProcessKey());
        }
        if (StringUtils.hasText(ticket.getProcessInstId()) && STATUS_IN_APPROVAL.equals(ticket.getStatus())) {
            Map<String, String> approvers = processRuntimeService.currentApprovers(
                    Collections.singletonList(ticket.getProcessInstId()));
            ticket.setCurrentApprover(approvers.get(ticket.getProcessInstId()));
        }
        if (!dataAccess) {
            // 当前办理人可以进入审批页，但业务字段必须由服务端清空，不能只在前端遮挡。
            ticket.setFormData(new HashMap<>());
        }
        return ticket;
    }

    /**
     * 工单详情的字段可见性与可编辑性。
     * 未发起时隐藏全部节点字段（只留创建环节字段）；已发起时隐藏尚未走到的节点字段。
     */
    public Map<String, Object> fieldAccess(Long id) {
        TkTicket ticket = ticketDetail(id);
        Map<String, Object> result = new HashMap<>();
        TkTicket stored = ticketMapper.selectById(id);
        boolean dataAccess = ticketDataAccessService.hasDataAccess(stored);
        result.put("dataAccess", dataAccess);
        if (!dataAccess) {
            result.put("accessMessage",
                    "您是当前审批人，但用户角色的数据权限不包含该工单，字段已隐藏且暂不能办理；授权后请刷新页面");
        }
        if (!StringUtils.hasText(ticket.getProcessInstId())) {
            List<String> nodeFields = typeNodeFields(ticket.getTypeId());
            result.put("nodeFields", nodeFields);
            result.put("hiddenFields", nodeFields);
            return result;
        }
        result.putAll(processRuntimeService.fieldVisibility(ticket.getProcessInstId()));
        Map<String, Object> task = processRuntimeService.myActiveTask(ticket.getProcessInstId());
        if (task != null) {
            result.putAll(task);
        }
        return result;
    }

    /** 类型绑定流程里所有节点字段，创建工单时用它把后续环节字段藏掉 */
    public List<String> typeNodeFields(Long typeId) {
        TkType type = typeDetail(typeId);
        if (!StringUtils.hasText(type.getProcessKey())) {
            return Collections.emptyList();
        }
        WfProcessDef def = processDefMapper.selectOne(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getProcessKey, type.getProcessKey())
                .eq(WfProcessDef::getStatus, 1)
                .last("LIMIT 1"));
        return def == null ? Collections.emptyList()
                : processRuntimeService.designNodeFields(def.getBpmnXml());
    }

    public TkTicket ticketByProcessInstance(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BizException("缺少流程实例");
        }
        TkTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<TkTicket>()
                .eq(TkTicket::getProcessInstId, processInstanceId)
                .last("LIMIT 1"));
        if (ticket == null) {
            throw new BizException("该流程实例未关联工单");
        }
        return ticketDetail(ticket.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TkTicket createDraft(TkTicket req) {
        if (req.getTypeId() == null) {
            throw new BizException("请选择工单类型");
        }
        TkType type = typeDetail(req.getTypeId());
        UserContext ctx = UserContext.get();
        TkTicket ticket = new TkTicket();
        ticket.setTypeId(type.getId());
        ticket.setStatus(STATUS_DRAFT);
        ticket.setFormData(req.getFormData() == null ? new HashMap<String, Object>() : req.getFormData());
        if (ctx != null) {
            ticket.setStarterId(ctx.getUserId());
            ticket.setStarterName(StringUtils.hasText(ctx.getRealName()) ? ctx.getRealName() : ctx.getUsername());
        } else if (req.getStarterId() != null) {
            ticket.setStarterId(req.getStarterId());
            SysUser u = userMapper.selectById(req.getStarterId());
            ticket.setStarterName(u == null ? String.valueOf(req.getStarterId()) : u.getRealName());
        }
        ticket.setTicketNo(nextTicketNo(type));
        // 标题是工单主表摘要，给待办/流程实例用；新建草稿不再让用户填，默认用类型名+工单号
        ticket.setTitle(StringUtils.hasText(req.getTitle())
                ? req.getTitle()
                : type.getTypeName() + " " + ticket.getTicketNo());
        ticket.setSchemaVersion(ticketUiService.latestPublishedFormVersion(type.getId()));
        ticketMapper.insert(ticket);
        ticketFileService.bindFromFormData(ticket.getId(), ticket.getFormData());
        return ticket;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkTicket updateDraft(Long id, TkTicket req) {
        TkTicket ticket = ticketDetail(id);
        // 被退回到发起人节点时流程还活着，工单仍是审批中，但持有重提待办的人可以改单
        boolean resubmitting = STATUS_IN_APPROVAL.equals(ticket.getStatus())
                && processRuntimeService.hasResubmitTask(ticket.getProcessInstId());
        if (!STATUS_DRAFT.equals(ticket.getStatus())
                && !STATUS_REJECTED.equals(ticket.getStatus())
                && !resubmitting) {
            throw new BizException("审批中或已结束的工单不能改业务字段");
        }
        if (StringUtils.hasText(req.getTitle())) {
            ticket.setTitle(req.getTitle());
        }
        if (req.getFormData() != null) {
            ticket.setFormData(req.getFormData());
        }
        ticketMapper.updateById(ticket);
        ticketFileService.bindFromFormData(ticket.getId(), ticket.getFormData());
        return ticket;
    }

    /**
     * 审批中的节点字段保存。不能复用 updateDraft：这里必须按当前任务节点的字段白名单合并，
     * 防止审批人篡改发起人字段或其他节点字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public TkTicket saveApprovalFields(Long id, Map<String, Object> submitted) {
        TkTicket stored = ticketMapper.selectById(id);
        if (!ticketDataAccessService.hasDataAccess(stored)) {
            throw ticketDataAccessService.denied();
        }
        TkTicket ticket = ticketDetail(id);
        if (!STATUS_IN_APPROVAL.equals(ticket.getStatus()) || !StringUtils.hasText(ticket.getProcessInstId())) {
            throw new BizException("仅审批中的工单可保存节点填写数据");
        }
        Map<String, Object> taskConfig = processRuntimeService.myTaskFieldConfig(ticket.getProcessInstId());
        Object configured = taskConfig.get("writableFields");
        Set<String> writable = new HashSet<>();
        if (configured instanceof Iterable) {
            for (Object field : (Iterable<?>) configured) {
                if (field != null) writable.add(String.valueOf(field));
            }
        }
        if (writable.isEmpty()) {
            throw new BizException("当前审批节点没有可填写字段");
        }
        Map<String, Object> incoming = submitted == null ? Collections.emptyMap() : submitted;
        List<String> forbidden = incoming.keySet().stream()
                .filter(key -> !writable.contains(key))
                .collect(java.util.stream.Collectors.toList());
        if (!forbidden.isEmpty()) {
            throw new BizException(403, "无权修改字段：" + String.join("、", forbidden));
        }
        Map<String, Object> merged = ticket.getFormData() == null
                ? new HashMap<>() : new HashMap<>(ticket.getFormData());
        for (String field : writable) {
            if (incoming.containsKey(field)) {
                merged.put(field, incoming.get(field));
            }
        }
        ticket.setFormData(merged);
        ticketMapper.updateById(ticket);
        ticketFileService.bindFromFormData(ticket.getId(), merged);
        processRuntimeService.syncFormData(ticket.getProcessInstId(), merged);
        return ticketDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id) {
        TkTicket ticket = ticketDetail(id);
        boolean draft = STATUS_DRAFT.equals(ticket.getStatus());
        boolean resubmit = STATUS_IN_APPROVAL.equals(ticket.getStatus())
                && UserContext.currentUserId() != null
                && UserContext.currentUserId().equals(ticket.getStarterId())
                && processRuntimeService.hasResubmitTask(ticket.getProcessInstId());
        if (!draft && !resubmit) {
            throw new BizException("仅草稿或等待发起人重新提交的工单可删除");
        }
        if (resubmit) {
            processRuntimeService.cancelForTicketDeletion(ticket.getProcessInstId());
        }
        ticketMapper.deleteById(id);
    }

    /**
     * 提交审批：先 start，成功才把工单改成审批中。同一事务，start 失败工单仍是草稿。
     * 终止驳回后允许再提交（新开实例，businessKey 仍是 ticket_no）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TkTicket submit(Long ticketId) {
        TkTicket ticket = ticketDetail(ticketId);
        if (!STATUS_DRAFT.equals(ticket.getStatus()) && !STATUS_REJECTED.equals(ticket.getStatus())) {
            throw new BizException("仅草稿或已驳回（已终止）的工单可提交");
        }
        TkType type = typeDetail(ticket.getTypeId());
        if (!StringUtils.hasText(ticket.getTicketNo())) {
            ticket.setTicketNo(nextTicketNo(type));
            ticketMapper.updateById(ticket);
        }
        if (!StringUtils.hasText(ticket.getTicketNo())) {
            throw new BizException("提交审批前必须已有工单号");
        }
        if (!StringUtils.hasText(type.getProcessKey())) {
            throw new BizException("工单类型未绑定流程，请填写已发布的 processKey");
        }
        assertPublishedProcess(type.getProcessKey());
        UserContext ctx = UserContext.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BizException("请先登录");
        }
        String starterName = StringUtils.hasText(ctx.getRealName()) ? ctx.getRealName() : ctx.getUsername();

        StartProcessRequest req = new StartProcessRequest();
        req.setProcessKey(type.getProcessKey());
        req.setBusinessKey(ticket.getTicketNo());
        req.setBusinessType("TICKET");
        req.setTitle(ticket.getTitle());
        req.setFormData(ticket.getFormData() == null ? new HashMap<String, Object>() : ticket.getFormData());
        req.setStarterId(String.valueOf(ctx.getUserId()));
        req.setStarterName(starterName);

        Map<String, Object> started = processRuntimeService.start(req);
        Object inst = started.get("processInstanceId");
        if (inst == null || !StringUtils.hasText(String.valueOf(inst))) {
            throw new BizException("流程发起失败");
        }
        ticket.setProcessInstId(String.valueOf(inst));
        ticket.setStatus(STATUS_IN_APPROVAL);
        ticket.setStarterId(ctx.getUserId());
        ticket.setStarterName(starterName);
        ticketMapper.updateById(ticket);
        return ticketDetail(ticket.getId());
    }

    private void applyDataScope(LambdaQueryWrapper<TkTicket> w) {
        if (permissionService.allScope()) {
            return;
        }
        UserContext ctx = UserContext.get();
        if (permissionService.deptScope()) {
            List<Long> ids = permissionService.scopeUserIds();
            if (ids == null || ids.isEmpty()) {
                w.eq(TkTicket::getStarterId, ctx == null ? -1L : ctx.getUserId());
            } else {
                w.in(TkTicket::getStarterId, ids);
            }
            return;
        }
        w.eq(TkTicket::getStarterId, ctx == null ? -1L : ctx.getUserId());
    }

    private void assertTicketScope(TkTicket ticket) {
        if (permissionService.allScope()) {
            return;
        }
        UserContext ctx = UserContext.get();
        Long uid = ctx == null ? null : ctx.getUserId();
        // 审批人往往不在发起人的数据范围内，但要能打开单据才能审批
        if (StringUtils.hasText(ticket.getProcessInstId())
                && processRuntimeService.isInvolved(ticket.getProcessInstId(), uid)) {
            return;
        }
        Long starter = ticket.getStarterId();
        if (starter == null) {
            throw new BizException(403, "无权查看或操作该工单");
        }
        if (permissionService.deptScope()) {
            List<Long> ids = permissionService.scopeUserIds();
            if (ids != null && ids.contains(starter)) {
                return;
            }
            throw new BizException(403, "无权查看其他部门或总部的工单");
        }
        if (uid == null || !starter.equals(uid)) {
            throw new BizException(403, "无权查看或操作该工单");
        }
    }

    private void assertPublishedProcess(String processKey) {
        WfProcessDef def = processDefMapper.selectOne(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getProcessKey, processKey)
                .eq(WfProcessDef::getStatus, 1)
                .last("LIMIT 1"));
        if (def == null) {
            throw new BizException("绑定的流程不存在或未发布：" + processKey);
        }
    }

    private String nextTicketNo(TkType type) {
        String prefix = StringUtils.hasText(type.getNoPrefix()) ? type.getNoPrefix().trim() : type.getTypeCode();
        String pattern = StringUtils.hasText(type.getNoDatePattern()) ? type.getNoDatePattern().trim() : "yyyyMMdd";
        if (!pattern.matches("yyyyMMdd|yyyyMM|yyMMdd|yyyy-MM-dd")) {
            pattern = "yyyyMMdd";
        }
        int seqLen = type.getNoSeqLen() == null ? 4 : type.getNoSeqLen();
        if (seqLen < 1) {
            seqLen = 4;
        }
        if (seqLen > 8) {
            seqLen = 8;
        }
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
        String head = prefix + "-" + day + "-";
        TkTicket last = ticketMapper.selectOne(new LambdaQueryWrapper<TkTicket>()
                .likeRight(TkTicket::getTicketNo, head)
                .orderByDesc(TkTicket::getTicketNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getTicketNo() != null && last.getTicketNo().length() > head.length()) {
            try {
                seq = Integer.parseInt(last.getTicketNo().substring(head.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        String fmt = "%0" + seqLen + "d";
        return head + String.format(fmt, seq);
    }
}
