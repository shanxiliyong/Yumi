<script setup>
import { ref } from 'vue'
import { ElButton, ElInput, ElCard, ElMessage } from 'element-plus'

const emit = defineEmits(['login-success'])

const username = ref('Yumi0')
const loading = ref(false)

const handleLogin = async () => {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }

  loading.value = true

  try {
    const response = await fetch('/api/user/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ username: username.value.trim() })
    })

    const data = await response.json()

    if (data.success) {
      // 存储用户信息
      localStorage.setItem('sessionId', data.sessionId)
      localStorage.setItem('username', data.username)
      // 用户ID使用用户名作为标识
      localStorage.setItem('userId', data.username)
      // 存储后端返回的默认会话（登录时如果没有会话，后端会自动创建一个）
      if (data.chatSessionId) {
        localStorage.setItem('currentSessionId', data.chatSessionId)
      }
      ElMessage.success('登录成功')
      emit('login-success', data)
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <ElCard class="login-card" shadow="hover">
      <div class="login-header">
        <div class="avatar">
          <img src="/Yumi.png" alt="Yumi" class="avatar-img" />
        </div>
        <h2>欢迎来到 Yumi 优秘</h2>
        <p>您的智能助手</p>
      </div>

      <div class="login-form">
        <ElInput v-model="username" placeholder="请输入用户名" class="username-input" @keyup.enter="handleLogin" />

        <ElButton type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
          登录
        </ElButton>
      </div>

      <div class="login-tip">
        <span>只需输入用户名即可登录</span>
      </div>
    </ElCard>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 20px;
  padding: 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.avatar {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.avatar-img {
  width: 80px;
  height: 80px;
  object-fit: contain;
  border-radius: 50%;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);
}

.login-header h2 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 24px;
}

.login-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.username-input {
  height: 48px;
  font-size: 16px;
}

.login-button {
  height: 48px;
  font-size: 16px;
  border-radius: 12px;
}

.login-tip {
  text-align: center;
  margin-top: 20px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>