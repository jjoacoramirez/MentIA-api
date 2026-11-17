package com.mentia.security;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mentia.security.domain.UserTokenVersion;

public interface UserTokenVersionRepository extends JpaRepository<UserTokenVersion, UUID> {

    /** Atómico: dos invalidaciones simultáneas no pierden un incremento. */
    @Modifying
    @Query(value = """
            INSERT INTO user_token_versions (user_id, version) VALUES (:userId, 1)
            ON CONFLICT (user_id) DO UPDATE SET version = user_token_versions.version + 1""", nativeQuery = true)
    void increment(@Param("userId") UUID userId);
}
