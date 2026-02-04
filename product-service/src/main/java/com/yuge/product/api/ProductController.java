package com.yuge.product.api;

import com.yuge.platform.infra.common.Result;
import com.yuge.product.api.dto.CreateSkuRequest;
import com.yuge.product.api.dto.CreateSpuRequest;
import com.yuge.product.api.dto.PublishSkuRequest;
import com.yuge.product.api.dto.SkuResponse;
import com.yuge.product.api.dto.SpuResponse;
import com.yuge.product.application.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品控制器
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 创建SPU
     * POST /products/spu
     */
    @PostMapping("/spu")
    public Result<SpuResponse> createSpu(@Valid @RequestBody CreateSpuRequest request) {
        log.info("创建SPU请求: {}", request);
        SpuResponse response = productService.createSpu(request);
        return Result.success(response);
    }

    /**
     * 查询SPU详情
     * GET /products/spu/{spuId}
     */
    @GetMapping("/spu/{spuId}")
    public Result<SpuResponse> getSpu(@PathVariable Long spuId) {
        log.info("查询SPU详情: spuId={}", spuId);
        SpuResponse response = productService.getSpuById(spuId);
        return Result.success(response);
    }

    /**
     * 创建SKU
     * POST /products/sku
     */
    @PostMapping("/sku")
    public Result<SkuResponse> createSku(@Valid @RequestBody CreateSkuRequest request) {
        log.info("创建SKU请求: {}", request);
        SkuResponse response = productService.createSku(request);
        return Result.success(response);
    }

    /**
     * 发布/上架SKU
     * POST /products/sku/{skuId}/publish
     */
    @PostMapping("/sku/{skuId}/publish")
    public Result<SkuResponse> publishSku(@PathVariable Long skuId,
                                          @RequestBody(required = false) PublishSkuRequest request) {
        log.info("发布SKU请求: skuId={}, request={}", skuId, request);
        SkuResponse response = productService.publishSku(skuId);
        return Result.success(response);
    }

    /**
     * 下架SKU
     * POST /products/sku/{skuId}/offline
     */
    @PostMapping("/sku/{skuId}/offline")
    public Result<SkuResponse> offlineSku(@PathVariable Long skuId) {
        log.info("下架SKU请求: skuId={}", skuId);
        SkuResponse response = productService.offlineSku(skuId);
        return Result.success(response);
    }

    /**
     * 查询SKU详情
     * GET /products/sku/{skuId}
     */
    @GetMapping("/sku/{skuId}")
    public Result<SkuResponse> getSku(@PathVariable Long skuId) {
        log.info("查询SKU详情: skuId={}", skuId);
        SkuResponse response = productService.getSkuById(skuId);
        return Result.success(response);
    }

    /**
     * 更新SKU
     * PUT /products/sku/{skuId}
     */
    @PutMapping("/sku/{skuId}")
    public Result<SkuResponse> updateSku(@PathVariable Long skuId,
                                         @Valid @RequestBody CreateSkuRequest request) {
        log.info("更新SKU请求: skuId={}, request={}", skuId, request);
        SkuResponse response = productService.updateSku(skuId, request);
        return Result.success(response);
    }

    /**
     * 查询SPU下的所有SKU
     * GET /products/spu/{spuId}/skus
     */
    @GetMapping("/spu/{spuId}/skus")
    public Result<List<SkuResponse>> getSkusBySpuId(@PathVariable Long spuId) {
        log.info("查询SPU下的SKU列表: spuId={}", spuId);
        List<SkuResponse> response = productService.getSkusBySpuId(spuId);
        return Result.success(response);
    }
}
