package com.girlshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.girlshop.entity.Product;

public interface ProductMapper extends BaseMapper<Product> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM tb_product WHERE id=#{id} FOR UPDATE")
    Product lockById(Long id);
}
