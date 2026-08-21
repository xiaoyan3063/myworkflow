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
 * 列表 schema 白名单：列/筛选字段只允许主表固定列或 tk_field.field_key。
 */
public final class TicketListSchema {

    public static final String FROM_MAIN = "main";
    public static final String FROM_JSON = "json";
    private static final Pattern FIELD_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final Set<String> OPS = new HashSet<String>(Arrays.asList("eq", "like", "gt", "gte", "lt", "lte"));
    private static final Set<String> ACTIONS = new HashSet<String>(
            Arrays.asList("view", "edit", "submit", "delete"));

    /** field -> { title, column }，column 是 tk_ticket 真实列名 */
    private static final Map<String, String[]> MAIN = new LinkedHashMap<>();

    static {
        MAIN.put("ticket_no", new String[]{"工单号", "ticket_no"});
        MAIN.put("title", new String[]{"标题", "title"});
        MAIN.put("status", new String[]{"状态", "status"});
        MAIN.put("createTime", new String[]{"创建时间", "create_time"});
    }

    private TicketListSchema() {
    }

    public static Map<String, Object> defaultSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("version", 1);
        schema.put("designer", "checkbox");
        schema.put("raw", null);
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(column("ticket_no", "工单号", 180, FROM_MAIN));
        columns.add(column("title", "标题", 200, FROM_MAIN));
        columns.add(column("status", "状态", 100, FROM_MAIN));
        columns.add(column("createTime", "创建时间", 180, FROM_MAIN));
        schema.put("columns", columns);
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(filter("ticket_no", "like", FROM_MAIN));
        filters.add(filter("status", "eq", FROM_MAIN));
        schema.put("filters", filters);
        schema.put("rowActions", Arrays.asList("view", "edit", "submit", "delete"));
        return schema;
    }

    public static Map<String, String[]> mainFields() {
        return MAIN;
    }

    public static boolean isSafeField(String field) {
        return StringUtils.hasText(field) && FIELD_KEY.matcher(field).matches();
    }

    /** 主表列名；不是主字段返回 null */
    public static String mainColumn(String field) {
        if (!StringUtils.hasText(field)) {
            return null;
        }
        if ("create_time".equals(field) || "createTime".equals(field)) {
            return "create_time";
        }
        if ("ticketNo".equals(field) || "ticket_no".equals(field)) {
            return "ticket_no";
        }
        String[] meta = MAIN.get(field);
        return meta == null ? null : meta[1];
    }

    public static String canonicalMainField(String field) {
        if ("create_time".equals(field) || "createTime".equals(field)) {
            return "createTime";
        }
        if ("ticketNo".equals(field) || "ticket_no".equals(field)) {
            return "ticket_no";
        }
        return MAIN.containsKey(field) ? field : null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(Map<String, Object> body, List<TkField> fields) {
        Set<String> jsonKeys = new HashSet<>();
        Map<String, String> jsonTitles = new HashMap<>();
        if (fields != null) {
            for (TkField f : fields) {
                if (f.getFieldKey() != null && isSafeField(f.getFieldKey())) {
                    jsonKeys.add(f.getFieldKey());
                    jsonTitles.put(f.getFieldKey(), f.getTitle());
                }
            }
        }
        List<Map<String, Object>> inCols = asMapList(body == null ? null : body.get("columns"));
        List<Map<String, Object>> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> c : inCols) {
            String field = str(c.get("field"));
            if (!isSafeField(field) || seen.contains(field)) {
                continue;
            }
            String from = FROM_JSON.equals(str(c.get("from"))) ? FROM_JSON : FROM_MAIN;
            if (FROM_MAIN.equals(from)) {
                String canon = canonicalMainField(field);
                if (canon == null) {
                    throw new BizException("列表列不是主表字段：" + field);
                }
                field = canon;
            } else if (!jsonKeys.contains(field)) {
                throw new BizException("列表列不在 tk_field 中：" + field);
            }
            seen.add(field);
            String title = str(c.get("title"));
            if (!StringUtils.hasText(title)) {
                title = FROM_MAIN.equals(from) ? MAIN.get(field)[0] : jsonTitles.get(field);
            }
            columns.add(column(field, title, toWidth(c.get("width")), from));
        }
        if (columns.isEmpty()) {
            throw new BizException("请至少勾选一列");
        }

        List<Map<String, Object>> inFilters = asMapList(body == null ? null : body.get("filters"));
        List<Map<String, Object>> filters = new ArrayList<>();
        Set<String> filterSeen = new HashSet<>();
        for (Map<String, Object> f : inFilters) {
            String field = str(f.get("field"));
            if (!isSafeField(field) || filterSeen.contains(field)) {
                continue;
            }
            String from = FROM_JSON.equals(str(f.get("from"))) ? FROM_JSON : FROM_MAIN;
            if (FROM_MAIN.equals(from)) {
                String canon = canonicalMainField(field);
                if (canon == null) {
                    throw new BizException("筛选项不是主表字段：" + field);
                }
                field = canon;
            } else if (!jsonKeys.contains(field)) {
                throw new BizException("筛选项不在 tk_field 中：" + field);
            }
            String op = str(f.get("op"));
            if (!OPS.contains(op)) {
                op = FROM_MAIN.equals(from) && "status".equals(field) ? "eq" : "like";
            }
            filterSeen.add(field);
            filters.add(filter(field, op, from));
        }

        List<String> actions = new ArrayList<>();
        Object rawActs = body == null ? null : body.get("rowActions");
        if (rawActs instanceof List) {
            for (Object a : (List<Object>) rawActs) {
                String name = str(a);
                if (ACTIONS.contains(name) && !actions.contains(name)) {
                    actions.add(name);
                }
            }
        }
        if (actions.isEmpty()) {
            actions.addAll(Arrays.asList("view", "edit", "submit", "delete"));
        }

        Map<String, Object> schema = new HashMap<>();
        schema.put("designer", "checkbox");
        schema.put("raw", null);
        schema.put("columns", columns);
        schema.put("filters", filters);
        schema.put("rowActions", actions);
        return schema;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> columnsOf(Map<String, Object> schema) {
        return asMapList(schema == null ? null : schema.get("columns"));
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> filtersOf(Map<String, Object> schema) {
        return asMapList(schema == null ? null : schema.get("filters"));
    }

    public static Map<String, Map<String, Object>> filterIndex(Map<String, Object> schema) {
        Map<String, Map<String, Object>> idx = new LinkedHashMap<>();
        for (Map<String, Object> f : filtersOf(schema)) {
            String field = str(f.get("field"));
            if (isSafeField(field)) {
                idx.put(field, f);
                if ("createTime".equals(field)) {
                    idx.put("create_time", f);
                }
                if ("ticket_no".equals(field)) {
                    idx.put("ticketNo", f);
                }
            }
        }
        return idx;
    }

    private static Map<String, Object> column(String field, String title, Integer width, String from) {
        Map<String, Object> m = new HashMap<>();
        m.put("field", field);
        m.put("title", title);
        m.put("width", width);
        m.put("from", from);
        return m;
    }

    private static Map<String, Object> filter(String field, String op, String from) {
        Map<String, Object> m = new HashMap<>();
        m.put("field", field);
        m.put("op", op);
        m.put("from", from);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<Object>) raw) {
            if (o instanceof Map) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Integer toWidth(Object o) {
        if (o instanceof Number) {
            int n = ((Number) o).intValue();
            return n > 0 ? Integer.valueOf(n) : null;
        }
        if (o != null && StringUtils.hasText(String.valueOf(o))) {
            try {
                int n = Integer.parseInt(String.valueOf(o).trim());
                return n > 0 ? Integer.valueOf(n) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
