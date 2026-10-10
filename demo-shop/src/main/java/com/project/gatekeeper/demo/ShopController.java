package com.project.gatekeeper.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ShopController {

    private final ShopOrderRepository orders;
    private final NewsRepository news;

    public ShopController(ShopOrderRepository orders, NewsRepository news) {
        this.orders = orders;
        this.news = news;
    }

    @GetMapping("/order")
    public List<ShopOrder> orders() {
        return orders.findAll();
    }

    @PostMapping("/order")
    public ShopOrder create(@RequestBody Map<String, Object> body) {
        ShopOrder order = new ShopOrder();
        order.product = String.valueOf(body.getOrDefault("product", "item"));
        Object quantity = body.get("quantity");
        order.quantity = quantity instanceof Number number ? number.intValue() : 1;
        return orders.save(order);
    }

    @GetMapping("/news")
    public List<NewsItem> news() {
        return news.findAll();
    }
}
