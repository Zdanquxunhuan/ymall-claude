package com.yuge.aftersales.infrastructure.repository;

import com.yuge.aftersales.domain.entity.AfterSaleStateFlow;
import com.yuge.aftersales.infrastructure.mapper.AfterSaleStateFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 售后状态流转仓储
 */
@Repository
@RequiredArgsConstructor
public class AfterSaleStateFlowRepository {

    private final AfterSaleStateFlowMapper stateFlowMapper;

    public void save(AfterSaleStateFlow stateFlow) {
        stateFlowMapper.insert(stateFlow);
    }
}
