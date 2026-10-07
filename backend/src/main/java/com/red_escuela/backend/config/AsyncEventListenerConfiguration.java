package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.event.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncEventListenerConfiguration implements AsyncConfigurer {

    private final ThreadPoolTaskExecutor eventListenerExecutor;

    public AsyncEventListenerConfiguration(ThreadPoolTaskExecutor eventListenerExecutor) {
        this.eventListenerExecutor = eventListenerExecutor;
    }

    @Bean
    static ThreadPoolTaskExecutor eventListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("event-listener-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventListenerExecutor;
    }
}
