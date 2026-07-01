<template>
  <div class="agent-assistant">
    <button v-if="!open" type="button" class="agent-fab" @click="open = true">智能导诊</button>
    <div v-if="open" class="agent-panel">
      <div class="agent-header">
        <div>
          <div class="agent-title">智能导诊 Agent</div>
          <div class="agent-subtitle">您直接告诉我哪里不舒服，我来指导您就医</div>
        </div>
        <button type="button" class="agent-close" @click="open = false">x</button>
      </div>

      <div class="agent-messages" ref="messages">
        <div v-for="(item, index) in messages" :key="index" :class="['agent-message', item.role]">
          <div class="agent-bubble">{{ item.text }}</div>
        </div>
        <div v-if="reportPreview && reportPreview.chiefComplaint" class="agent-report">
          <div class="report-title">预诊报告预览</div>
          <div>主诉：{{ reportPreview.chiefComplaint }}</div>
          <div>建议科室：{{ reportPreview.departmentRecommendation }}</div>
          <pre v-if="reportPreview.doctorCopyText">{{ reportPreview.doctorCopyText }}</pre>
        </div>
      </div>

      <div v-if="actions.length" class="agent-actions">
        <button v-for="(action, index) in actions" :key="index" type="button" class="agent-action" @click="handleAction(action)">
          {{ action.label }}
        </button>
      </div>

      <div class="agent-input">
        <textarea v-model="input" rows="2" placeholder="例如：我最近咳嗽嗓子疼三天了，有点发热" @keydown.ctrl.enter.prevent="submit"/>
        <button type="button" :disabled="loading || !input.trim()" @click="submit">{{ loading ? '发送中' : '发送' }}</button>
      </div>
    </div>
  </div>
</template>

<script>
import agentApi from '@/api/agent/agent'

export default {
  mounted() {
    this.restoreState()
  },

  watch: {
    open: 'saveState',
    sessionId: 'saveState',
    actions: {
      handler: 'saveState',
      deep: true
    },
    reportPreview: {
      handler: 'saveState',
      deep: true
    },
    messages: {
      handler: 'saveState',
      deep: true
    }
  },

  data() {
    return {
      open: false,
      input: '',
      loading: false,
      sessionId: '',
      actions: [],
      reportPreview: null,
      messages: [
        {
          role: 'assistant',
          text: '您直接告诉我哪里不舒服，我会先追问关键信息，再给出科室建议和挂号下一步。'
        }
      ]
    }
  },

  methods: {
    submit() {
      this.send(this.input)
    },

    handleAction(action) {
      if (!action) return
      if (action.type === 'NEED_LOGIN') {
        this.requestLogin()
        return
      }
      if (action.type === 'MANAGE_PATIENT') {
        this.saveState()
        window.location.href = '/patient'
        return
      }
      if (action.type === 'VIEW_ORDER') {
        const orderId = action.payload
        window.location.href = orderId ? '/order/show?orderId=' + orderId : '/order'
        return
      }
      this.send(action.label)
    },

    send(text) {
      const message = (text || '').trim()
      if (!message || this.loading) return
      this.input = ''
      this.loading = true
      this.messages.push({ role: 'user', text: message })
      agentApi.chat({
        sessionId: this.sessionId,
        message
      }).then(response => {
        const data = response.data || {}
        this.sessionId = data.sessionId
        this.actions = data.actions || []
        this.reportPreview = data.pretriageReportPreview
        this.messages.push({
          role: 'assistant',
          text: data.answer || '我已经收到，请继续补充。'
        })
        this.handleSystemActions()
        this.saveState()
        this.$nextTick(this.scrollBottom)
      }).catch(() => {
        this.messages.push({
          role: 'assistant',
          text: '导诊服务暂时不可用，请稍后重试。'
        })
      }).finally(() => {
        this.loading = false
      })
    },

    handleSystemActions() {
      const needLogin = (this.actions || []).some(action => action.type === 'NEED_LOGIN')
      if (needLogin) {
        this.requestLogin()
      }
    },

    requestLogin() {
      this.open = true
      this.saveState()
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.setItem('yygh-login-return', window.location.pathname + window.location.search)
      }
      if (window.loginEvent) {
        window.loginEvent.$emit('loginDialogEvent')
      }
    },

    saveState() {
      if (typeof window === 'undefined' || !window.sessionStorage) return
      const state = {
        open: this.open,
        sessionId: this.sessionId,
        actions: this.actions,
        reportPreview: this.reportPreview,
        messages: this.messages
      }
      window.sessionStorage.setItem('yygh-agent-state', JSON.stringify(state))
    },

    restoreState() {
      if (typeof window === 'undefined' || !window.sessionStorage) return
      const raw = window.sessionStorage.getItem('yygh-agent-state')
      if (!raw) return
      try {
        const state = JSON.parse(raw)
        if (state && state.sessionId) {
          this.open = state.open === true
          this.sessionId = state.sessionId || ''
          this.actions = state.actions || []
          this.reportPreview = state.reportPreview || null
          this.messages = state.messages && state.messages.length ? state.messages : this.messages
        }
      } catch (e) {
        window.sessionStorage.removeItem('yygh-agent-state')
      }
    },

    scrollBottom() {
      if (this.$refs.messages) {
        this.$refs.messages.scrollTop = this.$refs.messages.scrollHeight
      }
    }
  }
}
</script>

<style scoped>
.agent-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 5000;
  font-family: Arial, "Microsoft YaHei", sans-serif;
}

.agent-fab {
  width: 112px;
  height: 44px;
  border: none;
  border-radius: 4px;
  color: #fff;
  background: #2f7df6;
  box-shadow: 0 8px 24px rgba(47, 125, 246, 0.3);
  cursor: pointer;
}

.agent-panel {
  width: 380px;
  height: 560px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  box-shadow: 0 12px 36px rgba(20, 35, 60, 0.18);
  overflow: hidden;
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  color: #fff;
  background: #1f5fbf;
}

.agent-title {
  font-size: 16px;
  font-weight: 600;
}

.agent-subtitle {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.9;
}

.agent-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  cursor: pointer;
}

.agent-messages {
  flex: 1;
  padding: 14px;
  overflow-y: auto;
  background: #f6f8fb;
}

.agent-message {
  display: flex;
  margin-bottom: 10px;
}

.agent-message.user {
  justify-content: flex-end;
}

.agent-bubble {
  max-width: 82%;
  padding: 9px 11px;
  border-radius: 6px;
  line-height: 1.55;
  font-size: 13px;
  color: #303133;
  background: #fff;
  white-space: pre-wrap;
}

.agent-message.user .agent-bubble {
  color: #fff;
  background: #2f7df6;
}

.agent-report {
  margin: 12px 0;
  padding: 10px;
  border: 1px solid #c7dafc;
  border-radius: 6px;
  background: #eef5ff;
  color: #303133;
  font-size: 12px;
  line-height: 1.55;
}

.agent-report pre {
  max-height: 120px;
  margin: 8px 0 0;
  padding: 8px;
  overflow: auto;
  background: #fff;
  white-space: pre-wrap;
}

.report-title {
  margin-bottom: 6px;
  font-weight: 600;
}

.agent-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px 0;
  background: #fff;
}

.agent-action {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #2f7df6;
  border-radius: 4px;
  color: #2f7df6;
  background: #fff;
  cursor: pointer;
}

.agent-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}

.agent-input textarea {
  flex: 1;
  resize: none;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px;
  font-size: 13px;
  outline: none;
}

.agent-input button {
  width: 68px;
  border: none;
  border-radius: 4px;
  color: #fff;
  background: #2f7df6;
  cursor: pointer;
}

.agent-input button:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}
</style>
