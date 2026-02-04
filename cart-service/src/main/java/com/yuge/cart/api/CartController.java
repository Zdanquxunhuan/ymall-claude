package com.yuge.cart.api;

import com.yuge.cart.api.dto.*;
import com.yuge.cart.application.CartService;
import com.yuge.platform.infra.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 购物车控制器
 */
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 获取购物车
     */
    @GetMapping
    public Result<CartResponse> getCart(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId) {
        CartResponse response = cartService.getCart(userId, anonId);
        return Result.success(response);
    }

    /**
     * 添加商品到购物车
     */
    @PostMapping("/items")
    public Result<CartResponse> addItem(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @Valid @RequestBody AddCartRequest request) {
        CartResponse response = cartService.addItem(userId, anonId, request);
        return Result.success(response);
    }

    /**
     * 更新商品数量
     */
    @PutMapping("/items/qty")
    public Result<CartResponse> updateQty(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @Valid @RequestBody UpdateQtyRequest request) {
        CartResponse response = cartService.updateQty(userId, anonId, request);
        return Result.success(response);
    }

    /**
     * 更新商品选中状态
     */
    @PutMapping("/items/check")
    public Result<CartResponse> checkItem(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @Valid @RequestBody CheckItemRequest request) {
        CartResponse response = cartService.checkItem(userId, anonId, request);
        return Result.success(response);
    }

    /**
     * 全选/取消全选
     */
    @PutMapping("/items/check-all")
    public Result<CartResponse> checkAll(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @RequestParam boolean checked) {
        CartResponse response = cartService.checkAll(userId, anonId, checked);
        return Result.success(response);
    }

    /**
     * 移除单个商品
     */
    @DeleteMapping("/items/{skuId}")
    public Result<CartResponse> removeItem(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @PathVariable Long skuId) {
        CartResponse response = cartService.removeItem(userId, anonId, skuId);
        return Result.success(response);
    }

    /**
     * 批量移除商品
     */
    @DeleteMapping("/items")
    public Result<CartResponse> removeItems(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @Valid @RequestBody RemoveItemsRequest request) {
        CartResponse response = cartService.removeItems(userId, anonId, request.getSkuIds());
        return Result.success(response);
    }

    /**
     * 清空购物车
     */
    @DeleteMapping
    public Result<Void> clear(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId) {
        cartService.clear(userId, anonId);
        return Result.success(null);
    }

    /**
     * 合并购物车（游客车 -> 登录车）
     * 用户登录后调用，将游客购物车合并到用户购物车
     */
    @PostMapping("/merge")
    public Result<CartResponse> mergeCart(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody MergeCartRequest request) {
        CartResponse response = cartService.mergeCart(userId, request);
        return Result.success(response);
    }

    /**
     * 结算
     * 调用pricing锁价 + inventory库存校验，返回可下单结果
     */
    @PostMapping("/checkout")
    public Result<CheckoutResponse> checkout(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Anon-Id", required = false) String anonId,
            @Valid @RequestBody(required = false) CheckoutRequest request) {
        if (request == null) {
            request = new CheckoutRequest();
        }
        CheckoutResponse response = cartService.checkout(userId, anonId, request);
        return Result.success(response);
    }
}
