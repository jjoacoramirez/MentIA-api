package com.mentia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.LoginRequest;
import com.mentia.auth.dto.RegisterRequest;
import com.mentia.auth.dto.RegisterResponse;
import com.mentia.auth.dto.TokenResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.profile.ProfileService;
import com.mentia.profile.domain.AgeRange;
import com.mentia.security.AuthenticatedUser;
import com.mentia.security.JwtService;
import com.mentia.security.SecurityProperties;
import com.mentia.security.TokenRevocationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    private static final String RECOVERY_CODE = "7KQ2M-X9PTD-4HBWE-RC6ZN";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private TokenRevocationService revocationService;

    @Mock
    private RecoveryService recoveryService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        jwtService = new JwtService(new SecurityProperties("s".repeat(40), Duration.ofHours(24)), clock);
        authService = new AuthService(userRepository, profileService, passwordEncoder, jwtService,
                revocationService, recoveryService, clock);
        lenient().when(recoveryService.newCode()).thenReturn(new RecoveryService.NewCode(RECOVERY_CODE, "hash-del-codigo"));
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
        assertThat(response.recoveryCode()).as("se muestra una sola vez").isEqualTo(RECOVERY_CODE);
        assertThat(saved.getValue().getRecoveryCodeHash()).as("se guarda solo el hash").isEqualTo("hash-del-codigo");
    }

    @Test
    void elTokenLlevaLaVersionVigenteDelUsuario() {
        User user = existingUser("lector");
        given(revocationService.currentVersion(user.getId())).willReturn(2);

        TokenResponse response = authService.login(new LoginRequest("lector", PASSWORD));

        assertThat(jwtService.parse(response.accessToken())).get()
                .extracting(AuthenticatedUser::tokenVersion).isEqualTo(2);
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

    @Test
    void loginEmiteUnTokenDelUsuario() {
        User user = existingUser("lector");

        TokenResponse response = authService.login(new LoginRequest("LECTOR", PASSWORD));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofHours(24).toSeconds());
        Optional<AuthenticatedUser> parsed = jwtService.parse(response.accessToken());
        assertThat(parsed).get().extracting(AuthenticatedUser::userId).isEqualTo(user.getId());
    }

    @Test
    void contrasenaIncorrectaYUsuarioInexistenteDanElMismoError() {
        existingUser("lector");
        given(userRepository.findByUsername("nadie")).willReturn(Optional.empty());

        ApiException wrongPassword = loginFails(new LoginRequest("lector", "otra-clave-larga"));
        ApiException unknownUser = loginFails(new LoginRequest("nadie", PASSWORD));

        assertThat(wrongPassword.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownUser.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongPassword.getStatus()).isEqualTo(unknownUser.getStatus());
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownUser.getMessage());
    }

    @Test
    void logoutRevocaElJtiHastaQueVence() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "jti-1", NOW, NOW.plusSeconds(3600), 0);

        authService.logout(user);

        verify(revocationService).revoke("jti-1", NOW.plusSeconds(3600));
    }

    private ApiException loginFails(LoginRequest request) {
        try {
            authService.login(request);
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Se esperaba ApiException");
    }

    private User existingUser(String username) {
        User user = withId(new User(username, passwordEncoder.encode(PASSWORD), "hash-del-codigo", NOW),
                UUID.randomUUID());
        given(userRepository.findByUsername(username)).willReturn(Optional.of(user));
        return user;
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
