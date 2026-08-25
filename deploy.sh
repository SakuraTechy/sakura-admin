#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE_ROOT="$SCRIPT_DIR/continew-webapi/target/app"
DESTINATION_ROOT="$SCRIPT_DIR/docker/continew-admin"
SCHEDULE_SOURCE="$SCRIPT_DIR/continew-extension/continew-extension-schedule-server/target/continew-extension-schedule-server.jar"
SCHEDULE_DESTINATION_ROOT="$SCRIPT_DIR/docker/schedule-server"
PLAYWRIGHT_SOURCE="${SAKURA_PLAYWRIGHT_SOURCE:-$SCRIPT_DIR/../sakura-playwright}"
PLAYWRIGHT_SOURCE="$(CDPATH= cd -- "$PLAYWRIGHT_SOURCE" && pwd)"
PLAYWRIGHT_DESTINATION_ROOT="$SCRIPT_DIR/docker/sakura-playwright"
SKIP_PACKAGE=0

usage() {
    echo "用法：$0 [--skip-package]" >&2
    exit 2
}

if [ "$#" -gt 1 ]; then
    usage
fi

if [ "$#" -eq 1 ]; then
    [ "$1" = "--skip-package" ] || usage
    SKIP_PACKAGE=1
fi

if [ "$SKIP_PACKAGE" -eq 0 ]; then
    command -v mvn >/dev/null 2>&1 || {
        echo "未找到 Maven 命令 mvn，请先配置 Maven 环境变量。" >&2
        exit 1
    }

    echo "开始执行 mvn clean package..."
    mvn -f "$SCRIPT_DIR/pom.xml" clean package
else
    echo "跳过 Maven 打包，直接复制已有 target/app 产物。"
fi

if [ ! -d "$SOURCE_ROOT" ]; then
    echo "未找到构建产物目录：$SOURCE_ROOT" >&2
    exit 1
fi

if [ ! -f "$SCHEDULE_SOURCE" ]; then
    echo "未找到调度服务 JAR：$SCHEDULE_SOURCE" >&2
    exit 1
fi

if [ ! -d "$PLAYWRIGHT_SOURCE" ]; then
    echo "未找到 sakura-playwright 源码目录：$PLAYWRIGHT_SOURCE，可通过 SAKURA_PLAYWRIGHT_SOURCE 指定目录。" >&2
    exit 1
fi

for required_file in package.json package-lock.json src/index.js; do
    if [ ! -f "$PLAYWRIGHT_SOURCE/$required_file" ]; then
        echo "sakura-playwright 缺少必要文件：$PLAYWRIGHT_SOURCE/$required_file" >&2
        exit 1
    fi
done

mkdir -p "$DESTINATION_ROOT"

# 只清理 target/app 中同名的部署产物，保留 Dockerfile 和前端 html 目录。
for item in "$SOURCE_ROOT"/* "$SOURCE_ROOT"/.[!.]* "$SOURCE_ROOT"/..?*; do
    [ -e "$item" ] || continue
    name=$(basename "$item")
    destination_path="$DESTINATION_ROOT/$name"
    rm -rf -- "$destination_path"
    cp -R -- "$item" "$DESTINATION_ROOT/"
done

echo "部署文件已复制：$SOURCE_ROOT -> $DESTINATION_ROOT"
echo "Dockerfile 和 html 目录未被覆盖。"

mkdir -p "$SCHEDULE_DESTINATION_ROOT"
cp -f -- "$SCHEDULE_SOURCE" "$SCHEDULE_DESTINATION_ROOT/"
echo "调度服务 JAR 已复制：$SCHEDULE_SOURCE -> $SCHEDULE_DESTINATION_ROOT"

command -v tar >/dev/null 2>&1 || {
    echo '未找到 tar，无法复制 sakura-playwright 部署目录。' >&2
    exit 1
}
mkdir -p "$PLAYWRIGHT_DESTINATION_ROOT"
# 排除源码仓库、开发机依赖、执行产物、运行数据和本地凭据；npm 依赖与 Chromium 由容器启动命令安装。
tar -C "$PLAYWRIGHT_SOURCE" \
    --exclude='.git' \
    --exclude='.agents' \
    --exclude='node_modules' \
    --exclude='artifacts' \
    --exclude='data' \
    --exclude='logs' \
    --exclude='.env' \
    -cf - . | tar -C "$PLAYWRIGHT_DESTINATION_ROOT" -xf -
echo "sakura-playwright 已复制：$PLAYWRIGHT_SOURCE -> $PLAYWRIGHT_DESTINATION_ROOT"
