package com.red_escuela.backend.billing.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class BillingTestApplicationContext implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    static <T> T bean(Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("The Spring context is not available yet");
        }
        return context.getBean(type);
    }
}
