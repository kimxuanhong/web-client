package com.xhkzeroone.resttemplate.client;

import java.util.logging.Logger;

public class LoggingMiddleware implements Middleware {
    private static final Logger logger = Logger.getLogger(LoggingMiddleware.class.getName());

    @Override
    public Handler apply(Handler next) {
        return ctx -> {
            logger.info(String.format("📤 Sending Request - Method: %s, Path: %s, Headers: %s, Params: %s, PathVars: %s, Body: %s",
                    ctx.getMethod(), ctx.getPath(), ctx.getHeaders(), ctx.getParams(), ctx.getPathVars(), ctx.getRequest()
            ));
            long start = System.currentTimeMillis();
            next.handle(ctx);
            long elapsed = System.currentTimeMillis() - start;
            Object response = ctx.getResponse();
            logger.info("✅ Response (" + elapsed + " ms): " + response);
        };
    }
}

