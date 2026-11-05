package com.wherelee.edu.modules.learning.playback;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playback")
public class PlaybackLogController {

    private final PlaybackLogService service;

    public PlaybackLogController(PlaybackLogService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<Void> event(@RequestAttribute("userId") Long userId,
                                      @Valid @RequestBody PlaybackEventRequest request,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        service.recordFrontend(userId, request, userAgent);
        return ResponseEntity.noContent().build();
    }
}