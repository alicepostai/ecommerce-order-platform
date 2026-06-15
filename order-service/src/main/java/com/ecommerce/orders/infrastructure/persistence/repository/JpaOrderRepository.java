package com.ecommerce.orders.infrastructure.persistence.repository;

import com.ecommerce.orders.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaOrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("SELECT COUNT(o) > 0 FROM OrderEntity o WHERE o.customerId = :customerId " +
           "AND o.status IN ('CREATED', 'CONFIRMED', 'PAYMENT_PENDING')")
    boolean existsActiveOrderForCustomer(@Param("customerId") UUID customerId);
}
