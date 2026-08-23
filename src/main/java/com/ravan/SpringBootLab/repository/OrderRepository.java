package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderStatus;
import com.ravan.SpringBootLab.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT order FROM Order order WHERE order.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Integer orderId);

    @Query("""
            SELECT order.user.id
            FROM Order order
            WHERE order.id = :orderId
            """)
    Optional<Integer> findOwnerIdById(@Param("orderId") Integer orderId);

    @Query("""
            SELECT order.id
            FROM Order order
            WHERE order.status = :status
              AND order.reservationExpiresAt IS NOT NULL
              AND order.reservationExpiresAt <= :now
            """)
    List<Integer> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now
    );
}