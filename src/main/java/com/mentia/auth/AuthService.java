package com.mentia.auth;

import com.mentia.auth.domain.User;
import com.mentia.auth.dto.RegisterRequest;
import com.mentia.auth.dto.RegisterResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.profile.ProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String USERNAME_TAKEN_DETAIL = "Ese nombre de usuario ya está en uso. Probá con otro.";

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(UserRepository userRepository, ProfileService profileService,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw usernameTaken();
        }

        User user;
        try {
            user = userRepository.saveAndFlush(
                    new User(username, passwordEncoder.encode(request.password()), Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo username: gana el primero en llegar a la base.
            throw usernameTaken();
        }

        profileService.createInitialProfile(user.getId(), request.ageRange());
        return new RegisterResponse(user.getId(), null);
    }

    private static ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, USERNAME_TAKEN_DETAIL);
    }

    static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
