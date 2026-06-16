package com.ecommerce.orders.application.usecase;

import com.ecommerce.orders.application.dto.PaymentResult;
import com.ecommerce.orders.application.port.in.GetPaymentUseCase;
import com.ecommerce.orders.application.port.out.PaymentRepository;
import com.ecommerce.orders.domain.exception.PaymentNotFoundException;
import com.ecommerce.orders.domain.model.PaymentId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetPaymentUseCaseImpl implements GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentUseCaseImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentResult getById(UUID paymentId) {
        return paymentRepository.findById(new PaymentId(paymentId))
                .map(PaymentResult::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId.toString()));
    }
}
