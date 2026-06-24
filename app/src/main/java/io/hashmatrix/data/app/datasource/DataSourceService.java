package io.hashmatrix.data.app.datasource;

import io.hashmatrix.data.app.crypto.CryptoService;
import io.hashmatrix.starter.web.BusinessException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源保存/列表（WP3）—— 落 <b>D7</b>（口令加密存）+ <b>D9</b>（按 {@code tenant_id} 强制隔离）。
 *
 * <p>租户标识由调用方（控制器）从 {@code X-Tenant-Id} 取定后传入；本服务一律以该 {@code tenantId}
 * 作为读写边界——写时落、读时 {@link DataSourceRepository#findByTenantId}，不提供任何跨租户取数路径。
 *
 * <p>口令经 {@link CryptoService} AES-GCM 加密为 {@code secret_cipher} 落库；明文不入实体、不入日志（D7）。
 */
@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final CryptoService crypto;

    public DataSourceService(DataSourceRepository repository, CryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    /**
     * 登记一个数据源（口令加密落库）。
     *
     * @param password 明文口令（即用即弃：仅在此加密为密文，绝不存明文）
     * @throws BusinessException 同租户内名称冲突（409）
     */
    @Transactional
    public DataSourceEntity create(
            String tenantId,
            String name,
            String type,
            String host,
            int port,
            String database,
            String username,
            String password) {
        if (repository.existsByTenantIdAndName(tenantId, name)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DATASOURCE_NAME_CONFLICT", "数据源名称已存在：" + name);
        }
        String secretCipher = crypto.encrypt(password);
        DataSourceEntity entity =
                DataSourceEntity.create(
                        tenantId, name, type, host, port, database, username, secretCipher);
        return repository.save(entity);
    }

    /** 列出本租户的全部数据源（D9）。 */
    @Transactional(readOnly = true)
    public List<DataSourceEntity> list(String tenantId) {
        return repository.findByTenantId(tenantId);
    }
}
