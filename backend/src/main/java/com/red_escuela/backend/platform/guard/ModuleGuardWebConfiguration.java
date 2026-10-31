package com.red_escuela.backend.platform.guard;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ModuleGuardWebConfiguration implements WebMvcConfigurer {

    private final RequiresModuleInterceptor interceptor;

    public ModuleGuardWebConfiguration(RequiresModuleInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
