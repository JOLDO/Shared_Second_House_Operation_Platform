package com.busanit401.spring_back.domain.service;

import com.busanit401.spring_back.domain.Order;
import com.busanit401.spring_back.domain.OrderItem;
import com.busanit401.spring_back.domain.Product;
import com.busanit401.spring_back.domain.repository.CartRepository;
import com.busanit401.spring_back.domain.repository.OrderItemRepository;
import com.busanit401.spring_back.domain.repository.OrderRepository;
import com.busanit401.spring_back.domain.repository.ProductRepository;
// dto 패키지를 절대 경로 방식으로 import
import com.busanit401.spring_back.dto.OrderItemDto;
import com.busanit401.spring_back.dto.OrderRequestDto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    // 주문 생성 로직
    public Long createOrder(OrderRequestDto dto) {
        // 마스터 주문 저장
        Order order = Order.builder()
                .userId(dto.getUserId())
                .deliveryAddress(dto.getDeliveryAddress())
                .totalAmount(Math.toIntExact(dto.getTotalAmount()))
                .status("주문대기")
                .build();

        Order savedOrder = orderRepository.save(order);

        // 상세 아이템 목록 추가
        for (OrderItemDto itemDto : dto.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 ID입니다: " + itemDto.getProductId()));

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(itemDto.getQuantity())
                    .price(Math.toIntExact(itemDto.getPrice()))
                    .build();

            orderItemRepository.save(orderItem);
        }

        // 장바구니 비우기
        cartRepository.deleteByUserId(dto.getUserId());

        return savedOrder.getOrderId();
    }

    // 전체 주문 조회 로직
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}