package com.girlshop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girlshop.dto.Result;
import com.girlshop.entity.*;
import com.girlshop.mapper.*;
import com.girlshop.utils.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@Service
@DependsOn("prioritySchema")
public class FlashRequestService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ProductMapper products;
    @Resource private OrderMapper orders;
    @Resource private AddressMapper addresses;
    @Resource private RedisIdWorker ids;
    @Resource private ObjectMapper json;
    @Resource private CacheEvictionService evictions;
    @Resource private TradeExperienceService trade;
    @Resource private org.springframework.data.redis.core.StringRedisTemplate redis;
    @org.springframework.beans.factory.annotation.Value("${flash.redis-prefix:}") private String redisPrefix="";

    public boolean hasPending(Long productId, Long userId) {
        return !jdbc.queryForList("SELECT id FROM tb_flash_request WHERE product_id=? AND user_id=? AND status='PENDING' FOR UPDATE", productId,userId).isEmpty();
    }
    public boolean pendingForProduct(Long productId) {
        return !jdbc.queryForList("SELECT id FROM tb_flash_request WHERE product_id=? AND status='PENDING' FOR UPDATE",productId).isEmpty();
    }

    @Transactional
    public Result reserve(Long productId, Long addressId, Long userId) {
        if (productId == null || addressId == null) return Result.fail("请选择商品和地址");
        Product p = products.lockById(productId);
        if (p == null || userId.equals(p.getUserId())) return Result.fail("商品不存在或不能购买自己的商品");
        List<Map<String,Object>> pending = jdbc.queryForList("SELECT id FROM tb_flash_request WHERE product_id=? AND user_id=? AND status='PENDING' FOR UPDATE",productId,userId);
        if (!pending.isEmpty()) return Result.ok(pending.get(0).get("id").toString());
        LocalDateTime now = LocalDateTime.now();
        if (!Integer.valueOf(1).equals(p.getStatus()) || !Integer.valueOf(1).equals(p.getIsFlash())
                || p.getStock() == null || p.getStock() <= 0 || p.getFlashPrice() == null || p.getFlashPrice().signum() <= 0
                || p.getFlashBeginTime() == null || p.getFlashEndTime() == null || now.isBefore(p.getFlashBeginTime())
                || !now.isBefore(p.getFlashEndTime())) return Result.fail("活动未开始、已结束或库存不足");
        if (!jdbc.queryForList("SELECT id FROM tb_order WHERE product_id=? AND buyer_id=? AND status<>4 FOR UPDATE", productId,userId).isEmpty())
            return Result.fail("每人每商品限购一单");
        Address a = addresses.selectById(addressId);
        if (a == null || !userId.equals(a.getUserId())) return Result.fail("收货地址无效");
        long id = ids.nextId("order");
        Order order = new Order().setId(id).setOrderNo("GS"+id).setBuyerId(userId).setSellerId(p.getUserId())
                .setProductId(productId).setProductTitle(p.getTitle()).setProductImage(p.getImages()==null ? "" : p.getImages().split(",")[0])
                .setPrice(p.getFlashPrice()).setPayAmount(p.getFlashPrice()).setStatus(0)
                .setReceiverName(a.getReceiverName()).setReceiverPhone(a.getReceiverPhone())
                .setReceiverAddress(a.getProvince()+a.getCity()+a.getDistrict()+a.getDetail());
        String snapshot;
        try { snapshot=json.writeValueAsString(order); } catch(Exception e) { throw new IllegalStateException("订单快照失败",e); }
        if (jdbc.update("UPDATE tb_product SET status=IF(stock=1,2,status),stock=stock-1 WHERE id=? AND status=1 AND stock>0",productId)!=1)
            throw new IllegalStateException("库存预占失败");
        jdbc.update("INSERT INTO tb_flash_request(id,product_id,user_id,order_json,status,created_at,deadline_at,next_publish_at,updated_at) VALUES(?,?,?,?,'PENDING',NOW(),DATE_ADD(NOW(),INTERVAL 5 MINUTE),NOW(),NOW())",
                id,productId,userId,snapshot);
        invalidate(productId);
        return Result.ok(Long.toString(id));
    }

    private Map<String,Object> lockRequest(Long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT product_id FROM tb_flash_request WHERE id=?",id);
        if(rows.isEmpty()) return null;
        products.lockById(((Number)rows.get(0).get("product_id")).longValue());
        return jdbc.queryForMap("SELECT * FROM tb_flash_request WHERE id=? FOR UPDATE",id);
    }

    @Transactional
    public void complete(Long id) {
        Map<String,Object> r=lockRequest(id);
        if(r==null || !"PENDING".equals(r.get("status"))) return;
        if (!deadline(r).isAfter(LocalDateTime.now())) { release(r,"请求处理超时"); return; }
        Order order;
        try { order=json.readValue((String)r.get("order_json"),Order.class); } catch(Exception e){throw new IllegalArgumentException("订单快照无效",e);}
        if(orders.selectById(id)==null && orders.insert(order)!=1) throw new IllegalStateException("订单写入失败");
        jdbc.update("UPDATE tb_flash_request SET status='SUCCESS',updated_at=NOW() WHERE id=? AND status='PENDING'",id);
        trade.orderEvent(order,"捡漏下单成功");
    }

    @Transactional
    public void failedAttempt(Long id, String reason) {
        Map<String,Object> r=lockRequest(id);
        if(r==null || !"PENDING".equals(r.get("status"))) return;
        int attempts=((Number)r.get("attempts")).intValue()+1;
        jdbc.update("UPDATE tb_flash_request SET attempts=?,error_message=?,next_publish_at=DATE_ADD(NOW(),INTERVAL 10 SECOND),updated_at=NOW() WHERE id=?",attempts,shortReason(reason),id);
        if(attempts>=3) release(r,"订单创建失败，库存已释放");
    }

    @Transactional
    public void expire(Long id) {
        Map<String,Object> r=lockRequest(id);
        if(r!=null && "PENDING".equals(r.get("status")) && !deadline(r).isAfter(LocalDateTime.now()))
            release(r,"请求处理超时，库存已释放");
    }

    private void release(Map<String,Object> r,String reason) {
        Long id=((Number)r.get("id")).longValue(), productId=((Number)r.get("product_id")).longValue();
        // A successful order always wins over compensation, even after a duplicate delivery.
        if(orders.selectById(id)!=null) {
            jdbc.update("UPDATE tb_flash_request SET status='SUCCESS',updated_at=NOW() WHERE id=?",id); return;
        }
        if(jdbc.update("UPDATE tb_flash_request SET status='FAILED',error_message=?,updated_at=NOW() WHERE id=? AND status='PENDING'",reason,id)==1) {
            if(jdbc.update("UPDATE tb_product SET stock=stock+1,status=IF(status=2,1,status) WHERE id=?",productId)!=1)
                throw new IllegalStateException("补偿商品不存在");
            invalidate(productId);
            trade.notice(((Number)r.get("user_id")).longValue(),"捡漏下单未完成",reason,null);
        }
    }

    public Result status(Long id,Long userId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,error_message FROM tb_flash_request WHERE id=? AND user_id=?",id,userId);
        if(rows.isEmpty()) return Result.fail("请求不存在");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("requestId",id.toString()); result.put("orderId","SUCCESS".equals(rows.get(0).get("status"))?id.toString():null);
        result.put("status",rows.get(0).get("status")); result.put("errorMessage",rows.get(0).get("error_message"));
        return Result.ok(result);
    }
    public void orderClosed(Long id) { jdbc.update("UPDATE tb_flash_request SET status='CANCELLED',updated_at=NOW() WHERE id=? AND status='SUCCESS'",id); }
    public Result mine(Long userId) {
        return Result.ok(jdbc.queryForList("SELECT CAST(id AS CHAR) AS requestId,status,error_message AS errorMessage,created_at AS createdAt FROM tb_flash_request WHERE user_id=? ORDER BY created_at DESC LIMIT 20",userId));
    }
    private String shortReason(String s) { return s==null?"处理失败":s.substring(0,Math.min(250,s.length())); }
    private LocalDateTime deadline(Map<String,Object> row) {
        Object value=row.get("deadline_at");
        return value instanceof LocalDateTime ? (LocalDateTime)value : ((java.sql.Timestamp)value).toLocalDateTime();
    }
    private void invalidate(Long productId) {
        evictions.enqueue(productId);
    }

    @Transactional
    public void reconcile(Long productId) {
        Product p=products.lockById(productId);
        if(p==null)return;
        // Stock in DB already excludes every durable pending reservation. Never rebuild from stale cached values.
        redis.opsForValue().set(redisPrefix+"flash:stock:"+productId,String.valueOf(p.getStock()));
        redis.delete(redisPrefix+"flash:order:"+productId);
        redis.delete(java.util.Arrays.asList(redisPrefix+"cache:product:"+productId,redisPrefix+"cache:product:v2:"+productId,redisPrefix+"cache:flash-product:v2:"+productId));
    }
}
