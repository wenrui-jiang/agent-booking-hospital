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

  getSession(sessionId) {
    return request({
      baseURL: agentBaseUrl,
      url: `${api_name}/session/${sessionId}`,
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
