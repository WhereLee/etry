/**
 * 权益中心模块：拥有通用权益表 t_entitlement，对业务模块公开 EntitlementCommand/EntitlementQuery 端口。
 *
 * <p>依赖方向：entitlement -> platform。commerce 履约与管理员分配走 Command，
 * course/learning 鉴权走 Query，权益事实单一来源。</p>
 *
 * <p>Phase 3 权威已翻转：t_entitlement 为唯一读写源，t_student_series 退役（表/列保留到回滚窗口过）。</p>
 */
package com.wherelee.material.modules.entitlement;