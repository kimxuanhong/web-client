package com.xhkzeroone.resttemplate.example;


import com.xhkzeroone.resttemplate.client.ReqOption;
import com.xhkzeroone.resttemplate.client.RestClient;
import com.xhkzeroone.resttemplate.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
public class UserService {

    private final RestClient client;

    public UserService(RestClient client) {
        this.client = client;
    }

    public Object getUser(Object req) {
        return client.exchange(ReqOption.builder()
                .method(HttpMethod.POST)
                .path("/users")
                .body(req)
                .header("api-key", UUID.randomUUID().toString())
                .build(), Object.class);
    }


    public static void main(String[] args) {
        WebClient client = new WebClient()
                .use(new WebClient.Middleware() {
                    @Override
                    public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
                        WebClient.Middleware.super.beforeRequest(method, uri, headers, body);
                        System.out.printf("Before request %s%n", method);
                    }

                    @Override
                    public void afterResponse(HttpMethod method, URI uri, HttpHeaders headers, Object body, ResponseEntity<?> response) {
                        WebClient.Middleware.super.afterResponse(method, uri, headers, body, response);
                        System.out.println("After response " + method);
                    }
                }).use(new WebClient.Middleware() {
                    @Override
                    public void beforeRequest(HttpMethod method, URI uri, HttpHeaders headers, Object body) {
                        WebClient.Middleware.super.beforeRequest(method, uri, headers, body);
                        System.out.printf("Before request 2 %s%n", method);
                    }

                    @Override
                    public void afterResponse(HttpMethod method, URI uri, HttpHeaders headers, Object body, ResponseEntity<?> response) {
                        WebClient.Middleware.super.afterResponse(method, uri, headers, body, response);
                        System.out.println("After response 2 " + method);
                    }
                })
                .basicAuth("username", "password")
                .target("https://jsonplaceholder.typicode.com/posts/{id}")
                .timeout(5000, 5000)
                .pathVar("id", "1")
                .param("username", "username")
                .header("Accept", "application/json")
                .param("extra", "123")
                .enableLogging();
        client.get(String.class);
    }
}
