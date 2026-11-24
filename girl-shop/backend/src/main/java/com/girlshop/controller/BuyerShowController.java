package com.girlshop.controller;

import com.girlshop.dto.BuyerShowFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.service.IBuyerShowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 买家秀控制器
 */
@RestController
@RequestMapping("/buyer-show")
public class BuyerShowController {

    @Resource
    private IBuyerShowService buyerShowService;

    /**
     * 发布买家秀（需已完成对应订单）
     */
    @PostMapping
    public Result add(@RequestBody BuyerShowFormDTO form) {
        return buyerShowService.add(form);
    }

    /**
     * 商品买家秀列表（分页）
     */
    @GetMapping("/list/{productId}")
    public Result list(@PathVariable("productId") Long productId,
                        @RequestParam(value = "page", required = false) Integer page,
                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return buyerShowService.listByProduct(productId, page, pageSize);
    }

    /**
     * 检查当前用户是否可发布该商品的买家秀
     */
    @GetMapping("/check/{productId}")
    public Result checkCanPost(@PathVariable("productId") Long productId) {
        return buyerShowService.checkCanPost(productId);
    }
}
