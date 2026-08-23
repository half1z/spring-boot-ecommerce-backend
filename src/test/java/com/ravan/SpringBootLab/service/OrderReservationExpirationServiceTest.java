package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderItem;
import com.ravan.SpringBootLab.model.OrderStatus;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.repository.OrderItemRepository;
import com.ravan.SpringBootLab.repository.OrderRepository;
import com.ravan.SpringBootLab.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReservationExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    private OrderReservationExpirationService service;

    private Product product;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        service = new OrderReservationExpirationService(
                orderRepository,
                orderItemRepository,
                productRepository
        );

        product = new Product(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1000.00"),
                7
        );
        product.setId(1);

        order = new Order(
                null,
                new BigDecimal("3000.00"),
                OrderStatus.PENDING
        );
        order.setId(10);
        order.setReservationExpiresAt(
                LocalDateTime.now().minusMinutes(1)
        );

        orderItem = new OrderItem(
                order,
                product,
                product.getName(),
                product.getPrice(),
                3,
                new BigDecimal("3000.00")
        );
    }

    @Test
    void expiresPendingOrderAndRestoresStock() {
        when(orderRepository.findExpiredOrderIds(
                eq(OrderStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(10));

        when(orderRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrder(order))
                .thenReturn(List.of(orderItem));

        service.expireReservations();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(10, product.getStock());

        verify(productRepository).save(product);
        verify(orderRepository).save(order);
    }

    @Test
    void doesNotExpireOrderThatWasAlreadyPaid() {
        order.setStatus(OrderStatus.PAID);

        when(orderRepository.findExpiredOrderIds(
                eq(OrderStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(10));

        when(orderRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(order));

        service.expireReservations();

        assertEquals(OrderStatus.PAID, order.getStatus());

        verifyNoInteractions(
                orderItemRepository,
                productRepository
        );

        verify(orderRepository, never()).save(order);
    }
}