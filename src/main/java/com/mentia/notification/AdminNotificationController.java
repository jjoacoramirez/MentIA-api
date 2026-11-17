package com.mentia.notification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.notification.dto.TestPushRequest;
import com.mentia.notification.dto.TestPushResponse;

import jakarta.validation.Valid;

/** Solo ADMIN (lo controla SecurityConfig con {@code /api/v1/admin/**}). Herramienta de debug. */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final PushTestService service;

    public AdminNotificationController(PushTestService service) {
        this.service = service;
    }

    @PostMapping("/test")
    public TestPushResponse test(@Valid @RequestBody TestPushRequest request) {
        return service.send(request.username());
    }
}
