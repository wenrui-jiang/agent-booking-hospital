# Agent Booking Hospital

这是从原 OneDrive 尚医通项目迁移出的本机运行仓库，目标是保留完整前后端和微服务层级，同时去除或替换可见的尚医通/114 平台 logo 与品牌资源，作为后续部署到云服务器的源码基础。

## 本机工作路径

固定本机启动路径：

```powershell
E:\JavaCode2\agent-booking-hospital
```

OneDrive 中的旧项目目录只作为参考、归档或 GitHub clone 位置使用，不作为日常开发和启动路径。启动、构建、排错、依赖安装都应在上面的本地磁盘路径执行，避免 OneDrive 同步 `target`、`node_modules`、`.nuxt`、运行日志等大目录。

## 主要目录

- `backend\yygh_parent`：Spring Cloud / Maven 多模块后端主工程。
- `frontend\yygh-site`：Nuxt 前端。
- `agent-langgraph`：LangGraph 医疗预约智能体服务。
- `deploy\local-data`：本地演示 seed 数据。
- `docs`：本地启动、Agent 集成、功能闭环等说明。
- `scripts`：本机启动、停止、验证和数据导入脚本。

## 本机启动

在仓库根目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\full-stack.ps1 start
```

停止服务：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\full-stack.ps1 stop
```

常用访问地址：

- 前端：`http://localhost:3000`
- 网关：`http://localhost`
- 后端服务端口见 `scripts\manage-services.ps1`。

## 构建验证

后端：

```powershell
cd "E:\JavaCode2\agent-booking-hospital\backend\yygh_parent"
D:\apache-maven-3.6.1\bin\mvn.cmd clean package -DskipTests
```

前端：

```powershell
cd "E:\JavaCode2\agent-booking-hospital\frontend\yygh-site"
npm ci --registry=https://registry.npmmirror.com
$env:NODE_OPTIONS='--openssl-legacy-provider'
npm run build
```

## 品牌资源处理

已替换或抹去可见 logo、favicon、banner、认证示例图、二维码占位图和部分外链图片。前端标题已改为中性的“医疗预约 Agent 演示平台”。

没有对内部包名、模块名、数据库名做大规模重命名，例如 `yygh`、`com.atguigu` 等历史工程命名仍可能存在。它们属于代码结构兼容性问题，发布前如需彻底改名，应单独做一次带完整回归验证的重构。

## 提交边界

`.gitignore` 排除了本地配置、运行日志、Maven `target`、前端 `node_modules` / `.nuxt`、压缩包、证书和环境变量文件。不要提交 `local-services.json` 或任何真实邮箱、短信、云服务密钥。
