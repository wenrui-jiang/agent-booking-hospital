<template>
  <div class="header-container">
    <div class="wrapper yygh-header">
      <div class="left-wrapper v-link selected" @click="goHome">
        <img class="site-logo" width="50" height="50" src="~assets/images/logo.png">
        <span class="text">医疗预约 Agent 演示平台</span>
      </div>

      <div class="search-wrapper">
        <div class="hospital-search animation-show">
          <div v-show="showHeaderSearch">
            <el-autocomplete
              v-model="hosname"
              class="search-input"
              prefix-icon="el-icon-search"
              :fetch-suggestions="querySearchAsync"
              :trigger-on-focus="false"
              placeholder="点击输入医院名称"
              @select="handleSelect"
            >
              <span slot="suffix" class="search-btn v-link highlight clickable selected">搜索</span>
            </el-autocomplete>
          </div>
        </div>
      </div>

      <div class="right-wrapper yygh-actions">
        <span class="v-link clickable">帮助中心</span>
        <span class="quick-link" @click="loginMenu('/order')">
          <i class="el-icon-tickets"></i>
          我的订单
        </span>

        <template v-if="clientReady && !isLoggedIn">
          <span class="v-link clickable login-entry" id="loginDialog" @click="showLogin">登录/注册</span>
        </template>

        <template v-else-if="clientReady">
          <el-dropdown @command="loginMenu">
            <span class="el-dropdown-link user-dropdown">
              <i class="el-icon-user"></i>
              {{ name || '个人中心' }}
              <i class="el-icon-arrow-down el-icon--right"></i>
            </span>
            <el-dropdown-menu class="user-name-wrapper" slot="dropdown">
              <el-dropdown-item command="/center">个人中心</el-dropdown-item>
              <el-dropdown-item command="/user">实名认证</el-dropdown-item>
              <el-dropdown-item command="/order">挂号订单</el-dropdown-item>
              <el-dropdown-item command="/patient">就诊人管理</el-dropdown-item>
              <el-dropdown-item command="/logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </template>
      </div>
    </div>

    <el-dialog
      :visible.sync="dialogUserFormVisible"
      style="text-align: left;"
      top="50px"
      :append-to-body="true"
      width="960px"
      @close="closeDialog"
    >
      <div class="container">
        <div class="operate-view" v-if="dialogAtrr.showLoginType === 'email'">
          <div class="wrapper" style="width: 100%">
            <div class="mobile-wrapper" style="position: static;width: 70%">
              <div class="auth-tabs">
                <span :class="{ active: authMode === 'code' }" @click="switchAuthMode('code')">验证码登录</span>
                <span :class="{ active: authMode === 'password' }" @click="switchAuthMode('password')">密码登录</span>
                <span :class="{ active: authMode === 'reset' }" @click="switchAuthMode('reset')">忘记密码</span>
              </div>
              <span class="title">{{ dialogAtrr.labelTips }}</span>
              <el-form v-if="authMode === 'code'" @submit.native.prevent>
                <el-form-item>
                  <el-input
                    v-model.trim="dialogAtrr.inputValue"
                    :placeholder="dialogAtrr.placeholder"
                    :maxlength="dialogAtrr.maxlength"
                    class="input v-input"
                    @keyup.enter.native="btnClick"
                  >
                    <span slot="suffix" class="sendText v-link" v-if="dialogAtrr.second > 0">{{ dialogAtrr.second }}s</span>
                    <span slot="suffix" class="sendText v-link highlight clickable selected" v-if="dialogAtrr.second === 0" @click="getCodeFun">重新发送</span>
                  </el-input>
                </el-form-item>
              </el-form>
              <el-form v-else-if="authMode === 'password'" @submit.native.prevent>
                <el-form-item>
                  <el-input
                    v-model.trim="passwordForm.email"
                    placeholder="请输入邮箱地址"
                    maxlength="80"
                    class="input v-input"
                    @keyup.enter.native="passwordLogin"
                  />
                </el-form-item>
                <el-form-item>
                  <el-input
                    v-model="passwordForm.password"
                    placeholder="请输入密码"
                    show-password
                    maxlength="72"
                    class="input v-input"
                    @keyup.enter.native="passwordLogin"
                  />
                </el-form-item>
              </el-form>
              <el-form v-else @submit.native.prevent>
                <el-form-item>
                  <el-input
                    v-model.trim="resetForm.email"
                    placeholder="请输入邮箱地址"
                    maxlength="80"
                    class="input v-input"
                  >
                    <span slot="suffix" class="sendText v-link" v-if="resetSecond > 0">{{ resetSecond }}s</span>
                    <span slot="suffix" class="sendText v-link highlight clickable selected" v-if="resetSecond === 0" @click="sendResetCode">发送验证码</span>
                  </el-input>
                </el-form-item>
                <el-form-item>
                  <el-input
                    v-model.trim="resetForm.code"
                    placeholder="请输入验证码"
                    maxlength="6"
                    class="input v-input"
                  />
                </el-form-item>
                <el-form-item>
                  <el-input
                    v-model="resetForm.newPassword"
                    placeholder="请输入新密码，至少 8 位"
                    show-password
                    maxlength="72"
                    class="input v-input"
                    @keyup.enter.native="resetPassword"
                  />
                </el-form-item>
              </el-form>
              <div class="send-button v-button" @click="btnClick">{{ currentAuthButton }}</div>
            </div>
            <div class="bottom">
              <div class="wechat-wrapper" @click="weixinLogin"><span class="iconfont icon">微</span></div>
              <span class="third-text">第三方账号登录</span>
            </div>
          </div>
        </div>

        <div class="operate-view" v-if="dialogAtrr.showLoginType === 'weixin'">
          <div class="wrapper wechat" style="height: 400px">
            <div>
              <div id="weixinLogin"></div>
            </div>
            <div class="bottom wechat" style="margin-top: -80px;">
              <div class="phone-container">
                <div class="phone-wrapper" @click="emailLogin"><span class="iconfont icon">邮</span></div>
                <span class="third-text">邮箱验证码登录</span>
              </div>
            </div>
          </div>
        </div>

        <div class="info-wrapper">
          <div class="code-wrapper">
            <div>
              <img src="/images/demo-qr.png" class="code-img">
              <div class="code-text"><span class="iconfont icon">微</span>微信扫一扫关注</div>
              <div class="code-text">“本地预约演示”</div>
            </div>
            <div class="wechat-code-wrapper">
              <img src="/images/demo-qr.png" class="code-img">
              <div class="code-text">扫一扫下载</div>
              <div class="code-text">“演示平台”</div>
            </div>
          </div>
          <div class="slogan">
            <div>本地演示平台</div>
            <div>导诊预约 演示环境</div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import cookie from 'js-cookie'
import Vue from 'vue'

import userInfoApi from '@/api/user/userInfo'
import smsApi from '@/api/sms/sms'
import hospitalApi from '@/api/hosp/hospital'
import weixinApi from '@/api/user/weixin'

const LOGIN_STEP_ACCOUNT = 'account'
const LOGIN_STEP_CODE = 'code'

const createDefaultDialogAtrr = () => ({
  showLoginType: 'email',
  step: LOGIN_STEP_ACCOUNT,
  labelTips: '邮箱地址',
  inputValue: '',
  placeholder: '请输入您的邮箱地址',
  maxlength: 80,
  loginBtn: '获取验证码',
  sending: true,
  second: -1
})

export default {
  data() {
    return {
      hosname: '',
      userInfo: {
        email: '',
        phone: '',
        code: '',
        openid: ''
      },
      authMode: 'code',
      passwordForm: {
        email: '',
        password: ''
      },
      resetForm: {
        email: '',
        code: '',
        newPassword: ''
      },
      resetSecond: 0,
      clearResetTime: null,
      dialogUserFormVisible: false,
      dialogAtrr: createDefaultDialogAtrr(),
      clearSmsTime: null,
      name: '',
      showHeaderSearch: true,
      clientReady: false
    }
  },

  computed: {
    isLoggedIn() {
      return this.clientReady && !!cookie.get('token')
    },

    currentAuthButton() {
      if (this.authMode === 'password') {
        return '登录'
      }
      if (this.authMode === 'reset') {
        return '重置密码'
      }
      return this.dialogAtrr.loginBtn
    }
  },

  created() {
  },

  mounted() {
    this.clientReady = true
    this.showInfo()
    window.loginEvent = window.loginEvent || new Vue()
    window.eventLogin = window.loginEvent
    window.loginEvent.$off('loginDialogEvent')
    window.loginEvent.$on('loginDialogEvent', () => {
      this.showLogin()
    })
    window.addEventListener('yygh-header-search-visible', this.handleHeaderSearchVisible)

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = 'https://res.wx.qq.com/connect/zh_CN/htmledition/js/wxLogin.js'
    document.body.appendChild(script)

    window.loginCallback = (name, token, openid) => {
      this.loginCallback(name, token, openid)
    }
  },

  beforeDestroy() {
    window.removeEventListener('yygh-header-search-visible', this.handleHeaderSearchVisible)
  },

  methods: {
    goHome() {
      this.showHeaderSearch = false
      if (this.$router) {
        const navigation = this.$router.push('/')
        if (navigation && navigation.catch) {
          navigation.catch(() => {})
        }
        this.$nextTick(() => {
          const wrap = document.querySelector('.page-component__scroll .el-scrollbar__wrap')
          if (wrap) {
            wrap.scrollTop = 0
          }
        })
      } else {
        window.location.href = '/'
      }
    },

    handleHeaderSearchVisible(event) {
      this.showHeaderSearch = !!(event && event.detail && event.detail.visible)
    },

    loginCallback(name, token, openid) {
      if (openid) {
        this.userInfo.openid = openid
        this.showLogin()
      } else {
        this.setCookies(name, token)
      }
    },

    btnClick() {
      if (this.authMode === 'password') {
        this.passwordLogin()
        return
      }
      if (this.authMode === 'reset') {
        this.resetPassword()
        return
      }
      if (this.dialogAtrr.step === LOGIN_STEP_ACCOUNT) {
        this.userInfo.email = this.normalizeEmail(this.dialogAtrr.inputValue)
        this.userInfo.phone = this.userInfo.email
        this.getCodeFun()
      } else {
        this.login()
      }
    },

    showLogin() {
      this.dialogUserFormVisible = true
      this.authMode = 'code'
      this.dialogAtrr = createDefaultDialogAtrr()
      this.userInfo.email = ''
      this.userInfo.phone = ''
      this.userInfo.code = ''
      this.passwordForm.email = ''
      this.passwordForm.password = ''
      this.resetForm.email = ''
      this.resetForm.code = ''
      this.resetForm.newPassword = ''
      this.resetSecond = 0
    },

    login() {
      this.userInfo.code = (this.dialogAtrr.inputValue || '').trim()

      if (this.dialogAtrr.loginBtn === '正在提交...') {
        this.$message.error('请勿重复提交')
        return
      }

      if (!this.userInfo.code) {
        this.$message.error('验证码必须输入')
        return
      }

      if (!/^\d{6}$/.test(this.userInfo.code)) {
        this.$message.error('验证码格式不正确')
        return
      }

      this.dialogAtrr.loginBtn = '正在提交...'
      userInfoApi.login(this.userInfo).then(response => {
        this.$message.success('登录成功')
        this.setCookies(response.data.name, response.data.token, response.data.refreshToken)
      }).catch(() => {
        this.dialogAtrr.loginBtn = '马上登录'
      })
    },

    passwordLogin() {
      const email = this.normalizeEmail(this.passwordForm.email)
      if (!this.isValidEmail(email)) {
        this.$message.error('邮箱地址不正确')
        return
      }
      if (!this.passwordForm.password) {
        this.$message.error('密码必须输入')
        return
      }
      userInfoApi.passwordLogin({
        email,
        password: this.passwordForm.password
      }).then(response => {
        this.$message.success('登录成功')
        this.setCookies(response.data.name, response.data.token, response.data.refreshToken)
      })
    },

    resetPassword() {
      const email = this.normalizeEmail(this.resetForm.email)
      if (!this.isValidEmail(email)) {
        this.$message.error('邮箱地址不正确')
        return
      }
      if (!/^\d{6}$/.test(this.resetForm.code || '')) {
        this.$message.error('验证码格式不正确')
        return
      }
      if (!this.resetForm.newPassword || this.resetForm.newPassword.length < 8) {
        this.$message.error('新密码至少 8 位')
        return
      }
      userInfoApi.resetPassword({
        email,
        code: this.resetForm.code,
        newPassword: this.resetForm.newPassword
      }).then(() => {
        this.$message.success('密码已重置，请使用密码登录')
        this.switchAuthMode('password')
        this.passwordForm.email = email
      })
    },

    setCookies(name, token, refreshToken) {
      cookie.set('token', token)
      if (refreshToken) {
        cookie.set('refreshToken', refreshToken)
      }
      cookie.set('name', name || this.userInfo.phone || '个人中心')
      const returnTo = window.sessionStorage ? window.sessionStorage.getItem('yygh-login-return') : ''
      if (returnTo && window.sessionStorage) {
        window.sessionStorage.removeItem('yygh-login-return')
      }
      window.location.href = returnTo || '/center'
    },

    getCodeFun() {
      const account = this.normalizeEmail(this.dialogAtrr.inputValue || this.userInfo.email || this.userInfo.phone)
      this.userInfo.email = account
      this.userInfo.phone = account

      if (!this.isValidEmail(account)) {
        this.$message.error('邮箱地址不正确')
        return
      }

      if (!this.dialogAtrr.sending) return

      this.dialogAtrr.inputValue = ''
      this.dialogAtrr.placeholder = '请输入验证码'
      this.dialogAtrr.maxlength = 6
      this.dialogAtrr.loginBtn = '马上登录'
      this.dialogAtrr.step = LOGIN_STEP_CODE

      this.timeDown()
      this.dialogAtrr.sending = false
      smsApi.sendEmailCode(account).catch(() => {
        this.$message.error('发送失败，请重新发送')
        this.showLogin()
      })
    },

    sendResetCode() {
      const email = this.normalizeEmail(this.resetForm.email)
      if (!this.isValidEmail(email)) {
        this.$message.error('邮箱地址不正确')
        return
      }
      smsApi.sendEmailCode(email).then(() => {
        this.$message.success('验证码已发送')
        this.resetSecond = 60
        if (this.clearResetTime) {
          clearInterval(this.clearResetTime)
        }
        this.clearResetTime = setInterval(() => {
          --this.resetSecond
          if (this.resetSecond < 1) {
            clearInterval(this.clearResetTime)
            this.resetSecond = 0
          }
        }, 1000)
      })
    },

    switchAuthMode(mode) {
      this.authMode = mode
      if (mode === 'code') {
        this.dialogAtrr = createDefaultDialogAtrr()
      } else if (mode === 'password') {
        this.dialogAtrr.labelTips = '邮箱密码登录'
      } else {
        this.dialogAtrr.labelTips = '重置密码'
      }
    },

    normalizeEmail(value) {
      return (value || '').trim().toLowerCase()
    },

    isValidEmail(value) {
      return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value)
    },

    timeDown() {
      if (this.clearSmsTime) {
        clearInterval(this.clearSmsTime)
      }

      this.dialogAtrr.second = 60
      this.dialogAtrr.labelTips = '验证码已发送至 ' + this.userInfo.phone
      this.clearSmsTime = setInterval(() => {
        --this.dialogAtrr.second
        if (this.dialogAtrr.second < 1) {
          clearInterval(this.clearSmsTime)
          this.dialogAtrr.sending = true
          this.dialogAtrr.second = 0
        }
      }, 1000)
    },

    closeDialog() {
      if (this.clearSmsTime) {
        clearInterval(this.clearSmsTime)
      }
      if (this.clearResetTime) {
        clearInterval(this.clearResetTime)
      }
    },

    showInfo() {
      if (cookie.get('token')) {
        this.name = cookie.get('name') || '个人中心'
      }
    },

    loginMenu(command) {
      if (command === '/logout') {
        cookie.remove('name')
        cookie.remove('token')
        cookie.remove('refreshToken')
        window.location.href = '/'
        return
      }
      if (!this.isLoggedIn && command !== '/logout') {
        this.showLogin()
        return
      }
      window.location.href = command
    },

    querySearchAsync(queryString, cb) {
      if (!queryString) return
      hospitalApi.getByHosname(queryString).then(response => {
        for (let i = 0, len = response.data.length; i < len; i++) {
          response.data[i].value = response.data[i].hosname
        }
        cb(response.data)
      })
    },

    handleSelect(item) {
      window.location.href = '/hospital/' + item.hoscode
    },

    weixinLogin() {
      this.dialogAtrr.showLoginType = 'weixin'

      weixinApi.getLoginParam().then(response => {
        // eslint-disable-next-line no-undef
        new WxLogin({
          self_redirect: true,
          id: 'weixinLogin',
          appid: response.data.appid,
          scope: response.data.scope,
          redirect_uri: response.data.redirectUri,
          state: response.data.state,
          style: 'black',
          href: ''
        })
      })
    },

    emailLogin() {
      this.dialogAtrr.showLoginType = 'email'
      this.showLogin()
    }
  }
}
</script>

<style scoped>
.yygh-header {
  align-items: center;
}

.site-logo {
  width: 50px;
}

.left-wrapper {
  cursor: pointer;
}

.yygh-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  white-space: nowrap;
}

.login-entry {
  color: #4490f1;
  font-weight: 600;
}

.auth-tabs {
  display: flex;
  gap: 18px;
  margin-bottom: 20px;
  color: #666;
  font-size: 14px;
}

.auth-tabs span {
  cursor: pointer;
  padding-bottom: 6px;
}

.auth-tabs .active {
  color: #4490f1;
  border-bottom: 2px solid #4490f1;
  font-weight: 600;
}

.quick-link,
.user-dropdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #333;
  cursor: pointer;
}

.quick-link:hover,
.user-dropdown:hover {
  color: #4490f1;
}
</style>
