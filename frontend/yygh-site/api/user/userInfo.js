import request from '@/utils/request'

const api_name = `/api/user`
const user_service_api = process.env.USER_API_BASE_URL || `/api/user`

export default {
  login(userInfo) {
    return request({
      url: `${user_service_api}/login`,
      method: `post`,
      data: userInfo
    })
  },

  passwordLogin(loginInfo) {
    return request({
      url: `${user_service_api}/password/login`,
      method: `post`,
      data: loginInfo
    })
  },

  resetPassword(resetInfo) {
    return request({
      url: `${user_service_api}/password/reset`,
      method: `post`,
      data: resetInfo
    })
  },

  changePassword(changeInfo) {
    return request({
      url: `${api_name}/auth/password/change`,
      method: 'post',
      data: changeInfo
    })
  },

  refreshToken(refreshToken) {
    return request({
      url: `${user_service_api}/token/refresh`,
      method: `post`,
      data: { refreshToken }
    })
  },

  getUserInfo() {
    return request({
      url: `${api_name}/auth/getUserInfo`,
      method: `get`
    })
  },

  saveUserAuah(userAuah) {
    return request({
      url: `${api_name}/auth/userAuth`,
      method: 'post',
      data: userAuah
    })
  }
}
