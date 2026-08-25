#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 只读取启动脚本需要的键，避免把 Compose .env 当作任意 shell 脚本执行。
dotenv_value() {
  local key="$1"
  [[ -r "$SCRIPT_DIR/.env" ]] || return 0
  awk -F= -v key="$key" '
    $1 ~ /^[[:space:]]*#/ { next }
    $1 == key {
      value = substr($0, index($0, "=") + 1)
      sub(/[[:space:]]+#.*$/, "", value)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
      exit
    }
  ' "$SCRIPT_DIR/.env"
}

# 安装脚本生成的 Token 文件是 Admin 与 Agent 的唯一共享凭据来源。
agent_env_file_from_dotenv="$(dotenv_value SAKURA_AGENT_ENV_FILE)"
export SAKURA_AGENT_ENV_FILE="${SAKURA_AGENT_ENV_FILE:-${agent_env_file_from_dotenv:-/etc/sakura-execution-agent/agent.env}}"
[[ -r "$SAKURA_AGENT_ENV_FILE" ]] || {
  echo "Agent Token 文件不可读：$SAKURA_AGENT_ENV_FILE" >&2
  echo "请先执行 sakura-execution-agent/scripts/install-agent.sh。" >&2
  exit 1
}

# 显式传入 PROJECT_URL 时保留调用方配置，否则自动选择本机出站网卡地址。
project_url_from_dotenv="$(dotenv_value PROJECT_URL)"
if [[ -z "${PROJECT_URL:-}" && -n "$project_url_from_dotenv" ]]; then
  export PROJECT_URL="$project_url_from_dotenv"
fi
if [[ -z "${PROJECT_URL:-}" ]]; then
  server_ip="${SERVER_IP:-$(dotenv_value SERVER_IP)}"
  if [[ -z "$server_ip" ]]; then
    server_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
  fi
  if [[ -z "$server_ip" ]]; then
    server_ip="$(hostname -I 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i !~ /^127\./) { print $i; exit }}')"
  fi
  [[ "$server_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || {
    echo "无法自动获取服务器 IPv4 地址，请设置 SERVER_IP，例如：SERVER_IP=172.19.5.223 bash start-docker.sh" >&2
    exit 1
  }
  nginx_host_port="${NGINX_HOST_PORT:-$(dotenv_value NGINX_HOST_PORT)}"
  export PROJECT_URL="http://${server_ip}:${nginx_host_port:-5183}"
fi

if [[ "$#" -eq 0 ]]; then
  set -- up -d --build
fi

echo "PROJECT_URL=$PROJECT_URL"
echo "SAKURA_AGENT_ENV_FILE=$SAKURA_AGENT_ENV_FILE"
exec docker compose "$@"
