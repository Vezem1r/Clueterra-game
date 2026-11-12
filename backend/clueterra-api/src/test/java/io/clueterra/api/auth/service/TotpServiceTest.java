package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;

import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.config.TotpProperties;

class TotpServiceTest {

    private static final long USER_ID = 7L;
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final long CURRENT_STEP = 59_000_000L;
    private static final Instant NOW = Instant.ofEpochSecond(CURRENT_STEP * 30 + 12);

    private AuthJdbcRepository authRepository;
    private TotpSecretCipher cipher;
    private TotpService service;
    private String encryptedSecret;

    @BeforeEach
    void setUp() {
        TotpProperties properties = new TotpProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new TotpSecretCipher(properties);
        authRepository = mock(AuthJdbcRepository.class);
        service = new TotpService(cipher, authRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        encryptedSecret = cipher.encrypt(SECRET);
    }

    @Test
    @DisplayName("accepts the current code once and records its time step")
    void acceptsCurrentCodeAndRecordsStep() throws Exception {
        when(authRepository.recordTotpTimeStep(USER_ID, CURRENT_STEP)).thenReturn(true);

        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP))).isFalse();
        verify(authRepository).recordTotpTimeStep(USER_ID, CURRENT_STEP);
    }

    @Test
    @DisplayName("accepts codes from the adjacent time steps to tolerate clock drift")
    void acceptsAdjacentSteps() throws Exception {
        when(authRepository.recordTotpTimeStep(USER_ID, CURRENT_STEP - 1)).thenReturn(true);
        when(authRepository.recordTotpTimeStep(USER_ID, CURRENT_STEP + 1)).thenReturn(true);

        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP - 1))).isFalse();
        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP + 1))).isFalse();
    }

    @Test
    @DisplayName("rejects a code whose time step was already used")
    void rejectsReplayedCode() throws Exception {
        when(authRepository.recordTotpTimeStep(USER_ID, CURRENT_STEP)).thenReturn(false);

        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP))).isTrue();
    }

    @Test
    @DisplayName("rejects wrong and expired codes without recording a time step")
    void rejectsWrongAndExpiredCodes() throws Exception {
        String wrong = code(CURRENT_STEP).equals("000000") ? "111111" : "000000";

        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, wrong)).isTrue();
        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP - 2))).isTrue();
        assertThat(service.isCodeInvalid(USER_ID, encryptedSecret, code(CURRENT_STEP + 2))).isTrue();
        verify(authRepository, never()).recordTotpTimeStep(anyLong(), anyLong());
    }

    @Test
    @DisplayName("encryptSecret stores a value that only the configured key can read")
    void encryptSecretUsesCipher() {
        String stored = service.encryptSecret(SECRET);

        assertThat(stored).isNotEqualTo(SECRET);
        assertThat(cipher.decrypt(stored)).isEqualTo(SECRET);
    }

    private static String code(long step) throws Exception {
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(SECRET, step);
    }
}
