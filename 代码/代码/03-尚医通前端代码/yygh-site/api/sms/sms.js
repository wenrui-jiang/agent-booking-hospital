import request from '@/utils/request'

const api_name = `/api/msm`
const msm_service_api = `http://127.0.0.1:8204/api/msm`

export default {
  sendEmailCode(email) {
    return request({
      url: `${msm_service_api}/email/code`,
      method: `post`,
      data: { email }
    })
  },

  sendCode(account) {
    return request({
      url: `${api_name}/send/${encodeURIComponent(account)}`,
      method: `get`
    })
  }
}
