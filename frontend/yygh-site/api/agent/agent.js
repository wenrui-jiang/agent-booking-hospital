import request from '@/utils/request'

const api_name = '/api/agent'
const agentBaseUrl = process.env.AGENT_API_BASE_URL || ''

export default {
  chat(data) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/chat`,
      method: 'post',
      data
    })
  },

  listSessions() {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/sessions`,
      method: 'get'
    })
  },

  newSession() {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/new`,
      method: 'post'
    })
  },

  listSessions() {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/sessions`,
      method: 'get'
    })
  },

  newSession() {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/new`,
      method: 'post'
    })
  },

  getSession(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/${sessionId}`,
      method: 'get'
    })
  },

  getMessages(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/${sessionId}/messages`,
      method: 'get'
    })
  },

  getMessages(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/${sessionId}/messages`,
      method: 'get'
    })
  },

  getPretriageReport(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/pretriage-report/${sessionId}`,
      method: 'get'
    })
  },

  confirmPretriageReport(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/pretriage-report/${sessionId}/confirm`,
      method: 'post'
    })
  },

  getToolCalls(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/tool-calls/${sessionId}`,
      method: 'get'
    })
  }
}
