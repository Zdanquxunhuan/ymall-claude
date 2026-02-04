package com.yuge.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.order.api.dto.CancelOrderRequest;
import com.yuge.order.api.dto.CreateOrderRequest;
import com.yuge.order.api.dto.OrderResponse;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.platform.infra.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 订单服务集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试1: 幂等创建订单 - 相同clientRequestId返回相同结果
     */
    @Test
    @DisplayName("幂等创建订单 - 相同clientRequestId返回相同结果")
    void testIdempotentCreateOrder() throws Exception {
        String clientRequestId = "test-" + UUID.randomUUID().toString();
        CreateOrderRequest request = buildCreateOrderRequest(clientRequestId, 10001L);

        // 第一次创建
        MvcResult result1 = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.orderNo").exists())
                .andReturn();

        Result<OrderResponse> response1 = parseResponse(result1);
        String orderNo1 = response1.getData().getOrderNo();

        // 第二次创建（相同clientRequestId）
        MvcResult result2 = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andReturn();

        Result<OrderResponse> response2 = parseResponse(result2);
        String orderNo2 = response2.getData().getOrderNo();

        // 验证：两次返回相同的订单号
        assertEquals(orderNo1, orderNo2, "幂等创建应返回相同订单号");
    }

    /**
     * 测试2: 重复取消订单 - 幂等返回
     */
    @Test
    @DisplayName("重复取消订单 - 幂等返回")
    void testIdempotentCancelOrder() throws Exception {
        // 1. 先创建订单
        String clientRequestId = "test-cancel-" + UUID.randomUUID().toString();
        CreateOrderRequest createRequest = buildCreateOrderRequest(clientRequestId, 10002L);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Result<OrderResponse> createResponse = parseResponse(createResult);
        String orderNo = createResponse.getData().getOrderNo();

        // 2. 第一次取消
        CancelOrderRequest cancelRequest = new CancelOrderRequest();
        cancelRequest.setCancelReason("用户主动取消");
        cancelRequest.setOperator("test_user");

        String idempotencyKey = "cancel-" + UUID.randomUUID().toString();

        MvcResult cancelResult1 = mockMvc.perform(post("/orders/" + orderNo + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andReturn();

        // 3. 第二次取消（相同idempotencyKey）
        MvcResult cancelResult2 = mockMvc.perform(post("/orders/" + orderNo + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andReturn();

        Result<OrderResponse> response1 = parseResponse(cancelResult1);
        Result<OrderResponse> response2 = parseResponse(cancelResult2);

        // 验证：两次返回相同状态
        assertEquals(response1.getData().getStatus(), response2.getData().getStatus());
    }

    /**
     * 测试3: 非法状态取消 - 已取消的订单不能再取消
     */
    @Test
    @DisplayName("非法状态取消 - 已取消订单再次取消应幂等返回")
    void testCancelAlreadyCanceledOrder() throws Exception {
        // 1. 创建订单
        String clientRequestId = "test-invalid-" + UUID.randomUUID().toString();
        CreateOrderRequest createRequest = buildCreateOrderRequest(clientRequestId, 10003L);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Result<OrderResponse> createResponse = parseResponse(createResult);
        String orderNo = createResponse.getData().getOrderNo();

        // 2. 第一次取消
        mockMvc.perform(post("/orders/" + orderNo + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key1-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        // 3. 第二次取消（不同idempotencyKey，但订单已取消）
        // 应该幂等返回已取消状态，而不是报错
        mockMvc.perform(post("/orders/" + orderNo + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key2-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    /**
     * 测试4: 查询订单
     */
    @Test
    @DisplayName("查询订单详情")
    void testGetOrder() throws Exception {
        // 1. 创建订单
        String clientRequestId = "test-query-" + UUID.randomUUID().toString();
        CreateOrderRequest createRequest = buildCreateOrderRequest(clientRequestId, 10004L);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Result<OrderResponse> createResponse = parseResponse(createResult);
        String orderNo = createResponse.getData().getOrderNo();

        // 2. 查询订单
        mockMvc.perform(get("/orders/" + orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    /**
     * 测试5: 查询不存在的订单
     */
    @Test
    @DisplayName("查询不存在的订单返回404")
    void testGetNonExistentOrder() throws Exception {
        mockMvc.perform(get("/orders/NON_EXISTENT_ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    /**
     * 构建创建订单请求
     */
    private CreateOrderRequest buildCreateOrderRequest(String clientRequestId, Long userId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientRequestId(clientRequestId);
        request.setUserId(userId);
        request.setRemark("测试订单");

        List<CreateOrderRequest.OrderItemRequest> items = new ArrayList<>();
        
        CreateOrderRequest.OrderItemRequest item1 = new CreateOrderRequest.OrderItemRequest();
        item1.setSkuId(1001L);
        item1.setQty(2);
        item1.setTitle("测试商品1");
        item1.setPrice(new BigDecimal("99.99"));
        items.add(item1);

        CreateOrderRequest.OrderItemRequest item2 = new CreateOrderRequest.OrderItemRequest();
        item2.setSkuId(1002L);
        item2.setQty(1);
        item2.setTitle("测试商品2");
        item2.setPrice(new BigDecimal("199.99"));
        items.add(item2);

        request.setItems(items);
        return request;
    }

    /**
     * 解析响应
     */
    @SuppressWarnings("unchecked")
    private Result<OrderResponse> parseResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return objectMapper.readValue(content, Result.class);
    }
}
