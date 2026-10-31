package com.wherelee.edu.user;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
public class Role {

    public static final String CODE_STUDENT = "STUDENT";
    public static final String CODE_TEACHER = "TEACHER";
    public static final String CODE_ADMIN = "ADMIN";

    /**
     * 内置角色：代码里会按 code 做判断（比如审核通过要绑定 TEACHER），
     * 所以这三个不允许删除，也不允许改 code。
     */
    private static final Set<String> BUILT_IN_CODES = Set.of(CODE_STUDENT, CODE_TEACHER, CODE_ADMIN);

    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    public boolean isBuiltIn() {
        return BUILT_IN_CODES.contains(code);
    }
}
