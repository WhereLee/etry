package com.wherelee.edu.teacher.dto;

import com.wherelee.edu.teacher.Subject;

public record SubjectVO(Long id, String code, String name) {

    public static SubjectVO from(Subject s) {
        return new SubjectVO(s.getId(), s.getCode(), s.getName());
    }
}
