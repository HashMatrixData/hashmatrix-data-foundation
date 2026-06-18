# syntax=docker/dockerfile:1
# 多阶段构建：只 clone 本仓即可产出可运行镜像（构建期从 GitHub Packages 解析平台 parent/BOM）。

# ---- build ----
# 注：构建期 token 经 build-arg 传入，仅留在 build 阶段（最终 runtime 镜像不含）。加固项（后续）：
# 改用 BuildKit secret（RUN --mount=type=secret,id=gh_token ...），避免 token 进入构建层历史。
FROM maven:3.9-eclipse-temurin-17 AS build
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
WORKDIR /workspace
COPY . .
# 仅打 app 及其依赖模块（-am）；测试在 CI 单独跑，镜像构建跳过以加速。
RUN --mount=type=cache,target=/root/.m2 \
    GH_PACKAGES_USER="${GH_PACKAGES_USER}" GH_PACKAGES_TOKEN="${GH_PACKAGES_TOKEN}" \
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
EXPOSE 8080
# 健康检查命中 actuator 探针（status=UP 才算健康）
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
