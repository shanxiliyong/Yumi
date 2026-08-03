<script setup>
import { ref, onMounted } from 'vue'
import LoginPage from './components/LoginPage.vue'
import ChatPage from './components/ChatPage.vue'
import AgentPage from './components/AgentPage.vue'
import DigitalHumanPage from './components/DigitalHumanPage.vue'
import AdminPage from './components/AdminPage.vue'

const isLoggedIn = ref(false)
const username = ref('')
const currentPage = ref('chat')

const handleLoginSuccess = (data) => {
  username.value = data.username
  isLoggedIn.value = true
  currentPage.value = 'chat'
}

const handleLogout = () => {
  isLoggedIn.value = false
  username.value = ''
  currentPage.value = 'chat'
}

const handleNavigate = (page) => {
  currentPage.value = page
}

onMounted(() => {
  const sessionId = localStorage.getItem('sessionId')
  const savedUsername = localStorage.getItem('username')
  if (sessionId && savedUsername) {
    fetch('/api/user/check', {
      headers: { 'X-Session-Id': sessionId }
    })
    .then(response => response.json())
    .then(data => {
      if (data.success && data.loggedIn) {
        username.value = data.username
        isLoggedIn.value = true
      }
    })
    .catch(() => {
      localStorage.removeItem('sessionId')
      localStorage.removeItem('username')
    })
  }
})
</script>

<template>
  <div id="app">
    <LoginPage
      v-if="!isLoggedIn"
      @login-success="handleLoginSuccess"
    />
    <template v-else>
      <ChatPage
        v-if="currentPage === 'chat'"
        :username="username"
        @logout="handleLogout"
        @navigate="handleNavigate"
      />
      <AgentPage
        v-else-if="currentPage === 'agent'"
        :username="username"
        @logout="handleLogout"
        @navigate="handleNavigate"
      />
      <DigitalHumanPage
        v-else-if="currentPage === 'digitalHuman'"
        :username="username"
        @logout="handleLogout"
        @navigate="handleNavigate"
      />
      <AdminPage
        v-else-if="currentPage === 'admin'"
        :username="username"
        @logout="handleLogout"
        @navigate="handleNavigate"
      />
    </template>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
#app { height: 100%; }
</style>