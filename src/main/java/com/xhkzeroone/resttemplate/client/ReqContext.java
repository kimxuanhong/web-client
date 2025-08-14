package com.xhkzeroone.resttemplate.client;

import org.springframework.http.HttpMethod;

import java.util.Map;


public class ReqContext {
    private final Object request;
    private Object response;
    private HttpMethod method;
    private String path;
    private Object body;
    private Map<String, Object> headers;
    private Map<String, Object> params;
    private Map<String, Object> pathVars;

    public ReqContext(Object request) {
        this.request = request;
    }

    public Object getRequest() {
        return request;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getPathVars() {
        return pathVars;
    }

    public void setPathVars(Map<String, Object> pathVars) {
        this.pathVars = pathVars;
    }
}