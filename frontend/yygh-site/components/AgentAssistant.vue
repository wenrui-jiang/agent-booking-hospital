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

      <div v-if="isLoggedIn" class="agent-session-bar">
        <select v-model="sessionId" :disabled="loadingSessions" @change="switchSession(sessionId)">
          <option value="">新会话</option>
          <option v-for="session in sessions" :key="session.sessionId" :value="session.sessionId">
            {{ session.title || session.lastMessage || '未命名会话' }}
          </option>
        </select>
        <button type="button" :disabled="loadingSessions" @click="newSession">新建</button>
      </div>

      <div class="agent-messages" ref="messages">
        <div v-if="agentSteps.length" class="agent-task-card">
          <div class="task-title">任务执行过程</div>
          <div v-for="(step, index) in agentSteps" :key="'step-' + index" class="task-row">
            <span class="task-dot"></span>
            <div>
              <div class="task-node">{{ step.node || step.title || 'agent_step' }}</div>
              <div class="task-summary">{{ step.summary || step.status || '已处理' }}</div>
            </div>
          </div>
        </div>

        <div v-if="toolTraces.length" class="agent-tool-card">
          <div class="task-title">工具调用</div>
          <div v-for="(tool, index) in toolTraces" :key="'tool-' + index" class="tool-row">
            <span :class="['tool-status', String(tool.status || '').toLowerCase()]">{{ tool.status || 'DONE' }}</span>
            <span class="tool-name">{{ tool.toolName || tool.tool_name }}</span>
          </div>
        </div>

        <div v-if="safetyCard && safetyCard.title" class="agent-safety">
          <div class="report-title">{{ safetyCard.title }}</div>
          <div>{{ safetyCard.scope }}</div>
          <div>{{ safetyCard.emergency }}</div>
        </div>

        <div v-for="(item, index) in messages" :key="index" :class="['agent-message', item.role]">
          <div class="agent-bubble">{{ item.text }}</div>
        </div>
        <div v-if="bookingCard && bookingCard.requiresSecondConfirmation" class="agent-booking-card">
          <div class="report-title">挂号确认卡</div>
          <div class="booking-grid">
            <span>医院</span><strong>{{ bookingCard.hosname || '-' }}</strong>
            <span>科室</span><strong>{{ bookingCard.depname || '-' }}</strong>
            <span>日期</span><strong>{{ bookingCard.workDate || '-' }}</strong>
            <span>时间</span><strong>{{ bookingCard.workTime || '-' }}</strong>
            <span>医生</span><strong>{{ bookingCard.doctorName || '-' }} {{ bookingCard.title ? '（' + bookingCard.title + '）' : '' }}</strong>
            <span>费用</span><strong>{{ bookingCard.amount || '0' }} 元</strong>
            <span>就诊人</span><strong>{{ bookingCard.patientName || '待选择' }}</strong>
          </div>
          <button type="button" class="confirm-order" @click="openConfirmDialog">确认挂号</button>
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
        <textarea ref="inputBox" v-model="input" rows="2" :placeholder="inputPlaceholder" @keydown.ctrl.enter.prevent="submit"/>
        <button type="button" :disabled="loading || !input.trim()" @click="submit">{{ loading ? '发送中' : '发送' }}</button>
      </div>

      <div v-if="confirmDialog" class="agent-confirm-mask">
        <div class="agent-confirm-dialog">
          <div class="confirm-title">确认创建挂号订单？</div>
          <div class="confirm-body">系统将按确认卡片创建预约订单。本演示环境跳过支付，但仍会生成订单记录。</div>
          <div class="confirm-actions">
            <button type="button" @click="confirmDialog = false">再看看</button>
            <button type="button" class="primary" @click="confirmSubmitOrder">确认挂号</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import agentApi from '@/api/agent/agent'
import cookie from 'js-cookie'

export default {
  mounted() {
    this.restoreState()
    this.refreshLoginState()
    if (this.isLoggedIn) {
      this.loadSessions().then(() => {
        if (this.sessionId) {
          this.loadMessages(this.sessionId)
        }
      })
    }
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
    agentSteps: {
      handler: 'saveState',
      deep: true
    },
    toolTraces: {
      handler: 'saveState',
      deep: true
    },
    bookingCard: {
      handler: 'saveState',
      deep: true
    },
    safetyCard: {
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
      inputPlaceholder: '例如：我最近咳嗽嗓子疼三天了，有点发热',
      loading: false,
      loadingSessions: false,
      isLoggedIn: false,
      sessionId: '',
      sessions: [],
      actions: [],
      reportPreview: null,
      agentSteps: [],
      toolTraces: [],
      bookingCard: null,
      safetyCard: null,
      confirmDialog: false,
      messages: [
        {
          role: 'assistant',
          text: '您直接告诉我哪里不舒服，我会先追问关键信息，再给出科室建议和挂号下一步。'
        }
      ]
    }
  },

  methods: {
    refreshLoginState() {
      this.isLoggedIn = !!cookie.get('token')
    },

    loadSessions() {
      this.refreshLoginState()
      if (!this.isLoggedIn) return Promise.resolve()
      this.loadingSessions = true
      return agentApi.listSessions().then(response => {
        this.sessions = response.data || []
        if (!this.sessionId && this.sessions.length) {
          this.sessionId = this.sessions[0].sessionId
        }
      }).catch(() => {
        this.sessions = []
      }).finally(() => {
        this.loadingSessions = false
      })
    },

    newSession() {
      this.refreshLoginState()
      if (!this.isLoggedIn) {
        this.requestLogin()
        return
      }
      this.loadingSessions = true
      agentApi.newSession().then(response => {
        const data = response.data || {}
        this.sessionId = data.sessionId || ''
        this.actions = []
        this.reportPreview = null
        this.agentSteps = []
        this.toolTraces = []
        this.bookingCard = null
        this.safetyCard = null
        this.messages = [this.defaultWelcomeMessage()]
        this.saveState()
        return this.loadSessions()
      }).finally(() => {
        this.loadingSessions = false
      })
    },

    switchSession(sessionId) {
      if (!sessionId) {
        this.actions = []
        this.reportPreview = null
        this.agentSteps = []
        this.toolTraces = []
        this.bookingCard = null
        this.safetyCard = null
        this.messages = [this.defaultWelcomeMessage()]
        this.saveState()
        return
      }
      this.loadMessages(sessionId)
    },

    loadMessages(sessionId) {
      if (!sessionId || !this.isLoggedIn) return
      this.loadingSessions = true
      agentApi.getMessages(sessionId).then(response => {
        const rows = response.data || []
        this.messages = rows.length ? rows.map(item => ({
          role: item.role === 'user' ? 'user' : 'assistant',
          text: item.content
        })) : [this.defaultWelcomeMessage()]
        this.actions = []
        this.reportPreview = null
        this.agentSteps = []
        this.toolTraces = []
        this.bookingCard = null
        this.safetyCard = null
        this.saveState()
        this.$nextTick(this.scrollBottom)
      }).finally(() => {
        this.loadingSessions = false
      })
    },

    defaultWelcomeMessage() {
      return {
        role: 'assistant',
        text: '您直接告诉我哪里不舒服，我会先追问关键信息，再给出科室建议和挂号下一步。'
      }
    },

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
      if (action.type === 'CONFIRM_SUBMIT_ORDER') {
        this.openConfirmDialog()
        return
      }
      if (action.type === 'ASK_BOOKING_SLOT') {
        this.inputPlaceholder = '请直接输入想去的医院、就诊日期和上午/下午，例如：协和医院，明天上午'
        this.$nextTick(() => {
          if (this.$refs.inputBox) this.$refs.inputBox.focus()
        })
        return
      }
      if (action.type === 'ASK_USER') {
        this.inputPlaceholder = '请继续补充症状、持续时间、严重程度，或你想挂号的医院和时间'
        this.$nextTick(() => {
          if (this.$refs.inputBox) this.$refs.inputBox.focus()
        })
        return
      }
      this.send(action.label)
    },

    openConfirmDialog() {
      this.confirmDialog = true
    },

    confirmSubmitOrder() {
      this.confirmDialog = false
      this.send('确认挂号')
    },

    send(text) {
      const message = (text || '').trim()
      if (!message || this.loading) return
      this.input = ''
      this.loading = true
      this.messages.push({ role: 'user', text: message })
      agentApi.chat({
        sessionId: this.sessionId,
        message,
        messages: this.messages.slice(-20).map(item => ({
          role: item.role === 'user' ? 'user' : 'assistant',
          content: item.text
        }))
      }).then(response => {
        const data = response.data || {}
        this.sessionId = data.sessionId
        this.actions = data.actions || []
        this.reportPreview = data.pretriageReportPreview
        this.agentSteps = data.agentSteps || []
        this.toolTraces = data.toolTraces || []
        this.bookingCard = data.bookingCard
        this.safetyCard = data.safetyCard
        this.messages.push({
          role: 'assistant',
          text: data.answer || '我已经收到，请继续补充。'
        })
        this.handleSystemActions()
        this.saveState()
        this.loadSessions()
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
        sessions: this.sessions,
        actions: this.actions,
        reportPreview: this.reportPreview,
        agentSteps: this.agentSteps,
        toolTraces: this.toolTraces,
        bookingCard: this.bookingCard,
        safetyCard: this.safetyCard,
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
          this.sessions = state.sessions || []
          this.actions = state.actions || []
          this.reportPreview = state.reportPreview || null
          this.agentSteps = state.agentSteps || []
          this.toolTraces = state.toolTraces || []
          this.bookingCard = state.bookingCard || null
          this.safetyCard = state.safetyCard || null
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
  position: relative;
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

.agent-session-bar {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.agent-session-bar select {
  flex: 1;
  min-width: 0;
  height: 30px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 0 8px;
  font-size: 12px;
  color: #303133;
  background: #fff;
}

.agent-session-bar button {
  width: 56px;
  height: 30px;
  border: 1px solid #2f7df6;
  border-radius: 4px;
  color: #2f7df6;
  background: #fff;
  cursor: pointer;
}

.agent-session-bar button:disabled {
  color: #a0cfff;
  border-color: #a0cfff;
  cursor: not-allowed;
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

.agent-task-card,
.agent-tool-card,
.agent-safety,
.agent-booking-card {
  margin: 0 0 12px;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  font-size: 12px;
}

.task-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.task-row {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px solid #f0f2f5;
}

.task-row:first-of-type {
  border-top: none;
}

.task-dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: #2f7df6;
  flex: 0 0 auto;
}

.task-node {
  font-weight: 600;
  color: #1f5fbf;
}

.task-summary {
  margin-top: 2px;
  line-height: 1.45;
  color: #606266;
}

.tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 24px;
}

.tool-status {
  min-width: 54px;
  padding: 2px 5px;
  border-radius: 4px;
  color: #fff;
  background: #909399;
  text-align: center;
  font-size: 11px;
}

.tool-status.success,
.tool-status.done {
  background: #67c23a;
}

.tool-status.failed {
  background: #f56c6c;
}

.tool-name {
  color: #303133;
}

.agent-safety {
  border-color: #f3d19e;
  background: #fdf6ec;
  line-height: 1.5;
}

.agent-booking-card {
  border-color: #b3d8ff;
  background: #f5faff;
}

.booking-grid {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 6px 10px;
  line-height: 1.5;
}

.booking-grid span {
  color: #606266;
}

.confirm-order {
  width: 100%;
  height: 32px;
  margin-top: 10px;
  border: none;
  border-radius: 4px;
  color: #fff;
  background: #2f7df6;
  cursor: pointer;
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

.agent-confirm-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.32);
}

.agent-confirm-dialog {
  width: 300px;
  padding: 16px;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
}

.confirm-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.confirm-body {
  margin-top: 10px;
  line-height: 1.6;
  font-size: 13px;
  color: #606266;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.confirm-actions button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}

.confirm-actions button.primary {
  border-color: #2f7df6;
  color: #fff;
  background: #2f7df6;
}
</style>
