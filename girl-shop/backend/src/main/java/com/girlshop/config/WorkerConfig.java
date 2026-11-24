package com.girlshop.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
@Configuration public class WorkerConfig {
    @Bean public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler(){org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("trade-scheduler-");scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(5);return scheduler;}
    @Bean("cacheRebuildExecutor") public ThreadPoolTaskExecutor cacheRebuildExecutor(){
        ThreadPoolTaskExecutor pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("cache-rebuild-");pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(5);return pool;
    }
}
