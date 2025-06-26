package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.VoiceAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VoiceAnalyticsRepository extends JpaRepository<VoiceAnalytics, Long> {

    /**
     * Find analytics by session ID
     */
    List<VoiceAnalytics> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Find analytics by customer ID
     */
    List<VoiceAnalytics> findByCustomerIdOrderByTimestampDesc(String customerId);

    /**
     * Find analytics by event type
     */
    List<VoiceAnalytics> findByEventTypeOrderByTimestampDesc(String eventType);

    /**
     * Find analytics by date range
     */
    List<VoiceAnalytics> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Find analytics for specific date
     */
    List<VoiceAnalytics> findByDateOnly(Date date);

    /**
     * Find failed operations
     */
    List<VoiceAnalytics> findBySuccessFalseOrderByTimestampDesc();

    /**
     * Count events by type for date range
     */
    @Query("SELECT va.eventType, COUNT(va) FROM VoiceAnalytics va WHERE va.timestamp BETWEEN :start AND :end GROUP BY va.eventType")
    List<Object[]> countEventsByTypeInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Count successful vs failed operations
     */
    @Query("SELECT va.success, COUNT(va) FROM VoiceAnalytics va WHERE va.timestamp BETWEEN :start AND :end GROUP BY va.success")
    List<Object[]> countSuccessFailureInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get average processing time by operation
     */
    @Query("SELECT va.operation, AVG(va.processingTimeMs) FROM VoiceAnalytics va WHERE va.processingTimeMs IS NOT NULL AND va.timestamp BETWEEN :start AND :end GROUP BY va.operation")
    List<Object[]> getAverageProcessingTimeByOperation(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get total API costs for date range
     */
    @Query("SELECT SUM(va.apiCost) FROM VoiceAnalytics va WHERE va.apiCost IS NOT NULL AND va.timestamp BETWEEN :start AND :end")
    Double getTotalApiCostsInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get total tokens used for date range
     */
    @Query("SELECT SUM(va.tokenCount) FROM VoiceAnalytics va WHERE va.tokenCount IS NOT NULL AND va.timestamp BETWEEN :start AND :end")
    Long getTotalTokensInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Count orders created through voice
     */
    @Query("SELECT COUNT(DISTINCT va.createdOrderId) FROM VoiceAnalytics va WHERE va.createdOrderId IS NOT NULL AND va.timestamp BETWEEN :start AND :end")
    Long countVoiceOrdersInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get total order value from voice orders
     */
    @Query("SELECT SUM(va.orderValue) FROM VoiceAnalytics va WHERE va.orderValue IS NOT NULL AND va.timestamp BETWEEN :start AND :end")
    Double getTotalVoiceOrderValueInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get average session duration
     */
    @Query("SELECT AVG(va.processingTimeMs) FROM VoiceAnalytics va WHERE va.eventType = 'SESSION_ENDED' AND va.timestamp BETWEEN :start AND :end")
    Double getAverageSessionDurationInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get conversion rate (sessions that resulted in orders)
     */
    @Query(value = """
        SELECT 
            (SELECT COUNT(DISTINCT session_id) FROM voice_analytics WHERE event_type = 'ORDER_CONVERSION' AND timestamp BETWEEN :start AND :end) * 100.0 /
            NULLIF((SELECT COUNT(DISTINCT session_id) FROM voice_analytics WHERE event_type = 'SESSION_STARTED' AND timestamp BETWEEN :start AND :end), 0)
        """, nativeQuery = true)
    Double getConversionRateInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get most common errors
     */
    @Query("SELECT va.errorType, COUNT(va) FROM VoiceAnalytics va WHERE va.success = false AND va.timestamp BETWEEN :start AND :end GROUP BY va.errorType ORDER BY COUNT(va) DESC")
    List<Object[]> getMostCommonErrorsInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get most common intents
     */
    @Query("SELECT va.intent, COUNT(va) FROM VoiceAnalytics va WHERE va.intent IS NOT NULL AND va.timestamp BETWEEN :start AND :end GROUP BY va.intent ORDER BY COUNT(va) DESC")
    List<Object[]> getMostCommonIntentsInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get performance metrics for today
     */
    @Query("SELECT va.operation, AVG(va.processingTimeMs), COUNT(va) FROM VoiceAnalytics va WHERE va.dateOnly = CURRENT_DATE GROUP BY va.operation")
    List<Object[]> getTodaysPerformanceMetrics();

    /**
     * Delete old analytics data for cleanup
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);

    /**
     * Count total analytics records
     */
    @Query("SELECT COUNT(va) FROM VoiceAnalytics va")
    long getTotalRecordCount();

    /**
     * Get analytics summary for dashboard
     */
    @Query(value = """
        SELECT 
            COUNT(*) as total_events,
            COUNT(DISTINCT session_id) as unique_sessions,
            COUNT(DISTINCT customer_id) as unique_customers,
            SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_events,
            SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failed_events,
            AVG(processing_time_ms) as avg_processing_time,
            SUM(api_cost) as total_api_cost,
            COUNT(DISTINCT created_order_id) as orders_created
        FROM voice_analytics 
        WHERE timestamp BETWEEN :start AND :end
        """, nativeQuery = true)
    Object[] getAnalyticsSummaryInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}