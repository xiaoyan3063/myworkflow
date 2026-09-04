package com.myworkflow.module.process.util;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.myworkflow.common.exception.BizException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署前增强 BPMN：把设计器写在 documentation 里的审批配置翻译成 Flowable 的
 * TaskListener / 多实例 / 到期时间。
 * <p>
 * 增强结果只用于部署，不回写库里的设计稿，否则再次编辑后的配置会被上一次注入的监听器覆盖。
 */
public final class BpmnEnhanceUtil {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String ASSIGNEE_LISTENER = "${assigneeTaskListener}";
    public static final String SYSTEM_RESUBMIT_ACTIVITY_ID = "__wf_resubmit_starter";
    public static final String SYSTEM_RESUBMIT_ACTIVITY_NAME = "发起人重新提交";

    private BpmnEnhanceUtil() {
    }

    public static String enhance(String xml, String processKey, String processName) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("流程图内容无法解析，请回到设计器重新保存：" + e.getMessage());
        }
        try {
            Element root = doc.getDocumentElement();
            if (!root.hasAttribute("xmlns:flowable")) {
                root.setAttribute("xmlns:flowable", FLOWABLE_NS);
            }

            applyProcessIdentity(doc, processKey, processName);
            for (Element task : userTasks(doc)) {
                enhanceTask(doc, task);
            }
            appendStarterResubmitTask(doc);
            return toString(doc);
        } catch (Exception e) {
            throw new BizException("流程图处理失败：" + e.getMessage());
        }
    }

    /**
     * 读取设计器原稿里每个用户任务的审批人来源，返回 节点ID -> assigneeType。
     * 驳回回退时用它识别「发起人节点」，给前端一个更明确的选项标签。
     * 解析失败不抛异常：这只是展示层的锦上添花，不该阻断审批动作。
     */
    public static Map<String, String> readAssigneeTypes(String xml) {
        Map<String, String> result = new HashMap<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            for (Element task : userTasks(doc)) {
                String id = task.getAttribute("id");
                if (id == null || id.isEmpty()) {
                    continue;
                }
                Element config = firstChild(task, "documentation");
                if (config != null && JSONUtil.isTypeJSON(config.getTextContent())) {
                    result.put(id, JSONUtil.parseObj(config.getTextContent()).getStr("assigneeType", "user"));
                }
            }
        } catch (Exception ignored) {
            // 读不出来就当作没有发起人节点
        }
        return result;
    }

    /**
     * 读取节点可填写/必填字段。字段配置属于业务设计稿，不写入 Flowable 扩展属性，
     * 仍保存在 userTask 的 documentation JSON 中。
     */
    public static Map<String, List<String>> readTaskFieldConfig(String xml, String activityId) {
        Map<String, List<String>> result = new HashMap<>();
        result.put("writableFields", new ArrayList<>());
        result.put("requiredFields", new ArrayList<>());
        if (xml == null || xml.isEmpty() || activityId == null || activityId.isEmpty()) {
            return result;
        }
        try {
            for (Element task : userTasks(parse(xml))) {
                if (!activityId.equals(task.getAttribute("id"))) {
                    continue;
                }
                JSONObject json = configJson(task);
                if (json == null) {
                    return result;
                }
                List<String> writable = stringList(json.get("writableFields"));
                List<String> required = stringList(json.get("requiredFields"));
                required.removeIf(field -> !writable.contains(field));
                result.put("writableFields", writable);
                result.put("requiredFields", required);
                return result;
            }
        } catch (Exception ignored) {
            // 老流程或损坏的附加配置按“无可填字段”处理，不能扩大权限
        }
        return result;
    }

    /** 读取节点上的工单明细配置，返回值可直接交给 Jackson 输出。 */
    public static Map<String, Object> readTaskDetailConfig(String xml, String activityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detailConfigs", new ArrayList<>());
        result.put("childValidationMode", "NONE");
        result.put("childValidationRelationIds", new ArrayList<>());
        if (xml == null || xml.isEmpty() || activityId == null || activityId.isEmpty()) {
            return result;
        }
        try {
            for (Element task : userTasks(parse(xml))) {
                if (!activityId.equals(task.getAttribute("id"))) continue;
                JSONObject json = configJson(task);
                if (json == null) return result;
                Object details = json.get("detailConfigs");
                if (details != null) result.put("detailConfigs", details);
                result.put("childValidationMode", json.getStr("childValidationMode", "NONE"));
                result.put("childValidationRelationIds",
                        stringList(json.get("childValidationRelationIds")));
                return result;
            }
        } catch (Exception ignored) {
            // 损坏配置按不开放明细编辑、也不校验处理
        }
        return result;
    }

    /** 节点ID -> 本节点配置显示的明细关系ID。 */
    public static Map<String, List<String>> readTaskDetailRelationsByActivity(String xml) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (xml == null || xml.isEmpty()) return result;
        try {
            for (Element task : userTasks(parse(xml))) {
                String id = task.getAttribute("id");
                JSONObject json = configJson(task);
                if (id == null || id.isEmpty() || json == null) continue;
                List<String> ids = new ArrayList<>();
                Object raw = json.get("detailConfigs");
                if (raw instanceof Iterable) {
                    for (Object item : (Iterable<?>) raw) {
                        JSONObject detail = item instanceof JSONObject
                                ? (JSONObject) item : JSONUtil.parseObj(item);
                        String relationId = detail.getStr("relationId");
                        if (relationId != null && !relationId.isEmpty()
                                && detail.getBool("visible", true)) {
                            ids.add(relationId);
                        }
                    }
                }
                if (!ids.isEmpty()) result.put(id, ids);
            }
        } catch (Exception ignored) {
            // 老流程没有明细配置
        }
        return result;
    }

    /**
     * 读取全部用户任务的可填字段，返回 节点ID -> 字段列表。
     * 决定字段归属哪个节点：没有归属的字段属于发起环节，归属节点未走到的字段先不展示。
     */
    public static Map<String, List<String>> readTaskFieldsByActivity(String xml) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            for (Element task : userTasks(parse(xml))) {
                String id = task.getAttribute("id");
                JSONObject json = configJson(task);
                if (id == null || id.isEmpty() || json == null) {
                    continue;
                }
                List<String> writable = stringList(json.get("writableFields"));
                if (!writable.isEmpty()) {
                    result.put(id, writable);
                }
            }
        } catch (Exception ignored) {
            // 解析不出来时当作没有节点字段，保持旧流程的展示方式
        }
        return result;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static JSONObject configJson(Element task) {
        Element config = firstChild(task, "documentation");
        if (config == null || !JSONUtil.isTypeJSON(config.getTextContent())) {
            return null;
        }
        return JSONUtil.parseObj(config.getTextContent());
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    /**
     * 把流程的 id/name 对齐到业务定义。设计器导出的 process 上已带 name、isExecutable，
     * 必须用 DOM 设置属性，字符串拼接会产生重复属性导致 XML 解析失败。
     */
    private static void applyProcessIdentity(Document doc, String processKey, String processName) {
        Element process = firstElement(doc, "process");
        if (process == null) {
            throw new BizException("流程图中没有找到 process 节点");
        }
        String oldId = process.getAttribute("id");
        if (processKey != null && !processKey.isEmpty()) {
            process.setAttribute("id", processKey);
            if (oldId != null && !oldId.isEmpty() && !oldId.equals(processKey)) {
                retargetDiagramReference(doc, oldId, processKey);
            }
        }
        if (processName != null && !processName.isEmpty()) {
            process.setAttribute("name", processName);
        }
        process.setAttribute("isExecutable", "true");
    }

    /** process id 改名后，BPMNPlane 的 bpmnElement 也要跟着改，否则流程图坐标丢失。 */
    private static void retargetDiagramReference(Document doc, String oldId, String newId) {
        NodeList planes = doc.getElementsByTagNameNS("*", "BPMNPlane");
        for (int i = 0; i < planes.getLength(); i++) {
            Element plane = (Element) planes.item(i);
            if (oldId.equals(plane.getAttribute("bpmnElement"))) {
                plane.setAttribute("bpmnElement", newId);
            }
        }
    }

    private static Element firstElement(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(BPMN_NS, localName);
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName(localName);
        }
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static List<Element> userTasks(Document doc) {
        NodeList tasks = doc.getElementsByTagNameNS(BPMN_NS, "userTask");
        if (tasks.getLength() == 0) {
            tasks = doc.getElementsByTagName("userTask");
        }
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < tasks.getLength(); i++) {
            result.add((Element) tasks.item(i));
        }
        return result;
    }

    /**
     * 开始事件是瞬时节点，执行流不能停在上面等待发起人修改。部署时注入一个不在设计图上、
     * 正常路径也不会经过的系统用户任务。驳回到发起人时运行时服务把执行流移到此节点；
     * 发起人重新提交后，它沿开始事件原来的出口重新进入审批路径。
     */
    private static void appendStarterResubmitTask(Document doc) {
        if (elementById(doc, SYSTEM_RESUBMIT_ACTIVITY_ID) != null) {
            return;
        }
        Element process = firstElement(doc, "process");
        Element start = firstElement(doc, "startEvent");
        if (process == null || start == null) {
            return;
        }

        List<Element> startFlows = new ArrayList<>();
        for (Element flow : childElements(process)) {
            if ("sequenceFlow".equals(flow.getLocalName())
                    && start.getAttribute("id").equals(flow.getAttribute("sourceRef"))) {
                startFlows.add(flow);
            }
        }
        if (startFlows.isEmpty()) {
            return;
        }

        Element task = doc.createElementNS(BPMN_NS, "userTask");
        task.setAttribute("id", SYSTEM_RESUBMIT_ACTIVITY_ID);
        task.setAttribute("name", SYSTEM_RESUBMIT_ACTIVITY_NAME);
        task.setAttribute("flowable:assignee", "${starterId}");
        process.appendChild(task);

        int index = 1;
        for (Element startFlow : startFlows) {
            Element cloned = (Element) startFlow.cloneNode(true);
            cloned.setAttribute("id", SYSTEM_RESUBMIT_ACTIVITY_ID + "_flow_" + index++);
            cloned.setAttribute("sourceRef", SYSTEM_RESUBMIT_ACTIVITY_ID);
            process.appendChild(cloned);
        }
    }

    private static Element elementById(Document doc, String id) {
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private static void enhanceTask(Document doc, Element task) {
        String assigneeType = "user";
        String assigneeValue = "";
        String deptScope = "ALL";
        String fixedDeptId = "";
        String multiMode = "or";
        int dueHours = 0;

        Element config = firstChild(task, "documentation");
        if (config != null && JSONUtil.isTypeJSON(config.getTextContent())) {
            JSONObject obj = JSONUtil.parseObj(config.getTextContent());
            assigneeType = obj.getStr("assigneeType", "user");
            assigneeValue = obj.getStr("assigneeValue", "");
            deptScope = obj.getStr("deptScope", "ALL");
            fixedDeptId = obj.getStr("fixedDeptId", "");
            multiMode = obj.getStr("multiMode", "or");
            dueHours = obj.getInt("dueHours", 0);
            // 审批人配置已翻译成监听器，不必带进 Flowable；但节点可填字段要留在部署版本里，
            // 运行时按实例实际部署的版本判权限，未发布的设计改动才不会影响在途单据。
            List<String> writable = stringList(obj.get("writableFields"));
            List<String> required = stringList(obj.get("requiredFields"));
            required.retainAll(writable);
            Object detailConfigs = obj.get("detailConfigs");
            String validationMode = obj.getStr("childValidationMode", "NONE");
            List<String> validationIds = stringList(obj.get("childValidationRelationIds"));
            boolean hasDetailConfig = detailConfigs instanceof Iterable
                    && ((Iterable<?>) detailConfigs).iterator().hasNext();
            if (writable.isEmpty() && !hasDetailConfig && "NONE".equals(validationMode)) {
                task.removeChild(config);
            } else {
                JSONObject kept = new JSONObject();
                kept.set("writableFields", writable);
                kept.set("requiredFields", required);
                kept.set("detailConfigs", detailConfigs == null ? new ArrayList<>() : detailConfigs);
                kept.set("childValidationMode", validationMode);
                kept.set("childValidationRelationIds", validationIds);
                config.setTextContent(kept.toString());
            }
        } else if (hasOwnAssignment(task)) {
            // 手写的 BPMN 直接在 XML 里配了审批人，没有设计器配置时不能拿默认值把它覆盖掉，
            // 否则运行时解析不到审批人，任务会全部回退给发起人
            return;
        }

        if (dueHours > 0 && !task.hasAttribute("flowable:dueDate")) {
            task.setAttribute("flowable:dueDate", "PT" + dueHours + "H");
        }
        if ("and".equals(multiMode)) {
            appendMultiInstance(doc, task, assigneeType, assigneeValue, deptScope, fixedDeptId);
        }
        writeAssigneeListener(doc, task, assigneeType, assigneeValue, deptScope, fixedDeptId);
    }

    /** 任务自己声明了审批人（候选人属性或任务监听器）就算有配置 */
    private static boolean hasOwnAssignment(Element task) {
        if (task.hasAttribute("flowable:candidateUsers") || task.hasAttribute("flowable:candidateGroups")) {
            return true;
        }
        Element ext = firstChild(task, "extensionElements");
        if (ext == null) {
            return false;
        }
        for (Element child : childElements(ext)) {
            if ("taskListener".equals(child.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    private static void writeAssigneeListener(Document doc, Element task, String assigneeType,
                                              String assigneeValue, String deptScope, String fixedDeptId) {
        Element ext = findOrCreateExtension(doc, task);
        removeOwnListener(ext);

        Element listener = doc.createElementNS(FLOWABLE_NS, "flowable:taskListener");
        listener.setAttribute("event", "create");
        listener.setAttribute("delegateExpression", ASSIGNEE_LISTENER);
        listener.appendChild(field(doc, "assigneeType", assigneeType));
        listener.appendChild(field(doc, "assigneeValue", assigneeValue));
        listener.appendChild(field(doc, "deptScope", deptScope));
        listener.appendChild(field(doc, "fixedDeptId", fixedDeptId));
        ext.appendChild(listener);
    }

    private static Element field(Document doc, String name, String value) {
        Element field = doc.createElementNS(FLOWABLE_NS, "flowable:field");
        field.setAttribute("name", name);
        Element string = doc.createElementNS(FLOWABLE_NS, "flowable:string");
        string.setTextContent(value == null ? "" : value);
        field.appendChild(string);
        return field;
    }

    /** 只清掉本系统注入的监听器，用户自定义的监听器保持原样 */
    private static void removeOwnListener(Element ext) {
        for (Element child : childElements(ext)) {
            if ("taskListener".equals(child.getLocalName())
                    && ASSIGNEE_LISTENER.equals(child.getAttribute("delegateExpression"))) {
                ext.removeChild(child);
            }
        }
    }

    /**
     * 会签：转为并行多实例，集合由 AssigneeResolveService 在运行时解析，
     * 每个实例的审批人通过 elementVariable=assignee 注入。
     */
    private static void appendMultiInstance(Document doc, Element task, String assigneeType,
                                            String assigneeValue, String deptScope, String fixedDeptId) {
        if (firstChild(task, "multiInstanceLoopCharacteristics") != null) {
            return;
        }
        Element loop = doc.createElementNS(BPMN_NS, "multiInstanceLoopCharacteristics");
        loop.setAttribute("isSequential", "false");
        loop.setAttribute("flowable:collection", "${assigneeResolveService.collect(execution,'"
                + escape(assigneeType) + "','" + escape(assigneeValue) + "','"
                + escape(deptScope) + "','" + escape(fixedDeptId) + "')}");
        loop.setAttribute("flowable:elementVariable", "assignee");

        Element completion = doc.createElementNS(BPMN_NS, "completionCondition");
        completion.setTextContent("${nrOfCompletedInstances >= nrOfInstances}");
        loop.appendChild(completion);
        // loopCharacteristics 在 BPMN XSD 中排在最后，直接追加即可
        task.appendChild(loop);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "");
    }

    /**
     * BPMN XSD 要求 documentation 必须排在 extensionElements 之前，
     * 因此新建的 extensionElements 要插在最后一个 documentation 之后。
     */
    private static Element findOrCreateExtension(Document doc, Element task) {
        Element existing = firstChild(task, "extensionElements");
        if (existing != null) {
            return existing;
        }
        Element ext = doc.createElementNS(BPMN_NS, "extensionElements");
        Node anchor = task.getFirstChild();
        for (Element child : childElements(task)) {
            if ("documentation".equals(child.getLocalName())) {
                anchor = child.getNextSibling();
            }
        }
        task.insertBefore(ext, anchor);
        return ext;
    }

    private static Element firstChild(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            if (localName.equals(child.getLocalName()) || localName.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static String toString(Document doc) throws Exception {
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
