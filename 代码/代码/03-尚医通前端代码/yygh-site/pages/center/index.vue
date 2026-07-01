<template>
  <div class="center-page page-component">
    <div class="center-hero">
      <div>
        <div class="eyebrow">个人中心</div>
        <h1>{{ displayName }}，欢迎回来</h1>
        <p>在这里查看挂号订单和管理就诊人；本地演示环境添加就诊人后即可预约。</p>
      </div>
      <el-button type="primary" icon="el-icon-tickets" @click="go('/order')">
        查看挂号订单
      </el-button>
    </div>

    <div class="center-grid">
      <div class="action-card" @click="go('/order')">
        <div class="card-icon order">
          <i class="el-icon-tickets"></i>
        </div>
        <div>
          <h2>挂号订单</h2>
          <p>查看 agent 和网页端创建的挂号记录，继续支付、查看详情或取消订单。</p>
        </div>
      </div>

      <div class="action-card" @click="go('/patient')">
        <div class="card-icon patient">
          <i class="el-icon-user"></i>
        </div>
        <div>
          <h2>就诊人管理</h2>
          <p>维护当前账号下的就诊人。agent 提交挂号时会使用当前账号的第一个就诊人。</p>
        </div>
      </div>

      <div class="action-card" @click="go('/user')">
        <div class="card-icon auth">
          <i class="el-icon-postcard"></i>
        </div>
        <div>
          <h2>实名认证</h2>
          <p>该功能在本地演示环境中仅保留入口，不再阻断就诊人添加和挂号流程。</p>
        </div>
      </div>
    </div>

    <div class="center-section">
      <div class="section-title">
        <span>常用入口</span>
      </div>
      <div class="shortcut-row">
        <el-button icon="el-icon-search" @click="go('/')">查找医院</el-button>
        <el-button icon="el-icon-tickets" @click="go('/order')">我的订单</el-button>
        <el-button icon="el-icon-user" @click="go('/patient')">添加就诊人</el-button>
        <el-button icon="el-icon-postcard" @click="go('/user')">实名认证</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import cookie from 'js-cookie'

export default {
  data() {
    return {
      displayName: '用户'
    }
  },

  mounted() {
    if (!cookie.get('token')) {
      window.location.href = '/'
      return
    }
    this.displayName = cookie.get('name') || '用户'
  },

  methods: {
    go(path) {
      window.location.href = path
    }
  }
}
</script>

<style scoped>
.center-page {
  width: 1200px;
  margin: 0 auto;
  padding: 32px 0 48px;
}

.center-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px;
  background: #f7fbff;
  border: 1px solid #e6f0fb;
  border-radius: 6px;
}

.eyebrow {
  margin-bottom: 8px;
  color: #4490f1;
  font-size: 14px;
  font-weight: 600;
}

.center-hero h1 {
  margin: 0 0 10px;
  color: #222;
  font-size: 24px;
  font-weight: 700;
}

.center-hero p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.center-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-top: 22px;
}

.action-card {
  display: flex;
  gap: 16px;
  min-height: 132px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color .2s, box-shadow .2s, transform .2s;
}

.action-card:hover {
  border-color: #b7d9ff;
  box-shadow: 0 8px 24px rgba(68, 144, 241, .12);
  transform: translateY(-2px);
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border-radius: 6px;
  font-size: 22px;
}

.card-icon.order {
  color: #2f7de1;
  background: #edf5ff;
}

.card-icon.patient {
  color: #1f9d73;
  background: #edf9f4;
}

.card-icon.auth {
  color: #b26a00;
  background: #fff6e6;
}

.action-card h2 {
  margin: 0 0 10px;
  color: #222;
  font-size: 18px;
  font-weight: 700;
}

.action-card p {
  margin: 0;
  color: #666;
  font-size: 14px;
  line-height: 1.7;
}

.center-section {
  margin-top: 24px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.section-title {
  margin-bottom: 18px;
  color: #222;
  font-size: 18px;
  font-weight: 700;
}

.shortcut-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
