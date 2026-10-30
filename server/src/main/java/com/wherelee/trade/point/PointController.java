package com.wherelee.trade.point;

import com.wherelee.trade.auth.UserContext;
import com.wherelee.trade.common.ApiResponse;
import com.wherelee.trade.point.dto.PointSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public ApiResponse<PointSummaryVO> summary(@RequestParam(defaultValue = "20") int flowLimit) {
        return ApiResponse.ok(pointService.summary(UserContext.requireUserId(), flowLimit));
    }
}
