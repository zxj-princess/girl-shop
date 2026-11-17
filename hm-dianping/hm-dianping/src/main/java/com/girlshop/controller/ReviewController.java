package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.dto.ReviewFormDTO;
import com.girlshop.service.IReviewService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 交易评价控制器
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private IReviewService reviewService;
    @Resource private com.girlshop.service.IOrderService orders;
    @GetMapping("/order/{id}")
    public Result orderStatus(@PathVariable Long id) {
        com.girlshop.entity.Order order=orders.getById(id);
        if(order==null || !order.getBuyerId().equals(com.girlshop.utils.UserHolder.getUser().getId()))return Result.fail("订单不存在");
        boolean reviewed=reviewService.query().eq("order_id",id).count()>0;
        java.util.Map<String,Object> state=new java.util.HashMap<>();
        state.put("orderId",id);state.put("reviewed",reviewed);state.put("canReview",order.getStatus()==3&&!reviewed);
        return Result.ok(state);
    }
    @GetMapping("/eligible/{productId}")
    public Result eligible(@PathVariable Long productId) {
        Long userId=com.girlshop.utils.UserHolder.getUser().getId();
        java.util.List<com.girlshop.entity.Order> completed=orders.query().eq("buyer_id",userId).eq("product_id",productId).eq("status",3)
                .apply("NOT EXISTS (SELECT 1 FROM tb_review r WHERE r.order_id=tb_order.id)").orderByDesc("id").last("LIMIT 1").list();
        java.util.Map<String,Object> state=new java.util.HashMap<>();
        state.put("orderId",completed.isEmpty()?null:completed.get(0).getId());state.put("canReview",!completed.isEmpty());
        state.put("reviewed",completed.isEmpty()&&reviewService.query().eq("product_id",productId).eq("user_id",userId).count()>0);
        return Result.ok(state);
    }

    /**
     * 提交评价
     */
    @PostMapping
    public Result add(@RequestBody ReviewFormDTO form) {
        return reviewService.add(form);
    }

    /**
     * 商品评价列表
     */
    @GetMapping("/list/{productId}")
    public Result list(@PathVariable("productId") Long productId) {
        return reviewService.listByProduct(productId);
    }
}
