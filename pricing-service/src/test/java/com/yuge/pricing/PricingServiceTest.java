package com.yuge.pricing;

import com.yuge.pricing.api.dto.*;
import com.yuge.pricing.application.PricingService;
import com.yuge.pricing.application.PromotionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 定价服务测试
 */
@SpringBootTest
@ActiveProfiles("test")
class PricingServiceTest {

    @Autowired
    private PricingService pricingService;

    @Autowired
    private PromotionClient promotionClient;

    @BeforeEach
    void setUp() {
        // 清除模拟数据
        promotionClient.clearMockData();

        // 添加模拟优惠券
        PromotionClient.MockCoupon coupon = PromotionClient.MockCoupon.builder()
                .id(1L)
                .couponCode("CPN001")
                .name("满100减20")
                .type("FULL_REDUCTION")
                .thresholdAmount(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("20.00"))
                .build();
        promotionClient.addMockCoupon(coupon);

        // 添加模拟用户优惠券
        PromotionClient.MockUserCoupon userCoupon = PromotionClient.MockUserCoupon.builder()
                .id(1L)
                .userCouponNo("UC001")
                .userId(10001L)
                .couponCode("CPN001")
                .status("AVAILABLE")
                .build();
        promotionClient.addMockUserCoupon(userCoupon);
    }

    @Test
    void testQuoteWithoutCoupon() {
        // 准备请求
        QuoteRequest request = new QuoteRequest();
        request.setUserId(10001L);

        QuoteRequest.ItemInfo item1 = new QuoteRequest.ItemInfo();
        item1.setSkuId(1001L);
        item1.setQty(2);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setTitle("商品A");

        QuoteRequest.ItemInfo item2 = new QuoteRequest.ItemInfo();
        item2.setSkuId(1002L);
        item2.setQty(1);
        item2.setUnitPrice(new BigDecimal("80.00"));
        item2.setTitle("商品B");

        request.setItems(Arrays.asList(item1, item2));

        // 执行试算
        QuoteResponse response = pricingService.quote(request);

        // 验证结果
        assertNotNull(response);
        assertEquals(10001L, response.getUserId());
        assertEquals(new BigDecimal("180.00"), response.getOriginalAmount());
        assertEquals(BigDecimal.ZERO, response.getTotalDiscount());
        assertEquals(new BigDecimal("180.00"), response.getPayableAmount());

        // 验证分摊
        assertEquals(2, response.getAllocations().size());

        // 验证可用优惠券
        assertFalse(response.getAvailableCoupons().isEmpty());
    }

    @Test
    void testQuoteWithCoupon() {
        // 准备请求
        QuoteRequest request = new QuoteRequest();
        request.setUserId(10001L);

        QuoteRequest.ItemInfo item1 = new QuoteRequest.ItemInfo();
        item1.setSkuId(1001L);
        item1.setQty(2);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setTitle("商品A");

        QuoteRequest.ItemInfo item2 = new QuoteRequest.ItemInfo();
        item2.setSkuId(1002L);
        item2.setQty(1);
        item2.setUnitPrice(new BigDecimal("80.00"));
        item2.setTitle("商品B");

        request.setItems(Arrays.asList(item1, item2));
        request.setUserCouponNos(Arrays.asList("UC001"));

        // 执行试算
        QuoteResponse response = pricingService.quote(request);

        // 验证结果
        assertNotNull(response);
        assertEquals(new BigDecimal("180.00"), response.getOriginalAmount());
        assertEquals(new BigDecimal("20.00"), response.getTotalDiscount());
        assertEquals(new BigDecimal("160.00"), response.getPayableAmount());

        // 验证分摊 - 总优惠应该等于各行优惠之和
        BigDecimal totalLineDiscount = response.getAllocations().stream()
                .map(QuoteResponse.AllocationDetail::getLineDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getTotalDiscount(), totalLineDiscount);

        // 验证分摊 - 总应付应该等于各行应付之和
        BigDecimal totalLinePayable = response.getAllocations().stream()
                .map(QuoteResponse.AllocationDetail::getLinePayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getPayableAmount(), totalLinePayable);

        // 验证命中规则
        assertEquals(1, response.getPromotionHits().size());
        assertEquals("COUPON", response.getPromotionHits().get(0).getRuleType());
    }

    @Test
    void testAllocationAuditable() {
        // 准备请求 - 3个商品
        QuoteRequest request = new QuoteRequest();
        request.setUserId(10001L);

        QuoteRequest.ItemInfo item1 = new QuoteRequest.ItemInfo();
        item1.setSkuId(1001L);
        item1.setQty(1);
        item1.setUnitPrice(new BigDecimal("100.00"));
        item1.setTitle("商品A");

        QuoteRequest.ItemInfo item2 = new QuoteRequest.ItemInfo();
        item2.setSkuId(1002L);
        item2.setQty(2);
        item2.setUnitPrice(new BigDecimal("50.00"));
        item2.setTitle("商品B");

        QuoteRequest.ItemInfo item3 = new QuoteRequest.ItemInfo();
        item3.setSkuId(1003L);
        item3.setQty(1);
        item3.setUnitPrice(new BigDecimal("30.00"));
        item3.setTitle("商品C");

        request.setItems(Arrays.asList(item1, item2, item3));
        request.setUserCouponNos(Arrays.asList("UC001"));

        // 执行试算
        QuoteResponse response = pricingService.quote(request);

        // 验证每行可审计
        for (QuoteResponse.AllocationDetail allocation : response.getAllocations()) {
            // 行原价 - 行优惠 = 行应付
            BigDecimal calculated = allocation.getLineOriginalAmount()
                    .subtract(allocation.getLineDiscountAmount());
            assertEquals(allocation.getLinePayableAmount(), calculated,
                    "行应付金额应等于行原价减去行优惠");
        }

        // 验证总金额可复算
        BigDecimal sumOriginal = response.getAllocations().stream()
                .map(QuoteResponse.AllocationDetail::getLineOriginalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getOriginalAmount(), sumOriginal);

        BigDecimal sumDiscount = response.getAllocations().stream()
                .map(QuoteResponse.AllocationDetail::getLineDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getTotalDiscount(), sumDiscount);

        BigDecimal sumPayable = response.getAllocations().stream()
                .map(QuoteResponse.AllocationDetail::getLinePayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getPayableAmount(), sumPayable);
    }

    @Test
    void testLockAndVerify() {
        // 准备请求
        LockRequest request = new LockRequest();
        request.setUserId(10001L);

        LockRequest.ItemInfo item1 = new LockRequest.ItemInfo();
        item1.setSkuId(1001L);
        item1.setQty(2);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setTitle("商品A");

        LockRequest.ItemInfo item2 = new LockRequest.ItemInfo();
        item2.setSkuId(1002L);
        item2.setQty(1);
        item2.setUnitPrice(new BigDecimal("80.00"));
        item2.setTitle("商品B");

        request.setItems(Arrays.asList(item1, item2));
        request.setUserCouponNos(Arrays.asList("UC001"));
        request.setLockMinutes(15);

        // 执行锁价
        LockResponse response = pricingService.lock(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getPriceLockNo());
        assertNotNull(response.getSignature());
        assertEquals("LOCKED", response.getStatus());
        assertEquals(new BigDecimal("160.00"), response.getPayableAmount());

        // 验证签名
        boolean verified = pricingService.verifySignature(
                response.getPriceLockNo(), response.getSignature());
        assertTrue(verified);

        // 验证错误签名
        boolean wrongVerified = pricingService.verifySignature(
                response.getPriceLockNo(), "wrong-signature");
        assertFalse(wrongVerified);
    }

    @Test
    void testLockConsistentWithQuote() {
        // 准备相同的请求
        Long userId = 10001L;
        List<String> couponNos = Arrays.asList("UC001");

        QuoteRequest.ItemInfo qi1 = new QuoteRequest.ItemInfo();
        qi1.setSkuId(1001L);
        qi1.setQty(2);
        qi1.setUnitPrice(new BigDecimal("50.00"));
        qi1.setTitle("商品A");

        QuoteRequest.ItemInfo qi2 = new QuoteRequest.ItemInfo();
        qi2.setSkuId(1002L);
        qi2.setQty(1);
        qi2.setUnitPrice(new BigDecimal("80.00"));
        qi2.setTitle("商品B");

        // 试算
        QuoteRequest quoteRequest = new QuoteRequest();
        quoteRequest.setUserId(userId);
        quoteRequest.setItems(Arrays.asList(qi1, qi2));
        quoteRequest.setUserCouponNos(couponNos);
        QuoteResponse quoteResponse = pricingService.quote(quoteRequest);

        // 锁价
        LockRequest.ItemInfo li1 = new LockRequest.ItemInfo();
        li1.setSkuId(1001L);
        li1.setQty(2);
        li1.setUnitPrice(new BigDecimal("50.00"));
        li1.setTitle("商品A");

        LockRequest.ItemInfo li2 = new LockRequest.ItemInfo();
        li2.setSkuId(1002L);
        li2.setQty(1);
        li2.setUnitPrice(new BigDecimal("80.00"));
        li2.setTitle("商品B");

        LockRequest lockRequest = new LockRequest();
        lockRequest.setUserId(userId);
        lockRequest.setItems(Arrays.asList(li1, li2));
        lockRequest.setUserCouponNos(couponNos);
        LockResponse lockResponse = pricingService.lock(lockRequest);

        // 验证试算和锁价结果一致
        assertEquals(quoteResponse.getOriginalAmount(), lockResponse.getOriginalAmount());
        assertEquals(quoteResponse.getTotalDiscount(), lockResponse.getTotalDiscount());
        assertEquals(quoteResponse.getPayableAmount(), lockResponse.getPayableAmount());

        // 验证分摊一致
        assertEquals(quoteResponse.getAllocations().size(), lockResponse.getAllocations().size());
        for (int i = 0; i < quoteResponse.getAllocations().size(); i++) {
            QuoteResponse.AllocationDetail qa = quoteResponse.getAllocations().get(i);
            LockResponse.AllocationDetail la = lockResponse.getAllocations().get(i);
            assertEquals(qa.getSkuId(), la.getSkuId());
            assertEquals(qa.getLineOriginalAmount(), la.getLineOriginalAmount());
            assertEquals(qa.getLineDiscountAmount(), la.getLineDiscountAmount());
            assertEquals(qa.getLinePayableAmount(), la.getLinePayableAmount());
        }
    }
}
