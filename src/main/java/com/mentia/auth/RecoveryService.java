package com.mentia.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.RecoveryCodeResponse;
import com.mentia.auth.dto.RecoveryResetRequest;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.security.TokenRevocationService;

/**
 * Recuperación de cuenta con código, sin email ni datos personales.
 * El código se guarda hasheado, se muestra una sola vez y rota en cada uso.
 */
@Service
public class RecoveryService {

    private static final String INVALID_RECOVERY_DETAIL =
            "No pudimos restablecer la contraseña con esos datos. Revisá el usuario y el código.";
    private static final String INVALID_PASSWORD_DETAIL = "La contraseña no es correcta.";

    private final UserRepository userRepository;
    private final RecoveryCodeGenerator generator;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService revocationService;
    private final RecoveryProperties properties;
    private final Clock clock;

    // Para que un username inexistente cueste lo mismo que uno existente (ver reset).
    private final String dummyCodeHash;

    public RecoveryService(UserRepository userRepository, RecoveryCodeGenerator generator,
            PasswordEncoder passwordEncoder, TokenRevocationService revocationService,
            RecoveryProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.generator = generator;
        this.passwordEncoder = passwordEncoder;
        this.revocationService = revocationService;
        this.properties = properties;
        this.clock = clock;
        this.dummyCodeHash = passwordEncoder.encode(RecoveryCodeGenerator.normalize(generator.generate()));
    }

    /** Código nuevo ya hasheado para guardar, junto con el texto para mostrar una sola vez. */
    public NewCode newCode() {
        String code = generator.generate();
        return new NewCode(code, passwordEncoder.encode(RecoveryCodeGenerator.normalize(code)));
    }

    /** Pide la contraseña actual: con un token robado no alcanza para quedarse con la cuenta. */
    @Transactional
    public RecoveryCodeResponse regenerate(UUID userId, String currentPassword) {
        User user = userRepository.findById(userId)
                .filter(u -> passwordEncoder.matches(currentPassword, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                        INVALID_PASSWORD_DETAIL));
        NewCode code = newCode();
        user.changeRecoveryCode(code.hash());
        return new RecoveryCodeResponse(code.plain());
    }

    /**
     * Cambia la contraseña, rota el código e invalida todos los tokens del usuario.
     * Cualquier falla (usuario inexistente, código incorrecto, bloqueo por intentos) da el mismo error.
     * El intento fallido se persiste aunque se lance la excepción, por eso no hace rollback con ApiException.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RecoveryCodeResponse reset(RecoveryResetRequest request) {
        Instant now = Instant.now(clock);
        User user = userRepository.findByUsername(AuthService.normalizeUsername(request.username())).orElse(null);
        boolean hasCode = user != null && user.getRecoveryCodeHash() != null;

        // Siempre se paga un BCrypt, haya o no usuario: el tiempo de respuesta no delata si el username existe.
        boolean codeMatches = passwordEncoder.matches(RecoveryCodeGenerator.normalize(request.recoveryCode()),
                hasCode ? user.getRecoveryCodeHash() : dummyCodeHash);

        if (!hasCode || user.isRecoveryLocked(now)) {
            throw invalidRecovery();
        }
        if (!codeMatches) {
            user.registerFailedRecovery(now, properties.maxAttempts(), properties.lockout());
            throw invalidRecovery();
        }

        user.clearFailedRecoveries();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        NewCode code = newCode();
        user.changeRecoveryCode(code.hash());
        revocationService.revokeAll(user.getId());
        return new RecoveryCodeResponse(code.plain());
    }

    private static ApiException invalidRecovery() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RECOVERY, INVALID_RECOVERY_DETAIL);
    }

    public record NewCode(String plain, String hash) {

        @Override
        public String toString() {
            return "NewCode[plain=***, hash=***]";
        }
    }
}
