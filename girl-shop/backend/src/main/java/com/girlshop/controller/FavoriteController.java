package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.service.IFavoriteService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 收藏控制器
 */
@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Resource
    private IFavoriteService favoriteService;

    /**
     * 收藏商品
     */
    @PostMapping("/{productId}")
    public Result add(@PathVariable("productId") Long productId) {
        return favoriteService.add(productId);
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{productId}")
    public Result remove(@PathVariable("productId") Long productId) {
        return favoriteService.remove(productId);
    }

    /**
     * 我的收藏
     */
    @GetMapping("/my")
    public Result my() {
        return favoriteService.myList();
    }

    /**
     * 是否已收藏
     */
    @GetMapping("/status/{productId}")
    public Result status(@PathVariable("productId") Long productId) {
        return favoriteService.status(productId);
    }
}