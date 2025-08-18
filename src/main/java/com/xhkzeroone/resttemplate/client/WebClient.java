package com.xhkzeroone.resttemplate.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.*;


public class WebClient {
    private RestTemplate restTemplate = new RestTemplate();
    private final List<Middleware> middlewares = new ArrayList<>();
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> pathVars = new HashMap<>();
    private final Map<String, String> params = new HashMap<>();
    private String target;
    private Object body;

    // Default timeouts (ms)
    private int connectTimeout = 5000;
    private int readTimeout = 10000;

    public WebClient() {
        setTimeout(connectTimeout, readTimeout);
    }

    public WebClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        setTimeout(connectTimeout, readTimeout);
    }


    public WebClient(int connectTimeout, int readTimeout) {
        setTimeout(connectTimeout, readTimeout);
    }

    private void setTimeout(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        restTemplate.setRequestFactory(requestFactory);
    }

    // ================== Middleware Control ==================
    public WebClient use(Class<? extends Middleware> clazz) {
        try {
            this.middlewares.add(clazz.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Cannot initialize middleware: " + clazz.getName(), e);
        }
        return this;
    }

    public WebClient use(Middleware middleware) {
        this.middlewares.add(middleware);
        return this;
    }

    public WebClient enableLogging() {
        this.middlewares.add(new LoggingMiddleware());
        return this;
    }

    public WebClient enableRetry() {
        this.middlewares.add(new RetryMiddleware());
        return this;
    }

    public WebClient enableRetry(int maxRetries, long delayMillis) {
        this.middlewares.add(new RetryMiddleware(maxRetries, delayMillis));
        return this;
    }

    public WebClient bearerAuth(String token) {
        this.middlewares.add(new AuthMiddleware(token));
        return this;
    }

    public WebClient bearerAuth(AuthMiddleware.TokenProvider provider) {
        this.middlewares.add(new AuthMiddleware(provider));
        return this;
    }

    public WebClient basicAuth(String username, String password) {
        this.middlewares.add(new Middleware() {
            @Override
            public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
                Middleware.super.beforeRequest(method, uri, headers, body);
                headers.add("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
            }
        });
        return this;
    }

    public WebClient basicAuth(String value) {
        this.middlewares.add(new Middleware() {
            @Override
            public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
                Middleware.super.beforeRequest(method, uri, headers, body);
                headers.add("Authorization", "Basic " + value);
            }
        });
        return this;
    }

    // ================== Builder Methods ==================


    public WebClient timeout(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        setTimeout(connectTimeout, readTimeout);
        return this;
    }

    public WebClient requestFactory(ClientHttpRequestFactory factory) {
        this.restTemplate.setRequestFactory(factory);
        return this;
    }

    public WebClient target(String target) {
        this.target = target;
        return this;
    }

    public WebClient header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public WebClient headers(Map<String, String> headers) {
        if (headers != null) {
            this.headers.putAll(headers);
        }
        return this;
    }

    public WebClient pathVar(String key, String value) {
        this.pathVars.put(key, value);
        return this;
    }

    public WebClient pathVars(Map<String, String> pathVars) {
        if (pathVars != null) {
            this.pathVars.putAll(pathVars);
        }
        return this;
    }

    public WebClient param(String key, String value) {
        this.params.put(key, value);
        return this;
    }

    public WebClient params(Map<String, String> params) {
        if (params != null) {
            this.params.putAll(params);
        }
        return this;
    }

    public WebClient body(Object body) {
        this.body = body;
        return this;
    }

    // ================== Public Methods ==================
    public <T> ResponseEntity<T> get(Class<T> clazz) {
        return execute(HttpMethod.GET, clazz);
    }

    public <T> ResponseEntity<T> post(Class<T> clazz) {
        return execute(HttpMethod.POST, clazz);
    }

    public <T> ResponseEntity<T> put(Class<T> clazz) {
        return execute(HttpMethod.PUT, clazz);
    }

    public <T> ResponseEntity<T> delete(Class<T> clazz) {
        return execute(HttpMethod.DELETE, clazz);
    }

    // ================== Core Execute Logic ==================
    private <T> ResponseEntity<T> execute(HttpMethod method, Class<T> clazz) {
        URI uri = UriComponentsBuilder.fromUriString(this.target)
                .queryParams(toMultiValueMap(this.params))
                .buildAndExpand(this.pathVars)
                .toUri();

        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::add);

        for (Middleware mw : middlewares) {
            mw.beforeRequest(method, uri, httpHeaders, this.body);
        }

        HttpEntity<Object> entity = (method == HttpMethod.GET || method == HttpMethod.DELETE)
                ? new HttpEntity<>(httpHeaders)
                : new HttpEntity<>(this.body, httpHeaders);

        ResponseEntity<T> response;
        RetryMiddleware retryMw = getRetryMiddleware();
        if (retryMw != null) {
            response = retryMw.executeWithRetry(() -> this.restTemplate.exchange(uri, method, entity, clazz));
        } else {
            response = this.restTemplate.exchange(uri, method, entity, clazz);
        }

        for (Middleware mw : middlewares) {
            mw.afterResponse(method, uri, httpHeaders, this.body, response);
        }

        return response;
    }

    private RetryMiddleware getRetryMiddleware() {
        for (Middleware mw : middlewares) {
            if (mw instanceof RetryMiddleware) {
                return (RetryMiddleware) mw;
            }
        }
        return null;
    }

    private static org.springframework.util.MultiValueMap<String, String> toMultiValueMap(Map<String, String> map) {
        org.springframework.util.LinkedMultiValueMap<String, String> mvMap = new org.springframework.util.LinkedMultiValueMap<>();
        if (map != null) {
            map.forEach(mvMap::add);
        }
        return mvMap;
    }

    // ================== Middleware Interface ==================
    public interface Middleware {
        default void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
        }

        default void afterResponse(HttpMethod method, URI uri, HttpHeaders headers, Object body, ResponseEntity<?> response) {
        }
    }

    // ================== Logging Middleware ==================
    public static class LoggingMiddleware implements Middleware {
        @Override
        public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
            System.out.println("---- Request ----");
            System.out.println("Method: " + method);
            System.out.println("URL: " + uri);
            System.out.println("Headers: " + headers);
            System.out.println("Body: " + (body != null ? body : "(no body)"));
        }

        @Override
        public void afterResponse(HttpMethod method, URI uri, HttpHeaders headers, Object body, ResponseEntity<?> response) {
            System.out.println("---- Response ----");
            System.out.println("Status: " + response.getStatusCode());
            System.out.println("Headers: " + response.getHeaders());
            System.out.println("Body: " + (response.getBody() != null ? response.getBody() : "(no body)"));
        }
    }

    // ================== Retry Middleware ==================
    public static class RetryMiddleware implements Middleware {
        private final int maxRetries;
        private final long delayMillis;

        public RetryMiddleware() {
            this(3, 1000);
        }

        public RetryMiddleware(int maxRetries, long delayMillis) {
            this.maxRetries = maxRetries;
            this.delayMillis = delayMillis;
        }

        @Override
        public void afterResponse(HttpMethod method, URI uri, HttpHeaders headers, Object body, ResponseEntity<?> response) {
            if (response.getStatusCode().is5xxServerError()) {
                throw new RetryableException("Server error: " + response.getStatusCode());
            }
        }

        public <T> ResponseEntity<T> executeWithRetry(RetryableOperation<T> operation) {
            int attempt = 0;
            while (true) {
                try {
                    return operation.run();
                } catch (RetryableException ex) {
                    attempt++;
                    if (attempt > maxRetries) {
                        throw new RuntimeException("Max retries reached", ex);
                    }
                    System.out.println("Retry attempt " + attempt + " after error: " + ex.getMessage());
                    sleep(delayMillis);
                } catch (Exception ex) {
                    attempt++;
                    if (attempt > maxRetries) {
                        throw new RuntimeException("Max retries reached", ex);
                    }
                    System.out.println("Retry attempt " + attempt + " after exception: " + ex.getMessage());
                    sleep(delayMillis);
                }
            }
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        @FunctionalInterface
        public interface RetryableOperation<T> {
            ResponseEntity<T> run();
        }

        public static class RetryableException extends RuntimeException {
            public RetryableException(String message) {
                super(message);
            }
        }
    }

    // ================== Auth Middleware ==================
    public static class AuthMiddleware implements Middleware {
        private final TokenProvider tokenProvider;

        public AuthMiddleware(String token) {
            this(() -> token);
        }

        public AuthMiddleware(TokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
        }

        @Override
        public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
            String token = tokenProvider.getToken();
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }
        }

        @FunctionalInterface
        public interface TokenProvider {
            String getToken();
        }
    }
}
