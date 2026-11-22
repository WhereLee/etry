package com.wherelee.material.modules.identity.api;

import java.util.Collection;
import java.util.Map;

/**
 * 用户目录读端口（identity 拥有 t_user）。批量取昵称，供跨模块展示拼装，
 * 消费方不再直 join t_user。列表场景必须批量，避免 N+1。
 */
public interface UserDirectoryQuery {

    Map<Long, String> nicknamesById(Collection<Long> userIds);
}