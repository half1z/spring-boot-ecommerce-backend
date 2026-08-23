package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.exception.OrderNotFoundException;
import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderItem;
import com.ravan.SpringBootLab.model.OrderStatus;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.repository.OrderItemRepository;
import com.ravan.SpringBootLab.repository.OrderRepository;
import com.ravan.SpringBootLab.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderReservationExpirationService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderReservationExpirationService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public OrderReservationExpirationService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    @Scheduled(
            fixedDelayString =
                    "${order.reservation.expiration.fixed-delay-ms:60000}",
            initialDelayString =
                    "${order.reservation.expiration.initial-delay-ms:60000}"
    )
    @Transactional
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<Integer> expiredOrderIds =
                orderRepository.findExpiredOrderIds(
                        OrderStatus.PENDING,
                        now
                );

        if (expiredOrderIds.isEmpty()) {
            return;
        }

        int expiredCount = 0;

        for (Integer orderId : expiredOrderIds) {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            /*
             * The order could have been paid or cancelled after the
             * expiration query ran but before we acquired the lock.
             */
            if (order.getStatus() != OrderStatus.PENDING) {
                continue;
            }

            if (order.getReservationExpiresAt() == null
                    || order.getReservationExpiresAt().isAfter(now)) {
                continue;
            }

            List<OrderItem> orderItems =
                    orderItemRepository.findByOrder(order);

            for (OrderItem orderItem : orderItems) {
                Product product = orderItem.getProduct();

                product.setStock(
                        product.getStock() + orderItem.getQuantity()
                );
                product.setUpdatedAt(LocalDateTime.now());

                productRepository.save(product);
            }

            order.setStatus(OrderStatus.CANCELLED);
            order.setReservationExpiresAt(null);
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);

            expiredCount++;
        }

        log.info(
                "Expired unpaid order reservations: count={}",
                expiredCount
        );
    }
}