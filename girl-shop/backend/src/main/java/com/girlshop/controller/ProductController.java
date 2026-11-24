package com.girlshop.controller;

import com.girlshop.dto.FlashSaleFormDTO;
import com.girlshop.dto.ProductFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.service.IProductService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商品控制器
 */
@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    private IProductService productService;

    @Resource private com.girlshop.mapper.ProductMapper productMapper;
    @Resource private com.girlshop.mapper.OrderMapper orderMapper;
    @Resource private com.girlshop.service.FlashRequestService flashRequests;
    @GetMapping("/{id}/flash-permission")
    public Result flashPermission(@PathVariable Long id) {
        com.girlshop.entity.Product p=productMapper.selectById(id);
        if(p==null || !p.getUserId().equals(com.girlshop.utils.UserHolder.getUser().getId()))return Result.fail("商品不存在或无权操作");
        String reason=p.getStatus()!=1?"仅上架中的商品可设置捡漏":orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.girlshop.entity.Order>().eq("product_id",id).in("status",0,1,2))>0?"商品有进行中的订单，暂不可修改捡漏设置":flashRequests.pendingForProduct(id)?"有捡漏请求正在处理，暂不可修改捡漏设置":"";
        java.util.Map<String,Object> result=new java.util.HashMap<>();result.put("allowed",reason.isEmpty());result.put("reason",reason);return Result.ok(result);
    }
    /**
     * 发布商品
     */
    @PostMapping
    public Result publish(@RequestBody ProductFormDTO form) {
        return productService.publish(form);
    }

    /**
     * 编辑商品
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id, @RequestBody ProductFormDTO form) {
        return productService.updateProduct(id, form);
    }

    /**
     * 上下架
     */
    @PutMapping("/{id}/status")
    public Result setStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        return productService.setStatus(id, status);
    }

    /**
     * 首页列表：搜索、筛选、排序、分页
     */
    @GetMapping("/list")
    public Result list(@RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "pageSize", required = false) Integer pageSize,
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       @RequestParam(value = "brand", required = false) String brand,
                       @RequestParam(value = "size", required = false) String size,
                       @RequestParam(value = "condition", required = false) Integer condition,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "orderBy", required = false) String orderBy) {
        return productService.listProducts(page, pageSize, categoryId, brand, size, condition, keyword, orderBy);
    }

    /**
     * 商品详情（Redis 缓存）
     */
    @GetMapping("/detail/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return productService.detail(id);
    }

    /**
     * 我的发布
     */
    @GetMapping("/my")
    public Result my() {
        return productService.myList();
    }

    /**
     * 设置为捡漏商品
     */
    @PutMapping("/{id}/flash")
    public Result setFlash(@PathVariable("id") Long id, @RequestBody FlashSaleFormDTO form) {
        return productService.setFlash(id, form);
    }

    /**
     * 捡漏专区列表
     */
    @GetMapping("/flash")
    public Result flashList() {
        return productService.flashList();
    }
}