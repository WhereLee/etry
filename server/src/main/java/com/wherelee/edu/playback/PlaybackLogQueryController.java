package com.wherelee.edu.playback;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/playback")
public class PlaybackLogQueryController {

    private final PlaybackLogMapper mapper;

    public PlaybackLogQueryController(PlaybackLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/logs")
    public List<PlaybackLogVO> logs(@RequestAttribute("userId") Long userId,
                                    @RequestParam(required = false) String eventType,
                                    @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return mapper.findRecentByUser(userId, eventType, safeLimit);
    }
}