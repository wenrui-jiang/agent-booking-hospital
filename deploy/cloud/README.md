# Cloud deployment notes

Target: Ubuntu 24.04 EC2 in Tokyo, with only SSH, HTTP, and HTTPS open publicly.

Runtime layout:
- Docker Compose runs MySQL, MongoDB, Redis, RabbitMQ, and Nacos on localhost.
- Java services run on the host from built JARs.
- Nuxt runs on localhost:3000.
- Nginx listens on public port 80 and proxies `/api/` to gateway localhost:8080 and all other traffic to Nuxt.

Basic runbook:

```bash
sudo bash deploy/cloud/bootstrap-ubuntu.sh
cp deploy/cloud/.env.example deploy/cloud/.env
vim deploy/cloud/.env
docker compose --env-file deploy/cloud/.env -f deploy/cloud/docker-compose.yml up -d

cd "代码/代码/02-尚医通后端代码/yygh_parent"
mvn -DskipTests package

cd -
cd "代码/代码/03-尚医通前端代码/yygh-site"
npm install
npm run build

cd -
YYGH_MYSQL_PASSWORD=<mysql-root-password> bash scripts/manage-services.sh start
```
