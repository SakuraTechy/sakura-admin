#!/bin/sh
set -eu

database="${SCHEDULE_DATABASE:-continew_admin_job}"
case "$database" in
  ''|*[!A-Za-z0-9_]* )
    echo "SCHEDULE_DATABASE 只能包含字母、数字和下划线：$database" >&2
    exit 1
    ;;
esac

# 首次初始化时创建独立调度库，避免调度表与 Admin 业务表混用。
mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS \`$database\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
