package com.myworkflow.module.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.myworkflow.module.ticket.entity.TkField;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 从 FcDesigner 的 rule 树抽出稳定字段，写入 tk_field。
 * 布局节点没有 field，只往下递归 children。
 */
final class FcDesignerSchemaParser {

    private static final Set<String> LAYOUT = new HashSet<String>(Arrays.asList(
            "fcrow", "fccol", "row", "col", "elrow", "elcol",
            "div", "card", "collapse", "collapsetitem", "tabs", "tabpane",
            "space", "divider", "alert", "text", "html", "button", "elbutton"));

    private FcDesignerSchemaParser() {
    }

    static List<TkField> extractFields(ObjectMapper mapper, Object raw) {
        List<TkField> fields = new ArrayList<>();
        if (raw == null) {
            return fields;
        }
        JsonNode root = mapper.valueToTree(raw);
        walk(root, fields, new int[]{0});
        return fields;
    }

    private static void walk(JsonNode node, List<TkField> out, int[] sort) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, out, sort);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String type = node.path("type").asText("");
        String fieldKey = firstText(node, "field", "name");
        if (StringUtils.hasText(fieldKey) && !isLayout(type)) {
            TkField field = new TkField();
            field.setFieldKey(fieldKey.trim());
            field.setTitle(firstText(node, "title", "label"));
            if (!StringUtils.hasText(field.getTitle())) {
                field.setTitle(field.getFieldKey());
            }
            field.setFieldType(mapType(type));
            field.setRequired(isRequired(node) ? 1 : 0);
            field.setListVisible(1);
            field.setSortNo(sort[0]++);
            ArrayNode options = usableOptions(node.get("options"));
            if (options.size() > 0) {
                field.setOptionsJson(options.toString());
            }
            out.add(field);
        }
        walk(node.get("children"), out, sort);
        walk(node.get("control"), out, sort);
    }

    /** 只清空文字没删行的选项会留下空 label/value，运行时会渲染成一个空选项 */
    private static ArrayNode usableOptions(JsonNode options) {
        ArrayNode kept = JsonNodeFactory.instance.arrayNode();
        if (options == null || !options.isArray()) {
            return kept;
        }
        for (JsonNode option : options) {
            boolean blank = !StringUtils.hasText(option.path("label").asText(""))
                    && !StringUtils.hasText(option.path("value").asText(""));
            if (!blank) {
                kept.add(option);
            }
        }
        return kept;
    }

    private static boolean isLayout(String type) {
        return LAYOUT.contains(normalize(type));
    }

    private static String mapType(String type) {
        String t = normalize(type);
        if ("textarea".equals(t)) return "textarea";
        if ("inputnumber".equals(t) || "elinputnumber".equals(t)) return "number";
        if ("select".equals(t) || "elselect".equals(t) || "radio".equals(t) || "checkbox".equals(t)) {
            return "select";
        }
        if (t.contains("date") || t.contains("time")) return "date";
        if ("ticketuserselect".equals(t) || "userselect".equals(t) || "user".equals(t)) return "user";
        if ("ticketusersselect".equals(t) || "usersselect".equals(t) || "users".equals(t)) return "users";
        if ("input".equals(t) || "password".equals(t) || "elinput".equals(t)) return "input";
        return "input";
    }

    private static boolean isRequired(JsonNode node) {
        if (node.path("$required").asBoolean(false)) {
            return true;
        }
        JsonNode validate = node.get("validate");
        if (validate != null && validate.isArray()) {
            for (JsonNode v : validate) {
                if (v.path("required").asBoolean(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String firstText(JsonNode node, String a, String b) {
        String v = node.path(a).asText(null);
        if (StringUtils.hasText(v)) {
            return v;
        }
        return node.path(b).asText(null);
    }

    private static String normalize(String type) {
        if (type == null) {
            return "";
        }
        return type.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
