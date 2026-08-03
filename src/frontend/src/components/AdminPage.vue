<script setup>
import { ref } from 'vue';
import { ElButton, ElMenu, ElMenuItem, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
import {
  Setting,
  Tools,
  Document,
  Avatar,
  DataAnalysis,
  Fold,
  Expand,
  User,
  Key
} from '@element-plus/icons-vue';
import DigitalHumanManage from './DigitalHumanManage.vue';
import SkillManage from './SkillManage.vue';
import ToolManage from './ToolManage.vue';
import DashboardPage from './DashboardPage.vue';

const props = defineProps(['username']);
const emit = defineEmits(['logout', 'navigate']);

const activeMenu = ref('dashboard');
const isCollapse = ref(false);

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

const handleMenuSelect = (index) => {
  activeMenu.value = index;
};
</script>

<template>
  <div class="admin-page">
    <!-- 左侧边栏菜单 -->
    <div :class="['admin-sidebar', { collapsed: isCollapse }]">
      <div class="sidebar-header">
        <div class="logo-section">
          <span class="logo-text" v-if="!isCollapse">Yumi 管理后台</span>
        </div>
        <div class="collapse-toggle" @click="isCollapse = !isCollapse">
          <span>{{ isCollapse ? '›' : '‹' }}</span>
        </div>
      </div>

      <div class="sidebar-nav">
        <div class="nav-section-title" v-if="!isCollapse">导航</div>
        <ElMenu
          :default-active="activeMenu"
          :collapse="isCollapse"
          @select="handleMenuSelect"
          background-color="transparent"
          text-color="rgba(255, 255, 255, 0.9)"
          active-text-color="#ffffff"
          class="admin-menu"
        >
          <ElMenuItem index="dashboard">
            <ElIcon><DataAnalysis /></ElIcon>
            <template #title>Dashboard</template>
          </ElMenuItem>
          <ElMenuItem index="digitalHuman">
            <ElIcon><Avatar /></ElIcon>
            <template #title>数字人管理</template>
          </ElMenuItem>
          <ElMenuItem index="skill">
            <ElIcon><Document /></ElIcon>
            <template #title>Skill 管理</template>
          </ElMenuItem>
          <ElMenuItem index="tool">
            <ElIcon><Tools /></ElIcon>
            <template #title>Tool 管理</template>
          </ElMenuItem>
        </ElMenu>
      </div>

      <!-- 收起侧边栏按钮 -->
      <div class="sidebar-collapse-wrapper" v-if="!isCollapse">
        <ElButton class="collapse-btn" @click="isCollapse = true">
          <ElIcon><Fold /></ElIcon>
          <span>收起侧边栏</span>
        </ElButton>
      </div>
      <div class="sidebar-collapse-wrapper" v-else>
        <ElButton class="collapse-btn" @click="isCollapse = false">
          <ElIcon><Expand /></ElIcon>
        </ElButton>
      </div>
    </div>

    <!-- 右侧主内容区 -->
    <div class="admin-main">
      <!-- 顶部栏 -->
      <div class="admin-header">
        <div class="header-left">
          <span class="breadcrumb">首页 / {{ activeMenu === 'dashboard' ? 'Dashboard' : activeMenu === 'digitalHuman' ? '数字人管理' : activeMenu === 'skill' ? 'Skill 管理' : 'Tool 管理' }}</span>
        </div>
        <div class="header-right">
          <ElButton class="back-btn" @click="goBack">
            <span>💬 返回对话</span>
          </ElButton>

          <!-- 用户信息下拉菜单 -->
          <ElDropdown class="user-dropdown" trigger="click">
            <div class="user-trigger">
              <div class="user-avatar">{{ props.username.charAt(0).toUpperCase() }}</div>
              <span class="user-name">{{ props.username }}</span>
              <span class="dropdown-arrow"></span>
            </div>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem @click="handleLogout" class="logout-item">
                  <ElIcon><Setting /></ElIcon>
                  <span>退出登录</span>
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
      </div>

      <!-- 内容区域 -->
      <div class="admin-content">
        <DashboardPage v-if="activeMenu === 'dashboard'" />
        <DigitalHumanManage v-else-if="activeMenu === 'digitalHuman'" :username="props.username" />
        <SkillManage v-else-if="activeMenu === 'skill'" :username="props.username" />
        <ToolManage v-else-if="activeMenu === 'tool'" :username="props.username" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  height: 100vh;
  background: #f1f5f9;
}

/* 左侧边栏 */
.admin-sidebar {
  width: 240px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}

.admin-sidebar.collapsed {
  width: 64px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 16px;
  flex-shrink: 0;
}

.logo-text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.collapse-toggle {
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 18px;
  flex-shrink: 0;
}

.collapse-toggle:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}

.nav-section-title {
  padding: 8px 16px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.admin-menu {
  border-right: none;
}

.admin-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 4px 8px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.9);
}

.admin-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.3);
  color: #ffffff;
}

.sidebar-collapse-wrapper {
  padding: 8px 12px 12px;
}

.collapse-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.collapse-btn .el-icon {
  font-size: 16px;
}

/* 右侧主内容 */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  background: white;
  padding: 12px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  font-size: 14px;
  color: #64748b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.back-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

/* 用户下拉菜单 */
.user-dropdown {
  cursor: pointer;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  min-width: 100px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
}

.user-trigger:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: #475569;
  font-weight: 500;
}

.dropdown-arrow {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 4px solid #94a3b8;
  margin-left: 4px;
}

/* 下拉菜单样式 */
:deep(.el-dropdown-menu__item) {
  padding: 8px 16px;
  font-size: 14px;
}

.logout-item {
  color: #ef4444;
}

.logout-item:hover {
  color: #ef4444;
  background: #fef2f2;
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>