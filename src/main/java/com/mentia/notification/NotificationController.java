package com.mentia.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mentia.notification.dto.PushConfigResponse;
import com.mentia.notification.dto.PushSubscriptionRequest;
import com.mentia.security.AuthenticatedUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/push-config")
    public PushConfigResponse config() {
        return service.config();
    }

    @PutMapping("/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PushSubscriptionRequest request) {
        if (request.p256dh() == null && request.auth() == null) service.subscribe(user.userId(), request.endpoint());
        else service.subscribe(user.userId(), request.endpoint(), request.p256dh(), request.auth());
    }

    @DeleteMapping("/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PushSubscriptionRequest request) {
        service.unsubscribe(user.userId(), request.endpoint());
    }
}
