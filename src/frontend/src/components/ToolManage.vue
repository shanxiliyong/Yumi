<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { ElButton, ElInput, ElTable, ElTableColumn, ElDialog, ElMessage, ElPopconfirm, ElTag, ElSelect, ElOption } from 'element-plus';
const props = defineProps(['username']);
// ========= Tool管理 =========
const tools = ref([]);
const toolTotal = ref(0);
const toolPageNum = ref(1);
const toolPageSize = ref(10);
const toolKeyword = ref('');
const toolLoading = ref(false);
const toolDialogVisible = ref(false);
const toolFormTitle = ref('');
const toolConfigFullscreen = ref(false);
const toolForm = ref({
 id: null,
 name: '',
 type: '',
 config: '',
 permission: 'public',
 description: ''
});
const toolTypes = [
 { value: 'system', label: '系统工具' },
 { value: 'rpc', label: 'RPC接口' }
];
// ========= Tool列表加载 =========
const loadTools = async () => {
 toolLoading.value = true;
 try {
 const url = `/api/tool?pageNum=${toolPageNum.value}&pageSize=${toolPageSize.value}` + (toolKeyword.value ? `&keyword=${encodeURIComponent(toolKeyword.value)}` : '');
 const resp = await fetch(url);
 const data = await resp.json();
 if (data.success && data.data) {
 tools.value = data.data.records || [];
 toolTotal.value = data.data.total || 0;
 }
 else {
 tools.value = [];
 toolTotal.value = 0;
 }
 }
 catch (e) {
 ElMessage.error('加载失败：' + e.message);
 }
 finally {
 toolLoading.value = false;
 }
};
const handleToolSearch = () => {
 toolPageNum.value = 1;
 loadTools();
};
const openToolCreate = () => {
 toolForm.value = { id: null, name: '', type: '', config: '', permission: 'public', description: '' };
 toolFormTitle.value = '新建工具';
 toolDialogVisible.value = true;
};
const openToolEdit = async (row) => {
 try {
 const resp = await fetch(`/api/tool/${row.id}`);
 const data = await resp.json();
 if (data.success && data.data) {
 const d = data.data;
 toolForm.value = {
 id: d.id,
 name: d.name || '',
 type: d.type || '',
 config: d.config || '',
 permission: d.permission || 'public',
 description: d.description || ''
 };
 toolFormTitle.value = '编辑工具';
 toolDialogVisible.value = true;
 }
 else {
 ElMessage.error('获取详情失败');
 }
 }
 catch (e) {
 ElMessage.error('加载失败：' + e.message);
 }
};
const handleToolSave = async () => {
 if (!toolForm.value.name || !toolForm.value.name.trim()) {
 ElMessage.warning('请输入工具名称');
 return;
 }
 if (toolForm.value.config && toolForm.value.config.trim()) {
 try {
 const parsed = JSON.parse(toolForm.value.config);
 toolForm.value.config = JSON.stringify(parsed, null, 2);
 } catch (e) {
 ElMessage.error('JSON 格式错误，请检查配置内容');
 return;
 }
 }
 try {
 const body = {
 name: toolForm.value.name,
 type: toolForm.value.type,
 config: toolForm.value.config,
 permission: toolForm.value.permission,
 description: toolForm.value.description,
 updateUser: props.username
 };
 let resp, data;
 if (toolForm.value.id) {
 resp = await fetch(`/api/tool/${toolForm.value.id}`, {
 method: 'PUT',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify(body)
 });
 }
 else {
 resp = await fetch('/api/tool', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify(body)
 });
 }
 data = await resp.json();
 if (data.success) {
 ElMessage.success(data.message || '保存成功');
 toolDialogVisible.value = false;
 loadTools();
 }
 else {
 ElMessage.error(data.message || '保存失败');
 }
 }
 catch (e) {
 ElMessage.error('保存失败：' + e.message);
 }
};
const handleToolDelete = async (row) => {
 try {
 const resp = await fetch(`/api/tool/${row.id}`, { method: 'DELETE' });
 const data = await resp.json();
 if (data.success) {
 ElMessage.success(data.message || '删除成功');
 loadTools();
 }
 else {
 ElMessage.error(data.message || '删除失败');
 }
 }
 catch (e) {
 ElMessage.error('删除失败：' + e.message);
 }
};
const formatJson = () => {
 if (!toolForm.value.config || !toolForm.value.config.trim()) {
 ElMessage.warning('请先输入配置内容');
 return;
 }
 try {
 const parsed = JSON.parse(toolForm.value.config);
 toolForm.value.config = JSON.stringify(parsed, null, 2);
 ElMessage.success('JSON 格式化成功');
 } catch (e) {
 ElMessage.error('JSON 格式错误：' + e.message);
 }
};
const validateJson = () => {
 if (!toolForm.value.config || !toolForm.value.config.trim()) {
 ElMessage.warning('请先输入配置内容');
 return;
 }
 try {
 JSON.parse(toolForm.value.config);
 ElMessage.success('JSON 格式校验通过');
 } catch (e) {
 ElMessage.error('JSON 格式错误：' + e.message);
 }
};
const clearJson = () => {
 toolForm.value.config = '';
};
const loadRpcTemplate = () => {
 if (toolForm.value.type !== 'rpc') {
 ElMessage.warning('请先选择类型为 rpc');
 return;
 }
 const template = {
 interfaceName: 'com.example.order.OrderService',
 methodName: 'queryOrderDetail',
 group: 'order-group',
 version: '1.0.0',
 timeout: 5000,
 params: [
 {
 name: 'orderId',
 type: 'String',
 description: '订单ID',
 required: true,
 example: '10001'
 },
 {
 name: 'orderNo',
 type: 'String',
 description: '订单编号',
 required: false,
 example: 'ORD10001'
 }
 ],
 responseParams: [
 {
 name: 'orderId',
 type: 'String',
 description: '订单ID',
 example: '10001'
 },
 {
 name: 'orderNo',
 type: 'String',
 description: '订单编号',
 example: 'ORD10001'
 },
 {
 name: 'status',
 type: 'String',
 description: '订单状态',
 example: 'PAID'
 },
 {
 name: 'statusDesc',
 type: 'String',
 description: '状态描述',
 example: '已支付'
 },
 {
 name: 'amount',
 type: 'BigDecimal',
 description: '订单金额',
 example: '299.99'
 },
 {
 name: 'createTime',
 type: 'String',
 description: '创建时间/下单时间',
 example: '2026-07-28 10:30:00'
 },
 {
 name: 'payTime',
 type: 'String',
 description: '支付时间',
 example: '2026-07-28 10:32:00'
 },
 {
 name: 'shippingAddress',
 type: 'Object',
 description: '收货地址信息',
 example: '{...}'
 },
 {
 name: 'items',
 type: 'Array',
 description: '商品明细列表',
 example: '[...]'
 },
 {
 name: 'remark',
 type: 'String',
 description: '备注',
 example: '请尽快发货'
 }
 ]
 };
 toolForm.value.config = JSON.stringify(template, null, 2);
 ElMessage.success('已加载 RPC 模板');
};
const isRpcType = computed(() => toolForm.value.type === 'rpc');
const syncScroll = (e) => {
 const textarea = e.target;
 const highlight = textarea.previousElementSibling;
 if (highlight && highlight.classList.contains('code-highlight')) {
 highlight.scrollTop = textarea.scrollTop;
 }
};
const handleConfigInput = () => {
 // v-model is reactive, no need to manually trigger highlight
};
const isSystemType = computed(() => toolForm.value.type === 'system');
const highlightedConfig = computed(() => {
 const text = toolForm.value.config || '';
 if (!text)
 return '';
 const escaped = text
 .replace(/&/g, '&amp;')
 .replace(/"/g, '&quot;')
 .replace(/'/g, '&#39;')
 .replace(/</g, '&lt;')
 .replace(/>/g, '>');
 return escaped
 .replace(/\n/g, '<br/>')
 .replace(/("(?:[^"\\]|\\.)*"\s*:\s*)("(?:[^"\\]|\\.)*")/g, '$1<span class="json-string">$2</span>')
 .replace(/("(?:[^"\\]|\\.)*"\s*:\s*)(-?\d+\.?\d*)/g, '$1<span class="json-number">$2</span>')
 .replace(/("(?:[^"\\]|\\.)*"\s*:\s*)(true|false|null)/g, '$1<span class="json-keyword">$2</span>')
 .replace(/("(?:[^"\\]|\\.)*"\s*:)/g, '<span class="json-key">$1</span>')
 .replace(/:\s*("(?:[^"\\]|\\.)*")/g, ': <span class="json-string">$1</span>')
 .replace(/:\s*(-?\d+\.?\d*)/g, ': <span class="json-number">$1</span>')
 .replace(/:\s*(true|false|null)/g, ': <span class="json-keyword">$1</span>');
});
const getTypeLabel = (type) => {
 const found = toolTypes.find(t => t.value === type);
 return found ? found.label : (type || '-');
};
const getRpcConfigPreview = (config) => {
 try {
 const parsed = JSON.parse(config);
 const interfaceName = parsed.interfaceName || '';
 const methodName = parsed.methodName || '';
 const paramCount = parsed.params ? parsed.params.length : 0;
 const responseParamCount = parsed.responseParams ? parsed.responseParams.length : 0;
 const parts = [];
 if (interfaceName) parts.push(interfaceName.split('.').pop() || interfaceName);
 if (methodName) parts.push(methodName);
 let preview = parts.join('.');
 if (paramCount > 0 || responseParamCount > 0) {
 preview += ` (入参${paramCount}个 / 出参${responseParamCount}个)`;
 }
 return preview;
 } catch (e) {
 return '-';
 }
};
onMounted(() => {
 loadTools();
 document.addEventListener('keydown', handleKeydown);
});
onUnmounted(() => {
 document.removeEventListener('keydown', handleKeydown);
});
const handleKeydown = (e) => {
 if (e.key === 'Escape') {
 if (toolConfigFullscreen.value)
 toolConfigFullscreen.value = false;
 }
};
</script>

<template>
  <div class="tab-content">
    <div class="toolbar">
      <ElInput v-model="toolKeyword" class="search-input" placeholder="按名称或类型搜索" @keyup.enter="handleToolSearch" clearable />
      <ElButton @click="handleToolSearch">搜索</ElButton>
      <div style="flex: 1"></div>
      <ElButton type="primary" @click="openToolCreate">+ 新建工具</ElButton>
    </div>

    <div class="table-wrapper">
      <ElTable :data="tools" v-loading="toolLoading" border stripe style="width: 100%">
        <ElTableColumn prop="id" label="ID" width="80" />
        <ElTableColumn prop="name" label="工具名称" min-width="180" />
        <ElTableColumn label="类型" width="120">
          <template #default="{ row }">
            <ElTag :type="row.type === 'system' ? 'success' : row.type === 'rpc' ? 'primary' : 'info'">
              {{ getTypeLabel(row.type) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="接口信息" min-width="280">
          <template #default="{ row }">
            <span v-if="row.type === 'rpc' && row.config" class="rpc-config-preview">
              {{ getRpcConfigPreview(row.config) }}
            </span>
            <span v-else class="muted">{{ row.description || '-' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="权限" width="100">
          <template #default="{ row }">
            <ElTag :type="row.permission === 'public' ? 'success' : 'warning'">
              {{ row.permission === 'public' ? '公开' : '私有' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" width="180">
            <template #default="{ row }">
              <span>{{ row.updateTime ? row.updateTime.replace('T', ' ') : '-' }}</span>
            </template>
          </ElTableColumn>
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" @click="openToolEdit(row)">编辑</ElButton>
            <ElPopconfirm title="确定删除此工具？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleToolDelete(row)">
              <template #reference>
                <ElButton size="small" type="danger">删除</ElButton>
              </template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="pagination-bar">
        <span class="total">共 {{ toolTotal }} 条</span>
        <span class="page-nav" v-if="toolPageNum > 1" @click="toolPageNum--; loadTools()">上一页</span>
        <span class="page-curr">第 {{ toolPageNum }} 页</span>
        <span class="page-nav" v-if="toolPageNum * toolPageSize < toolTotal" @click="toolPageNum++; loadTools()">下一页</span>
      </div>
    </div>

    <!-- Tool对话框 -->
    <ElDialog v-model="toolDialogVisible" :title="toolFormTitle" width="1080px" :close-on-click-modal="false" destroy-on-close
      @closed="toolConfigFullscreen = false">
      <div class="form-body">
        <div class="form-row">
          <label>名称 <span class="required">*</span></label>
          <ElInput v-model="toolForm.name" placeholder="工具名称，如 query_order_detail" maxlength="100" />
        </div>
        <div class="form-row">
          <label>类型</label>
          <ElSelect v-model="toolForm.type" class="type-select" placeholder="请选择类型">
            <ElOption v-for="t in toolTypes" :key="t.value" :value="t.value" :label="t.label" />
          </ElSelect>
        </div>
        <div class="form-row">
          <label>权限</label>
          <ElSelect v-model="toolForm.permission" class="type-select">
            <ElOption value="public" label="公开" />
            <ElOption value="private" label="私有" />
          </ElSelect>
        </div>
        <div class="form-row">
          <label>描述</label>
          <ElInput v-model="toolForm.description" type="textarea" :rows="2" :placeholder="isRpcType ? '如：查询订单详情' : '工具描述'" maxlength="500" />
        </div>
        <div class="form-row prompt-row" v-if="!isSystemType">
          <label>配置参数</label>
          <div class="config-editor-wrapper">
            <div class="config-toolbar" v-if="!toolConfigFullscreen">
              <div class="config-toolbar-left">
                <span class="config-label" :class="{ 'rpc-label': isRpcType }">
                  {{ isRpcType ? 'RPC 接口 Schema（JSON）' : '配置参数（JSON）' }}
                </span>
              </div>
              <div class="config-actions">
                <ElButton v-if="isRpcType" size="small" @click="loadRpcTemplate">加载RPC模板</ElButton>
                <ElButton size="small" @click="formatJson">格式化</ElButton>
                <ElButton size="small" @click="validateJson">校验</ElButton>
                <ElButton size="small" @click="clearJson">清空</ElButton>
                <ElButton size="small" link @click="toolConfigFullscreen = !toolConfigFullscreen">
                  <span v-if="!toolConfigFullscreen">⛶ 全屏</span>
                  <span v-else>⤡ 退出全屏</span>
                </ElButton>
              </div>
            </div>
            <div class="preview-wrapper" :class="{ 'fullscreen-wrapper': toolConfigFullscreen }">
              <div v-if="toolConfigFullscreen" class="fullscreen-exit-bar">
                <div class="fullscreen-config-actions">
                  <ElButton v-if="isRpcType" size="small" @click="loadRpcTemplate">加载RPC模板</ElButton>
                  <ElButton size="small" @click="formatJson">格式化</ElButton>
                  <ElButton size="small" @click="validateJson">校验</ElButton>
                  <ElButton size="small" @click="clearJson">清空</ElButton>
                </div>
                <div class="fullscreen-exit-right">
                  <span class="fullscreen-exit-hint">全屏编辑模式</span>
                  <ElButton type="primary" size="small" round @click="toolConfigFullscreen = false">
                     退出全屏
                  </ElButton>
                </div>
              </div>
              <div class="code-editor" :class="{ 'fullscreen-code': toolConfigFullscreen }">
                <pre class="code-highlight" v-html="highlightedConfig || ' '"></pre>
                <textarea
                  v-model="toolForm.config"
                  spellcheck="false"
                  autocomplete="off"
                  autocorrect="off"
                  autocapitalize="off"
                  :placeholder="isRpcType ? 'RPC 接口 Schema JSON，包含 interfaceName、methodName、params 等' : '工具配置，JSON格式...'"
                  class="code-textarea"
                  :class="{ 'fullscreen-config': toolConfigFullscreen }"
                  @scroll="syncScroll"
                ></textarea>
              </div>
            </div>
          </div>
        </div>
        <div class="form-row" v-else>
          <label>配置说明</label>
          <div class="system-tool-tip">系统工具无需配置，工具名称将作为代码映射自动匹配内置实现。</div>
        </div>
      </div>
      <template #footer>
        <ElButton @click="toolDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleToolSave">保存</ElButton>
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
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
}
.search-input { width: 320px; }
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
.page-nav { cursor: pointer; color: #667eea; }
.page-nav:hover { text-decoration: underline; }
.form-body { padding: 10px 0; }
.form-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}
.form-row > label {
  width: 140px;
  text-align: right;
  font-size: 14px;
  color: #606266;
  padding-top: 8px;
  flex-shrink: 0;
}
.required { color: #f56c6c; }
.form-row > *:not(label) { flex: 1; }
.type-select { width: 100%; }
.prompt-row { align-items: stretch; }
.config-editor-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
}
.config-toolbar-left { display: flex; align-items: center; }
.config-label { font-size: 13px; font-weight: 500; color: #606266; }
.config-label.rpc-label { color: #667eea; font-weight: 600; }
.config-actions { display: flex; gap: 6px; }
.code-editor {
  position: relative;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.code-editor.focused {
  border-color: #667eea;
  box-shadow: 0 0 0 1px #667eea;
}
.code-highlight {
  margin: 0;
  padding: 12px;
  font-family: -apple-system, 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  background: #ffffff;
  white-space: pre-wrap;
  word-break: break-all;
  min-height: 280px;
  pointer-events: none;
}
.code-highlight :deep(.json-key) { color: #d6336c; }
.code-highlight :deep(.json-string) { color: #22863a; }
.code-highlight :deep(.json-number) { color: #005cc5; }
.code-highlight :deep(.json-keyword) { color: #a626a4; }
.code-textarea {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  padding: 12px;
  font-family: -apple-system, 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: transparent;
  caret-color: #303133;
  background: transparent;
  border: none;
  outline: none;
  resize: vertical;
  white-space: pre-wrap;
  word-break: break-all;
  min-height: 280px;
  box-sizing: border-box;
}
.code-textarea::selection { background: rgba(102, 126, 234, 0.25); }
.code-textarea::-webkit-scrollbar { width: 8px; height: 8px; }
.code-textarea::-webkit-scrollbar-thumb { background: #dcdfe6; border-radius: 4px; }
.code-textarea::-webkit-scrollbar-thumb:hover { background: #c0c4cc; }
.code-editor.fullscreen-code { border-radius: 0; border: none; }
.code-editor.fullscreen-code .code-highlight {
  min-height: calc(100vh - 180px);
  font-size: 14px; line-height: 1.7; padding: 20px 60px;
}
.code-editor.fullscreen-code .code-textarea {
  min-height: calc(100vh - 180px);
  font-size: 14px; line-height: 1.7; padding: 20px 60px;
}
.rpc-config-preview {
  color: #667eea;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
}
.muted { color: #909399; }
.system-tool-tip {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  padding: 12px 16px;
  color: #67c23a;
  font-size: 13px;
  line-height: 1.6;
}
.preview-wrapper { position: relative; }
.preview-wrapper.fullscreen-wrapper {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 9999;
  background: #f8f9fb;
  display: flex;
  flex-direction: column;
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
.fullscreen-config-actions { display: flex; align-items: center; gap: 8px; }
.fullscreen-exit-right { display: flex; align-items: center; gap: 12px; }
.fullscreen-exit-hint { font-size: 13px; color: #606266; }
</style>