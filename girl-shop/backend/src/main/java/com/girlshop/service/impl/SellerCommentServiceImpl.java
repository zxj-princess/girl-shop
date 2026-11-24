package com.girlshop.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.dto.SellerCommentFormDTO;
import com.girlshop.entity.Order;
import com.girlshop.entity.SellerComment;
import com.girlshop.entity.User;
import com.girlshop.mapper.SellerCommentMapper;
import com.girlshop.service.IOrderService;
import com.girlshop.service.ISellerCommentService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.SensitiveWordFilter;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 卖家评价服务实现类
 * - 评价卖家：需该卖家的已完成订单，一个订单只能评价一次（唯一索引约束）
 * - 卖家评价列表（分页）
 * - 卖家评分汇总
 */
@Slf4j
@Service
public class SellerCommentServiceImpl extends ServiceImpl<SellerCommentMapper, SellerComment> implements ISellerCommentService {

    @Resource
    private IOrderService orderService;

    @Resource
    private IUserService userService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Override
    public Result add(SellerCommentFormDTO form) {
        if (form == null || form.getOrderId() == null || form.getScore() == null
                || form.getScore() < 1 || form.getScore() > 5) {
            return Result.fail("参数错误，评分需1~5分");
        }
        Long userId = UserHolder.getUser().getId();
        // 1. 校验订单：必须存在、属于当前买家、状态为已完成(3)
        Order order = orderService.getById(form.getOrderId());
        if (order == null || !order.getBuyerId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 3) {
            return Result.fail("交易完成后才能评价卖家");
        }
        // 2. 一个订单只能评价一次（唯一索引兜底）
        long count = query().eq("order_id", form.getOrderId()).count();
        if (count > 0) {
            return Result.fail("该订单已评价过卖家");
        }
        // 3. 敏感词风控
        String content = form.getContent();
        if (StrUtil.isNotBlank(content) && sensitiveWordFilter.contains(content)) {
            return Result.fail("内容包含敏感词，请修改后重试");
        }
        // 4. 构建并保存（sellerId 取自订单快照）
        SellerComment comment = new SellerComment();
        comment.setOrderId(form.getOrderId());
        comment.setSellerId(order.getSellerId());
        comment.setUserId(userId);
        comment.setScore(form.getScore());
        comment.setContent(content);
        save(comment);
        return Result.ok(comment.getId());
    }

    @Override
    public Result listBySeller(Long sellerId, Integer page, Integer pageSize) {
        if (sellerId == null) {
            return Result.fail("参数错误");
        }
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<SellerComment> p = new Page<>(current, ps);
        page(p, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SellerComment>()
                .eq(SellerComment::getSellerId, sellerId)
                .orderByDesc(SellerComment::getCreateTime));
        enrichComments(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result summaryBySeller(Long sellerId) {
        if (sellerId == null) {
            return Result.fail("参数错误");
        }
        List<SellerComment> all = query().eq("seller_id", sellerId).list();
        double avg = 0;
        if (!all.isEmpty()) {
            int total = 0;
            for (SellerComment c : all) {
                total += c.getScore();
            }
            avg = total * 1.0 / all.size();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("avgScore", Math.round(avg * 10) / 10.0);
        data.put("total", all.size());
        return Result.ok(data);
    }

    @Override
    public Result checkCanPost(Long sellerId) {
        if (sellerId == null) {
            return Result.ok(null);
        }
        Long userId = UserHolder.getUser().getId();
        // 查找当前用户对该卖家的已完成订单
        Order order = orderService.query()
                .eq("buyer_id", userId)
                .eq("seller_id", sellerId)
                .eq("status", 3)
                .one();
        if (order == null) {
            return Result.ok(null);
        }
        // 已评价过则不可再评
        long count = query().eq("order_id", order.getId()).count();
        if (count > 0) {
            return Result.ok(null);
        }
        return Result.ok(order.getId());
    }

    /**
     * 批量填充评价人昵称、头像
     */
    private void enrichComments(List<SellerComment> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream().map(SellerComment::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (SellerComment c : list) {
            User u = userMap.get(c.getUserId());
            if (u != null) {
                c.setNickName(u.getNickName());
                c.setIcon(u.getIcon());
            }
        }
    }
}
