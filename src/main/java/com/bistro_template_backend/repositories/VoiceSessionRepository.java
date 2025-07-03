package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.VoiceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceSessionRepository extends JpaRepository<VoiceSession, String> {

    /**
     * Find active sessions by customer
     */
    List<VoiceSession> findByCustomerIdAndIsActiveTrue(String customerId);

    /**
     * Find all active sessions
     */
    List<VoiceSession> findByIsActiveTrue();

    /**
     * Find sessions by customer email
     */
    List<VoiceSession> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    /**
     * Find sessions created within date range
     */
    List<VoiceSession> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find sessions that resulted in orders
     */
    List<VoiceSession> findByOrderCreatedTrue();

    /**
     * Find sessions by end reason
     */
    List<VoiceSession> findByEndReason(String endReason);

    /**
     * Find inactive sessions older than specified time
     */
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.isActive = false AND vs.endedAt < :cutoffTime")
    List<VoiceSession> findInactiveSessionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find sessions with no activity since specified time
     */
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.isActive = true AND vs.lastActivityAt < :cutoffTime")
    List<VoiceSession> findStaleActiveSessions(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count active sessions
     */
    long countByIsActiveTrue();

    /**
     * Count sessions created today
     */
    @Query("SELECT COUNT(vs) FROM VoiceSession vs WHERE vs.createdAt >= :startOfDay AND vs.createdAt < :endOfDay")
    long countSessionsCreatedToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Count conversions today
     */
    @Query("SELECT COUNT(vs) FROM VoiceSession vs WHERE vs.orderCreated = true AND vs.createdAt >= :startOfDay AND vs.createdAt < :endOfDay")
    long countConversionsToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Get average session duration for completed sessions (returns list for manual calculation)
     */
    @Query("SELECT vs.createdAt, vs.endedAt FROM VoiceSession vs WHERE vs.endedAt IS NOT NULL")
    List<Object[]> getCompletedSessionTimestamps();

    /**
     * Get average turn count for completed sessions
     */
    @Query("SELECT AVG(vs.turnCount) FROM VoiceSession vs WHERE vs.endedAt IS NOT NULL")
    Double getAverageTurnCount();

    /**
     * Find most recent session for customer
     */
    Optional<VoiceSession> findFirstByCustomerIdOrderByCreatedAtDesc(String customerId);

    /**
     * Delete old inactive sessions for cleanup
     */
    void deleteByIsActiveFalseAndEndedAtBefore(LocalDateTime cutoffTime);

    Optional<VoiceSession> findBySessionId(String sessionId);
}