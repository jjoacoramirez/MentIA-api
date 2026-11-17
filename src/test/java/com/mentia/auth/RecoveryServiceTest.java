package com.mentia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.RecoveryCodeResponse;
import com.mentia.auth.dto.RecoveryResetRequest;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.security.TokenRevocationService;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String PASSWORD = "una-clave-larga";
    private static final String NEW_PASSWORD = "otra-clave-larga";
    private static final String CODE = "7KQ2M-X9PTD-4HBWE-RC6ZN";
    private static final RecoveryProperties PROPERTIES = new RecoveryProperties(3, Duration.ofMinutes(15));

    @Mock
    UserRepository userRepository;

    @Mock
    TokenRevocationService revocationService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final MutableClock clock = new MutableClock(NOW);

    RecoveryService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new RecoveryService(userRepository, new RecoveryCodeGenerator(), encoder, revocationService,
                PROPERTIES, clock);
        user = new User("lector", encoder.encode(PASSWORD), encoder.encode(RecoveryCodeGenerator.normalize(CODE)), NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void elResetCambiaLaContrasenaRotaElCodigoEInvalidaLosTokens() {
        given(userRepository.findByUsername("lector")).willReturn(Optional.of(user));

        RecoveryCodeResponse response = service.reset(new RecoveryResetRequest("Lector", CODE, NEW_PASSWORD));

        assertThat(encoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(response.recoveryCode()).isNotEqualTo(CODE);
        assertThat(encoder.matches(RecoveryCodeGenerator.normalize(response.recoveryCode()), user.getRecoveryCodeHash()))
                .as("el código nuevo es el que queda guardado").isTrue();
        assertThat(encoder.matches(RecoveryCodeGenerator.normalize(CODE), user.getRecoveryCodeHash()))
                .as("el código usado deja de servir").isFalse();
        verify(revocationService).revokeAll(user.getId());
    }

    @Test
    void aceptaElCodigoEnMinusculasYSinGuiones() {
        given(userRepository.findByUsername("lector")).willReturn(Optional.of(user));

        service.reset(new RecoveryResetRequest("lector", "7kq2mx9ptd4hbwerc6zn", NEW_PASSWORD));

        assertThat(encoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void codigoIncorrectoYUsuarioInexistenteDanElMismoError() {
        given(userRepository.findByUsername("lector")).willReturn(Optional.of(user));
        given(userRepository.findByUsername("nadie")).willReturn(Optional.empty());

        ApiException wrongCode = resetFails(new RecoveryResetRequest("lector", "XXXXX-XXXXX-XXXXX-XXXXX", NEW_PASSWORD));
        ApiException unknownUser = resetFails(new RecoveryResetRequest("nadie", CODE, NEW_PASSWORD));

        assertThat(wrongCode.getCode()).isEqualTo(ErrorCode.INVALID_RECOVERY);
        assertThat(unknownUser.getCode()).isEqualTo(ErrorCode.INVALID_RECOVERY);
        assertThat(wrongCode.getMessage()).isEqualTo(unknownUser.getMessage());
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).as("la contraseña no cambia").isTrue();
        verify(revocationService, never()).revokeAll(any());
    }

    @Test
    void despuesDeVariosIntentosFallidosSeBloqueaAunqueElCodigoSeaCorrecto() {
        given(userRepository.findByUsername("lector")).willReturn(Optional.of(user));

        for (int i = 0; i < PROPERTIES.maxAttempts(); i++) {
            resetFails(new RecoveryResetRequest("lector", "XXXXX-XXXXX-XXXXX-XXXXX", NEW_PASSWORD));
        }
        ApiException locked = resetFails(new RecoveryResetRequest("lector", CODE, NEW_PASSWORD));

        assertThat(locked.getCode()).isEqualTo(ErrorCode.INVALID_RECOVERY);
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void elBloqueoVenceYUnResetExitosoReiniciaLosIntentos() {
        given(userRepository.findByUsername("lector")).willReturn(Optional.of(user));
        for (int i = 0; i < PROPERTIES.maxAttempts(); i++) {
            resetFails(new RecoveryResetRequest("lector", "XXXXX-XXXXX-XXXXX-XXXXX", NEW_PASSWORD));
        }

        clock.advance(PROPERTIES.lockout().plusSeconds(1));
        service.reset(new RecoveryResetRequest("lector", CODE, NEW_PASSWORD));

        assertThat(encoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.isRecoveryLocked(clock.instant())).isFalse();
    }

    @Test
    void regenerarPideLaContrasenaActual() {
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.regenerate(user.getId(), "otra-clave"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(encoder.matches(RecoveryCodeGenerator.normalize(CODE), user.getRecoveryCodeHash())).isTrue();

        RecoveryCodeResponse response = service.regenerate(user.getId(), PASSWORD);
        assertThat(encoder.matches(RecoveryCodeGenerator.normalize(response.recoveryCode()), user.getRecoveryCodeHash()))
                .isTrue();
        assertThat(encoder.matches(RecoveryCodeGenerator.normalize(CODE), user.getRecoveryCodeHash())).isFalse();
    }

    private ApiException resetFails(RecoveryResetRequest request) {
        try {
            service.reset(request);
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Se esperaba ApiException");
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
