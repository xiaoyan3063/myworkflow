package com.myworkflow.module.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.exception.BizException;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.process.entity.WfFormDef;
import com.myworkflow.module.process.entity.WfProcessCategory;
import com.myworkflow.module.process.entity.WfProcessDef;
import com.myworkflow.module.process.entity.WfProcessInstanceExt;
import com.myworkflow.module.process.mapper.WfFormDefMapper;
import com.myworkflow.module.process.mapper.WfProcessCategoryMapper;
import com.myworkflow.module.process.mapper.WfProcessDefMapper;
import com.myworkflow.module.process.mapper.WfProcessInstanceExtMapper;
import com.myworkflow.module.process.util.BpmnEnhanceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefService {

    private final WfProcessDefMapper processDefMapper;
    private final WfProcessCategoryMapper categoryMapper;
    private final WfFormDefMapper formDefMapper;
    private final WfProcessInstanceExtMapper instanceExtMapper;
    private final RepositoryService repositoryService;

    public PageResult<WfProcessDef> page(long page, long size, String keyword, Integer status) {
        Page<WfProcessDef> p = processDefMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfProcessDef>()
                        .like(StringUtils.hasText(keyword), WfProcessDef::getProcessName, keyword)
                        .eq(status != null, WfProcessDef::getStatus, status)
                        .orderByDesc(WfProcessDef::getUpdateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public WfProcessDef detail(Long id) {
        WfProcessDef def = processDefMapper.selectById(id);
        if (def == null) throw new BizException("流程不存在");
        return def;
    }

    public WfProcessDef save(WfProcessDef def) {
        if (!StringUtils.hasText(def.getProcessKey())) {
            throw new BizException("流程标识不能为空");
        }
        if (def.getTicketTypeId() != null && def.getTicketTypeId() == 0L) {
            def.setTicketTypeId(null);
        }
        if (def.getFormId() != null && def.getFormId() == 0L) {
            def.setFormId(null);
        }
        if (def.getTicketTypeId() != null) {
            def.setFormId(null);
        } else if (def.getFormId() != null) {
            def.setTicketTypeId(null);
        }
        if (def.getId() == null) {
            Long cnt = processDefMapper.selectCount(new LambdaQueryWrapper<WfProcessDef>()
                    .eq(WfProcessDef::getProcessKey, def.getProcessKey()));
            if (cnt > 0) throw new BizException("流程标识已存在");
            if (def.getStatus() == null) def.setStatus(0);
            if (def.getVersion() == null) def.setVersion(1);
            processDefMapper.insert(def);
        } else {
            WfProcessDef db = detail(def.getId());
            db.setProcessName(def.getProcessName());
            db.setCategoryId(def.getCategoryId());
            db.setFormId(def.getFormId());
            db.setTicketTypeId(def.getTicketTypeId());
            db.setIcon(def.getIcon());
            db.setDescription(def.getDescription());
            if (StringUtils.hasText(def.getBpmnXml())) {
                db.setBpmnXml(def.getBpmnXml());
            }
            processDefMapper.updateById(db);
            return db;
        }
        return def;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deploy(Long id) {
        WfProcessDef def = detail(id);
        if (!StringUtils.hasText(def.getBpmnXml())) {
            throw new BizException("请先设计流程图");
        }
        String xml = BpmnEnhanceUtil.enhance(def.getBpmnXml(), def.getProcessKey(), def.getProcessName());
        Deployment deployment;
        try {
            deployment = repositoryService.createDeployment()
                    .name(def.getProcessName())
                    .key(def.getProcessKey())
                    .addString(def.getProcessKey() + ".bpmn20.xml", xml)
                    .deploy();
        } catch (Exception e) {
            log.error("流程部署失败, defId={}, xml=\n{}", id, xml, e);
            throw new BizException("流程发布失败：" + rootMessage(e));
        }
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        // 库里保留设计器原稿，增强后的 XML 只交给 Flowable，避免下次编辑被旧注入内容干扰
        def.setFlowableDeployId(deployment.getId());
        def.setFlowableDefId(pd.getId());
        def.setStatus(1);
        def.setVersion(pd.getVersion());
        processDefMapper.updateById(def);
    }

    public void disable(Long id) {
        WfProcessDef def = detail(id);
        def.setStatus(2);
        processDefMapper.updateById(def);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WfProcessDef def = detail(id);
        Long running = instanceExtMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getProcessDefId, id)
                .eq(WfProcessInstanceExt::getStatus, "RUNNING"));
        if (running != null && running > 0) {
            throw new BizException("该流程还有 " + running + " 个运行中的实例，请先处理完再删除");
        }
        if (StringUtils.hasText(def.getFlowableDeployId())) {
            try {
                repositoryService.deleteDeployment(def.getFlowableDeployId(), true);
            } catch (Exception e) {
                throw new BizException("流程已产生历史数据，无法删除，请改为停用");
            }
        }
        processDefMapper.deleteById(id);
    }

    public List<WfProcessDef> publishedList() {
        return processDefMapper.selectList(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getStatus, 1)
                .orderByDesc(WfProcessDef::getUpdateTime));
    }

    public List<WfProcessCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<WfProcessCategory>()
                .orderByAsc(WfProcessCategory::getSortNo));
    }

    public void saveCategory(WfProcessCategory category) {
        if (category.getId() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
    }

    public PageResult<WfFormDef> formPage(long page, long size, String keyword) {
        Page<WfFormDef> p = formDefMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfFormDef>()
                        .like(StringUtils.hasText(keyword), WfFormDef::getFormName, keyword)
                        .orderByDesc(WfFormDef::getUpdateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public WfFormDef saveForm(WfFormDef form) {
        if (form.getId() == null) {
            if (form.getStatus() == null) form.setStatus(1);
            formDefMapper.insert(form);
        } else {
            formDefMapper.updateById(form);
        }
        return form;
    }

    public WfFormDef formDetail(Long id) {
        WfFormDef form = formDefMapper.selectById(id);
        if (form == null) throw new BizException("表单不存在");
        return form;
    }

    /** Flowable 的部署异常层层包装，取最内层原因才能给出可读提示。 */
    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return StringUtils.hasText(msg) ? msg : e.toString();
    }
}
