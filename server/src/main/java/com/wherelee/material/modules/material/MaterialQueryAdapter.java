package com.wherelee.material.modules.material;

import com.wherelee.material.modules.material.api.MaterialQuery;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class MaterialQueryAdapter implements MaterialQuery {

    private final MaterialRepository materialRepository;

    public MaterialQueryAdapter(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public List<MaterialFact> findFacts(Collection<Long> materialIds) {
        return materialRepository.findByIds(materialIds).stream()
                .map(m -> new MaterialFact(m.id(), m.title(), m.category(),
                        m.priceCents(), m.currency(), m.saleStatus(), m.coverKey()))
                .toList();
    }
}