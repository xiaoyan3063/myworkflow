package com.myworkflow.module.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myworkflow.common.result.R;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.module.system.entity.SysOperLog;
import com.myworkflow.module.ticket.entity.TkTicket;
import com.myworkflow.module.ticket.entity.TkType;
import com.myworkflow.module.ticket.mapper.TkTicketMapper;
import com.myworkflow.module.ticket.mapper.TkTypeMapper;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int PARAM_LIMIT = 2000;
    private static final int ERROR_LIMIT = 1000;

    private static final Pattern TICKET_URI = Pattern.compile("/ticket/tickets/(\\d+)");
    private static final Pattern TYPE_URI = Pattern.compile("/ticket/types/(\\d+)");

    private final SystemLogService logService;
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;
    private final TkTicketMapper ticketMapper;
    private final TkTypeMapper typeMapper;

    @Around("@within(restController)")
    public Object around(ProceedingJoinPoint point, RestController restController) throws Throwable {
        String method = request.getMethod();
        String uri = path();
        // 登录另有 sys_login_log 记录，不重复写；删除、清理日志属于写操作，必须留痕
        if (!isWrite(method) || uri.startsWith("/auth/login")) {
            return point.proceed();
        }

        long start = System.currentTimeMillis();
        try {
            Object result = point.proceed();
            save(point, uri, method, start, 1, null, result);
            return result;
        } catch (Throwable e) {
            save(point, uri, method, start, 0, e.getMessage(), null);
            throw e;
        }
    }

    private void save(ProceedingJoinPoint point, String uri, String method, long start,
                      int status, String error, Object result) {
        SysOperLog item = new SysOperLog();
        UserContext ctx = UserContext.get();
        item.setTenantId(ctx == null || ctx.getTenantId() == null ? 0L : ctx.getTenantId());
        item.setUserId(ctx == null ? null : ctx.getUserId());
        item.setUsername(ctx == null ? openApiUser(uri) : ctx.getUsername());
        item.setRealName(ctx == null ? null : ctx.getRealName());
        item.setModule(moduleOf(uri));
        item.setTitle(titleOf(point));
        item.setRequestUri(limit(uri, 512));
        item.setHttpMethod(method);
        item.setOperParam(paramsOf(point.getArgs()));
        item.setStatus(status);
        item.setErrorMsg(limit(error, ERROR_LIMIT));
        item.setCostMs(System.currentTimeMillis() - start);
        item.setIp(clientIp(request));
        item.setUserAgent(limit(request.getHeader("User-Agent"), 512));
        item.setSource(uri.startsWith("/openapi/") ? "OPENAPI" : "WEB");
        item.setTicketTypeName(limit(operTypeOf(uri, point.getArgs(), result), 128));
        logService.recordOperation(item);
    }

    /** 去掉 server.servlet.context-path（/api），保证按 /system、/ticket 等前缀判断有效 */
    private String path() {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private String paramsOf(Object[] args) {
        try {
            JsonNode node = objectMapper.valueToTree(java.util.Arrays.stream(args)
                    .filter(this::serializable)
                    .toArray());
            mask(node);
            return limit(objectMapper.writeValueAsString(node), PARAM_LIMIT);
        } catch (Exception ignored) {
            return "[参数无法序列化]";
        }
    }

    private boolean serializable(Object value) {
        return !(value instanceof ServletRequest)
                && !(value instanceof ServletResponse)
                && !(value instanceof MultipartFile)
                && !(value instanceof MultipartFile[]);
    }

    private void mask(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (sensitive(entry.getKey())) {
                    obj.put(entry.getKey(), "******");
                } else {
                    mask(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private boolean sensitive(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("secret") || k.contains("token");
    }

    private String titleOf(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        ApiOperation operation = method.getAnnotation(ApiOperation.class);
        return operation == null ? method.getName() : operation.value();
    }

    private String openApiUser(String uri) {
        if (!uri.startsWith("/openapi/")) return "anonymous";
        String appKey = request.getHeader("X-App-Key");
        return appKey == null ? "openapi" : "app:" + limit(appKey, 50);
    }

    private String moduleOf(String uri) {
        if (uri.startsWith("/auth")) return "认证";
        if (uri.startsWith("/system")) return "系统管理";
        if (uri.startsWith("/ticket")) return "工单";
        if (uri.startsWith("/runtime")) return "流程运行";
        if (uri.startsWith("/process")) return "流程设计";
        if (uri.startsWith("/notify")) return "消息";
        if (uri.startsWith("/openapi")) return "开放接口";
        return "其他";
    }

    /**
     * 操作类型：工单业务记具体工单类型名（事件工单等），其余按接口归属记用户管理、流程设计等。
     */
    private String operTypeOf(String uri, Object[] args, Object result) {
        String ticketType = ticketTypeOf(uri, args, result);
        if (StringUtils.hasText(ticketType)) {
            return ticketType;
        }
        if (uri.contains("/system/logs")) return "系统日志";
        if (uri.contains("/system/users")) return "用户管理";
        if (uri.contains("/system/depts")) return "部门管理";
        if (uri.contains("/system/roles")) return "角色管理";
        if (uri.contains("/system/menus")) return "菜单管理";
        if (uri.contains("/ticket/types")) return "工单类型";
        if (uri.contains("/ticket/files") || uri.contains("/ticket/tickets")) return "工单";
        if (uri.contains("/process/form")) return "表单管理";
        if (uri.contains("/process")) return "流程设计";
        if (uri.contains("/runtime")) return "流程运行";
        if (uri.contains("/notify")) return "消息";
        if (uri.contains("/openapi")) return "开放接口";
        if (uri.contains("/auth")) return "认证";
        return moduleOf(uri);
    }

    /**
     * 工单类型：优先用接口返回值（新建草稿成功后最准），再看请求体里的 typeId/typeName，
     * 最后从 URL 上的工单 id 或类型 id 反查。非工单接口返回空。
     */
    private String ticketTypeOf(String uri, Object[] args, Object result) {
        try {
            String name = fromObject(unwrap(result));
            if (StringUtils.hasText(name)) {
                return name;
            }
            if (args != null) {
                for (Object arg : args) {
                    name = fromObject(arg);
                    if (StringUtils.hasText(name)) {
                        return name;
                    }
                }
            }
            Matcher ticket = TICKET_URI.matcher(uri);
            if (ticket.find()) {
                return typeNameByTicketId(Long.valueOf(ticket.group(1)));
            }
            Matcher type = TYPE_URI.matcher(uri);
            if (type.find()) {
                return typeNameByTypeId(Long.valueOf(type.group(1)));
            }
            String ticketId = request.getParameter("ticketId");
            if (StringUtils.hasText(ticketId)) {
                return typeNameByTicketId(Long.valueOf(ticketId));
            }
        } catch (Exception ignored) {
            // 反查失败不影响业务请求
        }
        return null;
    }

    private Object unwrap(Object value) {
        if (value instanceof R) {
            return ((R<?>) value).getData();
        }
        return value;
    }

    private String fromObject(Object value) {
        if (value instanceof TkType) {
            TkType type = (TkType) value;
            if (StringUtils.hasText(type.getTypeName())) {
                return type.getTypeName();
            }
            return typeNameByTypeId(type.getId());
        }
        if (value instanceof TkTicket) {
            TkTicket ticket = (TkTicket) value;
            if (StringUtils.hasText(ticket.getTypeName())) {
                return ticket.getTypeName();
            }
            if (ticket.getTypeId() != null) {
                return typeNameByTypeId(ticket.getTypeId());
            }
            return typeNameByTicketId(ticket.getId());
        }
        return null;
    }

    private String typeNameByTicketId(Long ticketId) {
        if (ticketId == null) {
            return null;
        }
        TkTicket ticket = ticketMapper.selectById(ticketId);
        return ticket == null ? null : typeNameByTypeId(ticket.getTypeId());
    }

    private String typeNameByTypeId(Long typeId) {
        if (typeId == null) {
            return null;
        }
        TkType type = typeMapper.selectById(typeId);
        return type == null ? null : type.getTypeName();
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    public static String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value != null && !value.trim().isEmpty()) {
            return limit(value.split(",")[0].trim(), 64);
        }
        value = request.getHeader("X-Real-IP");
        return value == null || value.trim().isEmpty()
                ? limit(request.getRemoteAddr(), 64) : limit(value, 64);
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
