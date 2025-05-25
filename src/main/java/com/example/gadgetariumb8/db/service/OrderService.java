package com.example.gadgetariumb8.db.service;

import com.example.gadgetariumb8.db.dto.request.UserOrderRequest;
import com.example.gadgetariumb8.db.dto.response.*;
import com.example.gadgetariumb8.db.dto.response.SimpleResponse;

import java.time.LocalDate;
import java.util.List;

public interface OrderService {
    PaginationResponse<OrderResponse> getAllOrders(String keyWord, String status, LocalDate from, LocalDate before, int page, int pageSize);

    UserOrderResponse ordering(UserOrderRequest userOrderRequest);

    SimpleResponse changeStatusOfOrder(Long orderId, String status);

    List<OrderGetByIdResponse> getByIdOrder(Long id);

    SimpleResponse delete(Long orderId);
}
