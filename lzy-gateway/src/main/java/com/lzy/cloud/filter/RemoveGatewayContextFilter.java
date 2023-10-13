package com.lzy.cloud.filter;

import com.lzy.cloud.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Quoted from @see https://github.com/chenggangpro/spring-cloud-gateway-plugin
 *
 * remove gatewayContext Attribute
 * @author 2428
 * @date 2022/06/27
 */
@Slf4j
public class RemoveGatewayContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
       return chain.filter(exchange).doFinally(s -> exchange.getAttributes().remove(GatewayContext.CACHE_GATEWAY_CONTEXT));
    }

    @Override
    public int getOrder() {
       return HIGHEST_PRECEDENCE;
    }

}
