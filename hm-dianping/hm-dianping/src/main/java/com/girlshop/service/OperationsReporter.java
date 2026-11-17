package com.girlshop.service;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
@Component @DependsOn("prioritySchema") @Slf4j
public class OperationsReporter {
    @Resource private JdbcTemplate jdbc;
    private String last="";
    @Scheduled(fixedDelayString="${operations.report-ms:60000}")public void inspect(){
        try{
            long expired=jdbc.queryForObject("SELECT COUNT(*) FROM tb_flash_request WHERE status='PENDING' AND deadline_at<NOW()",Long.class);
            long delayed=jdbc.queryForObject("SELECT COUNT(*) FROM tb_cache_eviction WHERE created_at<DATE_SUB(NOW(),INTERVAL 30 SECOND)",Long.class);
            long failed=jdbc.queryForObject("SELECT COUNT(*) FROM tb_flash_request WHERE status='FAILED' AND updated_at>DATE_SUB(NOW(),INTERVAL 5 MINUTE)",Long.class);
            String state=expired+":"+delayed+":"+failed;
            if(!state.equals(last)){if(expired+delayed+failed>0)log.warn("业务巡检 expiredRequests={} delayedCacheEvictions={} recentFailedRequests={}",expired,delayed,failed);else if(!last.isEmpty())log.info("业务巡检恢复正常");last=state;}
        }catch(RuntimeException e){if(!"DB_UNAVAILABLE".equals(last)){log.warn("业务巡检无法访问数据库");last="DB_UNAVAILABLE";}}
    }
}
