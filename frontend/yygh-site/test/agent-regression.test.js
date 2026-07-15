const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const agentAssistant = read('components/AgentAssistant.vue')
const request = read('utils/request.js')
const indexPage = read('pages/index.vue')

assert(
  agentAssistant.includes('messages: this.messages.slice(-20).map') &&
  agentAssistant.includes('content: item.text'),
  'Agent chat requests must include recent message history'
)

assert(
  request.includes('function isProtectedRequest') &&
  request.includes('isProtectedRequest(response.config)') &&
  request.includes("cookie.remove('token')"),
  'Login state should only be cleared for protected requests'
)

assert(
  indexPage.includes("getByHosname('北京协和医院')") &&
  (indexPage.includes('[xiehe].concat(list)') || indexPage.includes('this.list.unshift(item)')),
  'Homepage must publicly ensure Beijing Union Hospital is visible'
)

console.log('agent regression checks passed')
