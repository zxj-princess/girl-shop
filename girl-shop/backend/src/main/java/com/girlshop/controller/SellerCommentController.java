package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.dto.SellerCommentFormDTO;
import com.girlshop.service.ISellerCommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 卖家评价控制器
 */
@RestController
@RequestMapping("/seller-comment")
public class SellerCommentController {

    @Resource
    private ISellerCommentService sellerCommentService;

    /**
     * 评价卖家（需该卖家的已完成订单）
     */
    @PostMapping
    public Result add(@RequestBody SellerCommentFormDTO form) {
        return sellerCommentService.add(form);
    }

    /**
     * 卖家评价列表（分页）
     */
    @GetMapping("/list/{sellerId}")
    public Result list(@PathVariable("sellerId") Long sellerId,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return sellerCommentService.listBySeller(sellerId, page, pageSize);
    }

    /**
     * 卖家评分汇总（平均分 + 评价数）
     */
    @GetMapping("/summary/{sellerId}")
    public Result summary(@PathVariable("sellerId") Long sellerId) {
        return sellerCommentService.summaryBySeller(sellerId);
    }

    /**
     * 检查当前用户是否可评价该卖家
     */
    @GetMapping("/check/{sellerId}")
    public Result checkCanPost(@PathVariable("sellerId") Long sellerId) {
        return sellerCommentService.checkCanPost(sellerId);
    }
}
