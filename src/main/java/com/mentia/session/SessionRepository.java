package com.mentia.session;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mentia.session.domain.TrainingSession;

public interface SessionRepository extends JpaRepository<TrainingSession, UUID> {

    /** Ids de las sesiones más recientes del usuario, de la más nueva a la más vieja. */
    @Query("select s.id from TrainingSession s where s.userId = :userId order by s.createdAt desc, s.id")
    List<UUID> findRecentIds(@Param("userId") UUID userId, Pageable page);

    /** Filas {@code [sessionId, exerciseId, variantId]} de los ítems de esas sesiones. */
    @Query("select i.session.id, i.exerciseId, i.variantId from SessionItem i where i.session.id in :sessionIds")
    List<Object[]> findItemUsage(@Param("sessionIds") List<UUID> sessionIds);
}
