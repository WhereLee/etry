package com.wherelee.trade.product;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.common.PageResult;
import com.wherelee.trade.product.dto.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductMapper productMapper;

    public PageResult<ProductVO> page(String category, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        long total = productMapper.countPage(category);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<Product> products = productMapper.findPage(category, offset, safeSize);
        List<ProductVO> records = products.stream().map(ProductVO::from).toList();
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public ProductVO detail(Long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return ProductVO.from(product);
    }
}
