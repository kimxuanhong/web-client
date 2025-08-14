package com.xhkzeroone.resttemplate.client;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RestClient extends RestTemplate {

    private final ApplicationContext ctx;
    private final RestConfig config;
    private final List<Middleware> middlewares = new ArrayList<>();

    public RestClient(ApplicationContext ctx, RestConfig config) {
        super(createRequestFactory(config));
        this.ctx = ctx;
        this.config = config;

        if (config.getAddress() != null) {
            this.setUriTemplateHandler(new DefaultUriBuilderFactory(config.getAddress()));
        }
    }

    private static SimpleClientHttpRequestFactory createRequestFactory(RestConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeout());
        factory.setReadTimeout(config.getReadTimeout());
        return factory;
    }

    public void use(Class<? extends Middleware> clazz) {
        Middleware mw = MiddlewareBuilder.build(clazz, ctx);
        this.middlewares.add(mw);
    }
    public <ResT> ResT exchange(ReqOption opt, Class<ResT> resClass, Supplier<ResT> fallback) {
        try {
            return this.exchange(opt, resClass);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                return fallback.get();
            }
            throw e;
        }
    }

    public <ResT> ResT exchange(ReqOption opt, Class<ResT> resClass) {
        ReqContext context = new ReqContext(opt.getBody());
        context.setMethod(opt.getMethod());
        context.setPath(opt.getPath());

        // Merge default headers vs opt headers
        Map<String, Object> headers = new HashMap<>();
        if (config.getDefaultHeaders() != null) {
            headers.putAll(config.getDefaultHeaders());
        }
        if (opt.getHeaders() != null) {
            headers.putAll(opt.getHeaders()); // override key
        }
        context.setHeaders(headers);

        context.setParams(opt.getParams() != null ? new HashMap<>(opt.getParams()) : new HashMap<>());
        context.setPathVars(opt.getPathVars() != null ? new HashMap<>(opt.getPathVars()) : new HashMap<>());

        // Final handler call HTTP
        Handler finalHandler = ctx -> {
            String expandedPath = expandPath(ctx.getPath(), ctx.getPathVars());
            URI uri = buildUri(expandedPath, ctx.getParams());

            HttpHeaders httpHeaders = new HttpHeaders();
            ctx.getHeaders().forEach((k, v) -> httpHeaders.set(k, String.valueOf(v)));

            HttpEntity<?> entity = new HttpEntity<>(ctx.getRequest(), httpHeaders);

            HttpMethod method = ctx.getMethod();
            if (method == null) throw new IllegalArgumentException("HTTP method is required");

            ResponseEntity<ResT> response = super.exchange(uri, method, entity, resClass);
            ctx.setResponse(response.getBody());
        };

        Handler chain = MiddlewareBuilder.buildChain(middlewares, finalHandler);

        try {
            chain.handle(context);
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed", e);
        }

        Object response = context.getResponse();
        if (resClass.isInstance(response)) {
            return resClass.cast(response);
        }
        throw new IllegalStateException("Response is not of expected type: " + resClass.getName());
    }


    private String expandPath(String path, Map<String, Object> pathVars) {
        if (pathVars == null || pathVars.isEmpty()) return path;
        for (Map.Entry<String, Object> entry : pathVars.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return path;
    }

    private URI buildUri(String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }
}
