package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.FlashSaleFormDTO;
import com.girlshop.dto.ProductFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Product;

public interface IProductService extends IService<Product> {

    Result publish(ProductFormDTO form);

    Result updateProduct(Long id, ProductFormDTO form);

    Result setStatus(Long id, Integer status);

    Result listProducts(Integer page, Integer pageSize, Long categoryId, String brand,
                        String size, Integer condition, String keyword, String orderBy);

    Result detail(Long id);

    Result myList();

    Result setFlash(Long id, FlashSaleFormDTO form);

    Result flashList();

    Product getProductById(Long id);
}