# 部署配置说明（可供 AI 自动检查）

本文用于统一管理项目上线前的关键配置，目标是让任何 AI 在读取后都能回答：

1. 当前环境缺了哪些配置；
2. 哪些配置仍在开发模式；
3. 如何最小成本切换到可用的外部服务。

---

## 1. 配置优先级

本项目的配置优先级：

1. 启动命令参数（`--key=value`）
2. 环境变量（例如 `DEEPSEEK_API_KEY`）
3. `application.properties` 默认值

---

## 2. 必填配置清单

### 2.1 Agent / DeepSeek

- 服务：`service_agent`
- 代码位置：`代码/代码/02-尚医通后端代码/yygh_parent/service/service_agent/src/main/resources/application.properties`
- 关键项：
  - `deepseek.enabled=true`
  - `deepseek.base-url=https://api.deepseek.com`
  - `deepseek.model=deepseek-v4-pro`
  - `deepseek.api-key=${DEEPSEEK_API_KEY:}`

需要提供：

- `DEEPSEEK_API_KEY`（生产必须）

---

### 2.2 邮件验证码（SMTP）

- 服务：`service_msm`
- 代码位置：`代码/代码/02-尚医通后端代码/yygh_parent/service/service_msm/src/main/java/com/atguigu/yygh/msm/service/impl/MsmServiceImpl.java`
- 关键项（通过 `yygh.mail.*` 注入）：
  - `yygh.mail.enabled`（默认 `false`）
  - `yygh.mail.host`
  - `yygh.mail.port`
  - `yygh.mail.username`
  - `yygh.mail.password` 或环境变量 `YYGH_MAIL_PASSWORD`
  - `yygh.mail.auth-username`（可选）
  - `yygh.mail.from-address`（可选）
  - `yygh.mail.from-name`（可选）
  - `yygh.mail.ssl` / `yygh.mail.starttls`

---

### 2.3 当前运行模式（重要）

当前脚本默认以开发模式启动 `service_msm`：

- `--yygh.msm.dev-mode=true`
- `--yygh.msm.dev-code=123456`

在该模式下，验证码走开发逻辑，不代表真实邮件链路可用。

生产建议：

- `yygh.msm.dev-mode=false`
- 完整配置 `yygh.mail.*`

---

## 3. AI 自检流程（可直接执行）

> 让 AI 按顺序执行并输出“缺失项列表”。

1. 检查 DeepSeek Key 是否配置：
   - 查 `service_agent` 的 `deepseek.api-key` 是否仍是 `${DEEPSEEK_API_KEY:}` 占位。
   - 查运行环境是否存在 `DEEPSEEK_API_KEY`。

2. 检查邮件验证码是否仍在开发模式：
   - 查看 `service_msm` 进程参数里是否含 `yygh.msm.dev-mode=true`。

3. 检查 SMTP 必填项是否齐全：
   - `yygh.mail.enabled=true`
   - `yygh.mail.host`
   - `yygh.mail.username`
   - `yygh.mail.password` 或 `YYGH_MAIL_PASSWORD`

4. 结果输出模板：
   - `missing.required`: 缺失的必填配置
   - `running.in.devmode`: 仍在开发模式的服务
   - `security.risk`: 明文密钥、硬编码密码等风险
   - `ready.for.prod`: true/false

---

## 4. 外区邮箱方案（不使用国内服务商）

你已在 Cloudflare 购域名，可直接配置企业域名邮箱。

推荐优先级（按稳定性）：

1. **Google Workspace**（Gmail 商业版）
2. **Microsoft 365**（Outlook 商业版）
3. **Zoho Mail**
4. **Fastmail / Proton Mail Business**

说明：

- 这些方案通常支持域名邮箱（如 `login@yourdomain.com`）。
- 一般不要求中国式企业实名；是否触发身份验证取决于支付风控与地区策略。
- 个人护照/信用卡可完成大多数国际服务开通（以服务商实时规则为准）。

---

## 5. Gmail / Outlook 能否直接用于测试发信？

可以，但建议区分“临时测试”与“正式生产”：

### 5.1 Gmail（测试可用）

- SMTP Host: `smtp.gmail.com`
- 端口：`465`（SSL）或 `587`（STARTTLS）
- 认证：普通密码通常不可用，需 **App Password（应用专用密码）**
- 前提：账号开启 2FA

### 5.2 Outlook（测试可用）

- SMTP Host: `smtp.office365.com`
- 端口：`587`（STARTTLS）
- 认证：推荐 OAuth2；部分账号可用应用密码/SMTP AUTH（受租户策略影响）

### 5.3 生产不建议

- 不建议长期用个人邮箱作为生产验证码发信地址：
  - 配额小、风控严、易限流；
  - 发信信誉不可控；
  - 难做团队运维与权限管理。

---

## 6. SMTP 配置示例（仅示例）

```properties
# 关闭开发验证码模式
yygh.msm.dev-mode=false

# 启用邮件发送
yygh.mail.enabled=true
yygh.mail.host=smtp.gmail.com
yygh.mail.port=465
yygh.mail.username=your.mailbox@yourdomain.com
yygh.mail.password=${YYGH_MAIL_PASSWORD:}
yygh.mail.from-address=your.mailbox@yourdomain.com
yygh.mail.from-name=Your Brand
yygh.mail.ssl=true
yygh.mail.starttls=false
```

---

## 7. 交付前最小验收

1. 发码接口返回成功：`POST /api/msm/email/code`
2. 邮箱收到验证码
3. 验证码登录成功
4. 重置密码成功
5. 密码登录成功
6. 网关路径可访问（如使用 `:8080`）

若任一失败，先确认是否仍在 `yygh.msm.dev-mode=true`。

