package com.busanit401.spring_back.controller;

import com.busanit401.spring_back.domain.OrderRequest;
import com.busanit401.spring_back.domain.Order;
import com.busanit401.spring_back.domain.service.OrderService;
import com.busanit401.spring_back.dto.OrderRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 리액트 웹 환경과의 CORS 에러 처리 보장
public class OrderController {

    private final OrderService orderService;

    // 플러터 앱에서 장바구니 데이터를 전송하는 주문하기 API
    @PostMapping // 경로가 /api/orders 이므로 placeOrder만 호출됨
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequestDto orderRequest) {
        try {
            // orderRequestDto를 기반으로 서비스 로직 호출
            Long orderId = orderService.createOrder(orderRequest);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "주문이 정상적으로 등록되었습니다.",
                    "orderId", orderId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
    // 리액트 어드민 웹 페이지에서 주문 목록을 가져오는 API
    @GetMapping("/admin")
    public ResponseEntity<List<Order>> getAdminOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
}
