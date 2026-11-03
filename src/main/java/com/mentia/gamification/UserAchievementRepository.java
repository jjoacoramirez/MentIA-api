package com.mentia.gamification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mentia.gamification.domain.UserAchievement;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievement.Key> {

    List<UserAchievement> findByIdUserId(UUID userId);

    List<UserAchievement> findBySessionId(UUID sessionId);

    @Modifying
    @Query(value = """
            INSERT INTO user_categories_practiced (user_id, category_code) VALUES (:userId, :code)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    void markCategoryPracticed(@Param("userId") UUID userId, @Param("code") String categoryCode);

    @Query(value = "SELECT count(*) FROM user_categories_practiced WHERE user_id = :userId", nativeQuery = true)
    int countCategoriesPracticed(@Param("userId") UUID userId);
}
