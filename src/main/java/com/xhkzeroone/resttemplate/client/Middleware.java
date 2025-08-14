package com.xhkzeroone.resttemplate.client;

@FunctionalInterface
public interface Middleware {
    Handler apply(Handler next);
}