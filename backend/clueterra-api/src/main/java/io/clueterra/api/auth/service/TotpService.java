package io.clueterra.api.auth.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import java.util.stream.LongStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;

import org.springframework.stereotype.Service;

import io.clueterra.api.auth.repository.AuthJdbcRepository;

import lombok.RequiredArgsConstructor;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String ISSUER = "Clueterra";
    private static final int QR_SIZE = 250;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_STEP_DRIFT = 1;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final TotpSecretCipher secretCipher;
    private final AuthJdbcRepository authRepository;
    private final Clock clock;

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String encryptSecret(String secret) {
        return secretCipher.encrypt(secret);
    }

    public String generateQrCodeDataUri(String secret, String email) {
        String otpAuthUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                ISSUER, email, secret, ISSUER);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }

    public boolean isCodeInvalid(long userId, String encryptedSecret, String code) {
        String secret = secretCipher.decrypt(encryptedSecret);
        return matchingTimeStep(secret, code)
                .filter(step -> authRepository.recordTotpTimeStep(userId, step))
                .isEmpty();
    }

    private Optional<Long> matchingTimeStep(String secret, String code) {
        long currentStep = Math.floorDiv(clock.instant().getEpochSecond(), TIME_STEP_SECONDS);
        return LongStream.rangeClosed(currentStep - ALLOWED_STEP_DRIFT, currentStep + ALLOWED_STEP_DRIFT)
                .filter(step -> codeMatches(secret, step, code))
                .boxed()
                .findFirst();
    }

    private boolean codeMatches(String secret, long step, String code) {
        try {
            byte[] expected = codeGenerator.generate(secret, step).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.UTF_8));
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
    }
}
