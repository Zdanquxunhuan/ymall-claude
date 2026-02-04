package com.yuge.cart.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.cart.api.dto.*;
import com.yuge.cart.domain.entity.CartItem;
import com.yuge.cart.domain.entity.CartMergeLog;
import com.yuge.cart.domain.enums.MergeStrategy;
import com.yuge.cart.infrastructure.client.InventoryClient;
import com.yuge.cart.infrastructure.client.PricingClient;
import com.yuge.cart.infrastructure.redis.CartRedisService;
import com.yuge.cart.infrastructure.repository.CartMergeLogMapper;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRedisService cartRedisService;
    private final CartMergeLogMapper cartMergeLogMapper;
    private final PricingClient pricingClient;
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    /**
     * 添加商品到购物车
     */
    public CartResponse addItem(Long userId, String anonId, AddCartRequest request) {
        String cartKey = resolveCartKey(userId, anonId);
        
        CartItem item = CartItem.builder()
                .skuId(request.getSkuId())
                .spuId(request.getSpuId())
                .title(request.getTitle())
                .imageUrl(request.getImageUrl())
                .unitPrice(request.getUnitPrice())
                .qty(request.getQty())
                .skuAttrs(request.getSkuAttrs())
                .categoryId(request.getCategoryId())
                .warehouseId(request.getWarehouseId() != null ? request.getWarehouseId() : 1L)
                .checked(true)
                .build();

        cartRedisService.addItem(cartKey, item);
        
        log.info("[CartService] addItem success, cartKey={}, skuId={}, qty={}", 
                cartKey, request.getSkuId(), request.getQty());
        
        return getCart(userId, anonId);
    }

    /**
     * 更新商品数量
     */
    public CartResponse updateQty(Long userId, String anonId, UpdateQtyRequest request) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.updateQty(cartKey, request.getSkuId(), request.getQty());
        
        log.info("[CartService] updateQty success, cartKey={}, skuId={}, qty={}", 
                cartKey, request.getSkuId(), request.getQty());
        
        return getCart(userId, anonId);
    }

    /**
     * 更新商品选中状态
     */
    public CartResponse checkItem(Long userId, String anonId, CheckItemRequest request) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.checkItem(cartKey, request.getSkuId(), request.getChecked());
        
        log.debug("[CartService] checkItem success, cartKey={}, skuId={}, checked={}", 
                cartKey, request.getSkuId(), request.getChecked());
        
        return getCart(userId, anonId);
    }

    /**
     * 全选/取消全选
     */
    public CartResponse checkAll(Long userId, String anonId, boolean checked) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.checkAll(cartKey, checked);
        
        log.debug("[CartService] checkAll success, cartKey={}, checked={}", cartKey, checked);
        
        return getCart(userId, anonId);
    }

    /**
     * 移除商品
     */
    public CartResponse removeItem(Long userId, String anonId, Long skuId) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.removeItem(cartKey, skuId);
        
        log.info("[CartService] removeItem success, cartKey={}, skuId={}", cartKey, skuId);
        
        return getCart(userId, anonId);
    }

    /**
     * 批量移除商品
     */
    public CartResponse removeItems(Long userId, String anonId, List<Long> skuIds) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.removeItems(cartKey, skuIds);
        
        log.info("[CartService] removeItems success, cartKey={}, skuIds={}", cartKey, skuIds);
        
        return getCart(userId, anonId);
    }

    /**
     * 清空购物车
     */
    public void clear(Long userId, String anonId) {
        String cartKey = resolveCartKey(userId, anonId);
        
        cartRedisService.clear(cartKey);
        
        log.info("[CartService] clear success, cartKey={}", cartKey);
    }

    /**
     * 获取购物车
     */
    public CartResponse getCart(Long userId, String anonId) {
        String cartKey = resolveCartKey(userId, anonId);
        
        List<CartItem> items = cartRedisService.getAll(cartKey);
        
        return buildCartResponse(items);
    }

    /**
     * 合并购物车（游客车 -> 登录车）
     * 
     * 冲突策略说明：
     * - QTY_ADD（数量累加）：同SKU时，数量相加，上限99
     * - LATEST_WIN（以最新为准）：同SKU时，以最后更新时间较新的为准
     */
    @Transactional(rollbackFor = Exception.class)
    public CartResponse mergeCart(Long userId, MergeCartRequest request) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户未登录，无法合并购物车");
        }

        long startTime = System.currentTimeMillis();
        String anonId = request.getAnonId();
        MergeStrategy strategy = MergeStrategy.of(request.getMergeStrategy());

        String userCartKey = CartRedisService.userCartKey(userId);
        String anonCartKey = CartRedisService.anonCartKey(anonId);

        // 1. 获取两个购物车的商品
        List<CartItem> userItems = cartRedisService.getAll(userCartKey);
        List<CartItem> anonItems = cartRedisService.getAll(anonCartKey);

        // 如果游客车为空，无需合并
        if (anonItems.isEmpty()) {
            log.info("[CartService] mergeCart skipped, anonCart is empty, userId={}, anonId={}", 
                    userId, anonId);
            return buildCartResponse(userItems);
        }

        // 2. 记录合并前快照
        String anonCartSnapshot = toJson(anonItems);
        String userCartSnapshot = toJson(userItems);

        // 3. 执行合并
        Map<Long, CartItem> userItemMap = userItems.stream()
                .collect(Collectors.toMap(CartItem::getSkuId, Function.identity()));

        int conflictCount = 0;
        List<CartItem> mergedItems = new ArrayList<>(userItems);

        for (CartItem anonItem : anonItems) {
            CartItem existingItem = userItemMap.get(anonItem.getSkuId());
            
            if (existingItem != null) {
                // 冲突：同SKU存在于两个车中
                conflictCount++;
                
                if (strategy == MergeStrategy.QTY_ADD) {
                    // 数量累加策略
                    int newQty = Math.min(existingItem.getQty() + anonItem.getQty(), 99);
                    existingItem.setQty(newQty);
                    existingItem.setUpdatedAt(LocalDateTime.now());
                    // 更新价格为最新
                    existingItem.setUnitPrice(anonItem.getUnitPrice());
                } else {
                    // 以最新为准策略
                    if (anonItem.getUpdatedAt() != null && existingItem.getUpdatedAt() != null
                            && anonItem.getUpdatedAt().isAfter(existingItem.getUpdatedAt())) {
                        // 游客车的更新时间更新，使用游客车的数据
                        existingItem.setQty(anonItem.getQty());
                        existingItem.setUnitPrice(anonItem.getUnitPrice());
                        existingItem.setTitle(anonItem.getTitle());
                        existingItem.setImageUrl(anonItem.getImageUrl());
                        existingItem.setUpdatedAt(anonItem.getUpdatedAt());
                    }
                }
            } else {
                // 无冲突：直接添加
                anonItem.setUpdatedAt(LocalDateTime.now());
                mergedItems.add(anonItem);
                userItemMap.put(anonItem.getSkuId(), anonItem);
            }
        }

        // 4. 保存合并后的购物车
        cartRedisService.clear(userCartKey);
        cartRedisService.saveAll(userCartKey, mergedItems);

        // 5. 清空游客车
        cartRedisService.clear(anonCartKey);

        // 6. 记录合并日志
        long mergeTimeMs = System.currentTimeMillis() - startTime;
        CartMergeLog mergeLog = new CartMergeLog();
        mergeLog.setId(IdUtil.getSnowflakeNextId());
        mergeLog.setUserId(userId);
        mergeLog.setAnonId(anonId);
        mergeLog.setMergeStrategy(strategy.getCode());
        mergeLog.setAnonCartSnapshot(anonCartSnapshot);
        mergeLog.setUserCartSnapshot(userCartSnapshot);
        mergeLog.setMergedCartSnapshot(toJson(mergedItems));
        mergeLog.setMergedSkuCount(anonItems.size());
        mergeLog.setConflictSkuCount(conflictCount);
        mergeLog.setMergeTimeMs(mergeTimeMs);
        mergeLog.setRemark(String.format("策略:%s, 游客车SKU数:%d, 冲突数:%d", 
                strategy.getDesc(), anonItems.size(), conflictCount));
        mergeLog.setCreatedAt(LocalDateTime.now());

        cartMergeLogMapper.insert(mergeLog);

        log.info("[CartService] mergeCart success, userId={}, anonId={}, strategy={}, " +
                        "anonSkuCount={}, conflictCount={}, mergeTimeMs={}",
                userId, anonId, strategy.getCode(), anonItems.size(), conflictCount, mergeTimeMs);

        return buildCartResponse(mergedItems);
    }

    /**
     * 结算（调用pricing锁价 + inventory库存校验）
     */
    public CheckoutResponse checkout(Long userId, String anonId, CheckoutRequest request) {
        String cartKey = resolveCartKey(userId, anonId);
        
        // 1. 获取要结算的商品
        List<CartItem> allItems = cartRedisService.getAll(cartKey);
        List<CartItem> checkoutItems;
        
        if (request.getSkuIds() != null && !request.getSkuIds().isEmpty()) {
            // 指定SKU结算
            Set<Long> skuIdSet = new HashSet<>(request.getSkuIds());
            checkoutItems = allItems.stream()
                    .filter(item -> skuIdSet.contains(item.getSkuId()))
                    .collect(Collectors.toList());
        } else {
            // 结算所有选中商品
            checkoutItems = allItems.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                    .collect(Collectors.toList());
        }

        if (checkoutItems.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "没有可结算的商品");
        }

        // 2. 库存校验
        List<InventoryClient.StockQuery> stockQueries = checkoutItems.stream()
                .map(item -> InventoryClient.StockQuery.builder()
                        .skuId(item.getSkuId())
                        .warehouseId(item.getWarehouseId() != null ? item.getWarehouseId() : 1L)
                        .requestQty(item.getQty())
                        .build())
                .collect(Collectors.toList());

        List<InventoryClient.StockInfo> stockInfos = inventoryClient.queryAvailableStock(stockQueries);
        Map<Long, InventoryClient.StockInfo> stockMap = stockInfos.stream()
                .collect(Collectors.toMap(InventoryClient.StockInfo::getSkuId, Function.identity()));

        // 检查是否有库存不足的商品
        List<CheckoutResponse.StockCheckResult> stockCheckResults = new ArrayList<>();
        boolean allStockSufficient = true;
        
        for (CartItem item : checkoutItems) {
            InventoryClient.StockInfo stockInfo = stockMap.get(item.getSkuId());
            boolean sufficient = stockInfo != null && stockInfo.getSufficient();
            
            if (!sufficient) {
                allStockSufficient = false;
            }
            
            stockCheckResults.add(CheckoutResponse.StockCheckResult.builder()
                    .skuId(item.getSkuId())
                    .warehouseId(item.getWarehouseId())
                    .requestQty(item.getQty())
                    .availableQty(stockInfo != null ? stockInfo.getAvailableQty() : 0)
                    .sufficient(sufficient)
                    .message(sufficient ? "库存充足" : "库存不足")
                    .build());
        }

        // 3. 调用定价服务锁价
        Long effectiveUserId = userId != null ? userId : 0L;
        
        PricingClient.LockParam lockParam = PricingClient.LockParam.builder()
                .userId(effectiveUserId)
                .items(checkoutItems.stream()
                        .map(item -> PricingClient.LockParam.ItemInfo.builder()
                                .skuId(item.getSkuId())
                                .qty(item.getQty())
                                .unitPrice(item.getUnitPrice())
                                .title(item.getTitle())
                                .categoryId(item.getCategoryId())
                                .build())
                        .collect(Collectors.toList()))
                .userCouponNos(request.getUserCouponNos())
                .lockMinutes(request.getLockMinutes() != null ? request.getLockMinutes() : 15)
                .build();

        PricingClient.LockResult lockResult = pricingClient.lock(lockParam);

        // 4. 构建响应
        CheckoutResponse.CheckoutResponseBuilder responseBuilder = CheckoutResponse.builder()
                .stockCheckResults(stockCheckResults);

        if (!allStockSufficient) {
            // 库存不足，不可下单
            return responseBuilder
                    .canOrder(false)
                    .failReason("部分商品库存不足")
                    .items(buildCheckoutItems(checkoutItems, stockMap, null))
                    .build();
        }

        if (!lockResult.getSuccess()) {
            // 锁价失败
            return responseBuilder
                    .canOrder(false)
                    .failReason(lockResult.getErrorMessage())
                    .items(buildCheckoutItems(checkoutItems, stockMap, null))
                    .build();
        }

        // 5. 锁价成功，返回可下单结果
        Map<Long, PricingClient.LockResult.AllocationDetail> allocationMap = new HashMap<>();
        if (lockResult.getAllocations() != null) {
            allocationMap = lockResult.getAllocations().stream()
                    .collect(Collectors.toMap(PricingClient.LockResult.AllocationDetail::getSkuId, 
                            Function.identity()));
        }

        return responseBuilder
                .canOrder(true)
                .priceLockNo(lockResult.getPriceLockNo())
                .signature(lockResult.getSignature())
                .signVersion(lockResult.getSignVersion())
                .expireAt(lockResult.getExpireAt())
                .originalAmount(lockResult.getOriginalAmount())
                .totalDiscount(lockResult.getTotalDiscount())
                .payableAmount(lockResult.getPayableAmount())
                .items(buildCheckoutItems(checkoutItems, stockMap, allocationMap))
                .promotionHits(convertPromotionHits(lockResult.getPromotionHits()))
                .availableCoupons(convertAvailableCoupons(lockResult.getAvailableCoupons()))
                .build();
    }

    /**
     * 解析购物车Key
     */
    private String resolveCartKey(Long userId, String anonId) {
        if (userId != null) {
            return CartRedisService.userCartKey(userId);
        }
        if (anonId != null && !anonId.isEmpty()) {
            return CartRedisService.anonCartKey(anonId);
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "userId和anonId不能同时为空");
    }

    /**
     * 构建购物车响应
     */
    private CartResponse buildCartResponse(List<CartItem> items) {
        List<CartResponse.CartItemResponse> itemResponses = items.stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .skuId(item.getSkuId())
                        .spuId(item.getSpuId())
                        .title(item.getTitle())
                        .imageUrl(item.getImageUrl())
                        .unitPrice(item.getUnitPrice())
                        .qty(item.getQty())
                        .checked(item.getChecked())
                        .skuAttrs(item.getSkuAttrs())
                        .categoryId(item.getCategoryId())
                        .warehouseId(item.getWarehouseId())
                        .addedAt(item.getAddedAt())
                        .updatedAt(item.getUpdatedAt())
                        .lineAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                        .build())
                .collect(Collectors.toList());

        int totalQty = items.stream().mapToInt(CartItem::getQty).sum();
        int checkedQty = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .mapToInt(CartItem::getQty)
                .sum();
        BigDecimal checkedAmount = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .items(itemResponses)
                .totalQty(totalQty)
                .checkedQty(checkedQty)
                .checkedAmount(checkedAmount)
                .build();
    }

    /**
     * 构建结算商品项
     */
    private List<CheckoutResponse.CheckoutItem> buildCheckoutItems(
            List<CartItem> items,
            Map<Long, InventoryClient.StockInfo> stockMap,
            Map<Long, PricingClient.LockResult.AllocationDetail> allocationMap) {
        
        return items.stream()
                .map(item -> {
                    InventoryClient.StockInfo stockInfo = stockMap.get(item.getSkuId());
                    PricingClient.LockResult.AllocationDetail allocation = 
                            allocationMap != null ? allocationMap.get(item.getSkuId()) : null;

                    BigDecimal lineOriginal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty()));
                    BigDecimal lineDiscount = allocation != null ? allocation.getLineDiscountAmount() : BigDecimal.ZERO;
                    BigDecimal linePayable = allocation != null ? allocation.getLinePayableAmount() : lineOriginal;

                    return CheckoutResponse.CheckoutItem.builder()
                            .skuId(item.getSkuId())
                            .title(item.getTitle())
                            .imageUrl(item.getImageUrl())
                            .qty(item.getQty())
                            .unitPrice(item.getUnitPrice())
                            .lineOriginalAmount(lineOriginal)
                            .lineDiscountAmount(lineDiscount)
                            .linePayableAmount(linePayable)
                            .skuAttrs(item.getSkuAttrs())
                            .warehouseId(item.getWarehouseId())
                            .stockSufficient(stockInfo != null && stockInfo.getSufficient())
                            .availableQty(stockInfo != null ? stockInfo.getAvailableQty() : 0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<CheckoutResponse.PromotionHit> convertPromotionHits(
            List<PricingClient.LockResult.PromotionHit> hits) {
        if (hits == null) return new ArrayList<>();
        return hits.stream()
                .map(hit -> CheckoutResponse.PromotionHit.builder()
                        .ruleType(hit.getRuleType())
                        .ruleId(hit.getRuleId())
                        .ruleName(hit.getRuleName())
                        .userCouponNo(hit.getUserCouponNo())
                        .discountType(hit.getDiscountType())
                        .thresholdAmount(hit.getThresholdAmount())
                        .discountAmount(hit.getDiscountAmount())
                        .description(hit.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CheckoutResponse.AvailableCoupon> convertAvailableCoupons(
            List<PricingClient.LockResult.AvailableCoupon> coupons) {
        if (coupons == null) return new ArrayList<>();
        return coupons.stream()
                .map(c -> CheckoutResponse.AvailableCoupon.builder()
                        .userCouponNo(c.getUserCouponNo())
                        .couponId(c.getCouponId())
                        .couponName(c.getCouponName())
                        .couponType(c.getCouponType())
                        .thresholdAmount(c.getThresholdAmount())
                        .discountAmount(c.getDiscountAmount())
                        .discountRate(c.getDiscountRate())
                        .maxDiscountAmount(c.getMaxDiscountAmount())
                        .eligible(c.getEligible())
                        .ineligibleReason(c.getIneligibleReason())
                        .build())
                .collect(Collectors.toList());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[CartService] toJson failed", e);
            return "[]";
        }
    }
}
