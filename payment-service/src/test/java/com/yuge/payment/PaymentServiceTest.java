package com.yuge.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.payment.api.dto.CallbackResponse;
import com.yuge.payment.api.dto.CreatePaymentRequest;
import com.yuge.payment.api.dto.MockCallbackRequest;
import com.yuge.payment.api.dto.PaymentResponse;
import com.yuge.payment.application.PaymentService;
import com.yuge.payment.domain.entity.PayOrder;
import com.yuge.payment.domain.enums.PayStatus;
import com.yuge.payment.infrastructure.repository.PayOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付服务单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PayOrderRepository payOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.callback-secret}")
    private String callbackSecret;

    private String testOrderNo;

    @BeforeEach
    void setUp() {
        testOrderNo = "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);
    }

    @Test
    @DisplayName("创建支付单 - 成功")
    void testCreatePayment_Success() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(testOrderNo);
        request.setAmount(new BigDecimal("99.99"));
        request.setChannel("MOCK");

        // Act
        PaymentResponse response = paymentService.createPayment(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getPayNo());
        assertEquals(testOrderNo, response.getOrderNo());
        assertEquals(new BigDecimal("99.99"), response.getAmount());
        assertEquals(PayStatus.INIT.getCode(), response.getStatus());
        assertEquals("MOCK", response.getChannel());
        assertNotNull(response.getExpireAt());
    }

    @Test
    @DisplayName("创建支付单 - 幂等性（重复创建返回已存在的支付单）")
    void testCreatePayment_Idempotent() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(testOrderNo);
        request.setAmount(new BigDecimal("99.99"));

        // Act - 第一次创建
        PaymentResponse response1 = paymentService.createPayment(request);
        
        // Act - 第二次创建（相同orderNo）
        PaymentResponse response2 = paymentService.createPayment(request);

        // Assert - 应该返回相同的支付单
        assertEquals(response1.getPayNo(), response2.getPayNo());
        assertEquals(response1.getId(), response2.getId());
    }

    @Test
    @DisplayName("查询支付单 - 成功")
    void testGetPayment_Success() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(testOrderNo);
        request.setAmount(new BigDecimal("50.00"));
        PaymentResponse created = paymentService.createPayment(request);

        // Act
        PaymentResponse response = paymentService.getPayment(created.getPayNo());

        // Assert
        assertNotNull(response);
        assertEquals(created.getPayNo(), response.getPayNo());
        assertEquals(testOrderNo, response.getOrderNo());
    }

    @Test
    @DisplayName("根据订单号查询支付单 - 成功")
    void testGetPaymentByOrderNo_Success() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(testOrderNo);
        request.setAmount(new BigDecimal("50.00"));
        paymentService.createPayment(request);

        // Act
        PaymentResponse response = paymentService.getPaymentByOrderNo(testOrderNo);

        // Assert
        assertNotNull(response);
        assertEquals(testOrderNo, response.getOrderNo());
    }

    @Test
    @DisplayName("验证签名 - 成功")
    void testVerifySignature_Success() {
        // Arrange
        String payNo = "PAY123";
        String callbackStatus = "SUCCESS";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().substring(0, 8);

        // 生成签名
        String signature = paymentService.generateSignature(payNo, callbackStatus, timestamp, nonce);

        MockCallbackRequest request = new MockCallbackRequest();
        request.setPayNo(payNo);
        request.setCallbackStatus(callbackStatus);
        request.setTimestamp(timestamp);
        request.setNonce(nonce);
        request.setSignature(signature);

        // Act
        boolean valid = paymentService.verifySignature(request);

        // Assert
        assertTrue(valid);
    }

    @Test
    @DisplayName("验证签名 - 失败（签名错误）")
    void testVerifySignature_Failed() {
        // Arrange
        MockCallbackRequest request = new MockCallbackRequest();
        request.setPayNo("PAY123");
        request.setCallbackStatus("SUCCESS");
        request.setTimestamp(String.valueOf(System.currentTimeMillis()));
        request.setNonce("abc123");
        request.setSignature("INVALID_SIGNATURE");

        // Act
        boolean valid = paymentService.verifySignature(request);

        // Assert
        assertFalse(valid);
    }

    @Test
    @DisplayName("处理回调 - 支付成功")
    void testHandleMockCallback_Success() {
        // Arrange - 先创建支付单
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo(testOrderNo);
        createRequest.setAmount(new BigDecimal("100.00"));
        PaymentResponse payment = paymentService.createPayment(createRequest);

        // 构造回调请求
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String signature = paymentService.generateSignature(payment.getPayNo(), "SUCCESS", timestamp, nonce);

        MockCallbackRequest callbackRequest = new MockCallbackRequest();
        callbackRequest.setPayNo(payment.getPayNo());
        callbackRequest.setCallbackStatus("SUCCESS");
        callbackRequest.setChannelTradeNo("MOCK_" + System.currentTimeMillis());
        callbackRequest.setTimestamp(timestamp);
        callbackRequest.setNonce(nonce);
        callbackRequest.setSignature(signature);

        // Act
        CallbackResponse response = paymentService.handleMockCallback(callbackRequest);

        // Assert
        assertNotNull(response);
        assertEquals("PROCESSED", response.getResult());
        assertEquals(PayStatus.SUCCESS.getCode(), response.getCurrentStatus());

        // 验证数据库状态
        Optional<PayOrder> updatedOrder = payOrderRepository.findByPayNo(payment.getPayNo());
        assertTrue(updatedOrder.isPresent());
        assertEquals(PayStatus.SUCCESS.getCode(), updatedOrder.get().getStatus());
        assertNotNull(updatedOrder.get().getPaidAt());
    }

    @Test
    @DisplayName("处理回调 - 支付失败")
    void testHandleMockCallback_Failed() {
        // Arrange
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo(testOrderNo);
        createRequest.setAmount(new BigDecimal("100.00"));
        PaymentResponse payment = paymentService.createPayment(createRequest);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String signature = paymentService.generateSignature(payment.getPayNo(), "FAILED", timestamp, nonce);

        MockCallbackRequest callbackRequest = new MockCallbackRequest();
        callbackRequest.setPayNo(payment.getPayNo());
        callbackRequest.setCallbackStatus("FAILED");
        callbackRequest.setTimestamp(timestamp);
        callbackRequest.setNonce(nonce);
        callbackRequest.setSignature(signature);

        // Act
        CallbackResponse response = paymentService.handleMockCallback(callbackRequest);

        // Assert
        assertEquals("PROCESSED", response.getResult());
        assertEquals(PayStatus.FAILED.getCode(), response.getCurrentStatus());
    }

    @Test
    @DisplayName("处理回调 - 幂等性（重复回调返回IGNORED）")
    void testHandleMockCallback_Idempotent() {
        // Arrange
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo(testOrderNo);
        createRequest.setAmount(new BigDecimal("100.00"));
        PaymentResponse payment = paymentService.createPayment(createRequest);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String signature = paymentService.generateSignature(payment.getPayNo(), "SUCCESS", timestamp, nonce);

        MockCallbackRequest callbackRequest = new MockCallbackRequest();
        callbackRequest.setPayNo(payment.getPayNo());
        callbackRequest.setCallbackStatus("SUCCESS");
        callbackRequest.setChannelTradeNo("MOCK_123");
        callbackRequest.setTimestamp(timestamp);
        callbackRequest.setNonce(nonce);
        callbackRequest.setSignature(signature);

        // Act - 第一次回调
        CallbackResponse response1 = paymentService.handleMockCallback(callbackRequest);
        
        // Act - 第二次回调（相同参数）
        CallbackResponse response2 = paymentService.handleMockCallback(callbackRequest);

        // Assert
        assertEquals("PROCESSED", response1.getResult());
        assertEquals("IGNORED", response2.getResult());
        assertTrue(response2.getMessage().contains("重复回调"));
    }

    @Test
    @DisplayName("处理回调 - 签名验证失败")
    void testHandleMockCallback_InvalidSignature() {
        // Arrange
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo(testOrderNo);
        createRequest.setAmount(new BigDecimal("100.00"));
        PaymentResponse payment = paymentService.createPayment(createRequest);

        MockCallbackRequest callbackRequest = new MockCallbackRequest();
        callbackRequest.setPayNo(payment.getPayNo());
        callbackRequest.setCallbackStatus("SUCCESS");
        callbackRequest.setTimestamp(String.valueOf(System.currentTimeMillis()));
        callbackRequest.setNonce("abc123");
        callbackRequest.setSignature("INVALID_SIGNATURE");

        // Act
        CallbackResponse response = paymentService.handleMockCallback(callbackRequest);

        // Assert
        assertEquals("FAILED", response.getResult());
        assertTrue(response.getMessage().contains("签名验证失败"));
    }

    @Test
    @DisplayName("处理回调 - 支付单已终态（返回IGNORED）")
    void testHandleMockCallback_AlreadyTerminal() {
        // Arrange - 创建并完成支付
        CreatePaymentRequest createRequest = new CreatePaymentRequest();
        createRequest.setOrderNo(testOrderNo);
        createRequest.setAmount(new BigDecimal("100.00"));
        PaymentResponse payment = paymentService.createPayment(createRequest);

        // 第一次回调成功
        String timestamp1 = String.valueOf(System.currentTimeMillis());
        String nonce1 = UUID.randomUUID().toString().substring(0, 8);
        String signature1 = paymentService.generateSignature(payment.getPayNo(), "SUCCESS", timestamp1, nonce1);

        MockCallbackRequest callback1 = new MockCallbackRequest();
        callback1.setPayNo(payment.getPayNo());
        callback1.setCallbackStatus("SUCCESS");
        callback1.setChannelTradeNo("MOCK_1");
        callback1.setTimestamp(timestamp1);
        callback1.setNonce(nonce1);
        callback1.setSignature(signature1);
        paymentService.handleMockCallback(callback1);

        // 第二次回调（不同参数，但支付单已终态）
        String timestamp2 = String.valueOf(System.currentTimeMillis() + 1000);
        String nonce2 = UUID.randomUUID().toString().substring(0, 8);
        String signature2 = paymentService.generateSignature(payment.getPayNo(), "SUCCESS", timestamp2, nonce2);

        MockCallbackRequest callback2 = new MockCallbackRequest();
        callback2.setPayNo(payment.getPayNo());
        callback2.setCallbackStatus("SUCCESS");
        callback2.setChannelTradeNo("MOCK_2");
        callback2.setTimestamp(timestamp2);
        callback2.setNonce(nonce2);
        callback2.setSignature(signature2);

        // Act
        CallbackResponse response = paymentService.handleMockCallback(callback2);

        // Assert
        assertEquals("IGNORED", response.getResult());
        assertTrue(response.getMessage().contains("不允许接收回调"));
    }

    @Test
    @DisplayName("生成签名 - 一致性验证")
    void testGenerateSignature_Consistency() {
        // Arrange
        String payNo = "PAY123456";
        String callbackStatus = "SUCCESS";
        String timestamp = "1704067200000";
        String nonce = "abc123";

        // Act - 多次生成签名
        String signature1 = paymentService.generateSignature(payNo, callbackStatus, timestamp, nonce);
        String signature2 = paymentService.generateSignature(payNo, callbackStatus, timestamp, nonce);

        // Assert - 相同参数应生成相同签名
        assertEquals(signature1, signature2);
        assertNotNull(signature1);
        assertEquals(32, signature1.length()); // MD5 长度
    }
}
