# Playwright Runner 需要 Node.js 20；从官方运行时阶段复制 Node/npm，避免使用 Debian 默认的旧版本。
FROM node:20-bookworm-slim AS node-runtime

# bellsoft/liberica-openjdk-alpine:17.0.14 仅包含必要运行时，体积小（约 128MB），适用于对镜像大小敏感的场景
# bellsoft/liberica-openjdk-debian:17.0.14 为全量 JDK 版本，包含更多系统工具与依赖，兼容性更好
# 若对镜像大小有严格要求，可将当前镜像替换为 alpine 版本
FROM bellsoft/liberica-openjdk-debian:17.0.14

MAINTAINER Charles7c charles7c@126.com

# Playwright --with-deps 会通过 apt 安装 Chromium 系统依赖；构建时切换到国内 Debian 镜像。
ARG DEBIAN_MIRROR=mirrors.aliyun.com
RUN set -eux; \
    for source_file in /etc/apt/sources.list /etc/apt/sources.list.d/*.sources; do \
        [ -f "$source_file" ] || continue; \
        sed -i -E "s#https?://(deb.debian.org|security.debian.org)#https://${DEBIAN_MIRROR}#g" "$source_file"; \
    done

# 批次共享录屏在关闭浏览器后由 finalizer 使用 ffmpeg 按用例时间切片。
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ffmpeg; \
    rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=./bin/*.jar
COPY ${JAR_FILE} /app/bin/app.jar
# Admin 在容器内直接启动 Playwright Runner，必须同时具备 Node.js、npm 和 npx。
# 整体复制 /usr/local 以保留官方 Node 镜像入口脚本的符号链接及其依赖。
COPY --from=node-runtime /usr/local/ /usr/local/
WORKDIR /app/bin

ENTRYPOINT ["java", \
            "-XX:+UseZGC", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dspring.profiles.active=prod", \
            "-jar", \
            "/app/bin/app.jar"]

# ENTRYPOINT ["java", \
#             "-XX:+UseZGC", \
#             "-Djava.security.egd=file:/dev/./urandom", \
#             "-Dspring.profiles.active=prod", \
#             "-cp", \
#             "/app/bin/app.jar:/app/lib/*:/app/config", \
#             "top.continew.admin.ContiNewAdminApplication"]            
