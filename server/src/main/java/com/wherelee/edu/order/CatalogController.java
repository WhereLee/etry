package com.wherelee.edu.order;

import com.wherelee.edu.aop.Measured;
import com.wherelee.edu.aop.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/series")
public class CatalogController {

    private final OrderService orderService;

    public CatalogController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @RequireRole("STUDENT")
    @Measured("catalog.series")
    public List<CatalogItem> list(@RequestAttribute("userId") Long userId) {
        return orderService.catalog(userId);
    }
}
