<script setup>
import { ref } from 'vue';
import { ElButton, ElTabs, ElTabPane } from 'element-plus';
import DigitalHumanManage from './DigitalHumanManage.vue';
import SkillManage from './SkillManage.vue';
import ToolManage from './ToolManage.vue';
const props = defineProps(['username']);
const emit = defineEmits(['logout', 'navigate']);
const activeTab = ref('digitalHuman');
const goBack = () => {
  emit('navigate', 'chat');
};
const handleLogout = () => {
  localStorage.removeItem('sessionId');
  localStorage.removeItem('username');
  localStorage.removeItem('userId');
  localStorage.removeItem('currentSessionId');
  emit('logout');
};
</script>

<template>
  <div class="digital-human-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <ElButton class="back-btn" @click="goBack">← 返回对话</ElButton>
        <h2>数字人管理</h2>
      </div>
      <div class="header-right">
        <span class="user-tag">{{ props.username }}</span>
        <ElButton type="text" @click="handleLogout">退出登录</ElButton>
      </div>
    </div>

    <!-- Tab切换 -->
    <div class="tab-bar">
      <ElTabs v-model="activeTab" class="main-tabs">
        <ElTabPane label="数字人管理" name="digitalHuman"></ElTabPane>
        <ElTabPane label="Skill管理" name="skill"></ElTabPane>
        <ElTabPane label="工具管理" name="tool"></ElTabPane>
      </ElTabs>
    </div>

    <!-- 内容区域 -->
    <DigitalHumanManage v-if="activeTab === 'digitalHuman'" :username="props.username" />
    <SkillManage v-if="activeTab === 'skill'" :username="props.username" />
    <ToolManage v-if="activeTab === 'tool'" :username="props.username" />
  </div>
</template>

<style scoped>
.digital-human-page {
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

.tab-bar {
  background: white;
  border-bottom: 1px solid #e4e7ed;
  padding-left: 24px;
}

.main-tabs {
  margin: 0;
}
</style>