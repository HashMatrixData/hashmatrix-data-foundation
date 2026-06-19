# hashmatrix-data-foundation

> hashmatrix 数据中台子模块 · 所属：数据源接入 + 计算处理 + 存储（数据底座）
>
> 主仓：[HashMatrixData/hashmatrix](https://github.com/HashMatrixData/hashmatrix)

## 角色与位置（一眼看懂）

- **所属**：横跨**数据源接入层 / 计算处理层 / 存储层**——平台的数据底座。
- **一句话**：把外部数据"接进来、算出来、存下去"——流批采集 + 湖仓一体 + 统一计算 + 向量/非结构化。
- **数据流**：外部源 → **Connector SPI → 采集(Flink/SeaTunnel) → 湖仓(Paimon/Doris)** → 数据服务 API → 应用/BI/大屏。

## 职责与边界

- **做**：实时 CDC / 批量采集、入湖入仓、统一计算、向量/非结构化接入、**Connector SPI**（达梦/金仓/OceanBase 方言插件）。
- **不做（边界）**：不写业务应用逻辑；治理（元数据/血缘）由 `governance` 旁路采集；BI 取数经数据服务/Doris。

## 骨架技术选型（首选 · 待逐仓细化）

| 维度 | 选型 |
|--|--|
| 流 / CDC | **Flink + Kafka + Flink-CDC** |
| 批 | **SeaTunnel** / Spark |
| 湖仓 | **Paimon**（湖）+ MinIO · **Doris**（仓 / OLAP） |
| 向量 / 搜索 | Milvus · Elasticsearch |
| 数据源接入 | **Connector SPI**（标准 JDBC/CDC + 方言插件：达梦/金仓/OceanBase） |
| 运行时 | Java / Flink |

> 信创刚需收敛于 Connector 插件：新增一种数据库 = 加一个插件 jar，不动主干（见架构 03/04）。

## 产品形态与多租户（北极星）

**双模交付**：公网 SaaS（我们运营 · 统一**我们品牌** · 租户=企业）／私有化部署（客户环境 · **客户品牌**部署级 · 租户=客户部门）。品牌**部署级**、不按租户运行期换肤。多租户走 **C 分层桥接**：控制平面共享 + 数据平面按租户隔离（Keycloak Organizations 单 realm · schema/db-per-tenant · namespace-per-tenant），由 `control-plane` 编排开通。

**本仓视角**：采集 / 计算 / 湖仓按租户隔离 catalog/db，作业受租户配额约束。

> 详见主仓 `docs/00-主仓初始化-spec.md`、`docs/architecture/05-多租户与控制平面.md`。

## 模块结构

```
data-foundation/                  # reactor（<parent>=平台 parent，import BOM）
├── connector-spi/                # SPI 主干：连接/元数据扫描/读写 + ServiceLoader 注册表 + TenantCatalog + TCK
├── connector-jdbc/               # JDBC 参考实现（PG/MySQL）+ Dialect SPI，H2 契约测试（schema-per-tenant）
├── connector-dialect-dm/         # 达梦方言插件占位 ┐
├── connector-dialect-kingbase/   # 人大金仓方言插件占位 ├ 新增库 = 加一个 jar，主干零改动
├── connector-dialect-oceanbase/  # OceanBase 方言插件占位 ┘
├── ingest/                       # Flink 流采集骨架（mini-cluster 可跑）+ 租户配额；CDC/SeaTunnel 占位
└── app/                          # Spring Boot 服务骨架：SPI 注册表 + 连接器/方言清单 + 健康探针
```

**核心：Connector SPI「加插件不动主干」**——每种数据源实现 `Connector`（或 JDBC 方言实现 `Dialect`），经
`META-INF/services` 注册即被 `ServiceLoader` 发现。新增数据库 = 加一个插件 jar，主干与采集链路零改动。

**边界（AD-15）**：数据采集（本仓·主链路·搬行）≠ 元数据采集（`governance`·旁路·取结构）——
二者**共用同一套 Connector SPI**，连接器只实现一次（`MetadataScanner` 取结构、`RecordReader/Writer` 搬行）。

## 构建与运行

### 前置：制品仓鉴权（一次性）

平台公共 `parent`/`bom`/`starter-*`（`io.hashmatrix:*`）发布在 **GitHub Packages**，拉取需 `read:packages`。
`<parent>` 在 POM `<repositories>` 之前解析，故制品仓必须经 **settings.xml**（本仓已提供 `ci/settings.xml`，
凭据走环境变量、**不入库**）声明：

```bash
export GH_PACKAGES_USER=<你的 GitHub 用户名>
export GH_PACKAGES_TOKEN=<带 read:packages 的 PAT>
```

> 内网/信创交付：制品镜像到内网 Nexus/Artifactory，改 `ci/settings.xml` 的 url，或用平台 parent 的 `-Pxinchuang`。

### 只 clone 本仓即可构建

```bash
mvn -s ci/settings.xml package          # 多模块构建 + 测试（含 connector-jdbc 契约测试、Flink mini-cluster 集成测试）
```

### 本地独立运行/调试

```bash
docker compose -f docker-compose.local.yml up --build      # PG + MinIO + app
curl -s http://localhost:9084/actuator/health              # 管理口 9084 · 期望 {"status":"UP"}
curl -s http://localhost:8084/api/connectors               # 应用口 8084 · 已注册连接器(jdbc) + 方言(postgresql/mysql/dm/kingbase/oceanbase/ansi)
```

Flink 流采集样例（本地 mini-cluster，无需集群）：

```bash
mvn -s ci/settings.xml -pl ingest -am exec:java \
  -Dexec.mainClass=io.hashmatrix.data.ingest.flink.SampleStreamIngestJob
```

## 多租户

采集/计算/湖仓按租户隔离 catalog/database：`TenantCatalog.catalogFor("acme") -> df_acme`
（JDBC 参考实现采用 schema-per-tenant；Doris/Paimon per-tenant 遵同一约定）。作业受租户配额
（`QuotaGuard`）约束。租户上下文由网关注入的 `X-Tenant-*` 头经 `starter-tenant` 绑定
（契约 `icd/tenant-context-headers`）。

## 红线

凭据经 `DataSourceConfig.secretRef` → 环境变量/Secret 在运行期解析，**禁止硬编码**；示例/测试数据一律脱敏
（`acme` / `tenant-demo`）。详见 [`CLAUDE.md`](CLAUDE.md)。

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/data-foundation`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
