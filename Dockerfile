# syntax=docker/dockerfile:1
# 多阶段构建：只 clone 本仓即可产出可运行镜像（构建期从 GitHub Packages 解析平台 parent/BOM）。

# ---- build ----
# 凭据策略：用户名非密（公开 GitHub 账号）走 build-arg；token 走 BuildKit secret（--mount=type=secret），
# 仅在该 RUN 内存可见、绝不写入任何构建层历史 —— 故 build 阶段中间镜像即便被缓存/外泄也不含 token。
FROM maven:3.9-eclipse-temurin-17 AS build
ARG GH_PACKAGES_USER
WORKDIR /workspace
COPY . .
# 仅打 app 及其依赖模块（-am）；测试在 CI 单独跑，镜像构建跳过以加速。
# token 从 /run/secrets/gh_token 读入环境变量，供 ci/settings.xml（${env.GH_PACKAGES_TOKEN}）解析制品仓。
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=gh_token,required=true \
    GH_PACKAGES_USER="${GH_PACKAGES_USER}" \
    GH_PACKAGES_TOKEN="$(cat /run/secrets/gh_token)" \
    mvn -B -ntp -s ci/settings.xml -pl app -am -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:17-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser
WORKDIR /app
COPY --from=build /workspace/app/target/app-*.jar /app/app.jar
USER 1001
EXPOSE 8084 9084
# 健康检查命中 actuator 探针（管理口 9084；status=UP 才算健康）
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:9084/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
