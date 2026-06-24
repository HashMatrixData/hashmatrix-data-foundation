package io.hashmatrix.data.app.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * {@link CryptoService} 单测（无需 DB）：证明加解密往返、明文不可见、IV 不复用、密钥 fail-fast。
 *
 * <p>守 D7 红线：明文口令绝不出现在密文里，也不进任何字符串表示。
 */
class CryptoServiceTest {

    /** 32 字节脱敏占位主密钥（仅测试）。 */
    private static final String KEY =
            Base64.getEncoder()
                    .encodeToString(
                            "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8));

    private final CryptoService crypto = new CryptoService(KEY);

    @Test
    void roundtrip_returns_original_plaintext() {
        String plaintext = "s3cr3t-acme-demo"; // 脱敏占位口令
        String cipher = crypto.encrypt(plaintext);

        assertThat(crypto.decrypt(cipher)).isEqualTo(plaintext);
    }

    @Test
    void ciphertext_does_not_contain_plaintext() {
        String plaintext = "p@ssw0rd-demo";
        String cipher = crypto.encrypt(plaintext);

        // 密文（Base64）里不得出现明文，明文解码后的字节也不得出现在密文字节中。
        assertThat(cipher).doesNotContain(plaintext);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);
        assertThat(new String(cipherBytes, StandardCharsets.UTF_8)).doesNotContain(plaintext);
    }

    @Test
    void same_plaintext_yields_different_ciphertext_due_to_random_iv() {
        String plaintext = "tenant-demo-pwd";
        String a = crypto.encrypt(plaintext);
        String b = crypto.encrypt(plaintext);

        // GCM 随机 IV → 同明文两次加密密文不同（IV 不复用）；但都能解回原文。
        assertThat(a).isNotEqualTo(b);
        assertThat(crypto.decrypt(a)).isEqualTo(plaintext);
        assertThat(crypto.decrypt(b)).isEqualTo(plaintext);
    }

    @Test
    void tampered_ciphertext_is_rejected_by_gcm_tag() {
        String cipher = crypto.encrypt("demo");
        byte[] bytes = Base64.getDecoder().decode(cipher);
        bytes[bytes.length - 1] ^= 0x01; // 篡改最后一字节（认证标签）
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missing_key_fails_fast() {
        assertThatThrownBy(() -> new CryptoService("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DS_SECRET_KEY");
    }

    @Test
    void wrong_length_key_fails_fast() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new CryptoService(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
