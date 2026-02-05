package com.yuge.search.infrastructure.index;

import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchRequest;
import com.yuge.search.domain.model.SearchResult;
import com.yuge.search.domain.service.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 内存索引实现
 * 使用 ConcurrentHashMap 存储文档 + 简化倒排索引实现关键词搜索
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "search.index-type", havingValue = "memory", matchIfMissing = true)
public class MemorySearchIndexService implements SearchIndexService {

    /**
     * 主索引：skuId -> ProductDocument
     */
    private final ConcurrentHashMap<Long, ProductDocument> primaryIndex = new ConcurrentHashMap<>();

    /**
     * 倒排索引：term -> Set<skuId>
     * 用于关键词搜索
     */
    private final ConcurrentHashMap<String, Set<Long>> invertedIndex = new ConcurrentHashMap<>();

    /**
     * 分类索引：categoryId -> Set<skuId>
     */
    private final ConcurrentHashMap<Long, Set<Long>> categoryIndex = new ConcurrentHashMap<>();

    /**
     * 品牌索引：brandId -> Set<skuId>
     */
    private final ConcurrentHashMap<Long, Set<Long>> brandIndex = new ConcurrentHashMap<>();

    /**
     * 事件去重记录：skuId -> lastEventId
     */
    private final ConcurrentHashMap<Long, String> eventIdMap = new ConcurrentHashMap<>();

    /**
     * 读写锁，保证索引更新的一致性
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public boolean indexDocument(ProductDocument document) {
        if (document == null || document.getSkuId() == null) {
            log.warn("[MemoryIndex] Invalid document: null or missing skuId");
            return false;
        }

        lock.writeLock().lock();
        try {
            Long skuId = document.getSkuId();
            
            // 先删除旧索引
            ProductDocument oldDoc = primaryIndex.get(skuId);
            if (oldDoc != null) {
                removeFromInvertedIndex(oldDoc);
                removeFromCategoryIndex(oldDoc);
                removeFromBrandIndex(oldDoc);
            }

            // 添加到主索引
            primaryIndex.put(skuId, document);

            // 添加到倒排索引
            addToInvertedIndex(document);

            // 添加到分类索引
            addToCategoryIndex(document);

            // 添加到品牌索引
            addToBrandIndex(document);

            // 记录事件ID
            if (StringUtils.hasText(document.getLastEventId())) {
                eventIdMap.put(skuId, document.getLastEventId());
            }

            log.debug("[MemoryIndex] Indexed document: skuId={}, title={}", skuId, document.getTitle());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteDocument(Long skuId) {
        if (skuId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            ProductDocument doc = primaryIndex.remove(skuId);
            if (doc != null) {
                removeFromInvertedIndex(doc);
                removeFromCategoryIndex(doc);
                removeFromBrandIndex(doc);
                eventIdMap.remove(skuId);
                log.debug("[MemoryIndex] Deleted document: skuId={}", skuId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ProductDocument getDocument(Long skuId) {
        if (skuId == null) {
            return null;
        }
        return primaryIndex.get(skuId);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        lock.readLock().lock();
        try {
            // 1. 获取候选集
            Set<Long> candidates = getCandidates(request);

            // 2. 过滤
            List<ProductDocument> filtered = candidates.stream()
                    .map(primaryIndex::get)
                    .filter(Objects::nonNull)
                    .filter(doc -> matchFilters(doc, request))
                    .collect(Collectors.toList());

            // 3. 排序
            sortDocuments(filtered, request);

            // 4. 分页
            long total = filtered.size();
            int offset = request.getOffset();
            int limit = request.getPageSize();
            
            List<ProductDocument> paged;
            if (offset >= filtered.size()) {
                paged = Collections.emptyList();
            } else {
                int endIndex = Math.min(offset + limit, filtered.size());
                paged = filtered.subList(offset, endIndex);
            }

            long took = System.currentTimeMillis() - startTime;
            log.debug("[MemoryIndex] Search completed: keyword={}, total={}, took={}ms", 
                    request.getKeyword(), total, took);

            return SearchResult.of(new ArrayList<>(paged), total, request, took);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isEventProcessed(Long skuId, String eventId) {
        if (skuId == null || !StringUtils.hasText(eventId)) {
            return false;
        }
        String lastEventId = eventIdMap.get(skuId);
        return eventId.equals(lastEventId);
    }

    @Override
    public long count() {
        return primaryIndex.size();
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            primaryIndex.clear();
            invertedIndex.clear();
            categoryIndex.clear();
            brandIndex.clear();
            eventIdMap.clear();
            log.info("[MemoryIndex] Index cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 获取候选文档集合
     */
    private Set<Long> getCandidates(SearchRequest request) {
        Set<Long> candidates = null;

        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            candidates = searchByKeyword(request.getKeyword());
        }

        // 分类过滤
        if (request.getCategoryId() != null) {
            Set<Long> categoryDocs = categoryIndex.getOrDefault(request.getCategoryId(), Collections.emptySet());
            candidates = intersect(candidates, categoryDocs);
        }

        // 品牌过滤
        if (request.getBrandId() != null) {
            Set<Long> brandDocs = brandIndex.getOrDefault(request.getBrandId(), Collections.emptySet());
            candidates = intersect(candidates, brandDocs);
        }

        // 如果没有任何条件，返回所有文档
        if (candidates == null) {
            candidates = new HashSet<>(primaryIndex.keySet());
        }

        return candidates;
    }

    /**
     * 关键词搜索
     */
    private Set<Long> searchByKeyword(String keyword) {
        Set<Long> result = new HashSet<>();
        List<String> terms = tokenize(keyword.toLowerCase());
        
        for (String term : terms) {
            // 精确匹配
            Set<Long> exactMatch = invertedIndex.get(term);
            if (exactMatch != null) {
                result.addAll(exactMatch);
            }
            
            // 前缀匹配
            for (Map.Entry<String, Set<Long>> entry : invertedIndex.entrySet()) {
                if (entry.getKey().startsWith(term) || entry.getKey().contains(term)) {
                    result.addAll(entry.getValue());
                }
            }
        }
        
        return result;
    }

    /**
     * 交集操作
     */
    private Set<Long> intersect(Set<Long> set1, Set<Long> set2) {
        if (set1 == null) {
            return new HashSet<>(set2);
        }
        if (set2 == null) {
            return set1;
        }
        Set<Long> result = new HashSet<>(set1);
        result.retainAll(set2);
        return result;
    }

    /**
     * 过滤条件匹配
     */
    private boolean matchFilters(ProductDocument doc, SearchRequest request) {
        // 价格范围过滤
        if (request.getMinPrice() != null && doc.getPrice() != null) {
            if (doc.getPrice().compareTo(request.getMinPrice()) < 0) {
                return false;
            }
        }
        if (request.getMaxPrice() != null && doc.getPrice() != null) {
            if (doc.getPrice().compareTo(request.getMaxPrice()) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 排序
     */
    private void sortDocuments(List<ProductDocument> docs, SearchRequest request) {
        String sortField = request.getSortField();
        boolean asc = "asc".equalsIgnoreCase(request.getSortOrder());

        Comparator<ProductDocument> comparator;
        if ("price".equalsIgnoreCase(sortField)) {
            comparator = Comparator.comparing(
                    ProductDocument::getPrice,
                    Comparator.nullsLast(BigDecimal::compareTo)
            );
        } else if ("publishTime".equalsIgnoreCase(sortField)) {
            comparator = Comparator.comparing(
                    ProductDocument::getPublishTime,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else {
            // 默认按发布时间倒序
            comparator = Comparator.comparing(
                    ProductDocument::getPublishTime,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
            docs.sort(comparator);
            return;
        }

        if (!asc) {
            comparator = comparator.reversed();
        }
        docs.sort(comparator);
    }

    /**
     * 添加到倒排索引
     */
    private void addToInvertedIndex(ProductDocument doc) {
        if (!StringUtils.hasText(doc.getTitle())) {
            return;
        }
        List<String> terms = tokenize(doc.getTitle().toLowerCase());
        for (String term : terms) {
            invertedIndex.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet())
                    .add(doc.getSkuId());
        }
    }

    /**
     * 从倒排索引移除
     */
    private void removeFromInvertedIndex(ProductDocument doc) {
        if (!StringUtils.hasText(doc.getTitle())) {
            return;
        }
        List<String> terms = tokenize(doc.getTitle().toLowerCase());
        for (String term : terms) {
            Set<Long> skuIds = invertedIndex.get(term);
            if (skuIds != null) {
                skuIds.remove(doc.getSkuId());
                if (skuIds.isEmpty()) {
                    invertedIndex.remove(term);
                }
            }
        }
    }

    /**
     * 添加到分类索引
     */
    private void addToCategoryIndex(ProductDocument doc) {
        if (doc.getCategoryId() != null) {
            categoryIndex.computeIfAbsent(doc.getCategoryId(), k -> ConcurrentHashMap.newKeySet())
                    .add(doc.getSkuId());
        }
    }

    /**
     * 从分类索引移除
     */
    private void removeFromCategoryIndex(ProductDocument doc) {
        if (doc.getCategoryId() != null) {
            Set<Long> skuIds = categoryIndex.get(doc.getCategoryId());
            if (skuIds != null) {
                skuIds.remove(doc.getSkuId());
                if (skuIds.isEmpty()) {
                    categoryIndex.remove(doc.getCategoryId());
                }
            }
        }
    }

    /**
     * 添加到品牌索引
     */
    private void addToBrandIndex(ProductDocument doc) {
        if (doc.getBrandId() != null) {
            brandIndex.computeIfAbsent(doc.getBrandId(), k -> ConcurrentHashMap.newKeySet())
                    .add(doc.getSkuId());
        }
    }

    /**
     * 从品牌索引移除
     */
    private void removeFromBrandIndex(ProductDocument doc) {
        if (doc.getBrandId() != null) {
            Set<Long> skuIds = brandIndex.get(doc.getBrandId());
            if (skuIds != null) {
                skuIds.remove(doc.getSkuId());
                if (skuIds.isEmpty()) {
                    brandIndex.remove(doc.getBrandId());
                }
            }
        }
    }

    /**
     * 简单分词
     * 支持中文单字分词和英文空格分词
     */
    private List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                // 空格或标点，结束当前token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else if (isChinese(c)) {
                // 中文字符，先保存之前的token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                // 中文单字作为一个token
                tokens.add(String.valueOf(c));
            } else {
                // 英文或数字，累加
                currentToken.append(c);
            }
        }

        // 处理最后一个token
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    /**
     * 判断是否为中文字符
     */
    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    /**
     * 判断是否为标点符号
     */
    private boolean isPunctuation(char c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
}
