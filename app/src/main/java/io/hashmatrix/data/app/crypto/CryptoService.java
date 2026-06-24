package io.hashmatrix.data.app.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 数据源凭据对称加密（落 <b>D7</b>）—— AES-256-GCM。
 *
 * <p><b>主密钥</b>读自 {@code hashmatrix.datasource.crypto.secret-key}（← env {@code DS_SECRET_KEY}），
 * 为 Base64 编码的 32 字节随机密钥，<b>部署期注入、绝不入库</b>。密钥缺失/长度不符则<b>启动即 fail-fast</b>，
 * 拒绝「无密钥起服务」把明文裸奔的风险。
 *
 * <p><b>密文格式</b>：{@code Base64( iv(12B) || ciphertext+tag )}。每次加密用 {@link SecureRandom} 生成
 * 全新 12 字节 IV（GCM 绝不可复用 IV），认证标签 128 位。明文口令<b>绝不进日志/异常信息</b>。
 */
@Service
public class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32; // AES-256
    private static final int IV_BYTES = 12; // GCM 推荐 96-bit IV
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${hashmatrix.datasource.crypto.secret-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "缺少数据源加密主密钥：请经 env DS_SECRET_KEY 注入 Base64 编码的 32 字节密钥（D7，绝不入库）");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // 不回显密钥内容，仅提示格式错误。
            throw new IllegalStateException("数据源加密主密钥不是合法 Base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "数据源加密主密钥长度须为 32 字节（AES-256），实际 " + keyBytes.length + " 字节");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密明文口令，返回 Base64 密文（含随机 IV）。
     *
     * @param plaintext 明文口令（非空）
     * @return {@code Base64(iv || ciphertext+tag)}
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            // 不带明文/密钥进异常信息（D7 红线）。
            throw new IllegalStateException("数据源凭据加密失败", e);
        }
    }

    /**
     * 解密 {@link #encrypt} 产出的密文。
     *
     * @param cipherText Base64 密文（含 IV）
     * @return 明文口令
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText must not be blank");
        }
        byte[] all;
        try {
            all = Base64.getDecoder().decode(cipherText);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("数据源凭据密文不是合法 Base64", e);
        }
        if (all.length <= IV_BYTES) {
            throw new IllegalStateException("数据源凭据密文长度非法");
        }
        byte[] iv = Arrays.copyOfRange(all, 0, IV_BYTES);
        byte[] ct = Arrays.copyOfRange(all, IV_BYTES, all.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 解密失败（含篡改/换密钥导致的 GCM 校验失败）——不带明文/密钥。
            throw new IllegalStateException("数据源凭据解密失败", e);
        }
    }
}
