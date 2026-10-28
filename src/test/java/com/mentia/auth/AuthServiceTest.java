package com.mentia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.RegisterRequest;
import com.mentia.auth.dto.RegisterResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.profile.ProfileService;
import com.mentia.profile.domain.AgeRange;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String PASSWORD = "una-clave-larga";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileService profileService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, profileService, passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void normalizaElUsernameAMinusculasYGuardaElHash() {
        UUID id = UUID.randomUUID();
        given(userRepository.saveAndFlush(any())).willAnswer(inv -> withId(inv.getArgument(0), id));

        RegisterResponse response = authService.register(
                new RegisterRequest("Lector.Nocturno", PASSWORD, AgeRange.FROM_25_TO_30));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByUsername("lector.nocturno");
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("lector.nocturno");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();

        assertThat(response.userId()).isEqualTo(id);
        assertThat(response.recoveryCode()).isNull();
    }

    @Test
    void creaElPerfilDeEntrenamientoConElRangoEtario() {
        UUID id = UUID.randomUUID();
        given(userRepository.saveAndFlush(any())).willAnswer(inv -> withId(inv.getArgument(0), id));

        authService.register(new RegisterRequest("usuario", PASSWORD, AgeRange.FROM_31_TO_40));

        verify(profileService).createInitialProfile(id, AgeRange.FROM_31_TO_40);
    }

    @Test
    void usernameExistenteSinDistinguirMayusculasDevuelveUsernameTaken() {
        given(userRepository.existsByUsername("repetido")).willReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("REPETIDO", PASSWORD, AgeRange.FROM_18_TO_24)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.USERNAME_TAKEN);
        verify(userRepository, never()).saveAndFlush(any());
        verify(profileService, never()).createInitialProfile(any(), any());
    }

    @Test
    void registroConcurrenteDelMismoUsernameDevuelveUsernameTaken() {
        given(userRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_users_username"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("usuario", PASSWORD, AgeRange.FROM_41)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.USERNAME_TAKEN);
        verify(profileService, never()).createInitialProfile(any(), any());
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
