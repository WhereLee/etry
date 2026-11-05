package com.wherelee.edu.modules.course.teacher;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RequireRole;
import com.wherelee.edu.modules.course.series.dto.SeriesDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/series")
public class TeacherSeriesController {

    private final TeacherSeriesService service;

    public TeacherSeriesController(TeacherSeriesService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole("TEACHER")
    @Measured("teacher.series.page")
    public List<SeriesDtos.TeacherSeriesSummary> list(@RequestAttribute("userId") Long teacherId) {
        return service.list(teacherId);
    }

    @GetMapping("/{id}")
    @RequireRole("TEACHER")
    @Measured("teacher.series.detail")
    public SeriesDtos.TeacherSeriesDetail detail(@RequestAttribute("userId") Long teacherId,
                                                 @PathVariable Long id) {
        return service.detail(teacherId, id);
    }
}
