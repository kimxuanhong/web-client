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
                .result(Post.class)
                .enableLogging();
        ResponseEntity<Post>  response = client.get();
        System.out.printf(response.getBody().toString());
    }

    public static class Post {

        public Post() {
        }

        private int userId;
        private int id;
        private String title;
        private String body;

        // Getters & Setters
        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @Override
        public String toString() {
            return "Post{" +
                    "userId=" + userId +
                    ", id=" + id +
                    ", title='" + title + '\'' +
                    ", body='" + body + '\'' +
                    '}';
        }
    }

}
