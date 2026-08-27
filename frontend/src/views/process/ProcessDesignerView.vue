<template>
  <div class="designer page-card">
    <div class="toolbar">
      <div class="left">
        <el-input v-model="meta.processName" placeholder="流程名称" style="width: 170px" />
        <el-input v-model="meta.processKey" placeholder="流程标识(英文)" style="width: 170px" :disabled="!!meta.id" />
        <el-select
          v-model="bindSource"
          clearable
          filterable
          placeholder="绑定表单 / 工单类型"
          style="width: 240px"
        >
          <el-option-group label="审批表单">
            <el-option v-for="f in forms" :key="'f-' + f.id" :label="f.formName" :value="'f:' + f.id" />
          </el-option-group>
          <el-option-group label="工单类型">
            <el-option
              v-for="t in ticketTypes"
              :key="'t-' + t.id"
              :label="`${t.typeName}（${t.typeCode}）`"
              :value="'t:' + t.id"
            />
          </el-option-group>
        </el-select>
      </div>
      <div class="right">
        <el-tooltip content="撤销上一步（Ctrl+Z）" placement="bottom">
          <el-button @click="undo">撤销</el-button>
        </el-tooltip>
        <el-tooltip content="删除画布中已选中的元素（Delete 键同样可用，支持框选多个）" placement="bottom">
          <el-button type="danger" plain :disabled="!selectionCount" @click="removeSelection">
            删除选中{{ selectionCount > 1 ? `(${selectionCount})` : '' }}
          </el-button>
        </el-tooltip>
        <el-button @click="zoomReset">适应画布</el-button>
        <el-button @click="helpVisible = true">配置指南</el-button>
        <el-button @click="saveDraft" :loading="saving">保存草稿</el-button>
        <el-button type="primary" @click="saveAndDeploy" :loading="saving">保存并发布</el-button>
      </div>
    </div>

    <div class="body">
      <div class="canvas-wrap">
        <div ref="canvasRef" class="canvas"></div>
        <div class="legend">
          <span><i class="dot dot-task"></i>审批节点</span>
          <span><i class="dot dot-warn"></i>待配置审批人</span>
          <span><i class="dot dot-gw"></i>条件分支</span>
        </div>
      </div>

      <aside class="props">
        <div class="props-head">
          <div class="props-title">
            <span class="badge" :class="`badge-${selectedKind}`">{{ selectedLabel }}</span>
          </div>
          <small v-if="selected">{{ selected.id }}</small>
        </div>

        <el-empty v-if="!selected" description="在画布中点选一个节点开始配置" :image-size="80" />

        <el-form v-else label-position="top" size="small" class="props-form">
          <el-form-item label="节点名称">
            <el-input v-model="nodeForm.name" placeholder="显示在流程图上的名称" @change="applyTask" />
          </el-form-item>

          <template v-if="isUserTask">
            <el-form-item label="审批人来源">
              <el-select v-model="nodeForm.assigneeType" style="width: 100%" @change="onAssigneeTypeChange">
                <el-option label="按角色（推荐）" value="role" />
                <el-option label="指定用户" value="user" />
                <el-option label="按部门" value="dept" />
                <el-option label="发起人本人" value="starter" />
                <el-option label="从表单字段取值" value="formField" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="nodeForm.assigneeType === 'role'" label="选择角色">
              <el-select
                v-model="assigneeList"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%"
                placeholder="角色下所有启用用户都会收到待办"
                @change="applyTask"
              >
                <el-option
                  v-for="r in roles"
                  :key="r.id"
                  :label="`${r.roleName}（${r.roleCode}）`"
                  :value="r.roleCode"
                />
              </el-select>
            </el-form-item>

            <el-form-item v-else-if="nodeForm.assigneeType === 'user'" label="选择审批人">
              <el-select
                v-model="assigneeList"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%"
                placeholder="可多选"
                @change="applyTask"
              >
                <el-option
                  v-for="u in users"
                  :key="u.id"
                  :label="`${u.realName || u.username}（${u.username}）`"
                  :value="String(u.id)"
                />
              </el-select>
            </el-form-item>

            <el-form-item v-else-if="nodeForm.assigneeType === 'dept'" label="选择部门">
              <el-tree-select
                v-model="assigneeList"
                :data="deptTree"
                multiple
                filterable
                check-strictly
                collapse-tags
                collapse-tags-tooltip
                node-key="value"
                :render-after-expand="false"
                style="width: 100%"
                placeholder="部门下所有启用用户都会收到待办"
                @change="applyTask"
              />
            </el-form-item>

            <el-form-item v-else-if="nodeForm.assigneeType === 'formField'" label="选择人员字段">
              <el-select
                v-model="nodeForm.assigneeValue"
                style="width: 100%"
                placeholder="选择绑定表单或工单类型中的人员字段"
                @change="applyTask"
              >
                <el-option
                  v-for="f in assigneeFormFields"
                  :key="f.field"
                  :label="`${f.title || f.field}（${f.field}）`"
                  :value="f.field"
                />
              </el-select>
              <div class="tip">
                运行到该节点时读取字段里的用户 ID。人员选择字段可直接使用；普通文本字段需填写用户 ID，
                多人用英文逗号分隔。
              </div>
            </el-form-item>

            <el-form-item label="多人审批方式">
              <el-radio-group v-model="nodeForm.multiMode" @change="applyTask">
                <el-radio-button value="or">或签</el-radio-button>
                <el-radio-button value="and">会签</el-radio-button>
              </el-radio-group>
              <div class="tip">或签：任意一人处理即通过；会签：所有人都通过才进入下一节点。</div>
            </el-form-item>

            <el-form-item label="超时催办">
              <el-input-number
                v-model="nodeForm.dueHours"
                :min="0"
                :max="720"
                style="width: 100%"
                @change="applyTask"
              />
              <div class="tip">超过设定小时数未处理会推送催办消息，填 0 表示不催办。</div>
            </el-form-item>

            <el-form-item v-if="meta.ticketTypeId" label="本节点填写字段">
              <div v-if="formFields.length" class="node-fields">
                <div v-for="f in formFields" :key="f.field" class="node-field-row">
                  <el-checkbox
                    :model-value="nodeForm.writableFields.includes(f.field)"
                    @change="(checked: boolean) => toggleWritableField(f.field, checked)"
                  >
                    {{ f.title || f.field }}
                  </el-checkbox>
                  <el-checkbox
                    :model-value="nodeForm.requiredFields.includes(f.field)"
                    :disabled="!nodeForm.writableFields.includes(f.field)"
                    @change="(checked: boolean) => toggleRequiredField(f.field, checked)"
                  >
                    必填
                  </el-checkbox>
                </div>
              </div>
              <el-empty v-else description="请先绑定工单类型并配置字段" :image-size="48" />
              <div class="tip">勾选的字段仅当前节点处理人可编辑；未勾选字段仍只读显示。</div>
            </el-form-item>

            <div class="summary" :class="{ warn: !taskConfigured }">
              {{ taskSummary }}
            </div>
          </template>

          <template v-else-if="isGateway">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="条件分支在这里配置"
              description="下面列出了从本节点引出的每条分支，按顺序判断，命中第一条满足条件的分支。"
            />
            <el-empty v-if="!branches.length" description="请先从本节点拉出分支连线" :image-size="60" />
            <div v-for="(b, i) in branches" :key="b.id" class="branch" :style="{ borderLeftColor: b.color }">
              <div class="branch-head">
                <span class="branch-no" :style="{ background: b.color }">分支 {{ i + 1 }}</span>
                <span class="branch-to">→ {{ b.targetName }}</span>
              </div>
              <el-input v-model="b.name" placeholder="分支名称，如：大于3天" size="small" @change="applyBranch(b)" />

              <el-checkbox v-model="b.isDefault" size="small" @change="setDefaultBranch(b)">
                设为默认分支（其他分支都不满足时走这条）
              </el-checkbox>

              <template v-if="!b.isDefault">
                <el-radio-group v-model="b.mode" size="small" @change="applyBranch(b)">
                  <el-radio-button value="simple">按表单字段</el-radio-button>
                  <el-radio-button value="expr">表达式</el-radio-button>
                </el-radio-group>

                <div v-if="b.mode === 'simple'" class="cond-row">
                  <el-select v-model="b.field" placeholder="字段" size="small" @change="applyBranch(b)">
                    <el-option
                      v-for="f in formFields"
                      :key="f.field"
                      :label="f.title || f.field"
                      :value="f.field"
                    />
                  </el-select>
                  <el-select v-model="b.op" size="small" style="width: 96px" @change="applyBranch(b)">
                    <el-option v-for="o in OPS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                  <el-select
                    v-if="fieldOptions(b.field).length"
                    v-model="b.value"
                    size="small"
                    placeholder="值"
                    @change="applyBranch(b)"
                  >
                    <el-option
                      v-for="o in fieldOptions(b.field)"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                    />
                  </el-select>
                  <el-input v-else v-model="b.value" size="small" placeholder="值" @change="applyBranch(b)" />
                </div>

                <el-input
                  v-else
                  v-model="b.expr"
                  type="textarea"
                  :rows="2"
                  size="small"
                  placeholder="例如：${days > 3 &amp;&amp; leaveType == 'sick'}"
                  @change="applyBranch(b)"
                />

                <div v-if="!hasBindSource && b.mode === 'simple'" class="tip warn-text">
                  请先在顶部绑定审批表单或工单类型，才能选择字段。
                </div>
                <div v-else-if="b.expression" class="expr-preview">{{ b.expression }}</div>
              </template>
            </div>
          </template>

          <template v-else-if="isSequenceFlow">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="这是一条连线"
              description="条件建议直接在上游的「条件分支」节点里统一配置，那里能一次看到所有分支。"
            />
            <el-form-item label="分支条件表达式">
              <el-input
                v-model="nodeForm.condition"
                type="textarea"
                :rows="2"
                placeholder="留空表示无条件通过"
                @change="applyFlowCondition"
              />
            </el-form-item>
          </template>

          <el-alert
            v-else
            type="info"
            :closable="false"
            title="该元素无需额外配置"
            description="只有审批节点、条件分支和连线需要配置。"
          />
        </el-form>
      </aside>
    </div>

    <el-drawer v-model="helpVisible" title="流程配置指南" size="480px">
      <el-scrollbar>
        <div class="help">
          <section>
            <h4>第一步：画出流程骨架</h4>
            <ol>
              <li>画布左侧是工具箱，把「任务」拖到画布上。</li>
              <li>选中它，点右侧工具条的扳手图标，改成「审批节点」。</li>
              <li>把鼠标移到节点边缘，拖出箭头连到下一个节点，最后连到结束事件。</li>
            </ol>
          </section>

          <section>
            <h4>第二步：配置审批人</h4>
            <p class="help-text">
              点中审批节点，右侧面板会出现配置项。<b>选完即时生效</b>，节点下方的绿色标签会同步显示当前审批人；
              还没配审批人的节点是<b>橙色虚线</b>，一眼就能看出漏了哪个。
            </p>
            <div v-for="item in assigneeHelp" :key="item.name" class="help-item">
              <div class="help-name">{{ item.name }}</div>
              <p>{{ item.desc }}</p>
            </div>
          </section>

          <section>
            <h4>第三步：配置条件分支</h4>
            <p class="help-text">
              需要走不同路径时，拖入「排他网关」，从它拉出多条连线分别连到不同的审批节点。
              然后<b>点中网关本身</b>，右侧会列出所有分支，逐条设置条件即可，不用去点细细的连线。
            </p>
            <ol>
              <li>在顶部「绑定表单 / 工单类型」选好数据源，条件才能直接选字段。工单类型会出现在第二组。</li>
              <li>每条分支选「字段 + 运算符 + 值」，例如 请假天数 大于 3。</li>
              <li>留一条勾选「默认分支」兜底，避免所有条件都不满足时流程卡住。</li>
              <li>分支按列表顺序判断，命中第一条满足的就不再看后面的。</li>
            </ol>
          </section>

          <section>
            <h4>常用操作</h4>
            <div class="help-item">
              <div class="help-name">删除元素</div>
              <p>
                选中后按 Delete 键，或点工具栏的「删除选中」。用工具箱最上面的框选工具拖出一个范围可以一次选中多个，
                再一起删除。也可以点节点右侧操作条里的垃圾桶图标。
              </p>
            </div>
            <div class="help-item">
              <div class="help-name">撤销 / 重做</div>
              <p>Ctrl+Z 撤销，Ctrl+Shift+Z 重做，工具栏也有「撤销」按钮。删错了可以直接退回。</p>
            </div>
            <div class="help-item">
              <div class="help-name">分支线看不清</div>
              <p>
                从同一个网关引出的分支会自动配色，右侧分支卡片左边的色条与画布上的线一一对应。
                线上还会标出分支名。如果仍然重叠，把下游节点上下拖开一些即可。
              </p>
            </div>
          </section>

          <section>
            <h4>第四步：发布</h4>
            <p class="help-text">
              点「保存并发布」。发布前会自动检查审批人和分支条件是否漏配，有问题会直接提示是哪个节点。
              发布后即可在「发起审批」里选用。
            </p>
          </section>

          <section>
            <h4>节点类型速查</h4>
            <p class="help-text">
              选中节点后点扳手图标可以切换类型。菜单里是完整的 BPMN 类型，下表标出了每种在本系统里的可用程度：
              <b>已支持</b>=有配置界面，直接用；<b>需手工配置</b>=引擎能跑但要写表达式或后端代码；
              <b>暂不建议</b>=审批场景用不上，留待后续扩展。
            </p>
            <div v-for="g in nodeTypeHelp" :key="g.group" class="type-group">
              <div class="type-group-title">{{ g.group }}</div>
              <div v-for="item in g.items" :key="item.en" class="type-item">
                <div class="type-head">
                  <span class="type-name">{{ item.name }}</span>
                  <el-tag size="small" :type="supportTag[item.support].type" effect="light">
                    {{ supportTag[item.support].text }}
                  </el-tag>
                </div>
                <div class="type-en">{{ item.en }}</div>
                <p>{{ item.desc }}</p>
              </div>
            </div>
          </section>
        </div>
      </el-scrollbar>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { translateModule } from './bpmnZh'
import http from '@/utils/http'

const EMPTY_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
 xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
 xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
 xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
 xmlns:flowable="http://flowable.org/bpmn"
 targetNamespace="http://myworkflow.com/process">
  <process id="Process_1" name="新建流程" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

const TYPE_LABELS: Record<string, string> = {
  'bpmn:StartEvent': '开始事件',
  'bpmn:EndEvent': '结束事件',
  'bpmn:UserTask': '审批节点',
  'bpmn:Task': '任务节点',
  'bpmn:ServiceTask': '服务任务',
  'bpmn:ScriptTask': '脚本任务',
  'bpmn:ExclusiveGateway': '条件分支',
  'bpmn:ParallelGateway': '并行分支',
  'bpmn:InclusiveGateway': '包容分支',
  'bpmn:SequenceFlow': '连线',
  'bpmn:SubProcess': '子流程',
  'bpmn:Process': '流程',
}

const TYPE_MARKERS: Record<string, string> = {
  'bpmn:UserTask': 'wf-user-task',
  'bpmn:ServiceTask': 'wf-service-task',
  'bpmn:ScriptTask': 'wf-service-task',
  'bpmn:StartEvent': 'wf-start',
  'bpmn:EndEvent': 'wf-end',
  'bpmn:ExclusiveGateway': 'wf-gateway',
  'bpmn:ParallelGateway': 'wf-gateway',
  'bpmn:InclusiveGateway': 'wf-gateway',
}

const BRANCH_COLORS = ['#2f7fd1', '#16794c', '#c9741f', '#8b5cf6', '#c0392b']
const DEFAULT_BRANCH_COLOR = '#8a9a94'
const BRANCH_MARKERS = [...BRANCH_COLORS.map((_, i) => `wf-branch-${i}`), 'wf-branch-default']

const OPS = [
  { label: '等于', value: '==' },
  { label: '不等于', value: '!=' },
  { label: '大于', value: '>' },
  { label: '大于等于', value: '>=' },
  { label: '小于', value: '<' },
  { label: '小于等于', value: '<=' },
]

const assigneeHelp = [
  { name: '按角色（推荐）', desc: '例如选「部门经理」，该角色下所有启用用户都会收到待办。人员调整时只改角色成员，不用回来改流程。' },
  { name: '指定用户', desc: '固定某几个人审批。人员离职或转岗后需要回到这里修改流程。' },
  { name: '按部门', desc: '该部门下所有启用用户都会收到待办，适合整个小组一起看的场景。' },
  { name: '发起人本人', desc: '让提交人处理该审批节点，适合申请人确认、补充材料等环节。退回发起人无需再画此节点，系统会利用开始事件自动生成重新提交待办。' },
  { name: '从表单字段取值', desc: '把表单中的人员选择字段作为审批人。单选字段产生一个待办，多选字段可配或签或会签。' },
]

/**
 * 节点类型速查。support 决定表格里的标记：
 * ready   = 当前系统已完整支持，放心用
 * partial = BPMN 能画、引擎能跑，但本系统还没接管配置，需要人工写表达式
 * later   = 审批场景基本用不到，留着是为了以后扩展
 */
const nodeTypeHelp = [
  {
    group: '事件',
    items: [
      { name: '开始事件', en: 'Start Event', support: 'ready', desc: '流程入口，一个流程只留一个。审批流不需要消息/定时等特殊开始事件。' },
      { name: '结束事件', en: 'End Event', support: 'ready', desc: '流程终点，走到这里实例状态变为「已完成」。允许有多个，任意一个走到即结束该分支。' },
      { name: '终止结束事件', en: 'Terminate End Event', support: 'partial', desc: '立刻终止整个流程实例，包括还在并行分支里跑的任务。并行会审中途要一票否决时才用得上。' },
      { name: '定时边界事件', en: 'Timer Boundary Event', support: 'partial', desc: '挂在审批节点边上，超时后自动走另一条分支（比如自动同意、转交上级）。当前系统的超时是「催办提醒」，不会自动跳转，需要这个效果才画它。' },
      { name: '其他事件', en: 'Message / Signal / Error / Escalation …', support: 'later', desc: '消息、信号、错误、升级、补偿类事件，用于系统间通信和异常补偿。纯审批流用不到。' },
    ],
  },
  {
    group: '任务',
    items: [
      { name: '审批节点（用户任务）', en: 'User Task', support: 'ready', desc: '唯一会产生待办的节点，审批流的核心。必须配审批人；可设会签、超时催办。' },
      { name: '服务任务', en: 'Service Task', support: 'partial', desc: '不需要人参与的自动节点，用来回写 CRM、调接口。需要在后端写一个 Spring Bean，再在节点上填 ${beanName}，否则运行到这里会报找不到类。' },
      { name: '脚本任务', en: 'Script Task', support: 'partial', desc: '直接跑一段脚本做变量计算。能跑但没有配置界面，要手工写 XML。' },
      { name: '接收任务', en: 'Receive Task', support: 'later', desc: '停在这里等外部系统发消息才继续，用于跨系统等待。' },
      { name: '发送任务 / 手工任务 / 业务规则任务', en: 'Send / Manual / Business Rule Task', support: 'later', desc: '分别对应发消息、线下人工操作、调用规则引擎。审批场景一般用审批节点或服务任务代替。' },
    ],
  },
  {
    group: '网关（分支）',
    items: [
      { name: '排他网关', en: 'Exclusive Gateway', support: 'ready', desc: '最常用。从多条分支里挑第一条满足条件的走，其余不走。记得留一条默认分支兜底。' },
      { name: '并行网关', en: 'Parallel Gateway', support: 'ready', desc: '所有分支同时进行，必须全部完成才继续。用于多部门并行会审。分出去几条，就要用另一个并行网关合回来。' },
      { name: '包容网关', en: 'Inclusive Gateway', support: 'ready', desc: '满足条件的分支都会走，可能一条也可能多条。例如金额大要财务审、跨部门还要总监审，两个条件都满足就同时发给两人。' },
      { name: '事件网关', en: 'Event-based Gateway', support: 'later', desc: '按「哪个事件先发生」决定走向，配合消息/定时事件使用。' },
      { name: '复杂网关', en: 'Complex Gateway', support: 'later', desc: '用表达式描述复杂的汇聚规则，可读性差，建议改用前三种组合实现。' },
    ],
  },
  {
    group: '结构',
    items: [
      { name: '子流程', en: 'Sub Process', support: 'partial', desc: '把一段流程折叠成一个框，让主图更清爽。内部节点同样要配审批人，但当前属性面板不支持进到子流程里配置。' },
      { name: '调用活动', en: 'Call Activity', support: 'partial', desc: '在当前流程里调用另一个已发布的流程，适合抽出多个流程共用的审批段。需要手工填被调流程的 key。' },
      { name: '泳池 / 泳道', en: 'Pool / Lane', support: 'later', desc: '按参与方或部门给节点分区，纯粹是画图用的视觉分组，不影响执行。节点多时才有必要。' },
      { name: '数据对象 / 数据存储', en: 'Data Object / Data Store', support: 'later', desc: 'BPMN 里表示单据和数据库的图形符号，不参与执行。本系统的表单数据走「绑定表单」，所以已从工具箱隐藏。' },
      { name: '分组框 / 文字注释', en: 'Group / Text Annotation', support: 'ready', desc: '纯注释元素，给流程图加说明用，不影响执行，可以放心用。' },
    ],
  },
]

const supportTag: Record<string, { text: string; type: string }> = {
  ready: { text: '已支持', type: 'success' },
  partial: { text: '需手工配置', type: 'warning' },
  later: { text: '暂不建议', type: 'info' },
}

const route = useRoute()
const router = useRouter()
const canvasRef = ref<HTMLElement>()
const saving = ref(false)
const helpVisible = ref(false)
const forms = ref<any[]>([])
const ticketTypes = ref<any[]>([])
const users = ref<any[]>([])
const roles = ref<any[]>([])
const deptTree = ref<any[]>([])
const formFields = ref<any[]>([])
/**
 * 必须用 shallowRef + markRaw：ref() 会把 bpmn-js 元素深度包成响应式 Proxy，
 * 顺着 businessObject / parent / children 递归代理整张模型图。
 * bpmn-js 内部大量依赖对象同一性比较，被代理后这些判断会失效，
 * 表现为节点删不掉、属性写不进去等诡异问题。
 */
const selected = shallowRef<any>(null)
const selectionCount = ref(0)
const branches = ref<any[]>([])
const meta = reactive<any>({ id: null, processName: '', processKey: '', formId: null, ticketTypeId: null, description: '' })
const nodeForm = reactive({
  name: '',
  assigneeType: 'role',
  assigneeValue: '',
  multiMode: 'or',
  dueHours: 0,
  writableFields: [] as string[],
  requiredFields: [] as string[],
  condition: '',
})

let modeler: any = null
let decorateHandle = 0

const hasBindSource = computed(() => !!(meta.formId || meta.ticketTypeId))

const bindSource = computed({
  get: () => {
    if (meta.ticketTypeId) return 't:' + meta.ticketTypeId
    if (meta.formId) return 'f:' + meta.formId
    return ''
  },
  set: (v: string) => {
    if (!v) {
      meta.formId = null
      meta.ticketTypeId = null
    } else if (v.startsWith('t:')) {
      meta.ticketTypeId = v.slice(2)
      meta.formId = null
    } else {
      meta.formId = v.slice(2)
      meta.ticketTypeId = null
    }
    loadFormFields()
  },
})

const isUserTask = computed(() => selected.value?.type === 'bpmn:UserTask')
const isGateway = computed(() => /Gateway$/.test(selected.value?.type || ''))
const isSequenceFlow = computed(() => selected.value?.type === 'bpmn:SequenceFlow')
const selectedLabel = computed(() =>
  selected.value ? TYPE_LABELS[selected.value.type] || selected.value.type : '节点属性'
)
const selectedKind = computed(() => {
  if (isUserTask.value) return 'task'
  if (isGateway.value) return 'gateway'
  if (isSequenceFlow.value) return 'flow'
  return 'other'
})

const assigneeList = computed<string[]>({
  get: () => (nodeForm.assigneeValue ? nodeForm.assigneeValue.split(',').filter(Boolean) : []),
  set: (val) => {
    nodeForm.assigneeValue = (val || []).join(',')
  },
})

const assigneeFormFields = computed(() =>
  [...formFields.value].sort((a, b) => {
    const aUser = a.type === 'user' || a.type === 'users' ? 0 : 1
    const bUser = b.type === 'user' || b.type === 'users' ? 0 : 1
    return aUser - bUser
  })
)

const taskConfigured = computed(
  () => nodeForm.assigneeType === 'starter' || !!nodeForm.assigneeValue
)

const taskSummary = computed(() => {
  if (!taskConfigured.value) return '尚未选择审批人，发布前必须补齐'
  const who = describeAssignee(nodeForm.assigneeType, nodeForm.assigneeValue)
  const mode = nodeForm.multiMode === 'and' ? '会签' : '或签'
  const due = nodeForm.dueHours > 0 ? `，超过 ${nodeForm.dueHours} 小时催办` : ''
  return `当前：${who}（${mode}）${due}`
})

function describeAssignee(type: string, value: string) {
  if (type === 'starter') return '发起人本人'
  if (type === 'formField') {
    const field = formFields.value.find((f) => f.field === value)
    return `表单字段：${field?.title || value || '未配置'}`
  }
  const parts = (value || '').split(',').filter(Boolean)
  if (!parts.length) return '未配置'
  const names = parts.map((v) => {
    if (type === 'role') return roles.value.find((r) => r.roleCode === v)?.roleName || v
    if (type === 'user') return users.value.find((u) => String(u.id) === v)?.realName || v
    if (type === 'dept') return findDept(deptTree.value, v) || v
    return v
  })
  const prefix = type === 'role' ? '角色' : type === 'dept' ? '部门' : ''
  return prefix + names.join('、')
}

function findDept(nodes: any[], value: string): string {
  for (const n of nodes) {
    if (n.value === value) return n.label
    if (n.children) {
      const hit = findDept(n.children, value)
      if (hit) return hit
    }
  }
  return ''
}

function fieldOptions(field: string) {
  return formFields.value.find((f) => f.field === field)?.options || []
}

/**
 * bpmn-js 的事件回调是在命令执行过程中同步调用的，
 * 回调里抛异常会让 CommandStack 回滚整条命令（表现为节点删不掉）。
 * 这里兜住异常，保证界面逻辑永远不会阻断建模操作。
 */
function guard<T extends (...args: any[]) => any>(fn: T) {
  return (...args: Parameters<T>) => {
    try {
      return fn(...args)
    } catch (err) {
      console.error('[designer] 事件处理异常', err)
    }
  }
}

function toTree(nodes: any[]): any[] {
  return (nodes || []).map((n) => ({
    value: String(n.id),
    label: n.deptName || n.label,
    children: n.children?.length ? toTree(n.children) : undefined,
  }))
}

onMounted(async () => {
  const [formRes, typeRes, userRes, roleRes, deptRes]: any[] = await Promise.all([
    http.get('/process/forms', { params: { page: 1, size: 100 } }),
    http.get('/ticket/types/enabled').catch(() => ({ data: [] })),
    http.get('/system/users/simple'),
    http.get('/system/roles'),
    http.get('/system/depts/tree'),
  ])
  forms.value = formRes.data?.records || []
  ticketTypes.value = typeRes.data || []
  users.value = userRes.data || []
  roles.value = roleRes.data || []
  deptTree.value = toTree(deptRes.data || [])

  modeler = new BpmnModeler({
    container: canvasRef.value,
    additionalModules: [translateModule],
    // 不传 bindTo 时 diagram-js 根本不监听键盘，Delete / Ctrl+Z 全部失效。
    // 焦点在 input/textarea 里时它会自动忽略，不会误删节点。
    keyboard: { bindTo: document },
    bpmnRenderer: {
      defaultFillColor: '#ffffff',
      defaultStrokeColor: '#0b3d2e',
      defaultLabelColor: '#0b1f1a',
    },
  })

  modeler.on(
    'selection.changed',
    guard((e: any) => {
      const sel = e.newSelection || []
      selectionCount.value = sel.length
      // 框选多个元素时不进属性面板，避免误改到其中某一个
      applySelection(sel.length === 1 ? sel[0] : null)
    })
  )
  modeler.on(
    ['import.done', 'shape.added', 'element.changed', 'elements.changed'],
    guard(scheduleDecorate)
  )
  // 新拉/删除连线后，条件分支面板要跟着增减（不监听 element.changed，避免打断正在编辑的分支）
  modeler.on(
    ['connection.added', 'connection.removed'],
    guard(() => {
      if (selected.value && /Gateway$/.test(selected.value.type)) loadBranches(selected.value)
    })
  )

  const id = route.params.id
  if (id) {
    const res: any = await http.get(`/process/defs/${id}`)
    Object.assign(meta, {
      id: res.data.id,
      processName: res.data.processName,
      processKey: res.data.processKey,
      formId: res.data.formId || null,
      ticketTypeId: res.data.ticketTypeId || null,
      description: res.data.description,
    })
    await loadFormFields()
    await importDiagram(res.data.bpmnXml)
  } else {
    await importDiagram('')
  }
  modeler.get('canvas').zoom('fit-viewport')
})

onBeforeUnmount(() => {
  if (decorateHandle) window.cancelAnimationFrame(decorateHandle)
  modeler?.destroy()
})

/**
 * 缺少 BPMNDiagram 图形信息时 bpmn-js 能解析但画不出东西，画布会是一片空白。
 * 这里把这种情况和解析失败都提示出来，不然只能看到空画布无从排查。
 */
async function importDiagram(xml?: string) {
  if (!xml) {
    await modeler.importXML(EMPTY_BPMN)
    return
  }
  try {
    const { warnings } = await modeler.importXML(xml)
    if (!modeler.get('elementRegistry').filter((e: any) => e.type !== 'bpmn:Process').length) {
      ElMessage.warning('该流程缺少图形信息，无法在设计器中显示，请重新绘制后保存')
    } else if (warnings?.length) {
      ElMessage.warning(`流程图有 ${warnings.length} 处不识别的内容，已忽略`)
    }
  } catch (e: any) {
    ElMessage.error(`流程图解析失败：${e?.message || e}`)
    await modeler.importXML(EMPTY_BPMN)
  }
}

async function loadFormFields() {
  formFields.value = []
  if (meta.ticketTypeId) {
    try {
      const res: any = await http.get(`/ticket/types/${meta.ticketTypeId}/fields`)
      formFields.value = (res.data || []).map((f: any) => {
        let options: any[] = []
        if (f.optionsJson) {
          try {
            options = JSON.parse(f.optionsJson)
          } catch {
            options = []
          }
        }
        return {
          field: f.fieldKey,
          title: f.title,
          type: mapTicketFieldType(f.fieldType),
          options,
        }
      })
    } catch {
      formFields.value = []
    }
    return
  }
  if (!meta.formId) return
  const res: any = await http.get(`/process/forms/${meta.formId}`)
  try {
    const raw = JSON.parse(res.data?.formSchema || '[]')
    // 布局组件没有 field，不是能勾选填写的数据字段
    formFields.value = (Array.isArray(raw) ? raw : [])
      .filter((f: any) => f?.field)
      .map((f: any) => ({
        ...f,
        type: mapTicketFieldType(f.type),
      }))
  } catch {
    formFields.value = []
  }
}

function mapTicketFieldType(type?: string) {
  const t = String(type || '').replace(/-/g, '').toLowerCase()
  if (t === 'ticketuserselect' || t === 'user') return 'user'
  if (t === 'ticketusersselect' || t === 'users') return 'users'
  return type || 'input'
}

/* ---------- 选中 -> 表单（只在切换选中元素时读模型，编辑过程中表单是唯一权威） ---------- */

function applySelection(element: any) {
  selected.value = element ? markRaw(element) : null
  branches.value = []
  if (!element) return

  const bo = element.businessObject || {}
  nodeForm.name = bo.name || ''
  const cfg = readConfig(bo)
  nodeForm.assigneeType = cfg.assigneeType || 'role'
  nodeForm.assigneeValue = cfg.assigneeValue || ''
  nodeForm.multiMode = cfg.multiMode || 'or'
  nodeForm.dueHours = cfg.dueHours || 0
  nodeForm.writableFields = Array.isArray(cfg.writableFields) ? cfg.writableFields : []
  nodeForm.requiredFields = Array.isArray(cfg.requiredFields)
    ? cfg.requiredFields.filter((f: string) => nodeForm.writableFields.includes(f))
    : []
  nodeForm.condition = bo.conditionExpression?.body || ''

  if (/Gateway$/.test(element.type)) {
    loadBranches(element)
  }
}

function loadBranches(gateway: any) {
  const defaultId = gateway.businessObject?.default?.id
  branches.value = (gateway.outgoing || []).map((flow: any, i: number) => {
    const body = flow.businessObject?.conditionExpression?.body || ''
    const isDefault = defaultId === flow.id
    return {
      id: flow.id,
      name: flow.businessObject?.name || '',
      targetName: flow.target?.businessObject?.name || flow.target?.id || '未连接',
      isDefault,
      color: isDefault ? DEFAULT_BRANCH_COLOR : BRANCH_COLORS[i % BRANCH_COLORS.length],
      expression: body,
      ...parseCondition(body),
    }
  })
}

/** 由条件反推一个人能看懂的分支名，直接画在连线上，多条线挤在一起时靠它区分 */
function branchLabel(branch: any) {
  if (branch.isDefault) return '默认'
  if (branch.mode === 'expr') return branch.expr ? '自定义条件' : ''
  if (!branch.field) return ''
  const field = formFields.value.find((f) => f.field === branch.field)
  const title = field?.title || branch.field
  const op = OPS.find((o) => o.value === branch.op)?.label || branch.op
  const option = fieldOptions(branch.field).find((o: any) => String(o.value) === String(branch.value))
  return `${title} ${op} ${option?.label ?? branch.value}`
}

function parseCondition(body: string) {
  const m = /^\$\{\s*([A-Za-z_]\w*)\s*(==|!=|>=|<=|>|<)\s*(.+?)\s*\}$/.exec(body || '')
  if (m) {
    let value = m[3].trim()
    if (/^'.*'$/.test(value)) value = value.slice(1, -1)
    return { mode: 'simple', field: m[1], op: m[2], value, expr: body }
  }
  return { mode: body ? 'expr' : 'simple', field: '', op: '==', value: '', expr: body || '' }
}

/* ---------- 表单 -> 模型（每个控件 change 后立即写回，不依赖 watch） ---------- */

function onAssigneeTypeChange() {
  // 不同来源的取值含义不同（角色编码 / 用户ID / 部门ID），切换时清空避免串用
  nodeForm.assigneeValue = ''
  applyTask()
}

function toggleWritableField(field: string, checked: boolean) {
  nodeForm.writableFields = checked
    ? [...new Set([...nodeForm.writableFields, field])]
    : nodeForm.writableFields.filter((f) => f !== field)
  if (!checked) {
    nodeForm.requiredFields = nodeForm.requiredFields.filter((f) => f !== field)
  }
  applyTask()
}

function toggleRequiredField(field: string, checked: boolean) {
  if (!nodeForm.writableFields.includes(field)) return
  nodeForm.requiredFields = checked
    ? [...new Set([...nodeForm.requiredFields, field])]
    : nodeForm.requiredFields.filter((f) => f !== field)
  applyTask()
}

function applyTask() {
  const element = selected.value
  if (!element || !modeler) return
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const props: Record<string, any> = { name: nodeForm.name }

  if (isUserTask.value) {
    const cfg = {
      assigneeType: nodeForm.assigneeType,
      assigneeValue: nodeForm.assigneeType === 'starter' ? '' : nodeForm.assigneeValue,
      multiMode: nodeForm.multiMode,
      dueHours: nodeForm.dueHours,
      writableFields: nodeForm.writableFields,
      requiredFields: nodeForm.requiredFields,
    }
    const documentation = moddle.create('bpmn:Documentation', { text: JSON.stringify(cfg) })
    documentation.$parent = element.businessObject
    props.documentation = [documentation]
  }
  modeling.updateProperties(element, props)
}

function applyFlowCondition() {
  const element = selected.value
  if (!element || !modeler) return
  writeFlowCondition(element, nodeForm.condition, nodeForm.name)
}

function applyBranch(branch: any) {
  const flow = modeler.get('elementRegistry').get(branch.id)
  if (!flow) return
  const body = branch.isDefault ? '' : buildExpression(branch)
  branch.expression = body
  // 没起名字的分支自动按条件命名，画到线上后就能一眼分清哪条是哪条
  if (!branch.name) branch.name = branchLabel(branch)
  writeFlowCondition(flow, body, branch.name)
}

function buildExpression(branch: any) {
  if (branch.mode === 'expr') return (branch.expr || '').trim()
  if (!branch.field) return ''
  const field = formFields.value.find((f) => f.field === branch.field)
  const numeric = field?.type === 'number'
  const raw = branch.value === undefined || branch.value === null ? '' : String(branch.value)
  const literal = numeric ? (raw === '' ? '0' : raw) : `'${raw.replace(/'/g, '')}'`
  return `\${${branch.field} ${branch.op} ${literal}}`
}

function writeFlowCondition(flow: any, body: string, name?: string) {
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const props: Record<string, any> = {}
  if (name !== undefined) props.name = name
  if (body) {
    const expr = moddle.create('bpmn:FormalExpression', { body })
    expr.$parent = flow.businessObject
    props.conditionExpression = expr
  } else {
    props.conditionExpression = undefined
  }
  modeling.updateProperties(flow, props)
}

function setDefaultBranch(branch: any) {
  const gateway = selected.value
  if (!gateway || !modeler) return
  const registry = modeler.get('elementRegistry')
  const modeling = modeler.get('modeling')
  const flow = registry.get(branch.id)

  if (branch.isDefault) {
    // 默认分支不能带条件，同一网关也只能有一条
    branches.value.forEach((b: any) => {
      if (b.id !== branch.id) b.isDefault = false
    })
    branch.name = '默认'
    branch.expression = ''
    writeFlowCondition(flow, '', branch.name)
    modeling.updateProperties(gateway, { default: flow.businessObject })
  } else {
    modeling.updateProperties(gateway, { default: undefined })
  }
  loadBranches(gateway)
}

function readConfig(bo: any) {
  const text = (bo.documentation || [])[0]?.text || ''
  try {
    return JSON.parse(text) || {}
  } catch {
    return {}
  }
}

/* ---------- 画布装饰：类型配色 + 配置徽标 ---------- */

function scheduleDecorate() {
  if (decorateHandle) return
  decorateHandle = window.requestAnimationFrame(guard(() => {
    decorateHandle = 0
    decorate()
  }))
}

function decorate() {
  if (!modeler) return
  const canvas = modeler.get('canvas')
  const overlays = modeler.get('overlays')
  modeler.get('elementRegistry').forEach((el: any) => {
    const marker = TYPE_MARKERS[el.type]
    if (marker && !canvas.hasMarker(el, marker)) canvas.addMarker(el, marker)
    if (/Gateway$/.test(el.type)) colorBranches(el, canvas)
    if (el.type !== 'bpmn:UserTask') return

    const cfg = readConfig(el.businessObject)
    const configured = cfg.assigneeType === 'starter' || !!cfg.assigneeValue
    if (configured) canvas.removeMarker(el, 'wf-unconfigured')
    else canvas.addMarker(el, 'wf-unconfigured')

    overlays.remove({ element: el, type: 'wf-assignee' })
    const text = configured ? describeAssignee(cfg.assigneeType, cfg.assigneeValue) : '未配置审批人'
    overlays.add(el, 'wf-assignee', {
      position: { bottom: -4, left: 0 },
      html: `<div class="wf-assignee-badge ${configured ? '' : 'is-missing'}">${escapeHtml(text)}</div>`,
    })
  })
}

/** 网关引出的多条线常常几乎重叠，按顺序上色是最直接的区分手段 */
function colorBranches(gateway: any, canvas: any) {
  const defaultId = gateway.businessObject?.default?.id
  const outgoing = gateway.outgoing || []
  outgoing.forEach((flow: any, i: number) => {
    const wanted = flow.id === defaultId ? 'wf-branch-default' : `wf-branch-${i % BRANCH_COLORS.length}`
    const targets = [flow, ...(flow.labels || [])]
    targets.forEach((t: any) => {
      BRANCH_MARKERS.forEach((m) => {
        if (m !== wanted) canvas.removeMarker(t, m)
      })
      if (!canvas.hasMarker(t, wanted)) canvas.addMarker(t, wanted)
    })
  })
}

function escapeHtml(s: string) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string)
  )
}

/* ---------- 发布前校验 ---------- */

const NEEDS_OUTGOING = [
  'bpmn:StartEvent',
  'bpmn:UserTask',
  'bpmn:Task',
  'bpmn:ServiceTask',
  'bpmn:ExclusiveGateway',
  'bpmn:ParallelGateway',
  'bpmn:InclusiveGateway',
]

function validate(): string[] {
  const problems: string[] = []
  const registry = modeler.get('elementRegistry')
  let userTaskCount = 0
  let hasEnd = false

  registry.forEach((el: any) => {
    // 标签是独立元素，会跟着被遍历到，按类型白名单过滤掉
    if (el.type === 'label' || el.labelTarget) return
    if (el.type === 'bpmn:EndEvent') hasEnd = true
    if (el.type === 'bpmn:UserTask') {
      userTaskCount += 1
      const cfg = readConfig(el.businessObject)
      if (cfg.assigneeType !== 'starter' && !cfg.assigneeValue) {
        problems.push(`审批节点「${el.businessObject.name || el.id}」还没有配置审批人`)
      }
      if (cfg.assigneeType === 'formField') {
        if (!hasBindSource.value) {
          problems.push(`审批节点「${el.businessObject.name || el.id}」按表单字段取审批人，但流程还没有绑定表单或工单类型`)
        } else if (!formFields.value.some((f) => f.field === cfg.assigneeValue)) {
          problems.push(`审批节点「${el.businessObject.name || el.id}」引用的人员字段「${cfg.assigneeValue}」已不存在`)
        }
      }
    }
    if (/ExclusiveGateway$/.test(el.type)) {
      const outgoing = el.outgoing || []
      if (outgoing.length > 1) {
        const defaultId = el.businessObject?.default?.id
        const missing = outgoing.filter(
          (f: any) => f.id !== defaultId && !f.businessObject?.conditionExpression?.body
        )
        if (missing.length) {
          problems.push(
            `条件分支「${el.businessObject.name || el.id}」有 ${missing.length} 条分支没有设置条件`
          )
        }
      }
    }
    if (NEEDS_OUTGOING.includes(el.type) && !(el.outgoing || []).length) {
      problems.push(`「${el.businessObject?.name || el.id}」没有向下的连线，流程会在这里断掉`)
    }
  })

  if (!userTaskCount) problems.push('流程里还没有审批节点')
  if (!hasEnd) problems.push('流程缺少结束事件')
  return problems
}

function zoomReset() {
  modeler.get('canvas').zoom('fit-viewport')
}

function removeSelection() {
  const chosen = modeler.get('selection').get()
  if (!chosen.length) return
  // 连线会随着两端节点一起被删掉，先剔除避免重复删除报错
  const ids = new Set(chosen.map((el: any) => el.id))
  const targets = chosen.filter(
    (el: any) => !el.waypoints || !(ids.has(el.source?.id) || ids.has(el.target?.id))
  )
  modeler.get('modeling').removeElements(targets)
  modeler.get('selection').select([])
}

function undo() {
  modeler.get('commandStack').undo()
}

async function persist(deploy = false) {
  if (!meta.processName || !meta.processKey) {
    ElMessage.warning('请填写流程名称和标识')
    return
  }
  if (deploy) {
    const problems = validate()
    if (problems.length) {
      try {
        await ElMessageBox.confirm(
          problems.map((p, i) => `${i + 1}. ${p}`).join('\n'),
          '流程还有以下问题',
          { type: 'warning', confirmButtonText: '仍然发布', cancelButtonText: '回去修改' }
        )
      } catch {
        return
      }
    }
  }

  const { xml } = await modeler.saveXML({ format: true })
  saving.value = true
  try {
    const res: any = await http.post('/process/defs', {
      id: meta.id,
      processName: meta.processName,
      processKey: meta.processKey,
      formId: meta.formId || 0,
      ticketTypeId: meta.ticketTypeId || 0,
      description: meta.description,
      bpmnXml: xml,
    })
    meta.id = res.data.id
    if (deploy) {
      await http.post(`/process/defs/${meta.id}/deploy`)
      ElMessage.success('已保存并发布')
    } else {
      ElMessage.success('已保存')
    }
    if (!route.params.id) router.replace(`/process/design/${meta.id}`)
  } finally {
    saving.value = false
  }
}

function saveDraft() {
  return persist(false)
}
function saveAndDeploy() {
  return persist(true)
}
</script>

<style scoped lang="scss">
.designer {
  padding: 16px;
  height: 100%;
  min-height: 560px;
  display: flex;
  flex-direction: column;
  /* backdrop-filter 会改变绝对定位基准，导致调色板错位 */
  backdrop-filter: none;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.left,
.right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 12px;
  min-height: 0;
}
.canvas-wrap {
  position: relative;
  min-height: 0;
}
.canvas {
  /* bpmn-js 的调色板与右键菜单基于容器绝对定位，必须声明 relative */
  position: relative;
  height: 100%;
  min-height: 0;
  border: 1px solid rgba(11, 61, 46, 0.12);
  border-radius: 14px;
  overflow: hidden;
  background:
    linear-gradient(#e8f2ec 1px, transparent 1px),
    linear-gradient(90deg, #e8f2ec 1px, transparent 1px);
  background-size: 22px 22px;
  background-color: #f7fcf8;
}
.legend {
  position: absolute;
  right: 14px;
  bottom: 12px;
  display: flex;
  gap: 14px;
  padding: 7px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(11, 61, 46, 0.1);
  box-shadow: 0 4px 12px rgba(11, 61, 46, 0.08);
  font-size: 12px;
  color: rgba(11, 31, 26, 0.65);
}
.legend span {
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
.dot-task { background: #e6f6ec; border: 2px solid #16794c; }
.dot-warn { background: #fdf6ec; border: 2px dashed #e6a23c; }
.dot-gw { background: #fff7e6; border: 2px solid #d99b26; }

.props {
  border: 1px solid rgba(11, 61, 46, 0.12);
  border-radius: 14px;
  background: #fff;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.props-head {
  padding: 14px 14px 10px;
  border-bottom: 1px solid rgba(11, 61, 46, 0.08);
  background: linear-gradient(180deg, #f4faf6, #fff);
  position: sticky;
  top: 0;
  z-index: 2;
}
.props-title .badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}
.badge-task { background: #e6f6ec; color: #16794c; }
.badge-gateway { background: #fff7e6; color: #b3800f; }
.badge-flow { background: #eef2fb; color: #4a63a9; }
.badge-other { background: #eef0ef; color: #5a6b64; }
.props-head small {
  display: block;
  margin-top: 6px;
  color: rgba(11, 31, 26, 0.4);
  font-size: 12px;
}
.props-form {
  padding: 14px;
}
.tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(11, 31, 26, 0.55);
}
.warn-text {
  color: #d98c1e;
}
.summary {
  margin-top: 4px;
  padding: 9px 12px;
  border-radius: 9px;
  background: #e6f6ec;
  color: #16794c;
  font-size: 12px;
  line-height: 1.6;
}
.summary.warn {
  background: #fdf6ec;
  color: #b3800f;
}
.branch {
  border: 1px solid rgba(11, 61, 46, 0.12);
  border-left: 4px solid #2f7fd1;
  border-radius: 11px;
  padding: 11px;
  margin-top: 11px;
  background: #fbfdfc;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.branch-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
.branch-no {
  font-weight: 600;
  color: #fff;
  border-radius: 6px;
  padding: 2px 8px;
}
.branch-to {
  color: rgba(11, 31, 26, 0.5);
}
.cond-row {
  display: grid;
  grid-template-columns: 1fr 96px 1fr;
  gap: 6px;
}
.expr-preview {
  font-size: 12px;
  color: #4a63a9;
  background: #eef2fb;
  border-radius: 6px;
  padding: 5px 8px;
  word-break: break-all;
}

.help {
  padding: 0 16px 24px;
}
.help h4 {
  color: #0b3d2e;
  margin: 20px 0 10px;
  padding-left: 9px;
  border-left: 3px solid #16794c;
}
.help-text {
  font-size: 13px;
  line-height: 1.9;
  color: rgba(11, 31, 26, 0.75);
  margin: 0 0 10px;
}
.help-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f4faf6;
  margin-bottom: 8px;
}
.help-name {
  font-weight: 600;
  color: #0b3d2e;
  margin-bottom: 4px;
}
.help-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(11, 31, 26, 0.7);
}
.type-group {
  margin-bottom: 14px;
}
.type-group-title {
  font-weight: 600;
  color: #0b3d2e;
  font-size: 13px;
  padding: 4px 0 6px;
  border-bottom: 1px solid rgba(11, 61, 46, 0.12);
  margin-bottom: 8px;
}
.type-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f4faf6;
  margin-bottom: 8px;
}
.type-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.type-name {
  font-weight: 600;
  color: #0b3d2e;
}
.type-en {
  font-size: 11px;
  color: rgba(11, 31, 26, 0.45);
  margin: 2px 0 5px;
}
.type-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(11, 31, 26, 0.7);
}
.help ol {
  padding-left: 18px;
  line-height: 2;
  font-size: 13px;
  color: rgba(11, 31, 26, 0.75);
}

/* ---------- bpmn-js 画布美化 ---------- */

:deep(.djs-shape .djs-visual) {
  filter: drop-shadow(0 3px 6px rgba(11, 61, 46, 0.14));
}
:deep(.djs-connection .djs-visual > path) {
  stroke-width: 2px !important;
}
:deep(.wf-user-task .djs-visual > rect) {
  fill: #e6f6ec !important;
  stroke: #16794c !important;
  stroke-width: 2.5px !important;
  rx: 12px;
  ry: 12px;
}
:deep(.wf-user-task .djs-visual > text tspan) {
  fill: #0b3d2e !important;
  font-weight: 600 !important;
  font-size: 13px !important;
}
:deep(.wf-user-task .djs-visual > path) {
  stroke: #16794c !important;
  fill: #16794c !important;
}
:deep(.wf-unconfigured .djs-visual > rect) {
  fill: #fdf6ec !important;
  stroke: #e6a23c !important;
  stroke-dasharray: 7 4 !important;
}
:deep(.wf-unconfigured .djs-visual > path) {
  stroke: #e6a23c !important;
  fill: #e6a23c !important;
}
:deep(.wf-service-task .djs-visual > rect) {
  fill: #eef2fb !important;
  stroke: #4a63a9 !important;
  stroke-width: 2.5px !important;
  rx: 12px;
  ry: 12px;
}
:deep(.wf-start .djs-visual > circle) {
  fill: #e9f3ff !important;
  stroke: #2f7fd1 !important;
  stroke-width: 3px !important;
}
:deep(.wf-end .djs-visual > circle) {
  fill: #fdeceb !important;
  stroke: #d05353 !important;
  stroke-width: 4.5px !important;
}
:deep(.wf-gateway .djs-visual > polygon) {
  fill: #fff7e6 !important;
  stroke: #d99b26 !important;
  stroke-width: 2.5px !important;
}
:deep(.wf-gateway .djs-visual > path) {
  stroke: #d99b26 !important;
}

/* 节点下方的审批人徽标 */
:deep(.wf-assignee-badge) {
  max-width: 150px;
  padding: 3px 9px;
  border-radius: 8px;
  background: #16794c;
  color: #fff;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: 0 3px 8px rgba(11, 61, 46, 0.22);
  pointer-events: none;
}
:deep(.wf-assignee-badge.is-missing) {
  background: #e6a23c;
  box-shadow: 0 3px 8px rgba(230, 162, 60, 0.28);
}

/* 左侧工具箱 */
:deep(.djs-palette) {
  border-radius: 12px;
  border-color: rgba(11, 61, 46, 0.12);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 6px 20px rgba(11, 61, 46, 0.1);
  padding: 4px;
}
:deep(.djs-palette .entry) {
  border-radius: 9px;
  margin: 2px;
  color: #3f5850;
  transition: all 0.16s ease;
}
:deep(.djs-palette .entry:hover) {
  background: #e6f6ec;
  color: #16794c;
  transform: translateY(-1px);
}
:deep(.djs-palette .separator) {
  margin: 4px 6px;
  border-color: rgba(11, 61, 46, 0.1);
}
/* 隐藏与审批流无关的元素，降低理解成本 */
:deep(.djs-palette [data-action='create.data-object']),
:deep(.djs-palette [data-action='create.data-store']),
:deep(.djs-palette [data-action='create.participant-expanded']),
:deep(.djs-palette [data-action='create.group']) {
  display: none;
}

/* 分支连线配色：多条线几乎重叠时靠颜色区分，与右侧分支卡片一一对应 */
:deep(.wf-branch-0 .djs-visual > path) { stroke: #2f7fd1 !important; }
:deep(.wf-branch-1 .djs-visual > path) { stroke: #16794c !important; }
:deep(.wf-branch-2 .djs-visual > path) { stroke: #c9741f !important; }
:deep(.wf-branch-3 .djs-visual > path) { stroke: #8b5cf6 !important; }
:deep(.wf-branch-4 .djs-visual > path) { stroke: #c0392b !important; }
:deep(.wf-branch-default .djs-visual > path) {
  stroke: #8a9a94 !important;
  stroke-dasharray: 7 5 !important;
}
:deep(.wf-branch-0 .djs-visual > text tspan) { fill: #2f7fd1 !important; }
:deep(.wf-branch-1 .djs-visual > text tspan) { fill: #16794c !important; }
:deep(.wf-branch-2 .djs-visual > text tspan) { fill: #c9741f !important; }
:deep(.wf-branch-3 .djs-visual > text tspan) { fill: #8b5cf6 !important; }
:deep(.wf-branch-4 .djs-visual > text tspan) { fill: #c0392b !important; }
:deep(.wf-branch-default .djs-visual > text tspan) { fill: #7a8a84 !important; }
:deep(.djs-connection .djs-visual > text tspan) {
  font-size: 12px !important;
  font-weight: 600 !important;
  paint-order: stroke;
  stroke: #f7fcf8;
  stroke-width: 4px;
}

/* 选中节点右侧的操作条。
   原生样式是固定 72px 宽 + inline-block 小方块，这里放大到 3 列并保持 .open 的显示方式，
   不能改成 column，否则条目会溢出容器点不到。 */
:deep(.djs-context-pad.open) {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 3px;
  width: 118px;
  padding: 6px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(11, 61, 46, 0.1);
  box-shadow: 0 8px 22px rgba(11, 61, 46, 0.16);
}
:deep(.djs-context-pad .group) {
  display: contents;
}
:deep(.djs-context-pad .entry) {
  width: 30px;
  height: 30px;
  line-height: 30px;
  font-size: 19px;
  margin: 0;
  border-radius: 8px;
  color: #3f5850;
  background: transparent;
  box-shadow: none;
  pointer-events: all;
  transition: background 0.16s ease, color 0.16s ease;
}
:deep(.djs-context-pad .entry:hover) {
  background: #16794c;
  color: #fff;
}
:deep(.djs-context-pad .entry[data-action='delete']:hover) {
  background: #d05353;
  color: #fff;
}

/* 更改节点类型的弹出菜单 */
:deep(.djs-popup) {
  border-radius: 12px;
  border: 1px solid rgba(11, 61, 46, 0.1);
  box-shadow: 0 12px 32px rgba(11, 61, 46, 0.18);
  overflow: hidden;
}
:deep(.djs-popup .djs-popup-header),
:deep(.djs-popup .entry-header) {
  background: #f4faf6;
  color: #0b3d2e;
  font-weight: 600;
}
:deep(.djs-popup .entry) {
  border-radius: 8px;
  margin: 2px 4px;
  padding: 5px 8px;
  transition: background 0.16s ease;
}
:deep(.djs-popup .entry:hover) {
  background: #e6f6ec;
  color: #16794c;
}
.node-fields {
  width: 100%;
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.node-field-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.node-field-row:last-child { border-bottom: 0; }
:deep(.bjs-powered-by) {
  display: none;
}
</style>
