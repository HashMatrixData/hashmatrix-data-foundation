/**
 * 标准 JDBC 连接器参考实现：把 {@link io.hashmatrix.data.connector.spi.Connector} SPI 落到 JDBC。
 *
 * <p>内置 PostgreSQL / MySQL 方言；方言差异收敛到独立的 {@link io.hashmatrix.data.connector.jdbc.Dialect}
 * SPI——国产库（达梦 / 人大金仓 / OceanBase）经 {@code connector-dialect-*} 插件 jar 注册新方言即可接入，
 * 本模块与采集主干零改动。
 *
 * <p>多租户：参考实现采用 <b>schema-per-tenant</b>，隔离 schema 名由
 * {@link io.hashmatrix.data.connector.spi.TenantCatalog#catalogFor(String)} 解析。
 */
package io.hashmatrix.data.connector.jdbc;
