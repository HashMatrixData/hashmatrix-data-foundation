/**
 * Connector SPI 主干契约（纯 Java、零框架依赖）。
 *
 * <p><b>一次实现，两处复用</b>（边界 AD-15）：数据采集（本仓·主链路·搬行数据）与元数据采集
 * （{@code governance}·旁路·取结构）<b>共用同一套 SPI</b>，连接器只实现一次。
 *
 * <ul>
 *   <li>连接：{@link io.hashmatrix.data.connector.spi.Connector} /
 *       {@link io.hashmatrix.data.connector.spi.ConnectorConnection}</li>
 *   <li>元数据扫描（取结构）：{@link io.hashmatrix.data.connector.spi.MetadataScanner}</li>
 *   <li>读写（搬行）：{@link io.hashmatrix.data.connector.spi.RecordReader} /
 *       {@link io.hashmatrix.data.connector.spi.RecordWriter}</li>
 * </ul>
 *
 * <p><b>加插件不动主干</b>：新增数据源 = 加一个实现本 SPI 的 jar，并经
 * {@link java.util.ServiceLoader}（{@code META-INF/services}）注册，由
 * {@link io.hashmatrix.data.connector.spi.ConnectorRegistry} 发现——主干零改动。
 *
 * <p><b>多租户</b>：SPI 不直接读线程级租户上下文（保持纯净、可被旁路链路复用）；隔离键
 * （per-tenant catalog/database）由调用方经 {@link io.hashmatrix.data.connector.spi.TenantCatalog}
 * 解析后以参数下传。
 *
 * <p><b>红线</b>：SPI 与示例一律不含任何真实凭据/主机/甲方信息；凭据经
 * {@link io.hashmatrix.data.connector.spi.DataSourceConfig} 的 secret 引用在运行期注入，禁止硬编码。
 */
package io.hashmatrix.data.connector.spi;
