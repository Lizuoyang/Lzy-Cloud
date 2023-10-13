package com.lzy.cloud.filter;

import cn.hutool.core.util.ObjectUtil;
import com.lzy.cloud.context.ContextExtraDataGenerator;
import com.lzy.cloud.context.GatewayContext;
import com.lzy.cloud.context.GatewayContextExtraData;
import com.lzy.cloud.option.FilterOrderEnum;
import com.lzy.cloud.option.GatewayLogTypeEnum;
import com.lzy.cloud.properties.GatewayPluginProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Quoted from @see https://github.com/chenggangpro/spring-cloud-gateway-plugin
 *
 * Gateway Context Filter
 * @author 2428
 * @date 2022/06/27
 */
@Slf4j
@AllArgsConstructor
public class GatewayRequestContextFilter implements GlobalFilter, Ordered {

    private GatewayPluginProperties gatewayPluginProperties;

    private ContextExtraDataGenerator contextExtraDataGenerator;

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    /**
     * default HttpMessageReader
     */
    private static final List<HttpMessageReader<?>> MESSAGE_READERS = HandlerStrategies.withDefaults().messageReaders();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        GatewayContext gatewayContext = new GatewayContext();
        gatewayContext.setReadRequestData(shouldReadRequestData(exchange));
        gatewayContext.setReadResponseData(gatewayPluginProperties.getResponseLog());
        HttpHeaders headers = request.getHeaders();
        gatewayContext.setRequestHeaders(headers);
        if(Objects.nonNull(contextExtraDataGenerator)){
            GatewayContextExtraData gatewayContextExtraData = contextExtraDataGenerator.generateContextExtraData(exchange);
            gatewayContext.setGatewayContextExtraData(gatewayContextExtraData);
        }
        if(!gatewayContext.getReadRequestData()){
            exchange.getAttributes().put(GatewayContext.CACHE_GATEWAY_CONTEXT, gatewayContext);
            log.debug("[GatewayContext]Properties Set To Not Read Request Data");
            return chain.filter(exchange);
        }
        gatewayContext.getAllRequestData().addAll(request.getQueryParams());
        /*
         * save gateway context into exchange
         */
        exchange.getAttributes().put(GatewayContext.CACHE_GATEWAY_CONTEXT, gatewayContext);
        MediaType contentType = headers.getContentType();
        if(headers.getContentLength()>0){
            if(MediaType.APPLICATION_JSON.equals(contentType) || MediaType.APPLICATION_JSON_UTF8.equals(contentType)){
                return readBody(exchange, chain,gatewayContext);
            }
            if(MediaType.APPLICATION_FORM_URLENCODED.equals(contentType)){
                return readFormData(exchange, chain,gatewayContext);
            }
        }
        log.debug("[GatewayContext]ContentType:{},Gateway context is set with {}",contentType, gatewayContext);
        return chain.filter(exchange);

    }


    @Override
    public int getOrder() {
        return FilterOrderEnum.GATEWAY_CONTEXT_FILTER.getOrder();
    }

    /**
     * check should read request data whether or not
     * @return boolean
     */
    private boolean shouldReadRequestData(ServerWebExchange exchange){
        if(gatewayPluginProperties.getRequestLog()
                && GatewayLogTypeEnum.ALL.getType().equals(gatewayPluginProperties.getLogType())){
            log.debug("[GatewayContext]Properties Set Read All Request Data");
            return true;
        }

        boolean serviceFlag = false;
        boolean pathFlag = false;
        boolean lbFlag = false;

        List<String> readRequestDataServiceIdList = gatewayPluginProperties.getServiceIdList();

        List<String> readRequestDataPathList = gatewayPluginProperties.getPathList();

        if(!CollectionUtils.isEmpty(readRequestDataPathList)
                && (GatewayLogTypeEnum.PATH.getType().equals(gatewayPluginProperties.getLogType())
                    || GatewayLogTypeEnum.CONFIGURE.getType().equals(gatewayPluginProperties.getLogType()))){
            String requestPath = exchange.getRequest().getPath().pathWithinApplication().value();
            for(String path : readRequestDataPathList){
                if(ANT_PATH_MATCHER.match(path,requestPath)){
                    log.debug("[GatewayContext]Properties Set Read Specific Request Data With Request Path:{},Math Pattern:{}", requestPath, path);
                    pathFlag =  true;
                    break;
                }
            }
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI routeUri = route.getUri();
        if(!"lb".equalsIgnoreCase(routeUri.getScheme())){
            lbFlag = true;
        }

        String routeServiceId = routeUri.getHost().toLowerCase();
        if(!CollectionUtils.isEmpty(readRequestDataServiceIdList)
                && (GatewayLogTypeEnum.SERVICE.getType().equals(gatewayPluginProperties.getLogType())
                || GatewayLogTypeEnum.CONFIGURE.getType().equals(gatewayPluginProperties.getLogType()))){
            if(readRequestDataServiceIdList.contains(routeServiceId)){
                log.debug("[GatewayContext]Properties Set Read Specific Request Data With ServiceId:{}",routeServiceId);
                serviceFlag =  true;
            }
        }

        if (GatewayLogTypeEnum.CONFIGURE.getType().equals(gatewayPluginProperties.getLogType())
                && serviceFlag && pathFlag && !lbFlag)
        {
            return true;
        }
        else if (GatewayLogTypeEnum.SERVICE.getType().equals(gatewayPluginProperties.getLogType())
                && serviceFlag && !lbFlag)
        {
            return true;
        }
        else if (GatewayLogTypeEnum.PATH.getType().equals(gatewayPluginProperties.getLogType())
                && pathFlag)
        {
            return true;
        }

        return false;
    }

    /**
     * ReadFormData
     * @param exchange
     * @param chain
     * @return
     */
    private Mono<Void> readFormData(ServerWebExchange exchange, GatewayFilterChain chain, GatewayContext gatewayContext){
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return exchange.getFormData()
                .doOnNext(multiValueMap -> {
                    gatewayContext.setFormData(multiValueMap);
                    gatewayContext.getAllRequestData().addAll(multiValueMap);
                    log.debug("[GatewayContext]Read FormData Success");
                })
                .then(Mono.defer(() -> {
                    Charset charset = headers.getContentType().getCharset();
                    charset = charset == null? StandardCharsets.UTF_8:charset;
                    String charsetName = charset.name();
                    MultiValueMap<String, String> formData = gatewayContext.getFormData();
                    /*
                     * formData is empty just return
                     */
                    if(null == formData || formData.isEmpty()){
                        return chain.filter(exchange);
                    }
                    StringBuilder formDataBodyBuilder = new StringBuilder();
                    String entryKey;
                    List<String> entryValue;
                    try {
                        /*
                         * repackage form data
                         */
                        for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                            entryKey = entry.getKey();
                            entryValue = entry.getValue();
                            if (entryValue.size() > 1) {
                                for(String value : entryValue){
                                    formDataBodyBuilder.append(entryKey).append("=").append(URLEncoder.encode(value, charsetName)).append("&");
                                }
                            } else {
                                formDataBodyBuilder.append(entryKey).append("=").append(URLEncoder.encode(ObjectUtil.defaultIfNull(entryValue.get(0), ""), charsetName)).append("&");
                            }
                        }
                    }catch (UnsupportedEncodingException e){}
                    /*
                     * substring with the last char '&'
                     */
                    String formDataBodyString = "";
                    if(formDataBodyBuilder.length()>0){
                        formDataBodyString = formDataBodyBuilder.substring(0, formDataBodyBuilder.length() - 1);
                    }
                    /*
                     * get data bytes
                     */
                    byte[] bodyBytes =  formDataBodyString.getBytes(charset);
                    int contentLength = bodyBytes.length;
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.putAll(exchange.getRequest().getHeaders());
                    httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
                    /*
                     * in case of content-length not matched
                     */
                    httpHeaders.setContentLength(contentLength);
                    /*
                     * use BodyInserter to InsertFormData Body
                     */
                    BodyInserter<String, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromObject(formDataBodyString);
                    CachedBodyOutputMessage cachedBodyOutputMessage = new CachedBodyOutputMessage(exchange, httpHeaders);
                    log.debug("[GatewayContext]Rewrite Form Data :{}",formDataBodyString);
                    return bodyInserter.insert(cachedBodyOutputMessage,  new BodyInserterContext())
                            .then(Mono.defer(() -> {
                                ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
                                        exchange.getRequest()) {
                                    @Override
                                    public HttpHeaders getHeaders() {
                                        return httpHeaders;
                                    }
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return cachedBodyOutputMessage.getBody();
                                    }
                                };
                                return chain.filter(exchange.mutate().request(decorator).build());
                            }));
                }));
    }

    /**
     * ReadJsonBody
     * @param exchange
     * @param chain
     * @return
     */
    private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, GatewayContext gatewayContext){
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    /*
                     * read the body Flux<DataBuffer>, and release the buffer
                     * //TODO when SpringCloudGateway Version Release To G.SR2,this can be update with the new version's feature
                     * see PR https://github.com/spring-cloud/spring-cloud-gateway/pull/1095
                     */
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        DataBufferUtils.retain(buffer);
                        return Mono.just(buffer);
                    });
                    /*
                     * repackage ServerHttpRequest
                     */
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };
                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                    return ServerRequest.create(mutatedExchange, MESSAGE_READERS)
                            .bodyToMono(String.class)
                            .doOnNext(objectValue -> {
                                gatewayContext.setRequestBody(objectValue);
                                log.debug("[GatewayContext]Read JsonBody Success");
                            }).then(chain.filter(mutatedExchange));
                });
    }

}
