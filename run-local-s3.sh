#!/usr/bin/env bash
# 本地调试启动脚本（S3/MinIO 存储版）
# 与 run-local.sh 的唯一区别：storage.type=s3，对象存储指向远程 MinIO。
# 想切回本地磁盘存储，改用 ./run-local.sh 启动即可（两者互不干扰）。
# 使用 application-local.yml，中间件复用 docker 中已部署的 nuwax 服务
#   MySQL  : localhost:13306 (agent_platform / agent_custom_table, 用户 agent_platform)
#   Redis  : localhost:16379 (密码 123456)
#   Milvus : localhost:19530
#   ES     : localhost:9200  (elastic / elastic123)
#   MinIO  : 110.42.41.113:9000 (minioadmin/minioadmin, bucket=agent)
# 应用端口 8081，远程调试端口 5005
set -e
cd "$(dirname "$0")/app-platform-bootstrap/app-platform-web-bootstrap"

JAR="target/app-platform-web-bootstrap-0.0.1-SNAPSHOT.jar"
[ -f "$JAR" ] || { echo "未找到 $JAR，请先执行: mvn -B -DskipTests clean install"; exit 1; }

MYSQL_URL='jdbc:mysql://localhost:13306/%s?serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowMultiQueries=true&socketTimeout=300000&useSSL=false'

exec java -Xms1g -Xmx2g \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar "$JAR" \
  --spring.profiles.active=local \
  --spring.datasource.dynamic.datasource.master.url="$(printf "$MYSQL_URL" agent_platform)" \
  --spring.datasource.dynamic.datasource.master.username=agent_platform \
  --spring.datasource.dynamic.datasource.master.password=admin123 \
  --spring.datasource.dynamic.datasource.doris.url="$(printf "$MYSQL_URL" agent_custom_table)" \
  --spring.datasource.dynamic.datasource.doris.username=agent_platform \
  --spring.datasource.dynamic.datasource.doris.password=admin123 \
  --spring.data.redis.host=localhost \
  --spring.data.redis.port=16379 \
  --spring.data.redis.password=123456 \
  --search.elasticsearch.password=elastic123 \
  --jwt.secretKey=nuwax_local_debug_secret_key_please_change_me \
  --storage.type=s3 \
  --s3.endpoint=http://110.42.41.113:9000 \
  --s3.access-key=minioadmin \
  --s3.secret-key=minioadmin \
  --s3.bucket-name=agent \
  --s3.region=us-east-1 \
  --MODEL_API_BASE_URL=http://host.docker.internal:18086
