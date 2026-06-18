# CLAUDE.md — hashmatrix-data-foundation 协作与合规指引

本文件为 Claude Code 及所有协作者在本仓库工作的**强制约束**。违反「信息红线」的内容一律不得提交。

## 🔴 信息红线（强制 · 不可协商）

本仓库为**公开开源仓库**。所有内容（代码、注释、文档、配置样例、提交信息、Issue/PR、分支与标签名）必须满足：

1. **禁止出现任何甲方/客户可识别信息**，包括但不限于：真实单位名称/简称/品牌、人员姓名或账号、招标/合同/立项编号、内部项目代号、甲方专有业务术语、真实数据、具体部署地点、客户网络或系统拓扑。
2. **禁止透漏任何项目机密**：商务/合同条款、里程碑与报价、验收细节、甲方环境参数、真实业务数据样本。
3. **仅允许记录可面向大众公开的内容**：通用技术方案、代码实现、系统架构与产品决策、开源组件选型、通用工程最佳实践。
4. **示例/测试数据一律虚构脱敏**，使用通用占位（如 `example.com`、`acme`、`tenant-demo`），严禁使用任何真实甲方数据。
5. **敏感原始资料一律置于 `.gitignore`、不得入库**（仅本地留存）。

> 判定标准：把本仓任意文件公开到互联网，不会泄露任何客户身份或项目机密。不确定时一律按「不写入」处理。

## 提交前自检（每次 commit / PR 必过）

- [ ] 无甲方名称 / 编号 / 代号 / 人员 / 地点等可识别信息
- [ ] 无商务 / 合同 / 验收 / 报价等项目机密
- [ ] 示例数据均为虚构 / 脱敏
- [ ] 敏感原始资料未入库（已在 `.gitignore`）
- [ ] 提交信息与分支/标签名同样不含上述敏感信息

## 🧭 北极星：产品形态与多租户模式（开发者时刻谨记）

本平台**双模交付**，所有设计与代码都须按此模式思考：

| | 公网 SaaS | 私有化部署 |
|--|--|--|
| 运营 / 品牌 | 我们运营 · **我们公司统一品牌** | 客户环境 · **客户品牌（部署级）** |
| 租户 = | 企业客户 | 客户的部门 |

- **品牌是部署级**（部署期配置注入），**不按租户在运行期动态换肤**。
- **多租户隔离（C 分层桥接）**：控制平面共享 + 数据平面按租户隔离。身份 = Keycloak **Organizations 单 realm**（org=租户，JWT 带 tenant 声明）；数据 = **schema/db-per-tenant**；计算 = **namespace-per-tenant**；由 `control-plane` 编排开通。

**本仓视角（data-foundation）**：采集 / 计算 / 湖仓**按租户隔离 catalog/database**（Doris/Paimon per-tenant）；作业与资源消耗受该租户配额（ResourceQuota + 业务配额）约束。

> 全局定义见主仓 `docs/00-主仓初始化-spec.md` 与 `docs/architecture/05-多租户与控制平面.md`。

## 🔗 契约（Contracts）—— 跨子系统集成

本项目经**契约**与其它子系统集成。契约的**单一事实源在主仓** `HashMatrixData/hashmatrix` 的 `contracts/`：
- 索引（机器可读）`contracts/registry.yaml` · 规范 `contracts/CONVENTIONS.md` · 设计 `docs/architecture/06-契约治理.md`
- 在线：https://github.com/HashMatrixData/hashmatrix/tree/main/contracts

**铁律**：先改契约、再改实现；加法兼容默认放行，破坏性走 MAJOR + 弃用期双跑 + 通知消费方；消费方一律 tolerant reader。

**本仓契约**：
- producer：暂无
- consumer：`icd/tenant-context-headers`、`icd/governance-metadata`

**如何查阅（随时拉最新，勿存本地副本）**：
- 在 superproject（`hashmatrix/services/<本仓>`）下：直接读 `../../contracts/`。
- 独立 clone：WebFetch `https://raw.githubusercontent.com/HashMatrixData/hashmatrix/main/contracts/registry.yaml`（公开仓免鉴权）→ 按 registry 取对应契约；或 `gh api repos/HashMatrixData/hashmatrix/contents/contracts/<path> -H "Accept: application/vnd.github.raw"`。

## 仓库定位

数据基础分系统：流批采集、湖仓一体存储、统一计算、向量/非结构化接入、Connector SPI。

### 工程脚手架（已落地·核心纵切）

Maven 多模块（`<parent>` = 平台 `hashmatrix-platform-parent`，import `hashmatrix-bom`，**坐标引用、不依赖 submodule 路径**）：

| 模块 | 职责 |
|--|--|
| `connector-spi` | 连接器 SPI 主干（纯 Java）：连接 / 元数据扫描（旁路·取结构）/ 读写（主链路·搬行）；ServiceLoader 注册表 + per-tenant `TenantCatalog` + 契约 TCK（test-jar） |
| `connector-jdbc` | 标准 JDBC 参考实现（PG/MySQL）+ `Dialect` SPI；H2 契约测试；**schema-per-tenant** |
| `connector-dialect-{dm,kingbase,oceanbase}` | 国产库方言插件**占位**（实现 `Dialect` + `META-INF/services`）——**新增库 = 加一个 jar，主干零改动** |
| `ingest` | Flink 流采集骨架（本地 mini-cluster 可跑）+ 租户配额护栏；Flink-CDC / SeaTunnel 接入占位 |
| `app` | Spring Boot 服务骨架：装配 SPI 注册表、连接器/方言清单 + 健康探针；`mvn package` 出可运行 jar |

**边界（AD-15）**：数据采集（本仓·主链路·搬行）≠ 元数据采集（`governance`·旁路·取结构）——二者**共用同一套 Connector SPI**，连接器只实现一次。

**构建/运行红线**：凭据经 `DataSourceConfig.secretRef` → 环境变量/Secret 在运行期解析，**禁止硬编码**；制品仓鉴权（`read:packages`）经环境变量注入 `ci/settings.xml`，**密钥不入库**。

技术栈与具体选型**待独立讨论后逐步丰富**，湖仓 per-tenant catalog（Doris/Paimon）、Helm 子 chart 汇入主仓、CDC/SeaTunnel 实链路为后续轮次。
