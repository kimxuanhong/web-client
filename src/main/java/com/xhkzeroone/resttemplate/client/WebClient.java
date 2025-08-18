package com.xhkzeroone.resttemplate.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.*;


public class WebClient {
    private final RestTemplate restTemplate;
    private final List<Middleware> middlewares = new ArrayList<>();
    private String baseUrl;
    private int connectTimeout = 5000;
    private int readTimeout = 10000;

    public WebClient() {
        this.restTemplate = new RestTemplate();
        setTimeout(connectTimeout, readTimeout);
    }

    public WebClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public WebClient timeout(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        setTimeout(connectTimeout, readTimeout);
        return this;
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

    public WebClient baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    // ==== Factory method để tạo Request mới ====
    public RequestBuilder target(String target) {
        return new RequestBuilder(this, target);
    }

    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    List<Middleware> getMiddlewares() {
        return middlewares;
    }

    int getConnectTimeout() {
        return connectTimeout;
    }

    int getReadTimeout() {
        return readTimeout;
    }

    String getBaseUrl() {
        return baseUrl;
    }


    public static class RequestBuilder {
        private final WebClient client;
        private final String target;
        private final List<Middleware> middlewares = new ArrayList<>();
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> pathVars = new HashMap<>();
        private final Map<String, String> params = new HashMap<>();
        private Object body;
        private Class<?> resultType = String.class;

        RequestBuilder(WebClient client, String target) {
            this.client = client;
            this.target = target;
        }

        public RequestBuilder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public RequestBuilder param(String key, String value) {
            params.put(key, value);
            return this;
        }

        public RequestBuilder pathVar(String key, String value) {
            pathVars.put(key, value);
            return this;
        }

        public RequestBuilder body(Object body) {
            this.body = body;
            return this;
        }

        public <T> RequestBuilder result(Class<T> clazz) {
            this.resultType = clazz;
            return this;
        }

        @SuppressWarnings(value = "unchecked")
        public <T> ResponseEntity<T> get() {
            return execute(HttpMethod.GET, (Class<T>) resultType);
        }

        @SuppressWarnings(value = "unchecked")
        public <T> ResponseEntity<T> post() {
            return execute(HttpMethod.POST, (Class<T>) resultType);
        }

        @SuppressWarnings(value = "unchecked")
        public <T> ResponseEntity<T> put() {
            return execute(HttpMethod.PUT, (Class<T>) resultType);
        }

        @SuppressWarnings(value = "unchecked")
        public <T> ResponseEntity<T> delete() {
            return execute(HttpMethod.DELETE, (Class<T>) resultType);
        }

        // ================== Middleware Control ==================
        public RequestBuilder use(Class<? extends Middleware> clazz) {
            try {
                this.middlewares.add(clazz.getDeclaredConstructor().newInstance());
            } catch (NoSuchMethodException | InvocationTargetException |
                     InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Cannot initialize middleware: " + clazz.getName(), e);
            }
            return this;
        }

        public RequestBuilder use(Middleware middleware) {
            this.middlewares.add(middleware);
            return this;
        }

        private <T> ResponseEntity<T> execute(HttpMethod method, Class<T> clazz) {
            String fullUrl = Optional.ofNullable(this.target)
                    .map(target -> {
                        if (client.getBaseUrl() != null && !target.startsWith("http")) {
                            return client.getBaseUrl() + target;
                        }
                        return target;
                    })
                    .orElse(client.getBaseUrl());

            URI uri = UriComponentsBuilder.fromUriString(fullUrl)
                    .queryParams(toMultiValueMap(this.params))
                    .buildAndExpand(this.pathVars)
                    .toUri();

            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach(httpHeaders::add);

            List<Middleware> middlewares = new ArrayList<>(client.getMiddlewares());
            middlewares.addAll(this.middlewares);
            for (WebClient.Middleware mw : middlewares) {
                mw.beforeRequest(method, uri, httpHeaders, this.body);
            }

            HttpEntity<Object> entity = (method == HttpMethod.GET || method == HttpMethod.DELETE)
                    ? new HttpEntity<>(httpHeaders)
                    : new HttpEntity<>(this.body, httpHeaders);

            ResponseEntity<T> response = client.getRestTemplate().exchange(uri, method, entity, clazz);

            for (WebClient.Middleware mw : middlewares) {
                mw.afterResponse(method, uri, httpHeaders, this.body, response);
            }

            return response;
        }

        private static org.springframework.util.MultiValueMap<String, String> toMultiValueMap(Map<String, String> map) {
            org.springframework.util.LinkedMultiValueMap<String, String> mvMap = new org.springframework.util.LinkedMultiValueMap<>();
            if (map != null) {
                map.forEach(mvMap::add);
            }
            return mvMap;
        }
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
