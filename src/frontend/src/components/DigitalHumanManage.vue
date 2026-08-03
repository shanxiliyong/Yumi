<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue';
import { ElButton, ElInput, ElTable, ElTableColumn, ElDialog, ElMessage, ElPopconfirm, ElTag, ElSelect, ElOption, ElTabs, ElTabPane, ElRadio, ElRadioGroup } from 'element-plus';
import { marked } from 'marked';
marked.setOptions({ gfm: true, breaks: true, headerIds: false, mangle: false });
const props = defineProps(['username']);
// ========= 数字人管理 =========
const digitalHumans = ref([]);
const dhTotal = ref(0);
const dhPageNum = ref(1);
const dhPageSize = ref(10);
const dhKeyword = ref('');
const dhLoading = ref(false);
const dhDialogVisible = ref(false);
const dhFormTitle = ref('');
const dhForm = ref({
  id: null,
  code: '',
  name: '',
  agentType: 'parent',
  parentCode: '',
  avatar: '',
  description: '',
  systemPrompt: '',
  multiAgentEnabled: 0,
  streamingEnabled: 0,
  skillIds: [],
  toolIds: []
});
const availableSkills = ref([]);
const availableTools = ref([]);
// ========= Agent管理（数字人下）=========
const currentDhId = ref(null);
const currentDhCode = ref(null);
const currentDhName = ref('');
const agents = ref([]);
const agentTotal = ref(0);
const agentPageNum = ref(1);
const agentPageSize = ref(10);
const agentKeyword = ref('');
const agentLoading = ref(false);
const agentDialogVisible = ref(false);
const agentFormTitle = ref('');
const agentForm = ref({
  id: null,
  code: '',
  name: '',
  agentType: 'child',
  parentCode: '',
  systemPrompt: '',
  skillIds: [],
  toolIds: [],
  config: ''
});
const activePreviewTab = ref('edit');
const previewHtml = ref('');
const agentPreviewFullscreen = ref(false);
const dhActivePreviewTab = ref('edit');
const dhPreviewHtml = ref('');
const dhPreviewFullscreen = ref(false);
// ========= 数字人列表加载 =========
const loadDigitalHumans = async () => {
  dhLoading.value = true;
  try {
    const url = `/api/digital-human?pageNum=${dhPageNum.value}&pageSize=${dhPageSize.value}` + (dhKeyword.value ? `&keyword=${encodeURIComponent(dhKeyword.value)}` : '');
    const resp = await fetch(url);
    const data = await resp.json();
    if (data.success && data.data) {
      digitalHumans.value = data.data.records || [];
      dhTotal.value = data.data.total || 0;
    }
    else {
      digitalHumans.value = [];
      dhTotal.value = 0;
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
  finally {
    dhLoading.value = false;
  }
};
// ========= 数字人操作 =========
const handleDhSearch = () => {
  dhPageNum.value = 1;
  loadDigitalHumans();
};
const loadAvailableSkills = async () => {
  try {
    const resp = await fetch('/api/skill/all');
    const data = await resp.json();
    if (data.success && data.data) {
      availableSkills.value = data.data;
    }
  } catch (e) {
    console.error('加载技能列表失败:', e);
  }
};
const loadAvailableTools = async () => {
  try {
    const resp = await fetch('/api/tool/all');
    const data = await resp.json();
    if (data.success && data.data) {
      availableTools.value = data.data;
    }
  } catch (e) {
    console.error('加载工具列表失败:', e);
  }
};
const openDhCreate = async () => {
  await Promise.all([loadAvailableSkills(), loadAvailableTools()]);
  dhForm.value = { id: null, code: '', name: '', agentType: 'parent', parentCode: '', avatar: '', description: '', systemPrompt: '', multiAgentEnabled: 0, streamingEnabled: 0, skillIds: [], toolIds: [] };
  dhFormTitle.value = '新建数字人';
  dhActivePreviewTab.value = 'edit';
  computeDhPreview();
  dhDialogVisible.value = true;
};
const openDhEdit = async (row) => {
  try {
    await Promise.all([loadAvailableSkills(), loadAvailableTools()]);
    const resp = await fetch(`/api/digital-human/${row.id}`);
    const data = await resp.json();
    if (data.success && data.data) {
      const d = data.data;
      dhForm.value = {
        id: d.id,
        code: d.code || '',
        name: d.name || '',
        agentType: d.agentType || 'parent',
        parentCode: d.parentCode || '',
        avatar: d.avatar || '',
        description: d.description || '',
        systemPrompt: d.systemPrompt || '',
        multiAgentEnabled: d.multiAgentEnabled || 0,
        streamingEnabled: d.streamingEnabled || 0,
        skillIds: d.skillIdListParsed ? (Array.isArray(d.skillIdListParsed) ? d.skillIdListParsed : []) : [],
        toolIds: d.toolIdListParsed ? (Array.isArray(d.toolIdListParsed) ? d.toolIdListParsed : []) : []
      };
      dhFormTitle.value = '编辑数字人';
      dhActivePreviewTab.value = 'edit';
      computeDhPreview();
      dhDialogVisible.value = true;
    }
    else {
      ElMessage.error('获取详情失败');
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
};
const handleDhSave = async () => {
  if (!dhForm.value.code || !dhForm.value.code.trim()) {
    ElMessage.warning('请输入 code');
    return;
  }
  if (!dhForm.value.name || !dhForm.value.name.trim()) {
    ElMessage.warning('请输入数字人名称');
    return;
  }
  try {
    const body = {
      code: dhForm.value.code,
      name: dhForm.value.name,
      agentType: 'parent',
      parentCode: '',
      avatar: dhForm.value.avatar,
      description: dhForm.value.description,
      systemPrompt: dhForm.value.systemPrompt,
      multiAgentEnabled: dhForm.value.multiAgentEnabled,
      streamingEnabled: dhForm.value.streamingEnabled,
      skillIds: dhForm.value.skillIds || [],
      toolIds: dhForm.value.toolIds || [],
      updateUser: props.username
    };
    let resp, data;
    if (dhForm.value.id) {
      resp = await fetch(`/api/digital-human/${dhForm.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    else {
      resp = await fetch('/api/digital-human', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '保存成功');
      dhDialogVisible.value = false;
      loadDigitalHumans();
    }
    else {
      ElMessage.error(data.message || '保存失败');
    }
  }
  catch (e) {
    ElMessage.error('保存失败：' + e.message);
  }
};
const handleDhDelete = async (row) => {
  try {
    const resp = await fetch(`/api/digital-human/${row.id}`, { method: 'DELETE' });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '删除成功');
      loadDigitalHumans();
    }
    else {
      ElMessage.error(data.message || '删除失败');
    }
  }
  catch (e) {
    ElMessage.error('删除失败：' + e.message);
  }
};
// ========= 管理数字人下的Agent =========
const manageAgent = (row) => {
  currentDhId.value = row.id;
  currentDhCode.value = row.code;
  currentDhName.value = row.name;
  agentPageNum.value = 1;
  loadAgents();
};
// ========= Agent列表加载 =========
const loadAgents = async () => {
  agentLoading.value = true;
  try {
    const url = `/api/digital-human/children?pageNum=${agentPageNum.value}&pageSize=${agentPageSize.value}&parentCode=${currentDhCode.value}` + (agentKeyword.value ? `&keyword=${encodeURIComponent(agentKeyword.value)}` : '');
    const resp = await fetch(url);
    const data = await resp.json();
    if (data.success && data.data) {
      agents.value = data.data.records || [];
      agentTotal.value = data.data.total || 0;
    }
    else {
      agents.value = [];
      agentTotal.value = 0;
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
  finally {
    agentLoading.value = false;
  }
};
const handleAgentSearch = () => {
  agentPageNum.value = 1;
  loadAgents();
};
const openAgentCreate = async () => {
  await Promise.all([loadAvailableSkills(), loadAvailableTools()]);
  agentForm.value = {
    id: null,
    code: '',
    name: '',
    agentType: 'child',
    parentCode: currentDhCode.value,
    systemPrompt: '',
    skillIds: [],
    toolIds: [],
    config: ''
  };
  agentFormTitle.value = '新建 Agent';
  activePreviewTab.value = 'edit';
  computePreview();
  agentDialogVisible.value = true;
};
const openAgentEdit = async (row) => {
  try {
    await Promise.all([loadAvailableSkills(), loadAvailableTools()]);
    const resp = await fetch(`/api/digital-human/${row.id}`);
    const data = await resp.json();
    if (data.success && data.data) {
      const d = data.data;
      agentForm.value = {
        id: d.id,
        code: d.code || '',
        name: d.name || '',
        agentType: d.agentType || 'child',
        parentCode: d.parentCode || currentDhCode.value,
        systemPrompt: d.systemPrompt || '',
        skillIds: d.skillIdListParsed ? (Array.isArray(d.skillIdListParsed) ? d.skillIdListParsed : []) : [],
        toolIds: d.toolIdListParsed ? (Array.isArray(d.toolIdListParsed) ? d.toolIdListParsed : []) : [],
        config: d.config || ''
      };
      agentFormTitle.value = '编辑 Agent';
      activePreviewTab.value = 'edit';
      computePreview();
      agentDialogVisible.value = true;
    }
    else {
      ElMessage.error('获取详情失败');
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
};
const handleAgentSave = async () => {
  if (!agentForm.value.code || !agentForm.value.code.trim()) {
    ElMessage.warning('请输入 code');
    return;
  }
  if (!agentForm.value.name || !agentForm.value.name.trim()) {
    ElMessage.warning('请输入名称');
    return;
  }
  try {
    const body = {
      code: agentForm.value.code,
      name: agentForm.value.name,
      agentType: 'child',
      parentCode: currentDhCode.value,
      systemPrompt: agentForm.value.systemPrompt,
      skillIds: agentForm.value.skillIds || [],
      toolIds: agentForm.value.toolIds || [],
      config: agentForm.value.config || null,
      updateUser: props.username
    };
    let resp, data;
    if (agentForm.value.id) {
      resp = await fetch(`/api/digital-human/${agentForm.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    else {
      resp = await fetch('/api/digital-human', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '保存成功');
      agentDialogVisible.value = false;
      loadAgents();
    }
    else {
      ElMessage.error(data.message || '保存失败');
    }
  }
  catch (e) {
    ElMessage.error('保存失败：' + e.message);
  }
};
const handleAgentDelete = async (row) => {
  try {
    const resp = await fetch(`/api/digital-human/${row.id}`, { method: 'DELETE' });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '删除成功');
      loadAgents();
    }
    else {
      ElMessage.error(data.message || '删除失败');
    }
  }
  catch (e) {
    ElMessage.error('删除失败：' + e.message);
  }
};
const computePreview = () => {
  const text = agentForm.value.systemPrompt || '';
  if (!text.trim())
    previewHtml.value = '<p class="muted">暂无内容</p>';
  else
    previewHtml.value = marked.parse(text);
};
const computeDhPreview = () => {
  const text = dhForm.value.systemPrompt || '';
  if (!text.trim())
    dhPreviewHtml.value = '<p class="muted">暂无内容</p>';
  else
    dhPreviewHtml.value = marked.parse(text);
};
watch(dhActivePreviewTab, (newVal) => {
  if (newVal === 'preview') {
    computeDhPreview();
  }
});
watch(() => dhForm.value.systemPrompt, () => {
  if (dhActivePreviewTab.value === 'preview') {
    computeDhPreview();
  }
});
watch(activePreviewTab, (newVal) => {
  if (newVal === 'preview') {
    computePreview();
  }
});
watch(() => agentForm.value.systemPrompt, () => {
  if (activePreviewTab.value === 'preview') {
    computePreview();
  }
});
onMounted(() => {
  loadDigitalHumans();
  document.addEventListener('keydown', handleKeydown);
});
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown);
});
const handleKeydown = (e) => {
  if (e.key === 'Escape') {
    if (dhPreviewFullscreen.value)
      dhPreviewFullscreen.value = false;
    if (agentPreviewFullscreen.value)
      agentPreviewFullscreen.value = false;
  }
};
</script>

<template>
  <div class="tab-content">
    <!-- 数字人列表 -->
    <div v-if="!currentDhId" class="content-section">
      <div class="toolbar">
        <ElInput v-model="dhKeyword" class="search-input" placeholder="按名称搜索" @keyup.enter="handleDhSearch" clearable />
        <ElButton @click="handleDhSearch">搜索</ElButton>
        <div style="flex: 1"></div>
        <ElButton type="primary" @click="openDhCreate">+ 新建数字人</ElButton>
      </div>

      <div class="table-wrapper">
        <ElTable :data="digitalHumans" v-loading="dhLoading" border stripe style="width: 100%">
          <ElTableColumn prop="id" label="ID" width="80" />
          <ElTableColumn prop="code" label="Code" width="180" />
          <ElTableColumn prop="name" label="数字人名称" min-width="180" />
          <ElTableColumn label="多Agent开关" width="140">
            <template #default="{ row }">
              <ElTag :type="row.multiAgentEnabled === 1 ? 'success' : 'warning'">
                {{ row.multiAgentEnabled === 1 ? '开启' : '关闭' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="流式交互开关" width="140">
            <template #default="{ row }">
              <ElTag :type="row.streamingEnabled === 1 ? 'success' : 'warning'">
                {{ row.streamingEnabled === 1 ? '开启' : '关闭' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="更新时间" width="180">
            <template #default="{ row }">
              <span>{{ row.updateTime ? row.updateTime.replace('T', ' ') : '-' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="updateUser" label="更新人" width="120" />
          <ElTableColumn label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <ElButton size="small" @click="manageAgent(row)">子Agent管理</ElButton>
              <ElButton size="small" @click="openDhEdit(row)">编辑</ElButton>
              <ElPopconfirm title="确定删除此数字人？" confirm-button-text="删除" cancel-button-text="取消"
                @confirm="handleDhDelete(row)">
                <template #reference>
                  <ElButton size="small" type="danger">删除</ElButton>
                </template>
              </ElPopconfirm>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="pagination-bar">
          <span class="total">共 {{ dhTotal }} 条</span>
          <span class="page-nav" v-if="dhPageNum > 1" @click="dhPageNum--; loadDigitalHumans()">上一页</span>
          <span class="page-curr">第 {{ dhPageNum }} 页</span>
          <span class="page-nav" v-if="dhPageNum * dhPageSize < dhTotal"
            @click="dhPageNum++; loadDigitalHumans()">下一页</span>
        </div>
      </div>
    </div>

    <!-- 数字人下的Agent管理 -->
    <div v-else class="content-section">
      <div class="toolbar">
        <ElButton size="small" @click="currentDhId = null">← 返回数字人列表</ElButton>
        <span class="dh-label">当前数字人：{{ currentDhName }}</span>
        <div style="flex: 1"></div>
        <ElButton type="primary" @click="openAgentCreate">+ 新建 Agent</ElButton>
      </div>

      <div class="table-wrapper">
        <ElTable :data="agents" v-loading="agentLoading" border stripe style="width: 100%">
          <ElTableColumn prop="id" label="ID" width="80" />
          <ElTableColumn prop="code" label="Code" width="180" />
          <ElTableColumn prop="name" label="名称" min-width="180" />
          <ElTableColumn label="更新时间" width="180">
            <template #default="{ row }">
              <span>{{ row.updateTime ? row.updateTime.replace('T', ' ') : '-' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <ElButton size="small" @click="openAgentEdit(row)">编辑</ElButton>
              <ElPopconfirm title="确定删除此 Agent？" confirm-button-text="删除" cancel-button-text="取消"
                @confirm="handleAgentDelete(row)">
                <template #reference>
                  <ElButton size="small" type="danger">删除</ElButton>
                </template>
              </ElPopconfirm>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="pagination-bar">
          <span class="total">共 {{ agentTotal }} 条</span>
          <span class="page-nav" v-if="agentPageNum > 1" @click="agentPageNum--; loadAgents()">上一页</span>
          <span class="page-curr">第 {{ agentPageNum }} 页</span>
          <span class="page-nav" v-if="agentPageNum * agentPageSize < agentTotal"
            @click="agentPageNum++; loadAgents()">下一页</span>
        </div>
      </div>
    </div>

    <!-- 数字人对话框 -->
    <ElDialog v-model="dhDialogVisible" :title="dhFormTitle" width="900px" :close-on-click-modal="false"
      destroy-on-close @closed="dhPreviewFullscreen = false">
      <div class="form-body">
        <div class="form-row">
          <label>Code <span class="required">*</span></label>
          <ElInput v-model="dhForm.code" placeholder="业务编码，唯一" maxlength="50" />
        </div>
        <div class="form-row">
          <label>名称 <span class="required">*</span></label>
          <ElInput v-model="dhForm.name" placeholder="数字人名称" maxlength="100" />
        </div>
        <div class="form-row">
          <label>头像</label>
          <ElInput v-model="dhForm.avatar" placeholder="头像URL" />
        </div>
        <div class="form-row">
          <label>描述</label>
          <ElInput v-model="dhForm.description" type="textarea" :rows="3" placeholder="数字人描述" maxlength="500" />
        </div>
        <div class="form-row">
          <label>多Agent开关</label>
          <ElRadioGroup v-model="dhForm.multiAgentEnabled" class="radio-group">
            <ElRadio :label="0">关闭</ElRadio>
            <ElRadio :label="1">开启</ElRadio>
          </ElRadioGroup>
        </div>
        <div class="form-row">
          <label>流式交互开关</label>
          <ElRadioGroup v-model="dhForm.streamingEnabled" class="radio-group">
            <ElRadio :label="0">关闭</ElRadio>
            <ElRadio :label="1">开启</ElRadio>
          </ElRadioGroup>
        </div>
        <div class="form-row prompt-row">
          <label>系统提示词（支持 Markdown）</label>
          <div class="prompt-editor">
            <ElTabs v-model="dhActivePreviewTab" class="prompt-tabs">
              <ElTabPane label="编辑" name="edit">
                <ElInput v-model="dhForm.systemPrompt" type="textarea" class="prompt-textarea" :rows="8"
                  placeholder="输入系统提示词..." />
              </ElTabPane>
              <ElTabPane label="预览" name="preview">
                <div class="preview-tab-header">
                  <span class="preview-tab-label">预览</span>
                  <ElButton link size="small" @click="dhPreviewFullscreen = !dhPreviewFullscreen">
                    <span v-if="!dhPreviewFullscreen">⛶ 全屏</span>
                    <span v-else>⤡ 退出全屏</span>
                  </ElButton>
                </div>
                <div class="preview-wrapper" :class="{ 'fullscreen-wrapper': dhPreviewFullscreen }">
                  <div v-if="dhPreviewFullscreen" class="fullscreen-exit-bar">
                    <span class="fullscreen-exit-hint">全屏预览模式</span>
                    <ElButton type="primary" size="small" round @click="dhPreviewFullscreen = false">
                      ⤡ 退出全屏
                    </ElButton>
                  </div>
                  <div class="prompt-preview" :class="{ 'fullscreen-preview': dhPreviewFullscreen }"
                    v-html="dhPreviewHtml">
                  </div>
                </div>
              </ElTabPane>
            </ElTabs>
          </div>
        </div>
        <div class="form-row">
          <label>关联技能</label>
          <ElSelect v-model="dhForm.skillIds" class="type-select" multiple placeholder="请选择技能" style="width: 100%">
            <ElOption v-for="skill in availableSkills" :key="skill.id" :label="skill.name" :value="skill.id" />
          </ElSelect>
        </div>
        <div class="form-row">
          <label>关联工具</label>
          <ElSelect v-model="dhForm.toolIds" class="type-select" multiple placeholder="请选择工具" style="width: 100%">
            <ElOption v-for="tool in availableTools" :key="tool.id" :label="tool.name" :value="tool.id" />
          </ElSelect>
        </div>
      </div>
      <template #footer>
        <ElButton @click="dhDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleDhSave">保存</ElButton>
      </template>
    </ElDialog>

    <!-- Agent对话框 -->
    <ElDialog v-model="agentDialogVisible" :title="agentFormTitle" width="900px" :close-on-click-modal="false"
      destroy-on-close @closed="agentPreviewFullscreen = false">
      <div class="form-body">
        <div class="form-row">
          <label>Code <span class="required">*</span></label>
          <ElInput v-model="agentForm.code" placeholder="业务编码，唯一" maxlength="50" />
        </div>
        <div class="form-row">
          <label>名称 <span class="required">*</span></label>
          <ElInput v-model="agentForm.name" placeholder="Agent 名称" maxlength="100" />
        </div>
        <div class="form-row prompt-row">
          <label>系统提示词（支持 Markdown）</label>
          <div class="prompt-editor">
            <ElTabs v-model="activePreviewTab" class="prompt-tabs">
              <ElTabPane label="编辑" name="edit">
                <ElInput v-model="agentForm.systemPrompt" type="textarea" class="prompt-textarea" :rows="10"
                  placeholder="输入系统提示词..." />
              </ElTabPane>
              <ElTabPane label="预览" name="preview">
                <div class="preview-tab-header">
                  <span class="preview-tab-label">预览</span>
                  <ElButton link size="small" @click="agentPreviewFullscreen = !agentPreviewFullscreen">
                    <span v-if="!agentPreviewFullscreen">⛶ 全屏</span>
                    <span v-else>⤡ 退出全屏</span>
                  </ElButton>
                </div>
                <div class="preview-wrapper" :class="{ 'fullscreen-wrapper': agentPreviewFullscreen }">
                  <div v-if="agentPreviewFullscreen" class="fullscreen-exit-bar">
                    <span class="fullscreen-exit-hint">全屏预览模式</span>
                    <ElButton type="primary" size="small" round @click="agentPreviewFullscreen = false">
                      退出全屏
                    </ElButton>
                  </div>
                  <div class="prompt-preview" :class="{ 'fullscreen-preview': agentPreviewFullscreen }"
                    v-html="previewHtml" @update="computePreview"></div>
                </div>
              </ElTabPane>
            </ElTabs>
          </div>
        </div>
        <div class="form-row">
          <label>关联技能</label>
          <ElSelect v-model="agentForm.skillIds" class="type-select" multiple placeholder="请选择技能" style="width: 100%">
            <ElOption v-for="skill in availableSkills" :key="skill.id" :label="skill.name" :value="skill.id" />
          </ElSelect>
        </div>
        <div class="form-row">
          <label>关联工具</label>
          <ElSelect v-model="agentForm.toolIds" class="type-select" multiple placeholder="请选择工具" style="width: 100%">
            <ElOption v-for="tool in availableTools" :key="tool.id" :label="tool.name" :value="tool.id" />
          </ElSelect>
        </div>
        <div class="form-row">
          <label>配置（JSON Schema）</label>
          <ElInput v-model="agentForm.config" type="textarea" class="config-textarea" :rows="6"
            placeholder='{"type":"object","properties":{"topic":{"type":"string"},"wordCount":{"type":"integer"}},"required":["topic"]}' />
        </div>
      </div>
      <template #footer>
        <ElButton @click="agentDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleAgentSave">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.tab-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.content-section {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
}

.search-input {
  width: 320px;
}

.dh-label {
  color: #667eea;
  font-weight: 500;
}

.table-wrapper {
  flex: 1;
  padding: 20px 24px;
  overflow: auto;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 16px;
  color: #606266;
  font-size: 14px;
}

.page-nav {
  cursor: pointer;
  color: #667eea;
}

.page-nav:hover {
  text-decoration: underline;
}

.form-body {
  padding: 10px 0;
}

.form-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.form-row>label {
  width: 140px;
  text-align: right;
  font-size: 14px;
  color: #606266;
  padding-top: 8px;
  flex-shrink: 0;
}

.required {
  color: #f56c6c;
}

.form-row>*:not(label) {
  flex: 1;
}

.type-select {
  width: 100%;
}

.prompt-row {
  align-items: stretch;
}

.prompt-tabs {
  flex: 1;
}

.prompt-textarea :deep(.el-textarea__inner) {
  font-family: -apple-system, monospace;
  font-size: 14px;
  line-height: 1.7;
}

.prompt-preview {
  background: #f8f9fb;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px 20px;
  min-height: 220px;
  max-height: 500px;
  overflow: auto;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  transition: all 0.3s ease;
}

.preview-wrapper {
  position: relative;
}

.preview-wrapper.fullscreen-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: #f8f9fb;
  display: flex;
  flex-direction: column;
}

.preview-wrapper.fullscreen-wrapper .prompt-preview.fullscreen-preview {
  flex: 1;
  width: 100%;
  max-height: none;
  min-height: 0;
  border-radius: 0;
  border: none;
  padding: 20px 60px 40px 60px;
  font-size: 16px;
  line-height: 2;
  overflow: auto;
}

.fullscreen-exit-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  gap: 16px;
}

.fullscreen-exit-hint {
  font-size: 13px;
  color: #606266;
}

.preview-tab-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 4px 0 6px 0;
}

.preview-tab-label {
  margin-right: auto;
  font-size: 13px;
  color: #909399;
}

.muted {
  color: #909399;
}
</style>