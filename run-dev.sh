#!/usr/bin/env bash
# 本地调试启动脚本（连 dev 环境中间件，沿用之前验证可用的参数组合）
#   profile  : local（application-local.yml）
#   MySQL    : dev(gz-cynosdbmysql-grp-q0cjbw8l.sql.tencentcdb.com:20798) root/Lwb@8832652
#   Redis    : 110.42.41.113:6379 (密码 lwb8832652)
#   jwt      : 本地固定 secret，便于自签 token 调试
#   应用端口 8081，远程调试端口 5005，computer proxy 18085，custom-page proxy 18082
#
# 注意：连远端 Redis 时 RTT 较高，admin 账号登录会走 AuthServiceImpl.createToken 的
#       N+1 次 GET（遍历 user-token:<uid> 历史 token），可能耗时几十秒；建议直接用
#       dev 前端已登录的 token（浏览器 Authorization / cookie ticket）来调试。
set -e
cd "$(dirname "$0")/app-platform-bootstrap/app-platform-web-bootstrap"

JAR="target/app-platform-web-bootstrap-0.0.1-SNAPSHOT.jar"
[ -f "$JAR" ] || { echo "未找到 $JAR，请先执行: mvn -B -DskipTests clean install"; exit 1; }

MYSQL_URL='jdbc:mysql://gz-cynosdbmysql-grp-q0cjbw8l.sql.tencentcdb.com:20798/%s?serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowMultiQueries=true&socketTimeout=300000&useSSL=false'

exec java -Xms1g -Xmx2g \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar "$JAR" \
  --spring.profiles.active=local \
  --spring.datasource.dynamic.datasource.master.url="$(printf "$MYSQL_URL" agent_platform)" \
  --spring.datasource.dynamic.datasource.master.username=root \
  --spring.datasource.dynamic.datasource.master.password=Lwb@8832652 \
  --spring.datasource.dynamic.datasource.doris.url="$(printf "$MYSQL_URL" agent_custom_table)" \
  --spring.datasource.dynamic.datasource.doris.username=root \
  --spring.datasource.dynamic.datasource.doris.password=Lwb@8832652 \
  --spring.data.redis.host=110.42.41.113 \
  --spring.data.redis.port=6379 \
  --spring.data.redis.password=lwb8832652 \
  --search.elasticsearch.password=elastic123 \
  --jwt.secretKey=nuwax_local_debug_secret_key_please_change_me \
  --logging.level.com.xspaceagi.agent.core.infra.proxy=DEBUG \
  --logging.level.com.xspaceagi.agent.core.infra.rpc=DEBUG \
  --nuwax.im.qq.enabled=false \
  "$@"
