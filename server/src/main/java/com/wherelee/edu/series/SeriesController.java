package com.wherelee.edu.series;

import com.wherelee.edu.aop.RateLimit;
import com.wherelee.edu.aop.RequireRole;
import com.wherelee.edu.series.dto.SeriesDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class SeriesController {

    private final SeriesService seriesService;

    public SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping("/series")
    @RequireRole("STUDENT")
    public List<SeriesDtos.SeriesSummary> list(@RequestAttribute("userId") Long userId) {
        return seriesService.listForStudent(userId);
    }

    @GetMapping("/series/{id}")
    @RequireRole("STUDENT")
    public SeriesDtos.SeriesDetail detail(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        return seriesService.detail(userId, id);
    }

    @GetMapping("/videos/{id}/play")
    @RequireRole("STUDENT")
    @RateLimit(key = "play", limit = 120, windowSeconds = 60)
    public SeriesDtos.PlayInfo play(@RequestAttribute("userId") Long userId, @PathVariable Long id,
                                    @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return seriesService.play(userId, id, userAgent);
    }
}
