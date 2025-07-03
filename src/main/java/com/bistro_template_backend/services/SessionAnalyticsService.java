package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.SessionAnalytics;

public interface SessionAnalyticsService {
    SessionAnalytics getAnalytics(String sessionId);
}
