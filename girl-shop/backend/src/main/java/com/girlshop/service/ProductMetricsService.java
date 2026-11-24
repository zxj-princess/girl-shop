package com.girlshop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.Map;

/** Live counters are read separately from the cached product description. */
@Service
public class ProductMetricsService {
    @Resource private JdbcTemplate jdbc;

    public Map<String,Object> snapshot(long product) {
        return jdbc.queryForMap("SELECT COALESCE(views,0) views,(SELECT COUNT(*) FROM tb_favorite WHERE product_id=?) favoriteCount FROM tb_product WHERE id=?",product,product);
    }

    @Transactional
    public Map<String,Object> recordView(long product) {
        if(jdbc.update("UPDATE tb_product SET views=COALESCE(views,0)+1 WHERE id=?",product)!=1)
            throw new IllegalArgumentException("商品不存在");
        return snapshot(product);
    }

    @Transactional
    public Map<String,Object> favorite(long user,long product,boolean selected) {
        // Serialize changes for the same product so the returned total includes this committed operation.
        jdbc.queryForObject("SELECT id FROM tb_product WHERE id=? FOR UPDATE",Long.class,product);
        if(selected) jdbc.update("INSERT INTO tb_favorite(user_id,product_id) VALUES(?,?) ON DUPLICATE KEY UPDATE id=id",user,product);
        else jdbc.update("DELETE FROM tb_favorite WHERE user_id=? AND product_id=?",user,product);
        if(selected)jdbc.update("INSERT IGNORE INTO tb_product_alert(user_id,product_id,last_price,last_status) SELECT ?,id,price,status FROM tb_product WHERE id=?",user,product);
        else jdbc.update("DELETE FROM tb_product_alert WHERE user_id=? AND product_id=?",user,product);
        Map<String,Object> result=snapshot(product);
        result.put("favorited",selected);
        return result;
    }
}
