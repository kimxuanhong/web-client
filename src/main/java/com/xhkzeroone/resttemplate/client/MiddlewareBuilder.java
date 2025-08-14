package com.xhkzeroone.resttemplate.client;


import org.springframework.context.ApplicationContext;

import java.util.List;


public class MiddlewareBuilder {
    public static Middleware build(Class<?> middlewareClasses, ApplicationContext ctx) {
        Object instance = getOrCreateInstance(middlewareClasses, ctx);
        if (!(instance instanceof Middleware)) {
            throw new IllegalArgumentException("Class " + middlewareClasses.getName() + " does not implement Middleware interface");
        }
        return (Middleware) instance;
    }

    private static Object getOrCreateInstance(Class<?> clazz, ApplicationContext ctx) {
        try {
            return ctx.getBean(clazz);
        } catch (Exception e) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot instantiate middleware: " + clazz.getName(), ex);
            }
        }
    }

    public static Handler buildChain(List<Middleware> middlewares, Handler finalHandler) {
        Handler handler = finalHandler;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            handler = middlewares.get(i).apply(handler);
        }
        return handler;
    }
}
