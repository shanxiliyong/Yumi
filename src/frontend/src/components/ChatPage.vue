<script setup>
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { ElButton, ElInput, ElAvatar, ElMessage, ElSelect, ElOption, ElDrawer, ElPopconfirm, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem, ElDialog } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
  headerIds: false,
  mangle: false
})

const props = defineProps(['username'])
const emit = defineEmits(['logout', 'navigate'])

const navigateToAdmin = () => {
  emit('navigate', 'admin')
}

const messages = ref([])
const inputMessage = ref('')
const isSending = ref(false)
const chatContainer = ref(null)
const userId = ref(localStorage.getItem('userId') || '')
const sessionId = ref(localStorage.getItem('currentSessionId') ? parseInt(localStorage.getItem('currentSessionId')) : null)
const currentSessionName = ref('')

const digitalHumans = ref([])
const selectedDigitalHumanId = ref(null)
const sessions = ref([])
const showSidebar = ref(true)
const editingSessionId = ref(null)
const editingSessionName = ref('')
const showCreateDialog = ref(false)
const dialogDigitalHumanId = ref(null)

const isRecording = ref(false)
const speechSupported = ref(false)
const speechError = ref('')
const recognition = ref(null)
const transcript = ref('')

const initSpeechRecognition = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (SpeechRecognition) {
    speechSupported.value = true
    recognition.value = new SpeechRecognition()
    recognition.value.lang = 'zh-CN'
    recognition.value.continuous = true
    recognition.value.interimResults = true

    let recordingStartValue = ''

    recognition.value.onresult = (event) => {
      let text = ''
      for (let i = 0; i < event.results.length; i++) {
        text += event.results[i][0].transcript
      }
      const prefix = recordingStartValue ? (recordingStartValue + ' ') : ''
      inputMessage.value = prefix + text
      transcript.value = text
    }

    recognition.value.onstart = () => {
      recordingStartValue = inputMessage.value
    }

    recognition.value.onerror = (event) => {
      speechError.value = '识别出错: ' + event.error
      isRecording.value = false
      ElMessage.warning('语音识别出错，请重试或使用键盘输入')
    }

    recognition.value.onend = () => {
      isRecording.value = false
    }
  } else {
    speechSupported.value = false
  }
}

const startRecording = () => {
  if (!recognition.value) {
    ElMessage.error('您的浏览器不支持语音识别，请使用 Chrome 或 Edge')
    return
  }
  transcript.value = ''
  try {
    recognition.value.start()
    isRecording.value = true
    speechError.value = ''
  } catch (e) {
    console.error('录音启动失败:', e)
  }
}

const stopRecording = () => {
  if (recognition.value) {
    recognition.value.stop()
    isRecording.value = false
  }
}

onUnmounted(() => {
  if (recognition.value) recognition.value.stop()
})

const loadDigitalHumans = async () => {
  try {
    const response = await fetch('/api/digital-human/all')
    const data = await response.json()
    if (data.success) {
      digitalHumans.value = data.data || []
      if (digitalHumans.value.length > 0 && !selectedDigitalHumanId.value) {
        selectedDigitalHumanId.value = digitalHumans.value[0].id
      }
    }
  } catch (e) { console.error('加载数字人失败:', e) }
}

const loadSessions = async () => {
  if (!userId.value) return
  try {
    const response = await fetch(`/api/sessions?userId=${encodeURIComponent(userId.value)}`)
    const data = await response.json()
    if (data.success) {
      sessions.value = data.data
      console.log('会话列表:', sessions.value)
      if (sessions.value.length > 0) {
        const storedSessionId = sessionId.value
        const sessionExists = sessions.value.some(s => s.sessionId === storedSessionId)
        if (!sessionExists) {
          await switchSession(sessions.value[0])
        }
      }
    }
  } catch (e) { console.error('加载会话失败:', e) }
}

const openCreateDialog = () => {
  dialogDigitalHumanId.value = selectedDigitalHumanId.value || (digitalHumans.value.length > 0 ? digitalHumans.value[0].id : null)
  showCreateDialog.value = true
}

const confirmCreateSession = async () => {
  if (!dialogDigitalHumanId.value) {
    ElMessage.warning('请选择数字人')
    return
  }
  try {
    const dh = digitalHumans.value.find(d => d.id === dialogDigitalHumanId.value)

    // 调用后端 ID 生成器获取序号
    const idResponse = await fetch(`/api/id-generator/next?code=${encodeURIComponent(userId.value)}`, { method: 'POST' })
    const idData = await idResponse.json()
    const seqNo = idData.success ? idData.value : Date.now()

    const sessionName = dh ? `${dh.name}-${seqNo}` : `新对话-${seqNo}`

    const url = `/api/sessions?userId=${encodeURIComponent(userId.value)}&name=${encodeURIComponent(sessionName)}&digitalHumanId=${dialogDigitalHumanId.value}`

    const response = await fetch(url, { method: 'POST' })
    const data = await response.json()
    if (data.success) {
      sessionId.value = data.sessionId
      localStorage.setItem('currentSessionId', data.sessionId.toString())

      selectedDigitalHumanId.value = dialogDigitalHumanId.value

      currentSessionName.value = sessionName
      messages.value = [{ id: 1, type: 'bot', content: '你好！我是 Yumi 优秘，很高兴为你服务。' }]
      await loadSessions()
      showCreateDialog.value = false
      ElMessage.success('创建会话成功')
    }
  } catch (e) { ElMessage.error('创建会话失败') }
}

const updateSessionName = async () => {
  if (!editingSessionId.value || !editingSessionName.value.trim()) return
  try {
    const response = await fetch(`/api/sessions/${editingSessionId.value}?name=${encodeURIComponent(editingSessionName.value.trim())}`, { method: 'PUT' })
    const data = await response.json()
    if (data.success) {
      if (editingSessionId.value === sessionId.value) currentSessionName.value = editingSessionName.value.trim()
      await loadSessions()
      ElMessage.success('更新成功')
    }
  } catch (e) { ElMessage.error('更新失败') }
  editingSessionId.value = null
  editingSessionName.value = ''
}

const deleteSession = async (sessionIdToDelete) => {
  if (sessionIdToDelete === sessionId.value) { ElMessage.warning('不能删除当前会话'); return }
  try {
    const response = await fetch(`/api/sessions/${sessionIdToDelete}`, { method: 'DELETE' })
    const data = await response.json()
    if (data.success) { await loadSessions(); ElMessage.success('删除成功') }
  } catch (e) { ElMessage.error('删除失败') }
}

const loadMessagesFromBackend = async (sessionId) => {
  try {
    const requestBody = {
      userId: userId.value,
      sessionId: sessionId,
      agentType: 'single'
    }
    const response = await fetch('/api/sessions/messages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    })
    const data = await response.json()
    console.log('从 checkpoint 获取的消息:', data)
    if (data.success && data.data && data.data.length > 0) {
      messages.value = data.data.map((m, index) => ({
        id: Date.now() + index,
        type: m.type || 'bot',
        content: m.content || ''
      }))
      return true
    }
  } catch (e) {
    console.error('获取消息失败:', e)
  }
  messages.value = [{ id: 1, type: 'bot', content: '你好！我是 Yumi 优秘，很高兴为你服务。有什么我可以帮助你的吗？' }]
  return false
}

const switchSession = async (session) => {
  sessionId.value = session.sessionId
  currentSessionName.value = session.name
  localStorage.setItem('currentSessionId', session.sessionId.toString())
  if (session.digitalHumanId) {
    selectedDigitalHumanId.value = session.digitalHumanId
  }
  // 切换会话时，始终从后端加载该会话的消息历史
  await loadMessagesFromBackend(session.sessionId)
  await nextTick(); scrollToBottom()
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isSending.value) return
  if (!sessionId.value) { ElMessage.warning('请先创建会话'); return }

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  messages.value.push({ id: Date.now(), type: 'user', content: userMessage })
  scrollToBottom()

  isSending.value = true
  const loadingId = Date.now() + 1
  messages.value.push({ id: loadingId, type: 'loading' })
  scrollToBottom()

  let requestType = 'stream'

  try {
    if (requestType === 'stream') {
      await sendStreamMessage(userMessage)
    } else {
      await sendNonStreamMessage(userMessage)
    }
  } catch (e) {
    messages.value = messages.value.filter(m => m.id !== loadingId)
    messages.value.push({ id: Date.now() + 2, type: 'bot', content: '抱歉，处理请求时出现错误。' })
    ElMessage.error('发送失败')
  } finally { isSending.value = false; scrollToBottom() }
}

const sendStreamMessage = async (userMessage) => {
  const requestBody = {
    userId: userId.value,
    sessionId: sessionId.value,
    message: userMessage
  }
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(requestBody)
  })

  if (!response.ok) throw new Error('请求失败')

  messages.value = messages.value.filter(m => m.type !== 'loading')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let botMessage = ''
  const botMessageId = Date.now() + 2
  messages.value.push({ id: botMessageId, type: 'bot', content: '' })
  let buffer = ''
  let debugCount = 0

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const content = line.slice(5).replace(/data:/g, '')
        botMessage += content
        debugCount++
        if (debugCount <= 50) {
          console.log(`SSE chunk[${debugCount}]: "${content.replace(/\n/g, '\\n')}"`)
        }
        const msgIndex = messages.value.findIndex(m => m.id === botMessageId)
        if (msgIndex !== -1) messages.value[msgIndex].content = botMessage
        scrollToBottom()
      }
    }
  }

  if (buffer && buffer.startsWith('data:')) {
    const content = buffer.slice(5).replace(/data:/g, '')
    botMessage += content
    const msgIndex = messages.value.findIndex(m => m.id === botMessageId)
    if (msgIndex !== -1) messages.value[msgIndex].content = botMessage
    scrollToBottom()
  }

  console.log('Final message length:', botMessage.length)
  console.log('Final message:', botMessage.substring(0, 1000))
}

const sendNonStreamMessage = async (userMessage) => {
  const requestBody = {
    userId: userId.value,
    sessionId: sessionId.value,
    message: userMessage
  }
  const response = await fetch('/api/chat/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(requestBody)
  })

  if (!response.ok) throw new Error('请求失败')

  const data = await response.json()
  messages.value = messages.value.filter(m => m.type !== 'loading')

  if (data.success) {
    messages.value.push({ id: Date.now() + 2, type: 'bot', content: data.content })
  } else {
    messages.value.push({ id: Date.now() + 2, type: 'bot', content: data.message || '请求失败' })
  }
}

const handleLogout = () => {
  localStorage.removeItem('sessionId'); localStorage.removeItem('username'); localStorage.removeItem('userId'); localStorage.removeItem('currentSessionId')
  emit('logout')
}

const initApp = async () => {
  initSpeechRecognition()
  await loadDigitalHumans()
  await loadSessions()
  if (sessions.value.length > 0) {
    const currentSession = sessions.value.find(s => s.sessionId === sessionId.value)
    if (currentSession) {
      currentSessionName.value = currentSession.name
      if (!messages.value || messages.value.length === 0) {
        await loadMessagesFromBackend(sessionId.value)
      }
    } else {
      await switchSession(sessions.value[0])
    }
  } else {
    sessionId.value = null
    currentSessionName.value = ''
    messages.value = []
    localStorage.removeItem('currentSessionId')
  }
  scrollToBottom()
}

const renderMarkdown = (content) => {
  if (!content) return ''

  try {
    let text = content

    text = text.replace(/\\n/g, '\n')

    const hasGoodFormat = text.includes('\n\n')

    if (!hasGoodFormat) {
      text = text.replace(/([\u4e00-\u9fa5a-zA-Z0-9。！？、，：；])\s*(#{1,6}\s)/g, '$1\n\n$2')
      text = text.replace(/([\u4e00-\u9fa5a-zA-Z0-9。！？、，：；])\s*(-\s)/g, '$1\n$2')
      text = text.replace(/([\u4e00-\u9fa5a-zA-Z0-9。！？、，：；])\s*(\d+\.\s)/g, '$1\n$2')
      text = text.replace(/\n{3,}/g, '\n\n')
    }

    text = text.trim()
    console.log('Has good format:', hasGoodFormat)
    console.log('Processed markdown:', text.substring(0, 1000))

    return marked(text)
  } catch (e) {
    console.error('Markdown render error:', e)
    return content
  }
}

onMounted(() => {
  initApp()
})
</script>

<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <div :class="['sidebar', { collapsed: !showSidebar }]">
      <div class="sidebar-header">
        <div class="logo-section">
          <img class="logo-avatar" src="/Yumi.png" alt="Yumi" />
          <span class="logo-text" v-if="showSidebar">Yumi 优秘</span>
        </div>
      </div>

      <div class="sidebar-actions" v-if="showSidebar">
        <ElButton type="primary" class="new-chat-btn" @click="openCreateDialog">
          <span class="btn-icon">+</span>
          <span class="btn-text">新对话</span>
        </ElButton>
      </div>

      <div class="sidebar-divider" v-if="showSidebar"></div>

      <!-- 管理后台按钮 -->
      <div class="sidebar-admin" v-if="showSidebar">
        <ElButton class="admin-btn" @click="navigateToAdmin">
          <span class="btn-icon">⚙</span>
          <span class="btn-text">管理后台</span>
        </ElButton>
      </div>

      <div class="sidebar-divider" v-if="showSidebar"></div>

      <!-- 历史对话列表 -->
      <div class="history-section" v-if="showSidebar">
        <div class="section-title">历史对话</div>
        <div class="session-list">
          <div v-for="session in sessions" :key="session.sessionId"
            :class="['session-item', { active: session.sessionId === sessionId }]" @click="switchSession(session)">
            <div v-if="editingSessionId === session.sessionId" class="edit-name">
              <ElInput v-model="editingSessionName" size="small" @blur="updateSessionName"
                @keyup.enter="updateSessionName" autofocus />
            </div>
            <div v-else class="session-content">
              <span class="session-icon">💬</span>
              <div class="session-info">
                <span class="session-name">{{ session.name }}</span>
                <span class="session-preview">{{ session.lastMessage || '暂无消息' }}</span>
              </div>
              <div class="session-actions">
                <ElButton size="small" class="edit-btn"
                  @click.stop="editingSessionId = session.sessionId; editingSessionName = session.name">✎</ElButton>
                <ElPopconfirm title="确定删除？" @confirm="deleteSession(session.sessionId)">
                  <ElButton size="small" class="delete-btn">✕</ElButton>
                </ElPopconfirm>
              </div>
            </div>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty-sessions">暂无对话</div>
      </div>

      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="showSidebar = !showSidebar">
        <span>{{ showSidebar ? '‹' : '›' }}</span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航 -->
      <div class="chat-header">
        <div class="header-left">
          <ElAvatar class="avatar" :size="36">Y</ElAvatar>
          <div class="header-info">
            <h3>{{ currentSessionName }}</h3>
          </div>
        </div>

        <div class="header-right">
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

      <!-- 聊天消息区域 -->
      <div ref="chatContainer" class="chat-messages">
        <!-- 无会话提示 -->
        <div v-if="!sessionId" class="empty-state">
          <div class="empty-icon"></div>
          <div class="empty-text">暂无会话</div>
          <div class="empty-hint">请点击左侧 "+新对话" 开始聊天</div>
        </div>
        <!-- 有会话时显示消息 -->
        <template v-else>
          <div v-for="msg in messages" :key="msg.id" :class="['message-item', `message-${msg.type}`]">
            <template v-if="msg.type === 'user'">
              <div class="message-content user-content">{{ msg.content }}</div>
              <ElAvatar class="avatar user-avatar">{{ props.username.charAt(0) }}</ElAvatar>
            </template>
            <template v-else-if="msg.type === 'bot'">
              <ElAvatar class="avatar bot-avatar">Y</ElAvatar>
              <div class="message-content bot-content" v-html="renderMarkdown(msg.content)"></div>
            </template>
            <template v-else-if="msg.type === 'loading'">
              <ElAvatar class="avatar bot-avatar">Y</ElAvatar>
              <div class="loading-indicator"><span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </template>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-wrapper" :class="{ disabled: !sessionId }">
        <div class="chat-input-box">
          <textarea v-model="inputMessage" class="message-textarea"
            :placeholder="!sessionId ? '请先创建会话' : (isRecording ? '正在聆听，请说话...' : '向 Yumi 优秘 提问')" :disabled="isSending || !sessionId"
            @keyup.enter.exact.prevent="sendMessage"></textarea>

          <div class="input-tools">
            <div class="tools-left">
              <button class="tool-icon-btn mic-btn" :class="{ active: isRecording }" @mousedown.prevent="startRecording"
                @mouseup.prevent="stopRecording" @mouseleave="stopRecording"
                :disabled="!sessionId"
                :title="!sessionId ? '请先创建会话' : (speechSupported ? '按住说话（松开发送）' : '浏览器不支持语音')">
                <svg v-if="!isRecording" class="icon-svg" viewBox="0 0 24 24" width="22" height="22"
                  stroke="currentColor" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
                  <line x1="12" y1="19" x2="12" y2="23"></line>
                  <line x1="8" y1="23" x2="16" y2="23"></line>
                </svg>
                <svg v-else class="icon-svg mic-recording" viewBox="0 0 24 24" width="22" height="22"
                  stroke="currentColor" fill="currentColor">
                  <circle cx="12" cy="12" r="10"></circle>
                </svg>
              </button>
            </div>

            <button class="send-circle-btn" :class="{ disabled: !inputMessage.trim() && !isRecording || !sessionId }"
              @click="sendMessage" :disabled="isSending || !sessionId">
              <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" fill="none" stroke-width="2.5"
                stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="19" x2="12" y2="5"></line>
                <polyline points="5 12 12 5 19 12"></polyline>
              </svg>
            </button>
          </div>

          <div v-if="isRecording" class="recording-tip">
            <span class="rec-dot"></span>
            正在聆听...（松开鼠标发送）
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 新增对话弹窗 -->
  <ElDialog v-model="showCreateDialog" title="新增对话" width="400px" :close-on-click-modal="false">
    <div class="dialog-content">
      <div class="dialog-label">选择数字人</div>
      <ElSelect v-model="dialogDigitalHumanId" class="dialog-select" placeholder="请选择数字人">
        <ElOption v-for="dh in digitalHumans" :key="dh.id" :label="dh.name" :value="dh.id" />
      </ElSelect>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <ElButton @click="showCreateDialog = false">取消</ElButton>
        <ElButton type="primary" @click="confirmCreateSession">确定</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.app-container {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 280px;
  background: white;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-avatar {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border-radius: 8px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.sidebar-actions {
  padding: 12px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.btn-icon {
  font-size: 16px;
}

.btn-text {
  font-size: 14px;
}

.sidebar-admin {
  padding: 0 12px 12px;
}

.admin-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #f5f7fa;
  color: #606266;
  border: 1px solid #e4e7ed;
}

.admin-btn:hover {
  background: #ecf5ff;
  color: #409eff;
  border-color: #b3d8ff;
}

.sidebar-divider {
  height: 1px;
  background: #e4e7ed;
  margin: 0 12px;
}

.history-section {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.section-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding-left: 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: rgba(102, 126, 234, 0.1);
}

.session-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.session-icon {
  font-size: 16px;
}

.session-info {
  flex: 1;
  overflow: hidden;
}

.session-name {
  font-size: 14px;
  color: #303133;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-preview {
  font-size: 12px;
  color: #909399;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.session-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.edit-btn {
  color: #606266;
}

.delete-btn {
  color: #f56c6c;
}

.edit-name {
  flex: 1;
}

.empty-sessions {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}

.collapse-btn {
  position: absolute;
  right: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  background: white;
  border: 1px solid #e4e7ed;
  border-left: none;
  border-radius: 0 8px 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #909399;
  font-size: 16px;
}

.collapse-btn:hover {
  background: #f5f7fa;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  position: relative;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 12px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  background: rgba(255, 255, 255, 0.3);
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  margin-left: auto;
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
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: all 0.2s;
}

.user-trigger:hover {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.5);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}

.dropdown-arrow {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 4px solid rgba(255, 255, 255, 0.7);
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

.agent-admin-btn {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border-color: rgba(255, 255, 255, 0.3);
  font-size: 14px;
}

.agent-admin-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.logout-btn {
  color: white;
  border-color: rgba(255, 255, 255, 0.3);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 80%;
}

.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-bot {
  align-self: flex-start;
}

.message-content {
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.5;
}

.user-content {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 6px;
}

.bot-content {
  background: white;
  color: #303133;
  border-bottom-left-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.bot-content :deep(h1) {
  font-size: 1.5em;
  font-weight: bold;
  margin: 0.5em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #eee;
}

.bot-content :deep(h2) {
  font-size: 1.3em;
  font-weight: bold;
  margin: 0.5em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #eee;
}

.bot-content :deep(h3) {
  font-size: 1.1em;
  font-weight: bold;
  margin: 0.5em 0;
}

.bot-content :deep(strong) {
  font-weight: bold;
  color: #303133;
}

.bot-content :deep(ul),
.bot-content :deep(ol) {
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.bot-content :deep(li) {
  margin: 0.3em 0;
}

.bot-content :deep(p) {
  margin: 0.5em 0;
  line-height: 1.6;
}

.bot-content :deep(code) {
  background: #f4f4f4;
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 0.9em;
}

.bot-content :deep(pre) {
  background: #f4f4f4;
  padding: 1em;
  border-radius: 6px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.bot-content :deep(pre code) {
  background: none;
  padding: 0;
}

.bot-content :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 1em 0;
}

.loading-indicator {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
  background: white;
  border-radius: 18px;
  border-bottom-left-radius: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  background: #667eea;
  border-radius: 50%;
  animation: loading 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}

.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes loading {

  0%,
  80%,
  100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

.chat-input-wrapper {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: center;
}

.chat-input-box {
  width: 100%;
  max-width: 768px;
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 24px;
  padding: 12px 16px;
  position: relative;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.chat-input-box:hover {
  border-color: #667eea;
  box-shadow: 0 2px 12px rgba(102, 126, 234, 0.15);
}

.chat-input-box:focus-within {
  border-color: #667eea;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.2);
  background: white;
}

.message-textarea {
  width: 100%;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-size: 15px;
  line-height: 1.6;
  min-height: 40px;
  max-height: 200px;
  color: #303133;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.message-textarea::placeholder {
  color: #a8abb2;
}

.message-textarea:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

.input-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
}

.tools-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid #e4e7ed;
  background: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  transition: all 0.2s;
  padding: 0;
}

.tool-icon-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
  transform: scale(1.05);
}

.tool-icon-btn.mic-btn.active {
  background: #f56c6c;
  color: white;
  border-color: #f56c6c;
  animation: pulse 1.2s infinite;
}

.icon-svg {
  display: block;
}

.mic-recording {
  animation: recording-pulse 0.8s infinite;
  color: white;
}

@keyframes pulse {

  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.5);
  }

  50% {
    box-shadow: 0 0 0 10px rgba(245, 108, 108, 0);
  }
}

@keyframes recording-pulse {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(0.85);
  }
}

.send-circle-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  padding: 0;
}

.send-circle-btn:hover:not(.disabled) {
  transform: scale(1.08) translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.send-circle-btn:active:not(.disabled) {
  transform: scale(0.95);
}

.send-circle-btn.disabled {
  background: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.6;
}

.recording-tip {
  position: absolute;
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
  background: #f56c6c;
  color: white;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.rec-dot {
  width: 8px;
  height: 8px;
  background: white;
  border-radius: 50%;
  animation: blink 1s infinite;
}

@keyframes blink {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.3;
  }
}

/* 新增对话弹窗样式 */
.dialog-content {
  padding: 8px 0;
}

.dialog-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
  font-weight: 500;
}

.dialog-select {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>