# hashmatrix-data-foundation

> hashmatrix 数据中台子模块 · 所属：数据基础分系统
>
> 主仓：[HashMatrixData/hashmatrix](https://github.com/HashMatrixData/hashmatrix)

## 产品形态与多租户（北极星）

**双模交付**：公网 SaaS（我们运营 · 统一**我们品牌** · 租户=企业）／私有化部署（客户环境 · **客户品牌**部署级 · 租户=客户部门）。品牌**部署级**、不按租户运行期换肤。多租户走 **C 分层桥接**：控制平面共享 + 数据平面按租户隔离（Keycloak Organizations 单 realm · schema/db-per-tenant · namespace-per-tenant），由 `control-plane` 编排开通。

**本仓视角**：采集 / 计算 / 湖仓按租户隔离 catalog/db，作业受租户配额约束。

> 详见主仓 `docs/00-主仓初始化-spec.md`、`docs/architecture/05-多租户与控制平面.md`。

## 职责

流批采集、湖仓一体存储、统一计算、向量/非结构化接入、Connector SPI。

## 技术栈

Java/Flink（**具体技术选型待独立讨论，逐步丰富**）

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/data-foundation`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
