package com.wherelee.edu.rbac;

import com.wherelee.edu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前用户的权限集。
 *
 * 缓存粒度是「单个请求」而不是「整个会话」：权限一旦在后台被收回，
 * 下一次请求立刻失效。用全局缓存反而会让收回延时生效。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;

    private final ThreadLocal<Set<String>> cache = new ThreadLocal<>();

    public Set<String> currentPermissions() {
        Set<String> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        Long userId = UserContext.requireUserId();
        List<String> codes = permissionMapper.findCodesByUserId(userId);
        Set<String> permissions = new HashSet<>(codes);
        cache.set(permissions);
        return permissions;
    }

    public boolean has(String code) {
        return currentPermissions().contains(code);
    }

    public void clear() {
        cache.remove();
    }
}
