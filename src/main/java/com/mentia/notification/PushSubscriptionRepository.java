package com.mentia.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mentia.notification.domain.PushSubscription;

interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /** Suscripciones a las que todavía no se les mandó el recordatorio de {@code today}. */
    @Query("select s from PushSubscription s where s.lastRemindedOn is null or s.lastRemindedOn < :today")
    List<PushSubscription> findDue(@Param("today") LocalDate today);

    @Modifying
    @Query("delete from PushSubscription s where s.userId = :userId and s.endpoint = :endpoint")
    int deleteByUserAndEndpoint(@Param("userId") UUID userId, @Param("endpoint") String endpoint);
}
