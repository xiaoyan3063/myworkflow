package com.myworkflow.module.openapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.R;
import com.myworkflow.module.openapi.entity.OpenApp;
import com.myworkflow.module.openapi.mapper.OpenAppMapper;
import com.myworkflow.module.process.dto.StartProcessRequest;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.entity.WfProcessInstanceExt;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.mapper.WfProcessInstanceExtMapper;
import com.myworkflow.module.process.service.ProcessRuntimeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外开放 API：CRM / 其他系统通过 AppKey + AppSecret 发起与查询审批
 */
@Api(tags = "开放接口")
@RestController
@RequestMapping("/openapi/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenAppMapper openAppMapper;
    private final ProcessRuntimeService runtimeService;
    private final WfProcessDefMapper processDefMapper;
    private final WfProcessInstanceExtMapper instanceExtMapper;

    @ApiOperation("发起审批")
    @PostMapping("/process/start")
    public R<Map<String, Object>> start(@RequestBody StartProcessRequest req, HttpServletRequest request) {
        authenticate(request);
        return R.ok(runtimeService.start(req));
    }

    @ApiOperation("按业务单号查询")
    @GetMapping("/process/by-business")
    public R<Map<String, Object>> byBusiness(@RequestParam String businessKey,
                                             @RequestParam(required = false) String businessType,
                                             HttpServletRequest request) {
        authenticate(request);
        WfProcessInstanceExt ext = instanceExtMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getBusinessKey, businessKey)
                .eq(StringUtils.hasText(businessType), WfProcessInstanceExt::getBusinessType, businessType)
                .orderByDesc(WfProcessInstanceExt::getStartTime)
                .last("LIMIT 1"));
        if (ext == null) throw new BizException("未找到流程实例");
        Map<String, Object> m = new HashMap<>();
        m.put("instance", ext);
        m.put("timeline", runtimeService.timeline(ext.getProcessInstId()));
        return R.ok(m);
    }

    @ApiOperation("已发布流程")
    @GetMapping("/process/defs")
    public R<List<WfProcessDef>> defs(HttpServletRequest request) {
        authenticate(request);
        return R.ok(processDefMapper.selectList(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getStatus, 1)
                .select(WfProcessDef::getId, WfProcessDef::getProcessKey, WfProcessDef::getProcessName,
                        WfProcessDef::getCategoryId, WfProcessDef::getDescription, WfProcessDef::getVersion)));
    }

    private OpenApp authenticate(HttpServletRequest request) {
        String appKey = request.getHeader("X-App-Key");
        String appSecret = request.getHeader("X-App-Secret");
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw new BizException(401, "缺少 X-App-Key / X-App-Secret");
        }
        OpenApp app = openAppMapper.selectOne(new LambdaQueryWrapper<OpenApp>()
                .eq(OpenApp::getAppKey, appKey)
                .eq(OpenApp::getStatus, 1)
                .last("LIMIT 1"));
        if (app == null || !appSecret.equals(app.getAppSecret())) {
            throw new BizException(401, "应用认证失败");
        }
        return app;
    }
}
