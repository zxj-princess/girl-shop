package com.girlshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.girlshop.entity.Order;

public interface OrderMapper extends BaseMapper<Order> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM tb_order WHERE id=#{id} FOR UPDATE")
    Order lockById(Long id);
    @org.apache.ibatis.annotations.Insert("INSERT IGNORE INTO tb_order_hidden(order_id,user_id) VALUES(#{orderId},#{userId})")
    int hide(@org.apache.ibatis.annotations.Param("orderId") Long orderId, @org.apache.ibatis.annotations.Param("userId") Long userId);
}
