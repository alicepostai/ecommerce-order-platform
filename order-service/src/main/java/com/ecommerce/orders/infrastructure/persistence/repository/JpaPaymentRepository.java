package com.ecommerce.orders.infrastructure.persistence.repository;

import com.ecommerce.orders.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @Query("SELECT p FROM PaymentEntity p WHERE p.orderId = :orderId AND p.status = 'PENDING'")
    Optional<PaymentEntity> findPendingByOrderId(@Param("orderId") UUID orderId);

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
