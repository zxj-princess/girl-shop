package com.girlshop.service;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.scheduling.annotation.Scheduled;
import javax.annotation.Resource;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Service @DependsOn("prioritySchema") @Slf4j
public class CacheEvictionService {
    @Resource private JdbcTemplate jdbc;
    @Resource private StringRedisTemplate redis;
    @Transactional public void enqueue(Long id) {
        jdbc.update("INSERT INTO tb_cache_eviction(product_id,generation) VALUES(?,1) ON DUPLICATE KEY UPDATE generation=generation+1",id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){try{redis.delete(Arrays.asList("cache:product:"+id,"cache:product:v2:"+id,"cache:flash-product:v2:"+id));}catch(RuntimeException e){log.warn("缓存失效等待重试 productId={}",id);}}
        });
    }
    public void flushOne(Long id) {
        java.util.List<Long> generations=jdbc.queryForList("SELECT generation FROM tb_cache_eviction WHERE product_id=?",Long.class,id);
        if(generations.isEmpty())return;
        redis.delete(Arrays.asList("cache:product:"+id,"cache:product:v2:"+id,"cache:flash-product:v2:"+id));
        java.util.List<java.util.Map<String,Object>> product=jdbc.queryForList("SELECT is_flash,status FROM tb_product WHERE id=?",id);
        if(!product.isEmpty() && product.get(0).get("is_flash") instanceof Number && ((Number)product.get(0).get("is_flash")).intValue()==1 && product.get(0).get("status") instanceof Number && ((Number)product.get(0).get("status")).intValue()==1)
            redis.opsForSet().add("flash:list",id.toString());
        else redis.opsForSet().remove("flash:list",id.toString());
        jdbc.update("DELETE FROM tb_cache_eviction WHERE product_id=? AND generation=?",id,generations.get(0));
    }
    @Scheduled(fixedDelayString="${cache.eviction-retry-ms:10000}") public void retry() {
        try{for(Long id:jdbc.queryForList("SELECT product_id FROM tb_cache_eviction ORDER BY product_id LIMIT 100",Long.class)){
            try{flushOne(id);}catch(RuntimeException e){log.warn("缓存失效重试失败 productId={}",id);break;}
        }}catch(RuntimeException e){log.warn("缓存失效扫描失败");}
    }
}
