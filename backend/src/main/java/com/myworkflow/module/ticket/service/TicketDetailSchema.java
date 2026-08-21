package com.myworkflow.module.ticket.service;

import com.myworkflow.common.exception.BizException;
import com.myworkflow.module.ticket.entity.TkField;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 详情 schema：只决定分组、展示字段、轨迹、按钮。控件类型仍走 tk_form_ui。
 */
public final class TicketDetailSchema {

    private static final Pattern FIELD_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final Set<String> ACTIONS = new HashSet<String>(
            Arrays.asList("save", "submit", "cancel"));

    /** 详情可展示的主表字段：field -> 标题 */
    private static final Map<String, String> MAIN = new LinkedHashMap<>();

    static {
        MAIN.put("ticket_no", "工单号");
        MAIN.put("title", "标题");
        MAIN.put("status", "状态");
        MAIN.put("typeName", "类型");
        MAIN.put("starterName", "发起人");
        MAIN.put("currentApprover", "当前审批人");
        MAIN.put("processKey", "绑定流程");
        MAIN.put("createTime", "创建时间");
        MAIN.put("processInstId", "流程实例");
    }

    private TicketDetailSchema() {
    }

    public static Map<String, String> mainFields() {
        return MAIN;
    }

    public static Map<String, Object> defaultSchema(List<TkField> fields) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("version", 1);
        schema.put("designer", "checkbox");
        schema.put("raw", null);
        schema.put("showTimeline", Boolean.TRUE);
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section("基本信息", Arrays.asList("ticket_no", "title", "status", "starterName", "createTime")));
        List<String> formKeys = new ArrayList<>();
        if (fields != null) {
            for (TkField f : fields) {
                if (f.getFieldKey() != null && FIELD_KEY.matcher(f.getFieldKey()).matches()) {
                    formKeys.add(f.getFieldKey());
                }
            }
        }
        if (!formKeys.isEmpty()) {
            sections.add(section("申请内容", formKeys));
        }
        schema.put("sections", sections);
        schema.put("actions", Arrays.asList("save", "submit"));
        return schema;
    }

    public static Map<String, Object> normalize(Map<String, Object> body, List<TkField> fields) {
        Set<String> jsonKeys = new HashSet<>();
        if (fields != null) {
            for (TkField f : fields) {
                if (f.getFieldKey() != null && FIELD_KEY.matcher(f.getFieldKey()).matches()) {
                    jsonKeys.add(f.getFieldKey());
                }
            }
        }
        List<Map<String, Object>> inSections = asMapList(body == null ? null : body.get("sections"));
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Map<String, Object> s : inSections) {
            String title = str(s.get("title"));
            if (!StringUtils.hasText(title)) {
                title = "未命名区块";
            }
            List<String> keys = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Object rawFields = s.get("fields");
            if (rawFields instanceof List) {
                for (Object o : (List<?>) rawFields) {
                    String field = canon(str(o));
                    if (!FIELD_KEY.matcher(field).matches() || seen.contains(field)) {
                        continue;
                    }
                    if (!MAIN.containsKey(field) && !jsonKeys.contains(field)) {
                        throw new BizException("详情字段不在主表或 tk_field 中：" + field);
                    }
                    seen.add(field);
                    keys.add(field);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            sections.add(section(title, keys));
        }
        if (sections.isEmpty()) {
            throw new BizException("请至少配置一个有字段的区块");
        }
        List<String> actions = new ArrayList<>();
        Object rawActs = body == null ? null : body.get("actions");
        if (rawActs instanceof List) {
            for (Object a : (List<?>) rawActs) {
                String name = str(a);
                if (ACTIONS.contains(name) && !actions.contains(name)) {
                    actions.add(name);
                }
            }
        }
        boolean showTimeline = true;
        if (body != null && body.get("showTimeline") != null) {
            Object st = body.get("showTimeline");
            showTimeline = Boolean.TRUE.equals(st) || "true".equalsIgnoreCase(String.valueOf(st));
        }
        Map<String, Object> schema = new HashMap<>();
        schema.put("designer", "checkbox");
        schema.put("raw", null);
        schema.put("showTimeline", Boolean.valueOf(showTimeline));
        schema.put("sections", sections);
        schema.put("actions", actions);
        return schema;
    }

    private static String canon(String field) {
        if ("ticketNo".equals(field) || "ticket_no".equals(field)) {
            return "ticket_no";
        }
        if ("create_time".equals(field) || "createTime".equals(field)) {
            return "createTime";
        }
        return field;
    }

    private static Map<String, Object> section(String title, List<String> fields) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("fields", new ArrayList<String>(fields));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<?>) raw) {
            if (o instanceof Map) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
