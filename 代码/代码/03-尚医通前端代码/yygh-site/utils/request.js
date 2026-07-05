import axios from 'axios'
import { Message } from 'element-ui'
import cookie from 'js-cookie'

const service = axios.create({
  baseURL: process.env.API_BASE_URL || '',
  timeout: 15000
})

let authPrompting = false

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
  response => {
    if (response.data.code === 208) {
      if (typeof window !== 'undefined' && window.loginEvent) {
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
