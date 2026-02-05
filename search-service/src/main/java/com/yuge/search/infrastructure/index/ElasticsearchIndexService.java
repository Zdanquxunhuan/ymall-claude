package com.yuge.search.infrastructure.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.yuge.search.config.SearchProperties;
import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchRequest;
import com.yuge.search.domain.model.SearchResult;
import com.yuge.search.domain.service.SearchIndexService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch 索引实现
 * 当配置 search.index-type=elasticsearch 时启用
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.index-type", havingValue = "elasticsearch")
public class ElasticsearchIndexService implements SearchIndexService {

    private final SearchProperties searchProperties;

    private ElasticsearchClient client;
    private RestClient restClient;

    /**
     * 事件去重记录（ES中也可以用文档字段实现，这里简化处理）
     */
    private final ConcurrentHashMap<Long, String> eventIdMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            SearchProperties.ElasticsearchConfig esConfig = searchProperties.getElasticsearch();
            
            // 解析主机
            String[] hostParts = esConfig.getHosts().split(":");
            String host = hostParts[0];
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 9200;

            // 创建 RestClient
            RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"));

            // 配置认证
            if (StringUtils.hasText(esConfig.getUsername())) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(esConfig.getUsername(), esConfig.getPassword()));
                builder.setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
            }

            // 配置超时
            builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder
                            .setConnectTimeout(esConfig.getConnectTimeout())
                            .setSocketTimeout(esConfig.getSocketTimeout()));

            restClient = builder.build();

            // 创建 ElasticsearchClient
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            client = new ElasticsearchClient(transport);

            // 创建索引
            createIndexIfNotExists();

            log.info("[ES-Index] Elasticsearch client initialized, hosts={}", esConfig.getHosts());
        } catch (Exception e) {
            log.error("[ES-Index] Failed to initialize Elasticsearch client", e);
            throw new RuntimeException("Failed to initialize Elasticsearch", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (IOException e) {
                log.error("[ES-Index] Failed to close RestClient", e);
            }
        }
    }

    private void createIndexIfNotExists() throws IOException {
        String indexName = searchProperties.getElasticsearch().getIndexName();
        
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
        boolean exists = client.indices().exists(existsRequest).value();

        if (!exists) {
            CreateIndexRequest createRequest = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("skuId", p -> p.long_(l -> l))
                            .properties("spuId", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer("standard")))
                            .properties("attrsJson", p -> p.text(t -> t))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("categoryId", p -> p.long_(l -> l))
                            .properties("brandId", p -> p.long_(l -> l))
                            .properties("skuCode", p -> p.keyword(k -> k))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("publishTime", p -> p.date(d -> d))
                            .properties("indexTime", p -> p.date(d -> d))
                            .properties("eventVersion", p -> p.keyword(k -> k))
                            .properties("lastEventId", p -> p.keyword(k -> k))
                    )
            );
            client.indices().create(createRequest);
            log.info("[ES-Index] Created index: {}", indexName);
        }
    }

    @Override
    public boolean indexDocument(ProductDocument document) {
        if (document == null || document.getSkuId() == null) {
            log.warn("[ES-Index] Invalid document: null or missing skuId");
            return false;
        }

        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();
            
            IndexRequest<ProductDocument> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(document.getSkuId().toString())
                    .document(document)
            );

            IndexResponse response = client.index(request);

            // 记录事件ID
            if (StringUtils.hasText(document.getLastEventId())) {
                eventIdMap.put(document.getSkuId(), document.getLastEventId());
            }

            log.debug("[ES-Index] Indexed document: skuId={}, result={}", 
                    document.getSkuId(), response.result());
            return true;
        } catch (IOException e) {
            log.error("[ES-Index] Failed to index document: skuId={}", document.getSkuId(), e);
            return false;
        }
    }

    @Override
    public boolean deleteDocument(Long skuId) {
        if (skuId == null) {
            return false;
        }

        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();
            
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(indexName)
                    .id(skuId.toString())
            );

            DeleteResponse response = client.delete(request);
            eventIdMap.remove(skuId);

            log.debug("[ES-Index] Deleted document: skuId={}, result={}", skuId, response.result());
            return true;
        } catch (IOException e) {
            log.error("[ES-Index] Failed to delete document: skuId={}", skuId, e);
            return false;
        }
    }

    @Override
    public ProductDocument getDocument(Long skuId) {
        if (skuId == null) {
            return null;
        }

        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();
            
            GetRequest request = GetRequest.of(g -> g
                    .index(indexName)
                    .id(skuId.toString())
            );

            GetResponse<ProductDocument> response = client.get(request, ProductDocument.class);
            
            if (response.found()) {
                return response.source();
            }
            return null;
        } catch (IOException e) {
            log.error("[ES-Index] Failed to get document: skuId={}", skuId, e);
            return null;
        }
    }

    @Override
    public SearchResult search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();

            // 构建查询
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // 关键词搜索
            if (StringUtils.hasText(request.getKeyword())) {
                boolQuery.must(Query.of(q -> q
                        .match(m -> m
                                .field("title")
                                .query(request.getKeyword())
                        )
                ));
            }

            // 分类过滤
            if (request.getCategoryId() != null) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t
                                .field("categoryId")
                                .value(request.getCategoryId())
                        )
                ));
            }

            // 品牌过滤
            if (request.getBrandId() != null) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t
                                .field("brandId")
                                .value(request.getBrandId())
                        )
                ));
            }

            // 价格范围
            if (request.getMinPrice() != null || request.getMaxPrice() != null) {
                boolQuery.filter(Query.of(q -> q
                        .range(r -> {
                            r.field("price");
                            if (request.getMinPrice() != null) {
                                r.gte(co.elastic.clients.json.JsonData.of(request.getMinPrice()));
                            }
                            if (request.getMaxPrice() != null) {
                                r.lte(co.elastic.clients.json.JsonData.of(request.getMaxPrice()));
                            }
                            return r;
                        })
                ));
            }

            // 构建搜索请求
            co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchBuilder = 
                    new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
                    .index(indexName)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from(request.getOffset())
                    .size(request.getPageSize());

            // 排序
            if (StringUtils.hasText(request.getSortField())) {
                SortOrder order = "asc".equalsIgnoreCase(request.getSortOrder()) 
                        ? SortOrder.Asc : SortOrder.Desc;
                searchBuilder.sort(s -> s.field(f -> f.field(request.getSortField()).order(order)));
            } else {
                searchBuilder.sort(s -> s.field(f -> f.field("publishTime").order(SortOrder.Desc)));
            }

            SearchResponse<ProductDocument> response = client.search(searchBuilder.build(), ProductDocument.class);

            // 解析结果
            List<ProductDocument> items = new ArrayList<>();
            for (Hit<ProductDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    items.add(hit.source());
                }
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            long took = System.currentTimeMillis() - startTime;

            log.debug("[ES-Index] Search completed: keyword={}, total={}, took={}ms",
                    request.getKeyword(), total, took);

            return SearchResult.of(items, total, request, took);
        } catch (IOException e) {
            log.error("[ES-Index] Search failed", e);
            return SearchResult.empty(request);
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
        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();
            
            CountRequest request = CountRequest.of(c -> c.index(indexName));
            CountResponse response = client.count(request);
            
            return response.count();
        } catch (IOException e) {
            log.error("[ES-Index] Failed to count documents", e);
            return 0;
        }
    }

    @Override
    public void clear() {
        try {
            String indexName = searchProperties.getElasticsearch().getIndexName();
            
            // 删除所有文档
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(Query.of(q -> q.matchAll(m -> m)))
            );
            
            client.deleteByQuery(request);
            eventIdMap.clear();
            
            log.info("[ES-Index] Index cleared");
        } catch (IOException e) {
            log.error("[ES-Index] Failed to clear index", e);
        }
    }
}
