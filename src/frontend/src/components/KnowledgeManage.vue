<script setup>
import { ref, onMounted } from 'vue';
import { ElButton, ElInput, ElTable, ElTableColumn, ElDialog, ElMessage, ElPopconfirm, ElTag, ElSelect, ElOption, ElForm, ElFormItem, ElCard, ElRow, ElCol, ElPagination, ElIcon } from 'element-plus';
import { ArrowLeft, Plus, Search, Refresh, Document, Delete } from '@element-plus/icons-vue';

const props = defineProps(['username']);

// ========= 视图状态 =========
const currentView = ref('list'); // 'list' | 'documents'
const currentKbId = ref(null);
const currentKbName = ref('');

// ========= 知识库管理 =========
const knowledgeBases = ref([]);
const kbLoading = ref(false);
const kbDialogVisible = ref(false);
const kbFormTitle = ref('');
const kbForm = ref({
  id: null,
  name: '',
  description: '',
  tenantId: 'default'
});
const kbSearchKeyword = ref('');

// ========= 文档管理 =========
const documents = ref([]);
const docLoading = ref(false);
const docFormVisible = ref(false);
const docFormTitle = ref('');
const docForm = ref({
  id: null,
  knowledgeBaseId: null,
  title: '',
  content: '',
  docType: 'text',
  source: 'manual'
});
const docSearchKeyword = ref('');
const docStatusFilter = ref('');

// ========= 统计信息 =========
const stats = ref({
  kbCount: 0,
  docCount: 0,
  activeKbCount: 0
});

// ========= 知识库列表加载 =========
const loadKnowledgeBases = async () => {
  kbLoading.value = true;
  try {
    const resp = await fetch('/api/knowledge/base/list?tenantId=default');
    const data = await resp.json();
    if (data.success && data.data) {
      knowledgeBases.value = data.data;
      stats.value.kbCount = data.data.length;
      stats.value.activeKbCount = data.data.filter(kb => kb.status === 1).length;
    } else {
      knowledgeBases.value = [];
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message);
  } finally {
    kbLoading.value = false;
  }
};

const openKbCreate = () => {
  kbForm.value = { id: null, name: '', description: '', tenantId: 'default' };
  kbFormTitle.value = '新建知识库';
  kbDialogVisible.value = true;
};

const openKbEdit = (row) => {
  kbForm.value = {
    id: row.id,
    name: row.name || '',
    description: row.description || '',
    tenantId: row.tenantId || 'default'
  };
  kbFormTitle.value = '编辑知识库';
  kbDialogVisible.value = true;
};

const handleKbSave = async () => {
  if (!kbForm.value.name || !kbForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称');
    return;
  }
  try {
    const params = new URLSearchParams({
      name: kbForm.value.name,
      description: kbForm.value.description || '',
      tenantId: kbForm.value.tenantId
    });

    let resp, data;
    if (kbForm.value.id) {
      resp = await fetch(`/api/knowledge/base/${kbForm.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
      });
    } else {
      resp = await fetch(`/api/knowledge/base?${params}`, {
        method: 'POST'
      });
    }
    data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '保存成功');
      kbDialogVisible.value = false;
      loadKnowledgeBases();
    } else {
      ElMessage.error(data.message || '保存失败');
    }
  } catch (e) {
    ElMessage.error('保存失败：' + e.message);
  }
};

const handleKbDelete = async (row) => {
  try {
    const resp = await fetch(`/api/knowledge/base/${row.id}`, { method: 'DELETE' });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '删除成功');
      loadKnowledgeBases();
    } else {
      ElMessage.error(data.message || '删除失败');
    }
  } catch (e) {
    ElMessage.error('删除失败：' + e.message);
  }
};

// ========= 文档管理 =========
const navigateToDocuments = (row) => {
  currentKbId.value = row.id;
  currentKbName.value = row.name;
  currentView.value = 'documents';
  loadDocuments(row.id);
};

const navigateBack = () => {
  currentView.value = 'list';
  currentKbId.value = null;
  currentKbName.value = '';
};

const loadDocuments = async (kbId) => {
  docLoading.value = true;
  try {
    const resp = await fetch(`/api/knowledge/document/list?knowledgeBaseId=${kbId}`);
    const data = await resp.json();
    if (data.success && data.data) {
      documents.value = data.data;
      stats.value.docCount = data.data.length;
    } else {
      documents.value = [];
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message);
  } finally {
    docLoading.value = false;
  }
};

const openDocCreate = () => {
  docForm.value = {
    id: null,
    knowledgeBaseId: currentKbId.value,
    title: '',
    content: '',
    docType: 'text',
    source: 'manual'
  };
  docFormTitle.value = '添加文档';
  docFormVisible.value = true;
};

const handleDocSave = async () => {
  if (!docForm.value.title || !docForm.value.title.trim()) {
    ElMessage.warning('请输入文档标题');
    return;
  }
  if (!docForm.value.content || !docForm.value.content.trim()) {
    ElMessage.warning('请输入文档内容');
    return;
  }
  try {
    const params = new URLSearchParams({
      knowledgeBaseId: docForm.value.knowledgeBaseId,
      title: docForm.value.title,
      content: docForm.value.content,
      docType: docForm.value.docType,
      source: docForm.value.source,
      tenantId: 'default'
    });

    const resp = await fetch(`/api/knowledge/document?${params}`, {
      method: 'POST'
    });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success('添加成功');
      docFormVisible.value = false;
      loadDocuments(currentKbId.value);
    } else {
      ElMessage.error(data.message || '添加失败');
    }
  } catch (e) {
    ElMessage.error('添加失败：' + e.message);
  }
};

const handleDocProcess = async (row) => {
  try {
    const resp = await fetch(`/api/knowledge/document/process?documentId=${row.id}`, {
      method: 'POST'
    });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success('处理成功');
      loadDocuments(currentKbId.value);
    } else {
      ElMessage.error(data.message || '处理失败');
    }
  } catch (e) {
    ElMessage.error('处理失败：' + e.message);
  }
};

const chunkDialogVisible = ref(false);
const currentDocTitle = ref('');
const chunks = ref([]);
const chunkLoading = ref(false);

const handleDocChunks = async (row) => {
  currentDocTitle.value = row.title;
  chunkDialogVisible.value = true;
  loadChunks(row.id);
};

const loadChunks = async (documentId) => {
  chunkLoading.value = true;
  try {
    const resp = await fetch(`/api/knowledge/chunk/list?documentId=${documentId}`);
    const data = await resp.json();
    if (data.success && data.data) {
      chunks.value = data.data;
    } else {
      chunks.value = [];
    }
  } catch (e) {
    ElMessage.error('加载分块失败：' + e.message);
  } finally {
    chunkLoading.value = false;
  }
};

const handleDocDelete = async (row) => {
  try {
    const resp = await fetch(`/api/knowledge/document/${row.id}`, { method: 'DELETE' });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success('删除成功');
      loadDocuments(currentKbId.value);
    } else {
      ElMessage.error(data.message || '删除失败');
    }
  } catch (e) {
    ElMessage.error('删除失败：' + e.message);
  }
};

const getStatusTag = (status) => {
  const map = {
    0: { type: 'info', text: '待处理' },
    1: { type: 'warning', text: '处理中' },
    2: { type: 'success', text: '已完成' },
    3: { type: 'danger', text: '失败' }
  };
  return map[status] || { type: 'info', text: '未知' };
};

const filteredKnowledgeBases = () => {
  if (!kbSearchKeyword.value) return knowledgeBases.value;
  return knowledgeBases.value.filter(kb => 
    kb.name.toLowerCase().includes(kbSearchKeyword.value.toLowerCase())
  );
};

const filteredDocuments = () => {
  let result = documents.value;
  if (docSearchKeyword.value) {
    result = result.filter(doc => 
      doc.title.toLowerCase().includes(docSearchKeyword.value.toLowerCase())
    );
  }
  if (docStatusFilter.value) {
    result = result.filter(doc => doc.status === parseInt(docStatusFilter.value));
  }
  return result;
};

onMounted(() => {
  loadKnowledgeBases();
});
</script>

<template>
  <div class="knowledge-manage">
    <!-- 知识库列表视图 -->
    <div v-if="currentView === 'list'" class="kb-list-view">
      <!-- 页面标题 -->
      <div class="page-header">
        <div class="header-left">
          <h2>知识库管理</h2>
          <p class="page-desc">管理所有知识库及其文档</p>
        </div>
        <div class="header-right">
          <ElInput
            v-model="kbSearchKeyword"
            placeholder="搜索知识库名称"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
          />
          <ElButton @click="loadKnowledgeBases" :icon="Refresh">刷新</ElButton>
          <ElButton type="primary" @click="openKbCreate" :icon="Plus">新建知识库</ElButton>
        </div>
      </div>

      <!-- 统计卡片 -->
      <ElRow :gutter="16" class="stats-row">
        <ElCol :span="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-icon kb-icon">📚</div>
              <div class="stat-info">
                <div class="stat-label">知识库</div>
                <div class="stat-value">{{ stats.kbCount }}</div>
              </div>
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-icon doc-icon">📄</div>
              <div class="stat-info">
                <div class="stat-label">文档数</div>
                <div class="stat-value">{{ stats.docCount }}</div>
              </div>
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-icon active-icon">✅</div>
              <div class="stat-info">
                <div class="stat-label">含文档知识库</div>
                <div class="stat-value">{{ stats.activeKbCount }}</div>
              </div>
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <!-- 知识库表格 -->
      <ElCard shadow="never" class="table-card">
        <ElTable :data="filteredKnowledgeBases()" v-loading="kbLoading" stripe>
          <ElTableColumn prop="id" label="ID" width="80" />
          <ElTableColumn label="名称" min-width="200">
            <template #default="{ row }">
              <ElButton type="primary" link @click="navigateToDocuments(row)" class="kb-name-link">
                {{ row.name }}
              </ElButton>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="description" label="描述" show-overflow-tooltip />
          <ElTableColumn label="状态" width="100">
            <template #default="{ row }">
              <ElTag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="创建时间" width="180" />
          <ElTableColumn label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <ElButton type="primary" link @click="openKbEdit(row)">编辑</ElButton>
              <ElPopconfirm title="确定删除？" @confirm="handleKbDelete(row)">
                <ElButton type="danger" link>删除</ElButton>
              </ElPopconfirm>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>
    </div>

    <!-- 文档管理视图 -->
    <div v-else class="doc-manage-view">
      <!-- 页面标题 -->
      <div class="page-header">
        <div class="header-left">
          <ElButton @click="navigateBack" :icon="ArrowLeft" class="back-btn">返回知识库</ElButton>
          <div class="title-section">
            <h2>文档管理</h2>
            <p class="page-desc">{{ currentKbName }}</p>
          </div>
        </div>
        <div class="header-right">
          <ElButton type="primary" @click="openDocCreate" :icon="Plus">上传文档</ElButton>
        </div>
      </div>

      <!-- 文档表格 -->
      <ElCard shadow="never" class="table-card">
        <div class="table-header">
          <div class="table-title">
            <h3>文档列表</h3>
            <p>支持筛选与分块管理</p>
          </div>
          <div class="table-actions">
            <ElInput
              v-model="docSearchKeyword"
              placeholder="搜索文档名称"
              :prefix-icon="Search"
              clearable
              style="width: 240px"
            />
            <ElSelect v-model="docStatusFilter" placeholder="全部状态" clearable style="width: 120px">
              <ElOption label="待处理" :value="0" />
              <ElOption label="处理中" :value="1" />
              <ElOption label="已完成" :value="2" />
              <ElOption label="失败" :value="3" />
            </ElSelect>
            <ElButton @click="loadDocuments(currentKbId)" :icon="Refresh">刷新</ElButton>
          </div>
        </div>

        <ElTable :data="filteredDocuments()" v-loading="docLoading" stripe>
          <ElTableColumn prop="id" label="ID" width="80" />
          <ElTableColumn prop="title" label="文档" min-width="200" />
          <ElTableColumn prop="source" label="来源" width="120" />
          <ElTableColumn prop="docType" label="类型" width="100" />
          <ElTableColumn prop="chunkCount" label="分块数" width="100" />
          <ElTableColumn label="状态" width="120">
            <template #default="{ row }">
              <div class="status-cell">
                <span class="status-dot" :class="'status-' + row.status"></span>
                <span>{{ getStatusTag(row.status).text }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="更新时间" width="180" />
          <ElTableColumn label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="doc-actions">
                <ElButton 
                  type="primary" 
                  link 
                  @click="handleDocChunks(row)"
                  :disabled="row.status !== 2"
                  class="action-btn"
                  title="查看分块"
                >
                  <ElIcon><Document /></ElIcon>
                </ElButton>
                <ElButton 
                  type="danger" 
                  link 
                  @click="handleDocDelete(row)"
                  class="action-btn"
                  title="删除文档"
                >
                  <ElIcon><Delete /></ElIcon>
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>

        <div class="table-footer">
          <span>共 {{ filteredDocuments().length }} 条</span>
        </div>
      </ElCard>
    </div>

    <!-- 知识库编辑对话框 -->
    <ElDialog v-model="kbDialogVisible" :title="kbFormTitle" width="500px">
      <ElForm label-width="100px">
        <ElFormItem label="知识库名称">
          <ElInput v-model="kbForm.name" placeholder="请输入知识库名称" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput v-model="kbForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="kbDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleKbSave">保存</ElButton>
      </template>
    </ElDialog>

    <!-- 添加文档表单对话框 -->
    <ElDialog v-model="docFormVisible" :title="docFormTitle" width="600px">
      <ElForm label-width="100px">
        <ElFormItem label="文档标题">
          <ElInput v-model="docForm.title" placeholder="请输入文档标题" />
        </ElFormItem>
        <ElFormItem label="文档类型">
          <ElSelect v-model="docForm.docType">
            <ElOption label="文本" value="text" />
            <ElOption label="Markdown" value="markdown" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="文档内容">
          <ElInput v-model="docForm.content" type="textarea" :rows="10" placeholder="请输入文档内容" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="docFormVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleDocSave">保存</ElButton>
      </template>
    </ElDialog>

    <!-- 分块管理对话框 -->
    <ElDialog v-model="chunkDialogVisible" :title="`分块管理 - ${currentDocTitle}`" width="800px" top="5vh">
      <ElTable :data="chunks" v-loading="chunkLoading" stripe max-height="500">
        <ElTableColumn prop="chunkIndex" label="分块序号" width="100" />
        <ElTableColumn label="分块内容">
          <template #default="{ row }">
            <div class="chunk-content">{{ row.chunkContent }}</div>
          </template>
        </ElTableColumn>
      </ElTable>
      <template #footer>
        <ElButton @click="chunkDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.knowledge-manage {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.page-desc {
  margin: 4px 0 0 0;
  color: #909399;
  font-size: 14px;
}

.header-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.back-btn {
  margin-right: 8px;
}

.title-section {
  display: flex;
  flex-direction: column;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.kb-icon {
  background: #ecf5ff;
}

.doc-icon {
  background: #f0f9ff;
}

.active-icon {
  background: #f0f9eb;
}

.stat-info {
  flex: 1;
}

.stat-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

/* 表格卡片 */
.table-card {
  border-radius: 8px;
}

.kb-name-link {
  font-size: 14px;
  font-weight: 500;
}

/* 文档管理视图 */
.doc-manage-view .page-header {
  align-items: center;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.table-title h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
}

.table-title p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.table-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.status-0 {
  background: #909399;
}

.status-1 {
  background: #e6a23c;
}

.status-2 {
  background: #67c23a;
}

.status-3 {
  background: #f56c6c;
}

.table-footer {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  color: #909399;
  font-size: 14px;
}

/* 文档操作按钮 */
.doc-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-btn {
  padding: 6px;
  border-radius: 4px;
  transition: all 0.3s;
}

.action-btn:hover {
  background: #f5f7fa;
}

.action-icon {
  font-size: 16px;
}

/* 分块内容 */
.chunk-content {
  max-height: 100px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>