package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.system.entity.SysUser;
import com.myworkflow.module.system.mapper.SysUserMapper;
import com.myworkflow.module.ticket.entity.TkField;
import com.myworkflow.module.ticket.entity.TkFormUi;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.mapper.TkFieldMapper;
import com.myworkflow.module.ticket.mapper.TkFormUiMapper;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.module.ticket.mapper.TkTypeMapper;
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

    static final String STATUS_DRAFT = "DRAFT";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> FIELD_TYPES = new HashSet<String>(Arrays.asList(
            "input", "textarea", "number", "select", "date", "user", "users"));

    private final TkTypeMapper typeMapper;
    private final TkFieldMapper fieldMapper;
    private final TkFormUiMapper formUiMapper;
    private final TkTicketMapper ticketMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

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
        if (type.getStatus() == null) {
            type.setStatus(1);
        }
        if (type.getId() == null) {
            typeMapper.insert(type);
            TkFormUi ui = new TkFormUi();
            ui.setTypeId(type.getId());
            ui.setVersion(1);
            Map<String, Object> schema = new HashMap<>();
            schema.put("version", 1);
            schema.put("designer", "FcDesigner");
            schema.put("raw", null);
            schema.put("fields", Collections.emptyList());
            ui.setSchema(schema);
            formUiMapper.insert(ui);
        } else {
            typeMapper.updateById(type);
        }
        return type;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        Long tickets = ticketMapper.selectCount(new LambdaQueryWrapper<TkTicket>().eq(TkTicket::getTypeId, id));
        if (tickets != null && tickets > 0) {
            throw new BizException("该类型下已有工单，不能删除");
        }
        typeMapper.deleteById(id);
        fieldMapper.delete(new LambdaQueryWrapper<TkField>().eq(TkField::getTypeId, id));
        formUiMapper.delete(new LambdaQueryWrapper<TkFormUi>().eq(TkFormUi::getTypeId, id));
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

    public TkFormUi getFormUi(Long typeId) {
        typeDetail(typeId);
        TkFormUi ui = formUiMapper.selectOne(new LambdaQueryWrapper<TkFormUi>()
                .eq(TkFormUi::getTypeId, typeId)
                .last("LIMIT 1"));
        if (ui == null) {
            ui = new TkFormUi();
            ui.setTypeId(typeId);
            ui.setVersion(1);
            Map<String, Object> schema = new HashMap<>();
            schema.put("version", 1);
            schema.put("designer", "FcDesigner");
            schema.put("raw", Collections.emptyList());
            schema.put("fields", Collections.emptyList());
            ui.setSchema(schema);
            formUiMapper.insert(ui);
        }
        return ui;
    }

    /**
     * 保存设计器 schema：版本 +1，按 field_key 更新已有字段，新增没有的，删掉画布上已去掉的。
     */
    @Transactional(rollbackFor = Exception.class)
    public TkFormUi saveFormUi(Long typeId, Map<String, Object> body) {
        typeDetail(typeId);
        TkFormUi ui = formUiMapper.selectOne(new LambdaQueryWrapper<TkFormUi>()
                .eq(TkFormUi::getTypeId, typeId)
                .last("LIMIT 1"));
        if (ui == null) {
            ui = new TkFormUi();
            ui.setTypeId(typeId);
            ui.setVersion(0);
        }
        Object raw = body == null ? null : body.get("raw");
        if (raw == null && body != null) {
            raw = body.get("rule");
        }
        List<TkField> parsed = FcDesignerSchemaParser.extractFields(objectMapper, raw);
        upsertFields(typeId, parsed);

        List<Map<String, Object>> fieldViews = new ArrayList<>();
        for (TkField f : parsed) {
            Map<String, Object> m = new HashMap<>();
            m.put("field", f.getFieldKey());
            m.put("title", f.getTitle());
            m.put("type", f.getFieldType());
            m.put("required", Integer.valueOf(1).equals(f.getRequired()));
            fieldViews.add(m);
        }
        int next = (ui.getVersion() == null ? 0 : ui.getVersion()) + 1;
        Map<String, Object> schema = new HashMap<>();
        schema.put("version", next);
        schema.put("designer", "FcDesigner");
        schema.put("raw", raw == null ? Collections.emptyList() : raw);
        schema.put("fields", fieldViews);
        ui.setVersion(next);
        ui.setSchema(schema);
        if (ui.getId() == null) {
            formUiMapper.insert(ui);
        } else {
            formUiMapper.updateById(ui);
        }
        return ui;
    }

    private void upsertFields(Long typeId, List<TkField> parsed) {
        List<TkField> existing = fieldMapper.selectList(new LambdaQueryWrapper<TkField>()
                .eq(TkField::getTypeId, typeId));
        Map<String, TkField> byKey = new HashMap<>();
        for (TkField f : existing) {
            byKey.put(f.getFieldKey(), f);
        }
        Set<String> keep = new HashSet<>();
        for (TkField incoming : parsed) {
            keep.add(incoming.getFieldKey());
            TkField old = byKey.get(incoming.getFieldKey());
            incoming.setTypeId(typeId);
            if (old != null) {
                incoming.setId(old.getId());
                if (incoming.getListVisible() == null) {
                    incoming.setListVisible(old.getListVisible());
                }
                fieldMapper.updateById(incoming);
            } else {
                if (incoming.getListVisible() == null) {
                    incoming.setListVisible(1);
                }
                fieldMapper.insert(incoming);
            }
        }
        for (TkField old : existing) {
            if (!keep.contains(old.getFieldKey())) {
                fieldMapper.deleteById(old.getId());
            }
        }
    }

    public PageResult<TkTicket> ticketPage(long page, long size, Long typeId, String keyword) {
        Page<TkTicket> p = ticketMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TkTicket>()
                        .eq(typeId != null, TkTicket::getTypeId, typeId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(TkTicket::getTitle, keyword)
                                .or()
                                .like(TkTicket::getTicketNo, keyword))
                        .orderByDesc(TkTicket::getCreateTime));
        Map<Long, TkType> types = new HashMap<>();
        for (TkTicket t : p.getRecords()) {
            TkType type = types.computeIfAbsent(t.getTypeId(), id -> typeMapper.selectById(id));
            if (type != null) {
                t.setTypeName(type.getTypeName());
                t.setTypeCode(type.getTypeCode());
            }
        }
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public TkTicket ticketDetail(Long id) {
        TkTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BizException("工单不存在");
        }
        TkType type = typeMapper.selectById(ticket.getTypeId());
        if (type != null) {
            ticket.setTypeName(type.getTypeName());
            ticket.setTypeCode(type.getTypeCode());
        }
        return ticket;
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
        ticket.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle() : type.getTypeName() + "草稿");
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
        ticket.setTicketNo(nextTicketNo(type.getTypeCode()));
        ticketMapper.insert(ticket);
        return ticket;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkTicket updateDraft(Long id, TkTicket req) {
        TkTicket ticket = ticketDetail(id);
        if (!STATUS_DRAFT.equals(ticket.getStatus())) {
            throw new BizException("仅草稿可编辑");
        }
        if (StringUtils.hasText(req.getTitle())) {
            ticket.setTitle(req.getTitle());
        }
        if (req.getFormData() != null) {
            ticket.setFormData(req.getFormData());
        }
        ticketMapper.updateById(ticket);
        return ticket;
    }

    public void deleteDraft(Long id) {
        TkTicket ticket = ticketDetail(id);
        if (!STATUS_DRAFT.equals(ticket.getStatus())) {
            throw new BizException("仅草稿可删除");
        }
        ticketMapper.deleteById(id);
    }

    private String nextTicketNo(String typeCode) {
        String day = LocalDate.now().format(DAY);
        String prefix = typeCode + "-" + day + "-";
        TkTicket last = ticketMapper.selectOne(new LambdaQueryWrapper<TkTicket>()
                .likeRight(TkTicket::getTicketNo, prefix)
                .orderByDesc(TkTicket::getTicketNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getTicketNo() != null && last.getTicketNo().length() > prefix.length()) {
            try {
                seq = Integer.parseInt(last.getTicketNo().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
