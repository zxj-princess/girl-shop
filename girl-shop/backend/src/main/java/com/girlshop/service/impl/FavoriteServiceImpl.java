package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.entity.Favorite;
import com.girlshop.entity.Product;
import com.girlshop.mapper.FavoriteMapper;
import com.girlshop.service.IFavoriteService;
import com.girlshop.service.IProductService;
import com.girlshop.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收藏服务实现类
 */
@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements IFavoriteService {

    @Resource
    private IProductService productService;
    @Resource private com.girlshop.service.ProductMetricsService metrics;

    @Override
    public Result add(Long productId) {
        Long userId = UserHolder.getUser().getId();
        if (productService.getById(productId) == null) {
            return Result.fail("商品不存在");
        }
        return Result.ok(metrics.favorite(userId, productId, true));
    }

    @Override
    public Result remove(Long productId) {
        Long userId = UserHolder.getUser().getId();
        if (productService.getById(productId) == null) return Result.fail("商品不存在");
        return Result.ok(metrics.favorite(userId, productId, false));
    }

    @Override
    public Result myList() {
        Long userId = UserHolder.getUser().getId();
        List<Favorite> favorites = query().eq("user_id", userId).orderByDesc("id").list();
        if (favorites.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> productIds = favorites.stream().map(Favorite::getProductId).collect(Collectors.toList());
        List<Product> products = productService.listByIds(productIds);
        // 按收藏时间排序返回
        java.util.Map<Long, Product> map = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<Product> result = productIds.stream().map(map::get)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Override
    public Result status(Long productId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("product_id", productId).count();
        return Result.ok(count > 0);
    }
}
