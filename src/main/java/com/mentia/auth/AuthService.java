package com.mentia.auth;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.LoginRequest;
import com.mentia.auth.dto.RegisterRequest;
import com.mentia.auth.dto.RegisterResponse;
import com.mentia.auth.dto.TokenResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.profile.ProfileService;
import com.mentia.security.AuthenticatedUser;
import com.mentia.security.JwtService;
import com.mentia.security.Role;
import com.mentia.security.TokenRevocationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String USERNAME_TAKEN_DETAIL = "Ese nombre de usuario ya está en uso. Probá con otro.";
    private static final String INVALID_CREDENTIALS_DETAIL = "El nombre de usuario o la contraseña no son correctos.";

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService revocationService;
    private final RecoveryService recoveryService;
    private final Clock clock;

    // Hash contra el que se compara cuando el username no existe, para que la respuesta tarde lo mismo
    // y no se pueda averiguar qué usernames están registrados.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, ProfileService profileService,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       TokenRevocationService revocationService, RecoveryService recoveryService, Clock clock) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revocationService = revocationService;
        this.recoveryService = recoveryService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw usernameTaken();
        }

        RecoveryService.NewCode recoveryCode = recoveryService.newCode();
        User user;
        try {
            user = userRepository.saveAndFlush(new User(username, passwordEncoder.encode(request.password()),
                    recoveryCode.hash(), Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo username: gana el primero en llegar a la base.
            throw usernameTaken();
        }

        profileService.createInitialProfile(user.getId(), request.ageRange());
        return new RegisterResponse(user.getId(), recoveryCode.plain());
    }

    /** Mismo error y mismo costo exista o no el username. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByUsername(normalizeUsername(request.username()));
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_DETAIL);
        }
        UUID userId = user.get().getId();
        Role role = user.get().getRole();
        JwtService.IssuedToken token = jwtService.issue(userId, revocationService.currentVersion(userId), role);
        return TokenResponse.bearer(token.token(), token.expiresInSeconds(), role);
    }

    public void logout(AuthenticatedUser user) {
        revocationService.revoke(user.jti(), user.expiresAt());
    }

    private static ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, USERNAME_TAKEN_DETAIL);
    }

    static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
