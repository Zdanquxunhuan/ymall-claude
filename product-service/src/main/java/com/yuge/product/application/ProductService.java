package com.yuge.product.application;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.product.api.dto.CreateSkuRequest;
import com.yuge.product.api.dto.CreateSpuRequest;
import com.yuge.product.api.dto.SkuResponse;
import com.yuge.product.api.dto.SpuResponse;
import com.yuge.product.domain.entity.Outbox;
import com.yuge.product.domain.entity.Sku;
import com.yuge.product.domain.entity.Spu;
import com.yuge.product.domain.enums.AggregateType;
import com.yuge.product.domain.enums.EventType;
import com.yuge.product.domain.enums.OutboxStatus;
import com.yuge.product.domain.enums.SkuStatus;
import com.yuge.product.domain.enums.SpuStatus;
import com.yuge.product.domain.event.ProductPublishedEvent;
import com.yuge.product.domain.event.ProductUpdatedEvent;
import com.yuge.product.infrastructure.repository.OutboxRepository;
import com.yuge.product.infrastructure.repository.SkuRepository;
import com.yuge.product.infrastructure.repository.SpuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 商品服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final SpuRepository spuRepository;
    private final SkuRepository skuRepository;
    private final OutboxRepository outboxRepository;

    /**
     * 创建SPU
     */
    @Transactional(rollbackFor = Exception.class)
    public SpuResponse createSpu(CreateSpuRequest request) {
        log.info("创建SPU: {}", request);

        Spu spu = Spu.builder()
                .title(request.getTitle())
                .categoryId(request.getCategoryId())
                .brandId(request.getBrandId())
                .description(request.getDescription())
                .status(SpuStatus.DRAFT.getCode())
                .version(0)
                .build();

        spuRepository.save(spu);
        log.info("SPU创建成功, spuId={}", spu.getSpuId());

        return toSpuResponse(spu);
    }

    /**
     * 创建SKU
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuResponse createSku(CreateSkuRequest request) {
        log.info("创建SKU: {}", request);

        // 检查SPU是否存在
        Spu spu = spuRepository.findById(request.getSpuId())
                .orElseThrow(() -> new BizException("SPU不存在: " + request.getSpuId()));

        Sku sku = Sku.builder()
                .spuId(request.getSpuId())
                .title(request.getTitle())
                .attrsJson(request.getAttrsJson())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .skuCode(request.getSkuCode())
                .barCode(request.getBarCode())
                .weight(request.getWeight())
                .status(SkuStatus.DRAFT.getCode())
                .version(0)
                .build();

        skuRepository.save(sku);
        log.info("SKU创建成功, skuId={}", sku.getSkuId());

        return toSkuResponse(sku, spu);
    }

    /**
     * 发布SKU（上架）
     * 使用Outbox模式保证事务一致性
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuResponse publishSku(Long skuId) {
        log.info("发布SKU: skuId={}", skuId);

        // 查询SKU
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new BizException("SKU不存在: " + skuId));

        // 检查状态
        SkuStatus currentStatus = SkuStatus.fromCode(sku.getStatus());
        if (!currentStatus.canPublish()) {
            throw new BizException("当前状态不允许发布: " + currentStatus.getDesc());
        }

        // 查询SPU
        Spu spu = spuRepository.findById(sku.getSpuId())
                .orElseThrow(() -> new BizException("SPU不存在: " + sku.getSpuId()));

        // 更新SKU状态
        LocalDateTime publishTime = LocalDateTime.now();
        sku.setStatus(SkuStatus.PUBLISHED.getCode());
        sku.setPublishTime(publishTime);
        skuRepository.save(sku);

        // 如果SPU还是草稿状态，也更新为已发布
        if (SpuStatus.DRAFT.getCode().equals(spu.getStatus())) {
            spu.setStatus(SpuStatus.PUBLISHED.getCode());
            spuRepository.save(spu);
        }

        // 创建发布事件并写入Outbox
        String eventId = IdUtil.fastSimpleUUID();
        ProductPublishedEvent event = ProductPublishedEvent.create(
                eventId,
                sku.getSkuId(),
                sku.getSpuId(),
                sku.getTitle(),
                sku.getAttrsJson(),
                sku.getPrice(),
                spu.getCategoryId(),
                spu.getBrandId(),
                sku.getSkuCode(),
                publishTime
        );

        Outbox outbox = Outbox.builder()
                .eventId(eventId)
                .eventType(EventType.PRODUCT_PUBLISHED.getCode())
                .aggregateType(AggregateType.SKU.getCode())
                .aggregateId(skuId)
                .payload(JSONUtil.toJsonStr(event))
                .status(OutboxStatus.PENDING.getCode())
                .retryCount(0)
                .maxRetry(3)
                .version(0)
                .build();

        outboxRepository.save(outbox);
        log.info("SKU发布成功, skuId={}, eventId={}", skuId, eventId);

        return toSkuResponse(sku, spu);
    }

    /**
     * 更新SKU
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuResponse updateSku(Long skuId, CreateSkuRequest request) {
        log.info("更新SKU: skuId={}, request={}", skuId, request);

        // 查询SKU
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new BizException("SKU不存在: " + skuId));

        // 查询SPU
        Spu spu = spuRepository.findById(sku.getSpuId())
                .orElseThrow(() -> new BizException("SPU不存在: " + sku.getSpuId()));

        // 更新SKU信息
        if (request.getTitle() != null) {
            sku.setTitle(request.getTitle());
        }
        if (request.getAttrsJson() != null) {
            sku.setAttrsJson(request.getAttrsJson());
        }
        if (request.getPrice() != null) {
            sku.setPrice(request.getPrice());
        }
        if (request.getOriginalPrice() != null) {
            sku.setOriginalPrice(request.getOriginalPrice());
        }
        if (request.getSkuCode() != null) {
            sku.setSkuCode(request.getSkuCode());
        }
        if (request.getBarCode() != null) {
            sku.setBarCode(request.getBarCode());
        }
        if (request.getWeight() != null) {
            sku.setWeight(request.getWeight());
        }

        skuRepository.save(sku);

        // 如果SKU已发布，则发送更新事件
        if (SkuStatus.PUBLISHED.getCode().equals(sku.getStatus())) {
            String eventId = IdUtil.fastSimpleUUID();
            ProductUpdatedEvent event = ProductUpdatedEvent.create(
                    eventId,
                    sku.getSkuId(),
                    sku.getSpuId(),
                    sku.getTitle(),
                    sku.getAttrsJson(),
                    sku.getPrice(),
                    spu.getCategoryId(),
                    spu.getBrandId(),
                    sku.getSkuCode(),
                    sku.getStatus(),
                    Collections.emptyList()
            );

            Outbox outbox = Outbox.builder()
                    .eventId(eventId)
                    .eventType(EventType.PRODUCT_UPDATED.getCode())
                    .aggregateType(AggregateType.SKU.getCode())
                    .aggregateId(skuId)
                    .payload(JSONUtil.toJsonStr(event))
                    .status(OutboxStatus.PENDING.getCode())
                    .retryCount(0)
                    .maxRetry(3)
                    .version(0)
                    .build();

            outboxRepository.save(outbox);
            log.info("SKU更新事件已写入Outbox, skuId={}, eventId={}", skuId, eventId);
        }

        log.info("SKU更新成功, skuId={}", skuId);
        return toSkuResponse(sku, spu);
    }

    /**
     * 下架SKU
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuResponse offlineSku(Long skuId) {
        log.info("下架SKU: skuId={}", skuId);

        // 查询SKU
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new BizException("SKU不存在: " + skuId));

        // 检查状态
        SkuStatus currentStatus = SkuStatus.fromCode(sku.getStatus());
        if (!currentStatus.canOffline()) {
            throw new BizException("当前状态不允许下架: " + currentStatus.getDesc());
        }

        // 查询SPU
        Spu spu = spuRepository.findById(sku.getSpuId())
                .orElseThrow(() -> new BizException("SPU不存在: " + sku.getSpuId()));

        // 更新SKU状态
        sku.setStatus(SkuStatus.OFFLINE.getCode());
        skuRepository.save(sku);

        // 发送下架事件
        String eventId = IdUtil.fastSimpleUUID();
        ProductUpdatedEvent event = ProductUpdatedEvent.create(
                eventId,
                sku.getSkuId(),
                sku.getSpuId(),
                sku.getTitle(),
                sku.getAttrsJson(),
                sku.getPrice(),
                spu.getCategoryId(),
                spu.getBrandId(),
                sku.getSkuCode(),
                sku.getStatus(),
                List.of("status")
        );

        Outbox outbox = Outbox.builder()
                .eventId(eventId)
                .eventType(EventType.PRODUCT_OFFLINE.getCode())
                .aggregateType(AggregateType.SKU.getCode())
                .aggregateId(skuId)
                .payload(JSONUtil.toJsonStr(event))
                .status(OutboxStatus.PENDING.getCode())
                .retryCount(0)
                .maxRetry(3)
                .version(0)
                .build();

        outboxRepository.save(outbox);
        log.info("SKU下架成功, skuId={}, eventId={}", skuId, eventId);

        return toSkuResponse(sku, spu);
    }

    /**
     * 查询SKU详情
     */
    public SkuResponse getSkuById(Long skuId) {
        log.debug("查询SKU详情: skuId={}", skuId);

        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new BizException("SKU不存在: " + skuId));

        Spu spu = spuRepository.findById(sku.getSpuId())
                .orElse(null);

        return toSkuResponse(sku, spu);
    }

    /**
     * 查询SPU详情
     */
    public SpuResponse getSpuById(Long spuId) {
        log.debug("查询SPU详情: spuId={}", spuId);

        Spu spu = spuRepository.findById(spuId)
                .orElseThrow(() -> new BizException("SPU不存在: " + spuId));

        return toSpuResponse(spu);
    }

    /**
     * 查询SPU下的所有SKU
     */
    public List<SkuResponse> getSkusBySpuId(Long spuId) {
        log.debug("查询SPU下的SKU列表: spuId={}", spuId);

        Spu spu = spuRepository.findById(spuId)
                .orElseThrow(() -> new BizException("SPU不存在: " + spuId));

        List<Sku> skuList = skuRepository.findBySpuId(spuId);
        return skuList.stream()
                .map(sku -> toSkuResponse(sku, spu))
                .toList();
    }

    /**
     * 转换为SPU响应
     */
    private SpuResponse toSpuResponse(Spu spu) {
        SpuStatus status = SpuStatus.fromCode(spu.getStatus());
        return SpuResponse.builder()
                .spuId(spu.getSpuId())
                .title(spu.getTitle())
                .categoryId(spu.getCategoryId())
                .brandId(spu.getBrandId())
                .description(spu.getDescription())
                .status(spu.getStatus())
                .statusDesc(status.getDesc())
                .createdAt(spu.getCreatedAt())
                .updatedAt(spu.getUpdatedAt())
                .build();
    }

    /**
     * 转换为SKU响应
     */
    private SkuResponse toSkuResponse(Sku sku, Spu spu) {
        SkuStatus status = SkuStatus.fromCode(sku.getStatus());
        SkuResponse.SkuResponseBuilder builder = SkuResponse.builder()
                .skuId(sku.getSkuId())
                .spuId(sku.getSpuId())
                .title(sku.getTitle())
                .attrsJson(sku.getAttrsJson())
                .price(sku.getPrice())
                .originalPrice(sku.getOriginalPrice())
                .skuCode(sku.getSkuCode())
                .barCode(sku.getBarCode())
                .weight(sku.getWeight())
                .status(sku.getStatus())
                .statusDesc(status.getDesc())
                .publishTime(sku.getPublishTime())
                .createdAt(sku.getCreatedAt())
                .updatedAt(sku.getUpdatedAt());

        if (spu != null) {
            builder.spu(toSpuResponse(spu));
        }

        return builder.build();
    }
}
