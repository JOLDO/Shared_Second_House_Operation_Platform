package com.busanit401.spring_back.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequestDto {
    private Long userId;
    private String deliveryAddress;
    private Long totalAmount;
    private List<OrderItemDto> items;
}
