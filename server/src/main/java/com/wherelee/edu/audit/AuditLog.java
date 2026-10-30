package com.wherelee.edu.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要留痕的方法。切面会自动记录：谁、什么时候、对什么、成没成功、耗时多少。
 *
 * targetIdIndex 指定「第几个方法参数是业务目标 ID」，-1 表示不取（比如查询类操作）。
 * 用下标而不是 SpEL：少一层解析，出问题时也容易看懂。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 动作编码，如 USER_BAN / ROLE_ASSIGN */
    String action();

    /** 目标类型，如 USER / ROLE / TEACHER */
    String targetType() default "";

    /** 目标 ID 在方法参数中的下标 */
    int targetIdIndex() default -1;
}
