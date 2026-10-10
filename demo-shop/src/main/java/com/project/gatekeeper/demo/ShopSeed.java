package com.project.gatekeeper.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ShopSeed implements ApplicationRunner {

    private final ShopOrderRepository orders;
    private final NewsRepository news;

    public ShopSeed(ShopOrderRepository orders, NewsRepository news) {
        this.orders = orders;
        this.news = news;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (orders.count() == 0) {
            orders.save(order("pencil", 2));
            orders.save(order("notebook", 1));
        }
        if (news.count() == 0) {
            NewsItem item = new NewsItem();
            item.title = "Shop is open";
            news.save(item);
        }
    }

    private static ShopOrder order(String product, int quantity) {
        ShopOrder order = new ShopOrder();
        order.product = product;
        order.quantity = quantity;
        return order;
    }
}
