package com.lzy.cloud.option;

import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;

/**
 * Quoted from @see https://github.com/chenggangpro/spring-cloud-gateway-plugin
 * The Order Of Plugin Filter
 * @author 2428
 * @date 2022/06/27
 */
public enum FilterOrderEnum {

    /**
     * Gateway Context Filter
     */
    GATEWAY_CONTEXT_FILTER(Integer.MIN_VALUE),
    /**
     * Request Log Filter
     */
    REQUEST_LOG_FILTER(Integer.MIN_VALUE + 2),
    /**
     * Cache Response Data Filter
     */
    RESPONSE_DATA_FILTER(NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1),
    ;

    private int order;

    FilterOrderEnum(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
