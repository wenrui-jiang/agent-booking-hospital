import axios from 'axios'
import { Message } from 'element-ui'
import cookie from 'js-cookie'

const service = axios.create({
  baseURL: process.env.API_BASE_URL || '',
  timeout: 15000
})

let authPrompting = false
let refreshingToken = null

function isAuthPublicRequest(config) {
  const url = (config && config.url) || ''
  return url.indexOf('/api/user/login') >= 0 ||
    url.indexOf('/api/user/password/login') >= 0 ||
    url.indexOf('/api/user/password/reset') >= 0 ||
    url.indexOf('/api/user/token/refresh') >= 0 ||
    url.indexOf('/api/sms/') >= 0
}

function isProtectedRequest(config) {
  const url = (config && config.url) || ''
  if (isAuthPublicRequest(config)) {
    return false
  }
  return url.indexOf('/auth/') >= 0 ||
    url.indexOf('/api/agent/sessions') >= 0 ||
    url.indexOf('/api/agent/session/') >= 0 ||
    url.indexOf('/api/agent/pretriage-report/') >= 0 ||
    url.indexOf('/api/agent/tool-calls/') >= 0 ||
    url.indexOf('/api/order') >= 0 ||
    url.indexOf('/patient') >= 0
}

function clearAuthAndPrompt(config) {
  if (typeof window === 'undefined' || !window.loginEvent || !isProtectedRequest(config)) {
    return
  }
  cookie.remove('token')
  cookie.remove('refreshToken')
  cookie.remove('name')
  if (!authPrompting) {
    authPrompting = true
    window.loginEvent.$emit('loginDialogEvent')
    window.setTimeout(() => {
      authPrompting = false
    }, 1000)
  }
}

function refreshAccessToken() {
  const refreshToken = cookie.get('refreshToken')
  if (!refreshToken) {
    return Promise.reject(new Error('missing refresh token'))
  }
  if (!refreshingToken) {
    refreshingToken = axios({
      baseURL: process.env.API_BASE_URL || '',
      url: '/api/user/token/refresh',
      method: 'post',
      data: { refreshToken }
    }).then(response => {
      if (!response.data || response.data.code !== 200 || !response.data.data || !response.data.data.token) {
        return Promise.reject(response.data || new Error('refresh token failed'))
      }
      const data = response.data.data
      cookie.set('token', data.token)
      if (data.refreshToken) {
        cookie.set('refreshToken', data.refreshToken)
      }
      cookie.set('name', data.name || cookie.get('name') || '个人中心')
      return data.token
    }).finally(() => {
      refreshingToken = null
    })
  }
  return refreshingToken
}

service.interceptors.request.use(
  config => {
    if (cookie.get('token')) {
      config.headers['token'] = cookie.get('token')
      config.headers['Authorization'] = `Bearer ${cookie.get('token')}`
    }
    return config
  },
  err => {
    return Promise.reject(err)
  })

service.interceptors.response.use(
  async response => {
    if (response.data.code === 208) {
      const config = response.config || {}
      if (isProtectedRequest(response.config) && !config.__authRetry) {
        try {
          const token = await refreshAccessToken()
          config.__authRetry = true
          config.headers = config.headers || {}
          config.headers.token = token
          config.headers.Authorization = `Bearer ${token}`
          return service(config)
        } catch (e) {
          clearAuthAndPrompt(config)
        }
      } else {
        clearAuthAndPrompt(config)
      }
      return Promise.reject(response.data)
    }

    if (response.data.code !== 200) {
      if (typeof window !== 'undefined') {
        Message({
          message: response.data.message,
          type: 'error',
          duration: 5 * 1000
        })
      }
      return Promise.reject(response.data)
    }

    return response.data
  },
  error => {
    return Promise.reject(error && error.response ? error.response : error)
  })

export default service
