# 尚医通本机初始化 SOP

适用日期：2026-06-04

本文记录从拿到项目资料到在新机器上初始化本机开发环境的步骤。目标是优先跑通可演示链路，不完整复刻教学虚拟机环境。

## 1. 项目结构确认

主要目录：

- `backend\yygh_parent`：Spring Cloud 多模块后端主工程。
- `frontend\yygh-site`：Nuxt 前端工程。
- `docs\sql`：平台数据库建表和初始化数据。
- `backend\yygh_parent\hospital-manage`：医院模拟系统模块。
- `deploy\local-data\mongodb\yygh_hosp`：本地演示医院、科室、排班 MongoDB seed。

完整性检查结果：

- 原课程资料已收敛为当前仓库需要的源码、SQL、seed 数据和文档。
- `vue-admin-template-master.zip` 被 Windows 安全策略拦截，不建议绕过。

## 2. 必要软件

建议版本和路径：

- JDK8：`D:\JavaJDK`
- Maven 3.6.x：`D:\apache-maven-3.6.1`
- MySQL 8.0.27：`D:\mysql-8.0.27-winx64`
- Redis 3.0.504：`D:\Redis\Redis-x64-3.0.504`
- Nacos：`D:\Nacos`
- MongoDB Community 4.4.30：`D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30`
- Erlang OTP 25.3：`D:\ErlangOTP`
- RabbitMQ 3.11.23：`D:\Rabbitmq\rabbitmq_server-3.11.23`

官方下载来源：

- MongoDB Community Archive: https://www.mongodb.com/download-center/community/releases/archive
- MongoDB 4.4.30 zip: https://fastdl.mongodb.org/windows/mongodb-windows-x86_64-4.4.30.zip
- RabbitMQ Windows manual install: https://www.rabbitmq.com/docs/install-windows-manual
- RabbitMQ Erlang compatibility: https://www.rabbitmq.com/docs/3.13/which-erlang
- Erlang OTP 25.3: https://github.com/erlang/otp/releases/download/OTP-25.3/otp_win64_25.3.exe

## 3. 环境变量

已写入用户级环境变量：

```powershell
MONGODB_HOME=D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30
ERLANG_HOME=D:\ErlangOTP
RABBITMQ_SERVER=D:\Rabbitmq\rabbitmq_server-3.11.23
RABBITMQ_BASE=D:\Rabbitmq\rabbitmq_base
```

用户 Path 应包含：

```powershell
D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin
D:\ErlangOTP\bin
D:\Rabbitmq\rabbitmq_server-3.11.23\sbin
```

新开的终端才会读取最新用户环境变量。

## 4. 启动中间件

启动 Redis：

```powershell
Start-Process -FilePath "D:\Redis\Redis-x64-3.0.504\redis-server.exe" -ArgumentList "D:\Redis\Redis-x64-3.0.504\redis.windows.conf" -WorkingDirectory "D:\Redis\Redis-x64-3.0.504" -WindowStyle Hidden
```

启动 Nacos：

```powershell
Start-Process -FilePath "cmd.exe" -ArgumentList "/c","set JAVA_HOME=D:\JavaJDK&&set PATH=D:\JavaJDK\bin;%PATH%&&startup.cmd -m standalone" -WorkingDirectory "D:\Nacos\bin" -WindowStyle Hidden
```

启动 MongoDB：

```powershell
Start-Process -FilePath "D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongod.exe" -ArgumentList "--dbpath","D:\MongoDB\data\db","--logpath","D:\MongoDB\log\mongod.log","--logappend","--bind_ip","127.0.0.1","--port","27017" -WorkingDirectory "D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30" -WindowStyle Hidden
```

启动 RabbitMQ：

```powershell
$env:ERLANG_HOME="D:\ErlangOTP"
$env:RABBITMQ_SERVER="D:\Rabbitmq\rabbitmq_server-3.11.23"
$env:RABBITMQ_BASE="D:\Rabbitmq\rabbitmq_base"
$env:Path="$env:ERLANG_HOME\bin;$env:RABBITMQ_SERVER\sbin;$env:Path"
& "D:\Rabbitmq\rabbitmq_server-3.11.23\sbin\rabbitmq-server.bat" -detached
```

端口检查：

```powershell
Test-NetConnection 127.0.0.1 -Port 8848
Test-NetConnection 127.0.0.1 -Port 6379
Test-NetConnection 127.0.0.1 -Port 27017
Test-NetConnection 127.0.0.1 -Port 5672
```

当前已验证：

- Nacos `8848` 可用。
- Redis `6379` 可用。
- MongoDB `27017` 可用。
- RabbitMQ AMQP `5672` 可用。
- RabbitMQ 管理端 `15672` 暂未稳定启用，但不影响项目服务连接。

## 5. MySQL 初始化

本机 MySQL：

- 用户：`root`
- 密码：`1234`
- `123456` 已验证不可用。

导入平台 SQL：

```powershell
Copy-Item "docs\sql\yygh表结构.sql" "D:\Downloads\yygh-env\sql\schema.sql" -Force
Copy-Item "docs\sql\yygh初始化数据.sql" "D:\Downloads\yygh-env\sql\data.sql" -Force

D:\mysql-8.0.27-winx64\bin\mysql.exe -uroot -p1234 --default-character-set=utf8mb4 -e "source D:/Downloads/yygh-env/sql/schema.sql"
D:\mysql-8.0.27-winx64\bin\mysql.exe -uroot -p1234 --default-character-set=utf8mb4 yygh_cmn -e "source D:/Downloads/yygh-env/sql/data.sql"
```

恢复 MongoDB 医院演示数据：

```powershell
& "D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongo.exe" yygh_hosp deploy\local-data\mongodb\yygh_hosp\import-yygh-hosp-seed.js
```

验证：

```powershell
D:\mysql-8.0.27-winx64\bin\mysql.exe -uroot -p1234 -e "SELECT table_schema, COUNT(*) table_count FROM information_schema.tables WHERE table_schema IN ('yygh_cmn','yygh_hosp','yygh_order','yygh_user','yygh_manage') GROUP BY table_schema; SELECT COUNT(*) AS dict_rows FROM yygh_cmn.dict;"
```

当前结果：

- `yygh_cmn`：1 表，`dict` 3396 行。
- `yygh_hosp`：1 表。
- `yygh_order`：3 表。
- `yygh_user`：3 表。
- `yygh_manage`：3 表。

## 6. 构建后端

必须使用 JDK8。JDK11 下会因为 `com.sun.deploy.net.URLEncoder` 导入失败。

```powershell
$env:JAVA_HOME="D:\JavaJDK"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd "backend\yygh_parent"
mvn -DskipTests package
```

已验证 21 个 Maven 模块构建成功。

## 7. 本机启动 service_hosp

不直接修改原始 `application.properties`，用命令行覆盖教学环境配置。

```powershell
$root=(Resolve-Path ".").Path
$logDir=Join-Path $root "runtime-logs"
$java="D:\JavaJDK\bin\java.exe"
$jar=(Resolve-Path "backend\yygh_parent\service\service_hosp\target\service-hosp.jar").Path
$args='-jar "{0}" "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_hosp?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai" --spring.datasource.username=$env:YYGH_MYSQL_USERNAME --spring.datasource.password=$env:YYGH_MYSQL_PASSWORD --spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp --spring.rabbitmq.host=127.0.0.1 --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848' -f $jar
Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logDir "service-hosp.local.out.log") -RedirectStandardError (Join-Path $logDir "service-hosp.local.err.log") -WindowStyle Hidden
```

验证：

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8201/admin/hosp/hospitalSet/findAll" -Method Get
```

当前验证结果：

- `service_hosp` 监听 `8201`。
- 接口返回 `code=200`。
- `data=[]`，因为 `hospital_set` 当前没有业务数据。

## 8. 数据库密码来源

原教学项目配置里曾硬编码 MySQL 密码和教学网段地址。迁移后的仓库不提交真实密码；本机通过已忽略的 `local-services.json` 或环境变量 `YYGH_MYSQL_USERNAME` / `YYGH_MYSQL_PASSWORD` 注入。运行时应覆盖为：

- MySQL：`127.0.0.1:3306`
- MySQL 密码：通过 `YYGH_MYSQL_PASSWORD` 或本机 `local-services.json` 提供
- Redis：`127.0.0.1:6379`
- MongoDB：`127.0.0.1:27017`
- RabbitMQ：`127.0.0.1:5672`
- Nacos：`127.0.0.1:8848`

## 9. 后续建议

下一步先补两件事：

- 使用 `deploy\local-data\mongodb\yygh_hosp` 恢复医院、科室、排班模拟数据；如需联调医院端，再启动 `backend\yygh_parent\hospital-manage`。
- 启动 `service_cmn/service_user/service_order/gateway`，把前台预约链路串起来。

完成后再进入 Agent 改造，不要在环境还不稳定时改业务代码。
## 10. 本地 Agent 演示数据恢复记录

2026-06-09 已确认医院、科室、排班核心数据来自 MongoDB `yygh_hosp`，不是 MySQL 医院列表。课程资料目录已有模拟数据，可直接导入：

```powershell
& "D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongo.exe" --quiet yygh_hosp --eval "var DATA_DIR='C:/Users/ADMINI~1/AppData/Local/Temp/yygh-sample-data'; load('E:/下载/OneDrive - bjtu.edu.cn/各账号共享/Find a job/医疗项目--尚医通/scripts/import-yygh-sample-data.js')"
```

导入后验证：

```powershell
Invoke-RestMethod "http://127.0.0.1:8201/api/hosp/hospital/findHospList/1/10"
Invoke-RestMethod "http://127.0.0.1:8201/api/hosp/hospital/department/1000_0"
Invoke-RestMethod "http://127.0.0.1:8201/api/hosp/hospital/auth/getBookingScheduleRule/1/7/1000_0/200041246"
```

本地演示登录：

```powershell
& "D:\Redis\Redis-x64-3.0.504\redis-cli.exe" -h 127.0.0.1 -p 6379 setex 13900000000 86400 123456
```

前端登录使用手机号 `13900000000`，验证码 `123456`。默认就诊人 `patientId=1`。

订单表结构补丁：

```powershell
& "D:\mysql-8.0.27-winx64\bin\mysql.exe" -uroot -p1234 -e "ALTER TABLE yygh_order.order_info ADD COLUMN schedule_id varchar(50) NULL AFTER depname; CREATE INDEX idx_schedule_id ON yygh_order.order_info(schedule_id);"
```

原因：当前 Java 模型 `OrderInfo.scheduleId` 默认映射 `schedule_id`，但本地导入 SQL 表结构只有 `hos_schedule_id`。不补列会导致 `submitOrder` 返回 `code=201`，服务日志出现 `Unknown column 'schedule_id' in 'field list'`。

启动服务建议继续使用：

```powershell
powershell.exe -ExecutionPolicy Bypass -File "scripts\manage-services.ps1" start
```

`service-order` 必须带本地覆盖参数：

```text
--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp
--yygh.order.mock-hospital-submit=true
```

已验证的 Agent 闭环：

```text
1. 我咳嗽嗓子疼三天，有点发热
2. 体温38度，有痰，没有胸闷和呼吸困难。确认科室，就北京协和，明天上午，默认就诊人
3. 确认挂号
```

期望状态流转：`SYMPTOM_COLLECTING -> BOOKING_CONFIRMING -> VISIT_GUIDING`。本地已验证可创建订单并跳过真实支付。

## 11. 本地登录开发模式

前端手动挂号需要登录。原项目只有手机号短信验证码登录，没有密码登录流程。

本地开发环境使用固定短信验证码：

```text
手机号：13900000000
验证码：123456
```

关键实现：

- 前端短信接口使用 `/api/msm/send/{mobile}`。
- `service_msm` 开启 `yygh.msm.dev-mode=true` 后不调用阿里云短信，直接写 Redis。
- `service_user` 仍按原逻辑校验 Redis 中的验证码并生成 token。
- 前端手机号正则使用 `^1\d{10}$`，可测试 `166/198/199` 等号段。

启动 `service-msm` 时需要包含：

```text
--spring.redis.host=127.0.0.1
--spring.redis.port=6379
--yygh.msm.dev-mode=true
--yygh.msm.dev-code=123456
```

验证：

```powershell
Invoke-RestMethod "http://127.0.0.1/api/msm/send/13900000000"
& "D:\Redis\Redis-x64-3.0.504\redis-cli.exe" -h 127.0.0.1 -p 6379 get 13900000000
```

Redis 应返回 `123456`。
