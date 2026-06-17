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

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/data-foundation`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
