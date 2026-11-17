package com.girlshop.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
@Configuration public class WorkerConfig {
    @Bean("cacheRebuildExecutor") public ThreadPoolTaskExecutor cacheRebuildExecutor(){
        ThreadPoolTaskExecutor pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("cache-rebuild-");pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(5);return pool;
    }
    @Bean("productViewsExecutor") public ThreadPoolTaskExecutor productViewsExecutor(){
        ThreadPoolTaskExecutor pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(1);pool.setMaxPoolSize(2);pool.setQueueCapacity(300);
        pool.setThreadNamePrefix("product-views-");pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());return pool;
    }
}
