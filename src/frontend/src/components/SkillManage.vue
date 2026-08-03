<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { ElButton, ElInput, ElTable, ElTableColumn, ElDialog, ElMessage, ElPopconfirm, ElTag, ElTabs, ElTabPane, ElRadio, ElRadioGroup } from 'element-plus';
import { marked } from 'marked';
marked.setOptions({ gfm: true, breaks: true, headerIds: false, mangle: false });
const props = defineProps(['username']);
// ========= Skill管理 =========
const skills = ref([]);
const skillTotal = ref(0);
const skillPageNum = ref(1);
const skillPageSize = ref(10);
const skillKeyword = ref('');
const skillLoading = ref(false);
const skillDialogVisible = ref(false);
const skillFormTitle = ref('');
const skillForm = ref({
  id: null,
  name: '',
  category: '',
  version: 'v1.0.0',
  content: '',
  description: '',
  status: '1'
});
const skillCategories = ['问答', '总结', '创作', '翻译', '分析', '其他'];
const skillActivePreviewTab = ref('edit');
const skillPreviewHtml = ref('');
const skillPreviewFullscreen = ref(false);
// ========= Skill列表加载 =========
const loadSkills = async () => {
  skillLoading.value = true;
  try {
    const url = `/api/skill?pageNum=${skillPageNum.value}&pageSize=${skillPageSize.value}` + (skillKeyword.value ? `&keyword=${encodeURIComponent(skillKeyword.value)}` : '');
    const resp = await fetch(url);
    const data = await resp.json();
    if (data.success && data.data) {
      skills.value = data.data.records || [];
      skillTotal.value = data.data.total || 0;
    }
    else {
      skills.value = [];
      skillTotal.value = 0;
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
  finally {
    skillLoading.value = false;
  }
};
const handleSkillSearch = () => {
  skillPageNum.value = 1;
  loadSkills();
};
const openSkillCreate = () => {
  skillForm.value = { id: null, name: '', category: '', version: 'v1.0.0', content: '', description: '', status: 1 };
  skillFormTitle.value = '新建技能';
  skillActivePreviewTab.value = 'edit';
  computeSkillPreview();
  skillDialogVisible.value = true;
};
const openSkillEdit = async (row) => {
  try {
    const resp = await fetch(`/api/skill/${row.id}`);
    const data = await resp.json();
    if (data.success && data.data) {
      const d = data.data;
      skillForm.value = {
        id: d.id,
        name: d.name || '',
        category: d.category || '',
        version: d.version || 'v1.0.0',
        content: d.content || '',
        description: d.description || '',
        status: String(d.status || '1')
      };
      skillFormTitle.value = '编辑技能';
      skillActivePreviewTab.value = 'edit';
      computeSkillPreview();
      skillDialogVisible.value = true;
    }
    else {
      ElMessage.error('获取详情失败');
    }
  }
  catch (e) {
    ElMessage.error('加载失败：' + e.message);
  }
};
const handleSkillSave = async () => {
  if (!skillForm.value.name || !skillForm.value.name.trim()) {
    ElMessage.warning('请输入技能名称');
    return;
  }
  try {
    const body = {
      name: skillForm.value.name,
      category: skillForm.value.category,
      version: skillForm.value.version,
      content: skillForm.value.content,
      description: skillForm.value.description,
      status: skillForm.value.status,
      updateUser: props.username
    };
    let resp, data;
    if (skillForm.value.id) {
      resp = await fetch(`/api/skill/${skillForm.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    else {
      resp = await fetch('/api/skill', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    }
    data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '保存成功');
      skillDialogVisible.value = false;
      loadSkills();
    }
    else {
      ElMessage.error(data.message || '保存失败');
    }
  }
  catch (e) {
    ElMessage.error('保存失败：' + e.message);
  }
};
const handleSkillDelete = async (row) => {
  try {
    const resp = await fetch(`/api/skill/${row.id}`, { method: 'DELETE' });
    const data = await resp.json();
    if (data.success) {
      ElMessage.success(data.message || '删除成功');
      loadSkills();
    }
    else {
      ElMessage.error(data.message || '删除失败');
    }
  }
  catch (e) {
    ElMessage.error('删除失败：' + e.message);
  }
};
const stripFrontmatter = (text) => {
  if (!text || !text.startsWith('---'))
    return text;
  const endIndex = text.indexOf('---', 3);
  if (endIndex === -1)
    return text;
  return text.substring(endIndex + 3).trim();
};
const computeSkillPreview = () => {
  const text = skillForm.value.content || '';
  const cleanText = stripFrontmatter(text);
  if (!cleanText.trim())
    skillPreviewHtml.value = '<p class="muted">暂无内容</p>';
  else
    skillPreviewHtml.value = marked.parse(cleanText);
};
watch(skillActivePreviewTab, (newVal) => {
  if (newVal === 'preview') {
    computeSkillPreview();
  }
});
watch(() => skillForm.value.content, () => {
  if (skillActivePreviewTab.value === 'preview') {
    computeSkillPreview();
  }
});
onMounted(() => {
  loadSkills();
  document.addEventListener('keydown', handleKeydown);
});
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown);
});
const handleKeydown = (e) => {
  if (e.key === 'Escape') {
    if (skillPreviewFullscreen.value)
      skillPreviewFullscreen.value = false;
  }
};
</script>

<template>
  <div class="tab-content">
    <div class="toolbar">
      <ElInput v-model="skillKeyword" class="search-input" placeholder="按名称或分类搜索" @keyup.enter="handleSkillSearch"
        clearable />
      <ElButton @click="handleSkillSearch">搜索</ElButton>
      <div style="flex: 1"></div>
      <ElButton type="primary" @click="openSkillCreate">+ 新建技能</ElButton>
    </div>

    <div class="table-wrapper">
      <ElTable :data="skills" v-loading="skillLoading" border stripe style="width: 100%">
        <ElTableColumn prop="id" label="ID" width="80" />
        <ElTableColumn prop="name" label="技能名称" min-width="180" />
        <ElTableColumn prop="version" label="版本" width="100" />
        <ElTableColumn prop="description" label="描述" min-width="200">
          <template #default="{ row }">
            <span>{{ row.description || '-' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '启用' : '禁用' }}
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
            <ElButton size="small" @click="openSkillEdit(row)">编辑</ElButton>
            <ElPopconfirm title="确定删除此技能？" confirm-button-text="删除" cancel-button-text="取消"
              @confirm="handleSkillDelete(row)">
              <template #reference>
                <ElButton size="small" type="danger">删除</ElButton>
              </template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="pagination-bar">
        <span class="total">共 {{ skillTotal }} 条</span>
        <span class="page-nav" v-if="skillPageNum > 1" @click="skillPageNum--; loadSkills()">上一页</span>
        <span class="page-curr">第 {{ skillPageNum }} 页</span>
        <span class="page-nav" v-if="skillPageNum * skillPageSize < skillTotal"
          @click="skillPageNum++; loadSkills()">下一页</span>
      </div>
    </div>

    <!-- Skill对话框 -->
    <ElDialog v-model="skillDialogVisible" :title="skillFormTitle" width="900px" :close-on-click-modal="false"
      destroy-on-close @closed="skillPreviewFullscreen = false">
      <div class="form-body">
        <div class="form-row">
          <label>名称 <span class="required">*</span></label>
          <ElInput v-model="skillForm.name" placeholder="技能名称" maxlength="100" />
        </div>
        <div class="form-row">
          <label>版本</label>
          <ElInput v-model="skillForm.version" placeholder="版本号，如 v1.0.0" />
        </div>
        <div class="form-row">
          <label>状态</label>
          <ElRadioGroup v-model="skillForm.status" class="radio-group">
            <ElRadio label="1">启用</ElRadio>
            <ElRadio label="0">禁用</ElRadio>
          </ElRadioGroup>
        </div>
        <div class="form-row">
          <label>描述</label>
          <ElInput v-model="skillForm.description" type="textarea" :rows="3" placeholder="技能描述" maxlength="500" />
        </div>
        <div class="form-row prompt-row">
          <label>技能内容（支持 Markdown）</label>
          <div class="prompt-editor">
            <ElTabs v-model="skillActivePreviewTab" class="prompt-tabs">
              <ElTabPane label="编辑" name="edit">
                <ElInput v-model="skillForm.content" type="textarea" class="prompt-textarea" :rows="8"
                  placeholder="技能代码或配置内容..." />
              </ElTabPane>
              <ElTabPane label="预览" name="preview">
                <div class="preview-tab-header">
                  <span class="preview-tab-label">预览</span>
                  <ElButton link size="small" @click="skillPreviewFullscreen = !skillPreviewFullscreen">
                    <span v-if="!skillPreviewFullscreen">⛶ 全屏</span>
                    <span v-else>⤡ 退出全屏</span>
                  </ElButton>
                </div>
                <div class="preview-wrapper" :class="{ 'fullscreen-wrapper': skillPreviewFullscreen }">
                  <div v-if="skillPreviewFullscreen" class="fullscreen-exit-bar">
                    <span class="fullscreen-exit-hint">全屏预览模式</span>
                    <ElButton type="primary" size="small" round @click="skillPreviewFullscreen = false">
                      退出全屏
                    </ElButton>
                  </div>
                  <div class="prompt-preview" :class="{ 'fullscreen-preview': skillPreviewFullscreen }"
                    v-html="skillPreviewHtml"></div>
                </div>
              </ElTabPane>
            </ElTabs>
          </div>
        </div>
      </div>
      <template #footer>
        <ElButton @click="skillDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSkillSave">保存</ElButton>
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

.search-input {
  width: 320px;
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