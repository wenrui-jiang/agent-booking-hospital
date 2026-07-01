import axios from 'axios'
import { Message } from 'element-ui'
import cookie from 'js-cookie'

const service = axios.create({
  baseURL: 'http://localhost',
  timeout: 15000
})

service.interceptors.request.use(
  config => {
    if (cookie.get('token')) {
      config.headers['token'] = cookie.get('token')
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
        window.loginEvent.$emit('loginDialogEvent')
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
    return Promise.reject(error.response)
  })

export default service
