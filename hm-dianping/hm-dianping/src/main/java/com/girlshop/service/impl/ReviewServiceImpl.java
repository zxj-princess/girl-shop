package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.dto.ReviewFormDTO;
import com.girlshop.entity.Order;
import com.girlshop.entity.Review;
import com.girlshop.entity.User;
import com.girlshop.mapper.ReviewMapper;
import com.girlshop.service.IOrderService;
import com.girlshop.service.IReviewService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 交易评价服务实现类
 */
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private IOrderService orderService;

    @Resource
    private IUserService userService;
    @Resource private com.girlshop.mapper.OrderMapper orders;
    @Resource private com.girlshop.service.UploadAssetService assets;
    @Resource private com.girlshop.service.TradeExperienceService trade;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result add(ReviewFormDTO form) {
        if (form == null || form.getOrderId() == null || form.getScore() == null
                || form.getScore() < 1 || form.getScore() > 5) {
            return Result.fail("参数错误");
        }
        Long userId = UserHolder.getUser().getId();
        if(form.getContent()==null || form.getContent().trim().isEmpty() || form.getContent().trim().length()>500)
            return Result.fail("请填写1至500字评价");
        Order order = orders.lockById(form.getOrderId());
        if (order == null || !order.getBuyerId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 3) {
            return Result.fail("交易完成后才能评价");
        }
        if(trade.activeCase(order.getId()))return Result.fail("售后处理中，暂不能评价");
        long count = query().eq("order_id", form.getOrderId()).count();
        if (count > 0) {
            return Result.fail("该订单已评价");
        }
        Review review = new Review();
        review.setOrderId(form.getOrderId());
        review.setProductId(order.getProductId());
        review.setUserId(userId);
        review.setScore(form.getScore());
        review.setContent(form.getContent());
        assets.claim(userId,form.getImages());
        review.setContent(form.getContent().trim());
        review.setImages(form.getImages()==null?"":String.join(",",form.getImages()));
        save(review);
        trade.notice(order.getSellerId(),"收到买家评价","商品："+order.getProductTitle(),order.getId());
        return Result.ok();
    }

    @Override
    public Result listByProduct(Long productId) {
        List<Review> list = query().eq("product_id", productId).orderByDesc("create_time").list();
        for (Review r : list) {
            User u = userService.getById(r.getUserId());
            if (u != null) {
                r.setNickName(u.getNickName());
                r.setIcon(u.getIcon());
            }
        }
        return Result.ok(list);
    }
}
