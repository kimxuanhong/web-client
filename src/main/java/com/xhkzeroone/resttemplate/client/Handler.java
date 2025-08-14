package com.xhkzeroone.resttemplate.client;

@FunctionalInterface
public interface Handler {
    void handle(ReqContext ctx) throws Exception;
}
