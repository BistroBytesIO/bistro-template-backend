
package com.bistro_template_backend.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WebSocketStartupListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("=".repeat(60));
        System.out.println("🚀 BISTRO TEMPLATE BACKEND STARTED SUCCESSFULLY");
        System.out.println("=".repeat(60));
        System.out.println("📡 WebSocket Endpoints Available:");
        System.out.println("   • ws://localhost:8080/ws-orders/websocket (Native)");
        System.out.println("   • http://localhost:8080/ws-orders (SockJS)");
        System.out.println("🧪 Test WebSocket:");
        System.out.println("   • POST http://localhost:8080/api/test/websocket");
        System.out.println("=".repeat(60));
    }
}