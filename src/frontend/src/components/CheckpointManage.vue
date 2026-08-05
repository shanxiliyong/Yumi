<script setup>
import { ref, onMounted } from 'vue';
import { ElButton, ElSelect, ElOption, ElTable, ElTableColumn, ElMessage, ElTag, ElDialog, ElCard } from 'element-plus';

const userId = ref(localStorage.getItem('userId') || '');
const sessions = ref([]);
const selectedSessionId = ref(null);
const checkpoints = ref([]);
const loading = ref(false);
const detailDialogVisible = ref(false);
const selectedCheckpoint = ref(null);

const loadSessions = async () => {
  if (!userId.value) {
    ElMessage.warning('未获取到用户ID');
    return;
  }
  try {
    const response = await fetch(`/api/sessions?userId=${encodeURIComponent(userId.value)}`);
    const data = await response.json();
    if (data.success) {
      sessions.value = data.data || [];
    }
  } catch (e) {
    console.error('加载会话列表失败:', e);
    ElMessage.error('加载会话列表失败');
  }
};

const loadCheckpoints = async () => {
  if (!selectedSessionId.value) {
    ElMessage.warning('请选择会话');
    return;
  }
  loading.value = true;
  try {
    const response = await fetch(`/api/checkpoint?sessionId=${selectedSessionId.value}`);
    const data = await response.json();
    if (data.success) {
      checkpoints.value = data.data || [];
      if (checkpoints.value.length === 0) {
        ElMessage.info('该会话暂无执行轨迹数据');
      }
    } else {
      ElMessage.error(data.message || '查询失败');
    }
  } catch (e) {
    console.error('加载执行轨迹失败:', e);
    ElMessage.error('加载失败');
  } finally {
    loading.value = false;
  }
};

const viewDetail = (row) => {
  selectedCheckpoint.value = row;
  detailDialogVisible.value = true;
};

const formatStateData = (jsonStr) => {
  if (!jsonStr) return '';
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2);
  } catch {
    return jsonStr;
  }
};

const getSessionLabel = (session) => {
  return `${session.name} (ID: ${session.sessionId})`;
};

onMounted(() => {
  loadSessions();
});
</script>

<template>
  <div class="checkpoint-manage">
    <ElCard class="search-card">
      <div class="search-bar">
        <ElSelect
          v-model="selectedSessionId"
          placeholder="请选择会话"
          style="width: 500px"
          clearable
          filterable
        >
          <ElOption
            v-for="session in sessions"
            :key="session.sessionId"
            :label="getSessionLabel(session)"
            :value="session.sessionId"
          />
        </ElSelect>
        <ElButton type="primary" @click="loadCheckpoints" :loading="loading">查询</ElButton>
      </div>
    </ElCard>

    <ElCard class="table-card" v-if="checkpoints.length > 0">
      <div class="table-header">
        <h3>执行轨迹 (共 {{ checkpoints.length }} 条)</h3>
      </div>
      <ElTable :data="checkpoints" stripe style="width: 100%" v-loading="loading">
        <ElTableColumn prop="checkpointSeq" label="序号" width="80" />
        <ElTableColumn prop="checkpointId" label="Checkpoint ID" width="300" show-overflow-tooltip />
        <ElTableColumn prop="nodeId" label="当前节点" width="180" show-overflow-tooltip />
        <ElTableColumn prop="nextNodeId" label="下一节点" width="180" show-overflow-tooltip />
        <ElTableColumn prop="savedAt" label="保存时间" width="180" />
        <ElTableColumn label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <ElButton type="primary" link size="small" @click="viewDetail(row)">详情</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <div v-else-if="!loading && selectedSessionId" class="empty-state">
      <p>暂无执行轨迹数据</p>
    </div>

    <!-- 详情弹窗 -->
    <ElDialog
      v-model="detailDialogVisible"
      title="Checkpoint 详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="selectedCheckpoint" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">Checkpoint ID:</span>
          <span class="detail-value">{{ selectedCheckpoint.checkpointId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Thread ID:</span>
          <span class="detail-value">{{ selectedCheckpoint.threadId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">当前节点:</span>
          <ElTag type="primary">{{ selectedCheckpoint.nodeId || '-' }}</ElTag>
        </div>
        <div class="detail-row">
          <span class="detail-label">下一节点:</span>
          <ElTag type="success">{{ selectedCheckpoint.nextNodeId || '-' }}</ElTag>
        </div>
        <div class="detail-row">
          <span class="detail-label">保存时间:</span>
          <span class="detail-value">{{ selectedCheckpoint.savedAt }}</span>
        </div>
        <div class="detail-section">
          <div class="section-title">State Data (JSON)</div>
          <pre class="json-viewer">{{ formatStateData(selectedCheckpoint.stateDataJson) }}</pre>
        </div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.checkpoint-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  margin-bottom: 0;
}

.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.table-card {
  margin-top: 0;
}

.table-header {
  margin-bottom: 16px;
}

.table-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #909399;
  font-size: 14px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-label {
  font-weight: 600;
  color: #606266;
  min-width: 100px;
  font-size: 14px;
}

.detail-value {
  color: #303133;
  font-size: 14px;
  word-break: break-all;
}

.detail-section {
  margin-top: 16px;
}

.section-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  font-size: 14px;
}

.json-viewer {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  color: #303133;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
}
</style>