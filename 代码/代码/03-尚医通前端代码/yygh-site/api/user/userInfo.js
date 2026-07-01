import request from '@/utils/request'

const api_name = `/api/user`
const user_service_api = `http://127.0.0.1:8150/api/user`

export default {
  login(userInfo) {
    return request({
      url: `${user_service_api}/login`,
      method: `post`,
      data: userInfo
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
