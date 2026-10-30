package com.wherelee.trade.point;

import com.wherelee.trade.common.BizException;
import com.wherelee.trade.common.ErrorCode;
import com.wherelee.trade.point.dto.PointSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private static final int MAX_FLOW_LIMIT = 100;

    private final PointAccountMapper pointAccountMapper;
    private final PointFlowMapper pointFlowMapper;

    public PointSummaryVO summary(Long userId, int flowLimit) {
        PointAccount account = pointAccountMapper.findByUserId(userId);
        if (account == null) {
            throw BizException.of(ErrorCode.POINT_ACCOUNT_NOT_FOUND);
        }
        int safeLimit = Math.min(Math.max(flowLimit, 1), MAX_FLOW_LIMIT);
        List<PointFlow> flows = pointFlowMapper.findRecent(userId, safeLimit);
        return new PointSummaryVO(
                account.getBalance(),
                account.getFrozen(),
                flows.stream().map(PointSummaryVO.FlowItem::from).toList());
    }
}
