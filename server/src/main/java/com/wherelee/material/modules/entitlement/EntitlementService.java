package com.wherelee.material.modules.entitlement;

import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.EntitlementQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权益中心应用服务，实现写/读端口。写操作幂等并由唯一键与状态条件保证 grant-once/revoke-once。
 */
@Service
public class EntitlementService implements EntitlementCommand, EntitlementQuery {

    private final EntitlementStore repository;

    public EntitlementService(EntitlementStore repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void grant(GrantCommand command) {
        repository.grantOrder(command.userId(), command.productId(), command.productType(), command.sourceOrderId());
    }

    @Override
    @Transactional
    public void grantManual(GrantCommand command) {
        repository.insertManual(command.userId(), command.productId(), command.productType(), command.displayOrder());
    }

    @Override
    @Transactional
    public void revoke(RevokeCommand command) {
        repository.revokeOrder(command.userId(), command.productId(), command.productType(),
                command.sourceOrderId(), command.reason());
    }

    @Override
    @Transactional
    public void replaceManual(Long userId, String productType, List<Long> productIds) {
        repository.deleteManual(userId, productType);
        int order = 1;
        for (Long productId : productIds) {
            repository.insertManual(userId, productId, productType, order++);
        }
    }

    @Override
    public boolean hasActive(Long userId, Long productId, String productType) {
        return repository.hasActive(userId, productId, productType);
    }

    @Override
    public Set<Long> findActiveIds(Long userId, String productType, Collection<Long> productIds) {
        return repository.findActiveIds(userId, productType, productIds);
    }

    @Override
    public List<ActiveProduct> listActive(Long userId, String productType) {
        return repository.listActive(userId, productType);
    }

    @Override
    public List<Long> listManualProductIds(Long userId, String productType) {
        return repository.listManualProductIds(userId, productType);
    }

    @Override
    public long countActiveByUser(Long userId) {
        return repository.countActiveByUser(userId);
    }

    @Override
    public Map<Long, Long> countActiveByUsers(Collection<Long> userIds, String productType) {
        return repository.countActiveByUsers(userIds, productType);
    }

    @Override
    public long countActiveByProduct(Long productId, String productType) {
        return repository.countActiveByProduct(productId, productType);
    }
}