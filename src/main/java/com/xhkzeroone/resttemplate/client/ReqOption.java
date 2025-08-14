package com.xhkzeroone.resttemplate.client;

import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class ReqOption {
    private final HttpMethod method;
    private final String path;
    private final Object body;
    private final Map<String, Object> headers;
    private final Map<String, Object> params;
    private final Map<String, Object> pathVars;

    private ReqOption(Builder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.body = builder.body;
        this.headers = builder.headers;
        this.params = builder.params;
        this.pathVars = builder.pathVars;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HttpMethod method;
        private String path;
        private Object body;
        private final Map<String, Object> headers = new HashMap<>();
        private final Map<String, Object> params = new HashMap<>();
        private final Map<String, Object> pathVars = new HashMap<>();

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder body(Object body) {
            if (this.body != null) {
                throw new IllegalArgumentException("Just single body allowed");
            }
            this.body = body;
            return this;
        }

        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder params(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        public Builder pathVars(String key, Object value) {
            this.pathVars.put(key, value);
            return this;
        }

        public ReqOption build() {
            if (method == null || path == null) {
                throw new IllegalStateException("Method and path are required");
            }
            return new ReqOption(this);
        }
    }
    
    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public Object getBody() { return body; }
    public Map<String, Object> getHeaders() { return headers; }
    public Map<String, Object> getParams() { return params; }
    public Map<String, Object> getPathVars() { return pathVars; }
}
