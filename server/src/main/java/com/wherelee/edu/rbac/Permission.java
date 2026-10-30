package com.wherelee.edu.rbac;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Permission {

    private Long id;
    private String code;
    private String name;
    private String module;
}
