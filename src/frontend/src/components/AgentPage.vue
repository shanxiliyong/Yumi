<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElButton, ElInput, ElTable, ElTableColumn, ElDialog, ElMessage, ElPopconfirm, ElTag, ElSelect, ElOption, ElTabs, ElTabPane } from 'element-plus'
import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: true, headerIds: false, mangle: false })

const props = defineProps(['username'])
const emit = defineEmits(['logout', 'navigate'])

const agents = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)

// 表单
const dialogVisible = ref(false)
const formTitle = ref('')
const form = ref({
  id: null,
  code: '',
  name: '',
  agentType: 'child',
  parentCode: '',
  systemPrompt: ''
})
const formRules = {
  code: [{ required: true, message: '请输入 code', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  parentCode: [{ required: true, message: '请选择父数字人', trigger: 'change' }]
}
const formRef = ref(null)

// 父数字人列表
const parentOptions = ref([])

// 根据 code 获取父数字人名称
const getParentName = (code) => {
  if (!code) return '-'
  const parent = parentOptions.value.find(p => p.code === code)
  return parent ? parent.name : code
}

// markdown 预览
const activePreviewTab = ref('edit') // 'edit' | 'preview'
const previewHtml = computed(() => {
  const text = form.value.systemPrompt || ''
  if (!text.trim()) return '<p class="muted">暂无内容，在左侧输入 Markdown 后预览</p>'
  return marked.parse(text)
})

// ========= 数据加载 =========
const loadParentOptions = async () => {
  try {
    const resp = await fetch('/api/digital-human/all')
    const data = await resp.json()
    if (data.success && data.data) {
      parentOptions.value = data.data
    }
  } catch (e) {
    console.error('加载父数字人列表失败:', e)
  }
}

const loadAgents = async () => {
  loading.value = true
  try {
    const url = `/api/digital-human/children?pageNum=${pageNum.value}&pageSize=${pageSize.value}`
      + (keyword.value ? `&keyword=${encodeURIComponent(keyword.value)}` : '')
    const resp = await fetch(url)
    const data = await resp.json()
    if (data.success && data.data) {
      agents.value = data.data.records || []
      total.value = data.data.total || 0
    } else {
      agents.value = []
      total.value = 0
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  loadAgents()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
  loadAgents()
}

const handlePageChange = (page) => {
  pageNum.value = page
  loadAgents()
}

// ========= 新建 =========
const openCreate = async () => {
  await loadParentOptions()
  form.value = { id: null, code: '', name: '', agentType: 'child', parentCode: '', systemPrompt: '' }
  formTitle.value = '新建 Agent'
  activePreviewTab.value = 'edit'
  dialogVisible.value = true
}

// ========= 编辑 =========
const openEdit = async (row) => {
  try {
    await loadParentOptions()
    const resp = await fetch(`/api/digital-human/${row.id}`)
    const data = await resp.json()
    if (data.success && data.data) {
      const d = data.data
      form.value = {
        id: d.id,
        code: d.code || '',
        name: d.name || '',
        agentType: d.agentType || 'child',
        parentCode: d.parentCode || '',
        systemPrompt: d.systemPrompt || ''
      }
      formTitle.value = '编辑 Agent'
      activePreviewTab.value = 'edit'
      dialogVisible.value = true
    } else {
      ElMessage.error('获取详情失败')
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  }
}

// ========= 保存 =========
const handleSave = async () => {
  if (!form.value.code || !form.value.code.trim()) {
    ElMessage.warning('请输入 code')
    return
  }
  if (!form.value.name || !form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!form.value.parentCode || !form.value.parentCode.trim()) {
    ElMessage.warning('请选择父数字人')
    return
  }
  try {
    const body = {
      code: form.value.code,
      name: form.value.name,
      agentType: 'child',
      parentCode: form.value.parentCode,
      systemPrompt: form.value.systemPrompt,
      updateUser: props.username
    }
    let resp, data
    if (form.value.id) {
      resp = await fetch(`/api/digital-human/${form.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
    } else {
      resp = await fetch('/api/digital-human', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
    }
    data = await resp.json()
    if (data.success) {
      ElMessage.success(data.message || '保存成功')
      dialogVisible.value = false
      loadAgents()
    } else {
      ElMessage.error(data.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + e.message)
  }
}

// ========= 删除 =========
const handleDelete = async (row) => {
  try {
    const resp = await fetch(`/api/digital-human/${row.id}`, { method: 'DELETE' })
    const data = await resp.json()
    if (data.success) {
      ElMessage.success(data.message || '删除成功')
      loadAgents()
    } else {
      ElMessage.error(data.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败：' + e.message)
  }
}

// ========= 导航 =========
const goBack = () => {
  emit('navigate', 'chat')
}

const handleLogout = () => {
  localStorage.removeItem('sessionId')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  localStorage.removeItem('currentSessionId')
  emit('logout')
}

onMounted(() => {
  loadParentOptions()
  loadAgents()
})
</script>

<template>
  <div class="agent-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <ElButton class="back-btn" @click="goBack">← 返回对话</ElButton>
        <h2>Agent 管理</h2>
      </div>
      <div class="header-right">
        <span class="user-tag">{{ props.username }}</span>
        <ElButton type="text" @click="handleLogout">退出登录</ElButton>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="toolbar">
      <ElInput v-model="keyword" class="search-input" placeholder="按名称或 code 搜索" @keyup.enter="handleSearch"
        clearable />
      <ElButton @click="handleSearch">搜索</ElButton>
      <div style="flex: 1"></div>
      <ElButton type="primary" @click="openCreate">+ 新建 Agent</ElButton>
    </div>

    <!-- 表格 -->
    <div class="table-wrapper">
      <ElTable :data="agents" v-loading="loading" border stripe style="width: 100%">
        <ElTableColumn prop="id" label="ID" width="80" />
        <ElTableColumn prop="code" label="Code" width="180">
          <template #default="{ row }">
            <span class="code-cell">{{ row.code }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="name" label="名称" min-width="180" />
        <ElTableColumn prop="parentCode" label="父数字人" width="180">
          <template #default="{ row }">
            <span class="parent-code">{{ getParentName(row.parentCode) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="系统提示词" min-width="260">
          <template #default="{ row }">
            <div class="prompt-cell">{{ row.systemPrompt || '-' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="updateUser" label="更新人" width="120" />
        <ElTableColumn prop="updateTime" label="更新时间" width="180" />
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" @click="openEdit(row)">编辑</ElButton>
            <ElPopconfirm title="确定删除此 Agent？" confirm-text="删除" cancel-text="取消" @confirm="handleDelete(row)">
              <ElButton size="small" type="danger">删除</ElButton>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
      </ElTable>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="total">共 {{ total }} 条</span>
        <ElSelect v-model="pageSize" class="size-select" @change="handleSizeChange">
          <ElOption :value="10" label="10 条/页" />
          <ElOption :value="20" label="20 条/页" />
          <ElOption :value="50" label="50 条/页" />
        </ElSelect>
        <span class="page-nav" v-if="pageNum > 1" @click="handlePageChange(pageNum - 1)">上一页</span>
        <span class="page-curr">第 {{ pageNum }} 页</span>
        <span class="page-nav" v-if="pageNum * pageSize < total" @click="handlePageChange(pageNum + 1)">下一页</span>
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <ElDialog v-model="dialogVisible" :title="formTitle" width="900px" :close-on-click-modal="false" destroy-on-close>
      <div class="form-body">
        <div class="form-row">
          <label>Code <span class="required">*</span></label>
          <ElInput v-model="form.code" placeholder="业务编码，唯一" maxlength="50" />
        </div>
        <div class="form-row">
          <label>名称 <span class="required">*</span></label>
          <ElInput v-model="form.name" placeholder="Agent 名称" maxlength="100" />
        </div>
        <div class="form-row">
          <label>父数字人 <span class="required">*</span></label>
          <ElSelect v-model="form.parentCode" class="type-select" placeholder="请选择父数字人">
            <ElOption v-for="item in parentOptions" :key="item.code" :label="item.name + ' (' + item.code + ')'"
              :value="item.code" />
          </ElSelect>
        </div>
        <div class="form-row prompt-row">
          <label>系统提示词（支持 Markdown）</label>
          <div class="prompt-editor">
            <ElTabs v-model="activePreviewTab" class="prompt-tabs">
              <ElTabPane label="编辑" name="edit">
                <ElInput v-model="form.systemPrompt" type="textarea" class="prompt-textarea" :rows="10"
                  placeholder="输入系统提示词，支持 Markdown 语法...&#10;例如：&#10;## 角色&#10;你是 Yumi 优秘，一个友好的 AI 助手。&#10;&#10;## 能力&#10;- 回答问题&#10;- **重点内容**用加粗标注" />
              </ElTabPane>
              <ElTabPane label="Markdown 预览" name="preview">
                <div class="prompt-preview" v-html="previewHtml"></div>
              </ElTabPane>
            </ElTabs>
          </div>
        </div>
      </div>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.agent-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-header .header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-header h2 {
  font-size: 20px;
  margin: 0;
  color: white;
}

.page-header .header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  color: white;
}

.page-header .back-btn {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.page-header .back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.page-header .user-tag {
  font-size: 14px;
  opacity: 0.9;
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

.table-wrapper {
  flex: 1;
  padding: 20px 24px;
  overflow: auto;
}

.code-cell {
  font-family: monospace;
  color: #667eea;
}

.parent-code {
  color: #67c23a;
  font-weight: 500;
}

.prompt-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #606266;
  font-size: 13px;
}

.list-cell {
  color: #909399;
  font-size: 13px;
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

.size-select {
  width: 130px;
}

.page-nav {
  cursor: pointer;
  color: #667eea;
}

.page-nav:hover {
  text-decoration: underline;
}

/* 表单 */
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

.prompt-tabs :deep(.el-tabs__item) {
  font-size: 14px;
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
}

.prompt-preview h1,
.prompt-preview h2,
.prompt-preview h3,
.prompt-preview h4 {
  margin: 16px 0 8px;
  color: #303133;
}

.prompt-preview h1 {
  font-size: 22px;
}

.prompt-preview h2 {
  font-size: 18px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 4px;
}

.prompt-preview h3 {
  font-size: 16px;
}

.prompt-preview p {
  margin: 8px 0;
}

.prompt-preview ul,
.prompt-preview ol {
  padding-left: 24px;
  margin: 8px 0;
}

.prompt-preview code {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 13px;
  color: #c7254e;
}

.prompt-preview pre {
  background: #2d2d2d;
  color: #f8f8f2;
  padding: 12px 16px;
  border-radius: 4px;
  overflow-x: auto;
}

.prompt-preview pre code {
  background: transparent;
  color: inherit;
  padding: 0;
}

.prompt-preview blockquote {
  border-left: 4px solid #667eea;
  padding: 4px 16px;
  margin: 12px 0;
  color: #606266;
  background: #f8f9fb;
}

.prompt-preview strong {
  color: #303133;
  font-weight: 600;
}

.prompt-preview .muted {
  color: #c0c4cc;
  font-style: italic;
}
</style>