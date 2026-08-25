package com.myworkflow.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.ticket.entity.TkDetailUi;
import com.myworkflow.module.ticket.entity.TkField;
import com.myworkflow.module.ticket.entity.TkFormUi;
import com.myworkflow.module.ticket.entity.TkListUi;
import com.myworkflow.module.ticket.mapper.TkDetailUiMapper;
import com.myworkflow.module.ticket.mapper.TkFieldMapper;
import com.myworkflow.module.ticket.mapper.TkFormUiMapper;
import com.myworkflow.module.ticket.mapper.TkListUiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketUiService {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    private final TkFormUiMapper formUiMapper;
    private final TkListUiMapper listUiMapper;
    private final TkDetailUiMapper detailUiMapper;
    private final TkFieldMapper fieldMapper;
    private final ObjectMapper objectMapper;

    public void initForType(Long typeId) {
        TkFormUi form = new TkFormUi();
        form.setTypeId(typeId);
        form.setVersion(1);
        form.setStatus(DRAFT);
        Map<String, Object> schema = new HashMap<>();
        schema.put("version", 1);
        schema.put("designer", "FcDesigner");
        schema.put("raw", null);
        schema.put("fields", Collections.emptyList());
        form.setSchema(schema);
        formUiMapper.insert(form);

        TkListUi listUi = new TkListUi();
        listUi.setTypeId(typeId);
        listUi.setVersion(1);
        listUi.setStatus(DRAFT);
        listUi.setSchema(TicketListSchema.defaultSchema());
        listUiMapper.insert(listUi);

        TkDetailUi detailUi = new TkDetailUi();
        detailUi.setTypeId(typeId);
        detailUi.setVersion(1);
        detailUi.setStatus(DRAFT);
        detailUi.setSchema(TicketDetailSchema.defaultSchema(Collections.<TkField>emptyList()));
        detailUiMapper.insert(detailUi);
    }

    public TkFormUi getFormUi(Long typeId, boolean published, Integer version) {
        if (published) {
            return runtimeForm(typeId, version);
        }
        return draftForm(typeId);
    }

    public TkListUi getListUi(Long typeId, boolean published, Integer version) {
        if (published) {
            return runtimeList(typeId, version);
        }
        return draftList(typeId);
    }

    public TkDetailUi getDetailUi(Long typeId, boolean published, Integer version) {
        if (published) {
            return runtimeDetail(typeId, version);
        }
        return draftDetail(typeId);
    }

    public Integer latestPublishedFormVersion(Long typeId) {
        TkFormUi ui = latest(formUiMapper.selectList(publishedFormQ(typeId)));
        if (ui != null) {
            return ui.getVersion();
        }
        TkFormUi draft = draftForm(typeId);
        return draft.getVersion();
    }

    @Transactional(rollbackFor = Exception.class)
    public TkFormUi saveFormUi(Long typeId, Map<String, Object> body) {
        TkFormUi ui = draftForm(typeId);
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
        ui.setStatus(DRAFT);
        ui.setSchema(schema);
        formUiMapper.updateById(ui);
        return ui;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkListUi saveListUi(Long typeId, Map<String, Object> body, List<TkField> fields) {
        Map<String, Object> schema = TicketListSchema.normalize(body, fields);
        TkListUi ui = draftList(typeId);
        int next = (ui.getVersion() == null ? 0 : ui.getVersion()) + 1;
        schema.put("version", next);
        ui.setVersion(next);
        ui.setStatus(DRAFT);
        ui.setSchema(schema);
        listUiMapper.updateById(ui);
        return ui;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkDetailUi saveDetailUi(Long typeId, Map<String, Object> body, List<TkField> fields) {
        Map<String, Object> schema = TicketDetailSchema.normalize(body, fields);
        TkDetailUi ui = draftDetail(typeId);
        int next = (ui.getVersion() == null ? 0 : ui.getVersion()) + 1;
        schema.put("version", next);
        ui.setVersion(next);
        ui.setStatus(DRAFT);
        ui.setSchema(schema);
        detailUiMapper.updateById(ui);
        return ui;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkFormUi publishForm(Long typeId) {
        TkFormUi draft = draftForm(typeId);
        int ver = nextPublishedVersion(maxVersion(formUiMapper.selectList(publishedFormQ(typeId))));
        TkFormUi pub = new TkFormUi();
        pub.setTypeId(typeId);
        pub.setStatus(PUBLISHED);
        pub.setVersion(ver);
        Map<String, Object> schema = draft.getSchema() == null ? new HashMap<String, Object>() : new HashMap<String, Object>(draft.getSchema());
        schema.put("version", ver);
        pub.setSchema(schema);
        formUiMapper.insert(pub);
        return pub;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkListUi publishList(Long typeId) {
        TkListUi draft = draftList(typeId);
        int ver = nextPublishedVersion(maxVersionList(listUiMapper.selectList(publishedListQ(typeId))));
        TkListUi pub = new TkListUi();
        pub.setTypeId(typeId);
        pub.setStatus(PUBLISHED);
        pub.setVersion(ver);
        Map<String, Object> schema = draft.getSchema() == null ? new HashMap<String, Object>() : new HashMap<String, Object>(draft.getSchema());
        schema.put("version", ver);
        pub.setSchema(schema);
        listUiMapper.insert(pub);
        return pub;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkDetailUi publishDetail(Long typeId) {
        TkDetailUi draft = draftDetail(typeId);
        int ver = nextPublishedVersion(maxVersionDetail(detailUiMapper.selectList(publishedDetailQ(typeId))));
        TkDetailUi pub = new TkDetailUi();
        pub.setTypeId(typeId);
        pub.setStatus(PUBLISHED);
        pub.setVersion(ver);
        Map<String, Object> schema = draft.getSchema() == null ? new HashMap<String, Object>() : new HashMap<String, Object>(draft.getSchema());
        schema.put("version", ver);
        pub.setSchema(schema);
        detailUiMapper.insert(pub);
        return pub;
    }

    private TkFormUi draftForm(Long typeId) {
        TkFormUi draft = formUiMapper.selectOne(new LambdaQueryWrapper<TkFormUi>()
                .eq(TkFormUi::getTypeId, typeId)
                .eq(TkFormUi::getStatus, DRAFT)
                .orderByDesc(TkFormUi::getUpdateTime)
                .last("LIMIT 1"));
        if (draft != null) {
            return draft;
        }
        TkFormUi src = latest(formUiMapper.selectList(new LambdaQueryWrapper<TkFormUi>()
                .eq(TkFormUi::getTypeId, typeId)
                .orderByDesc(TkFormUi::getVersion)));
        TkFormUi created = new TkFormUi();
        created.setTypeId(typeId);
        created.setStatus(DRAFT);
        if (src != null) {
            created.setVersion(src.getVersion());
            created.setSchema(src.getSchema());
        } else {
            created.setVersion(1);
            Map<String, Object> schema = new HashMap<>();
            schema.put("version", 1);
            schema.put("designer", "FcDesigner");
            schema.put("raw", Collections.emptyList());
            schema.put("fields", Collections.emptyList());
            created.setSchema(schema);
        }
        formUiMapper.insert(created);
        return created;
    }

    private TkListUi draftList(Long typeId) {
        TkListUi draft = listUiMapper.selectOne(new LambdaQueryWrapper<TkListUi>()
                .eq(TkListUi::getTypeId, typeId)
                .eq(TkListUi::getStatus, DRAFT)
                .orderByDesc(TkListUi::getUpdateTime)
                .last("LIMIT 1"));
        if (draft != null) {
            return draft;
        }
        TkListUi src = latestList(listUiMapper.selectList(new LambdaQueryWrapper<TkListUi>()
                .eq(TkListUi::getTypeId, typeId)
                .orderByDesc(TkListUi::getVersion)));
        TkListUi created = new TkListUi();
        created.setTypeId(typeId);
        created.setStatus(DRAFT);
        if (src != null) {
            created.setVersion(src.getVersion());
            created.setSchema(src.getSchema());
        } else {
            created.setVersion(1);
            created.setSchema(TicketListSchema.defaultSchema());
        }
        listUiMapper.insert(created);
        return created;
    }

    private TkDetailUi draftDetail(Long typeId) {
        TkDetailUi draft = detailUiMapper.selectOne(new LambdaQueryWrapper<TkDetailUi>()
                .eq(TkDetailUi::getTypeId, typeId)
                .eq(TkDetailUi::getStatus, DRAFT)
                .orderByDesc(TkDetailUi::getUpdateTime)
                .last("LIMIT 1"));
        if (draft != null) {
            return draft;
        }
        TkDetailUi src = latestDetail(detailUiMapper.selectList(new LambdaQueryWrapper<TkDetailUi>()
                .eq(TkDetailUi::getTypeId, typeId)
                .orderByDesc(TkDetailUi::getVersion)));
        TkDetailUi created = new TkDetailUi();
        created.setTypeId(typeId);
        created.setStatus(DRAFT);
        if (src != null) {
            created.setVersion(src.getVersion());
            created.setSchema(src.getSchema());
        } else {
            created.setVersion(1);
            created.setSchema(TicketDetailSchema.defaultSchema(Collections.<TkField>emptyList()));
        }
        detailUiMapper.insert(created);
        return created;
    }

    private TkFormUi runtimeForm(Long typeId, Integer version) {
        if (version != null) {
            TkFormUi exact = formUiMapper.selectOne(new LambdaQueryWrapper<TkFormUi>()
                    .eq(TkFormUi::getTypeId, typeId)
                    .eq(TkFormUi::getVersion, version)
                    .eq(TkFormUi::getStatus, PUBLISHED)
                    .last("LIMIT 1"));
            if (exact != null) {
                return exact;
            }
            TkFormUi anyVer = formUiMapper.selectOne(new LambdaQueryWrapper<TkFormUi>()
                    .eq(TkFormUi::getTypeId, typeId)
                    .eq(TkFormUi::getVersion, version)
                    .last("LIMIT 1"));
            if (anyVer != null) {
                return anyVer;
            }
        }
        TkFormUi pub = latest(formUiMapper.selectList(publishedFormQ(typeId)));
        if (pub != null) {
            return pub;
        }
        return draftForm(typeId);
    }

    private TkListUi runtimeList(Long typeId, Integer version) {
        if (version != null) {
            TkListUi exact = listUiMapper.selectOne(new LambdaQueryWrapper<TkListUi>()
                    .eq(TkListUi::getTypeId, typeId)
                    .eq(TkListUi::getVersion, version)
                    .eq(TkListUi::getStatus, PUBLISHED)
                    .last("LIMIT 1"));
            if (exact != null) {
                return exact;
            }
        }
        TkListUi pub = latestList(listUiMapper.selectList(publishedListQ(typeId)));
        if (pub != null) {
            return pub;
        }
        return draftList(typeId);
    }

    private TkDetailUi runtimeDetail(Long typeId, Integer version) {
        if (version != null) {
            TkDetailUi exact = detailUiMapper.selectOne(new LambdaQueryWrapper<TkDetailUi>()
                    .eq(TkDetailUi::getTypeId, typeId)
                    .eq(TkDetailUi::getVersion, version)
                    .eq(TkDetailUi::getStatus, PUBLISHED)
                    .last("LIMIT 1"));
            if (exact != null) {
                return exact;
            }
        }
        TkDetailUi pub = latestDetail(detailUiMapper.selectList(publishedDetailQ(typeId)));
        if (pub != null) {
            return pub;
        }
        return draftDetail(typeId);
    }

    private LambdaQueryWrapper<TkFormUi> publishedFormQ(Long typeId) {
        return new LambdaQueryWrapper<TkFormUi>()
                .eq(TkFormUi::getTypeId, typeId)
                .eq(TkFormUi::getStatus, PUBLISHED)
                .orderByDesc(TkFormUi::getVersion);
    }

    private LambdaQueryWrapper<TkListUi> publishedListQ(Long typeId) {
        return new LambdaQueryWrapper<TkListUi>()
                .eq(TkListUi::getTypeId, typeId)
                .eq(TkListUi::getStatus, PUBLISHED)
                .orderByDesc(TkListUi::getVersion);
    }

    private LambdaQueryWrapper<TkDetailUi> publishedDetailQ(Long typeId) {
        return new LambdaQueryWrapper<TkDetailUi>()
                .eq(TkDetailUi::getTypeId, typeId)
                .eq(TkDetailUi::getStatus, PUBLISHED)
                .orderByDesc(TkDetailUi::getVersion);
    }

    private TkFormUi latest(List<TkFormUi> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private TkListUi latestList(List<TkListUi> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private TkDetailUi latestDetail(List<TkDetailUi> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private int maxVersion(List<TkFormUi> list) {
        int max = 0;
        if (list == null) {
            return max;
        }
        for (TkFormUi ui : list) {
            if (ui.getVersion() != null && ui.getVersion() > max) {
                max = ui.getVersion();
            }
        }
        return max;
    }

    private int maxVersionList(List<TkListUi> list) {
        int max = 0;
        if (list == null) {
            return max;
        }
        for (TkListUi ui : list) {
            if (ui.getVersion() != null && ui.getVersion() > max) {
                max = ui.getVersion();
            }
        }
        return max;
    }

    private int maxVersionDetail(List<TkDetailUi> list) {
        int max = 0;
        if (list == null) {
            return max;
        }
        for (TkDetailUi ui : list) {
            if (ui.getVersion() != null && ui.getVersion() > max) {
                max = ui.getVersion();
            }
        }
        return max;
    }

    private int nextPublishedVersion(int maxPublished) {
        return maxPublished + 1;
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
}
