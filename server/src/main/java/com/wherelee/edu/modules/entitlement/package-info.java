/**
 * 权益中心模块：拥有通用权益表 t_entitlement，对业务模块公开 EntitlementCommand/EntitlementQuery 端口。
 *
 * <p>依赖方向：entitlement -> platform。commerce 履约与管理员分配走 Command，
 * course/learning 鉴权走 Query，权益事实单一来源。</p>
 *
 * <p>Phase 1 过渡期：t_student_series 仍为权威读写源，t_entitlement 双写影子并对账校验；
 * Phase 3 切换权威到 t_entitlement 并退役旧表。</p>
 */
package com.wherelee.edu.modules.entitlement;