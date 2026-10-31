package com.wherelee.edu.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TagDtos {

    public record SaveRequest(

            @NotBlank(message = "标签名不能为空")
            @Size(max = 30, message = "标签名不能超过 30 字")
            String name,

            Integer sort
    ) {
    }

    public record TagVO(Long id, String name, Integer sort) {
    }
}
