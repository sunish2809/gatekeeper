package com.project.gatekeeper.demo;

import org.springframework.data.jpa.repository.JpaRepository;

interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
}

interface NewsRepository extends JpaRepository<NewsItem, Long> {
}
