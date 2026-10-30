package com.wherelee.trade.product;

import com.wherelee.trade.common.ApiResponse;
import com.wherelee.trade.common.PageResult;
import com.wherelee.trade.product.dto.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResult<ProductVO>> page(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(productService.page(category, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.detail(id));
    }
}
