package com.example.notification.interfaces.rest;

import com.example.notification.application.query.notification.ListNotificationsQuery;
import com.example.notification.application.query.notification.NotificationView;
import com.example.notification.interfaces.rest.response.NotificationResponse;
import com.example.seedwork.application.bus.QueryBus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final QueryBus queryBus;

    @GetMapping
    List<NotificationResponse> list(@RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NotificationView> views = queryBus.dispatch(new ListNotificationsQuery(customerId, page, size));
        return views.stream().map(NotificationResponse::from).toList();
    }
}
