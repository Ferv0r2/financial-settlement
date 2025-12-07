package com.financial.settlement.common.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationContext를 정적으로 접근하기 위한 유틸리티
 * JPA EntityListener 등 Spring Bean이 아닌 클래스에서 Bean을 가져올 때 사용
 */
@Component
public class BeanUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    public static <T> T getBean(@NonNull Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(clazz);
    }

    public static <T> T getBean(@NonNull String name, @NonNull Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(name, clazz);
    }

    public static boolean isContextAvailable() {
        return applicationContext != null;
    }
}
