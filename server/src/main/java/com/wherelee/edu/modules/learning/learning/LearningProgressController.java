package com.wherelee.edu.modules.learning.learning;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RateLimit;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
public class LearningProgressController {

    private final LearningProgressService service;

    public LearningProgressController(LearningProgressService service) {
        this.service = service;
    }

    @PutMapping("/videos/{id}/progress")
    @RequireRole("STUDENT")
    @RateLimit(key = "learning:progress", limit = 240, windowSeconds = 60)
    @Measured("learning.progress.update")
    public void update(@RequestAttribute("userId") Long userId,
                       @PathVariable Long id,
                       @Valid @RequestBody LearningProgressDtos.UpdateRequest request) {
        service.update(userId, id, request);
    }
}
