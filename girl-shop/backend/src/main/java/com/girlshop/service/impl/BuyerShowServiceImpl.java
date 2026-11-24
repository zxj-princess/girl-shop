package com.girlshop.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.BuyerShowFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.BuyerShow;
import com.girlshop.entity.Order;
import com.girlshop.entity.User;
import com.girlshop.mapper.BuyerShowMapper;
import com.girlshop.service.IBuyerShowService;
import com.girlshop.service.IOrderService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.SensitiveWordFilter;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 买家秀服务实现类
 * - 发布买家秀：需已完成对应订单，一个订单只能发一次（唯一索引约束）
 * - 商品买家秀列表（分页）
 */
@Slf4j
@Service
public class BuyerShowServiceImpl extends ServiceImpl<BuyerShowMapper, BuyerShow> implements IBuyerShowService {

    @Resource
    private IOrderService orderService;

    @Resource
    private IUserService userService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;
    @Resource private com.girlshop.service.UploadAssetService assets;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result add(BuyerShowFormDTO form) {
        if (form == null || form.getOrderId() == null) {
            return Result.fail("参数错误");
        }
        Long userId = UserHolder.getUser().getId();
        // 1. 校验订单：必须存在、属于当前买家、状态为已完成(3)
        Order order = orderService.getById(form.getOrderId());
        if (order == null || !order.getBuyerId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 3) {
            return Result.fail("交易完成后才能发布买家秀");
        }
        // 2. 一个订单只能发布一次买家秀（唯一索引兜底）
        long count = query().eq("order_id", form.getOrderId()).count();
        if (count > 0) {
            return Result.fail("该订单已发布过买家秀");
        }
        // 3. 敏感词风控
        String content = form.getContent();
        if (StrUtil.isNotBlank(content) && sensitiveWordFilter.contains(content)) {
            return Result.fail("内容包含敏感词，请修改后重试");
        }
        assets.claim(userId,form.getImages());
        // 4. 构建并保存
        BuyerShow show = new BuyerShow();
        show.setOrderId(form.getOrderId());
        show.setProductId(order.getProductId());
        show.setUserId(userId);
        show.setSellerId(order.getSellerId());
        show.setImages(form.getImages() == null ? "" : String.join(",", form.getImages()));
        show.setContent(content);
        save(show);
        return Result.ok(show.getId());
    }

    @Override
    public Result listByProduct(Long productId, Integer page, Integer pageSize) {
        if (productId == null) {
            return Result.fail("参数错误");
        }
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<BuyerShow> p = new Page<>(current, ps);
        page(p, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BuyerShow>()
                .eq(BuyerShow::getProductId, productId)
                .orderByDesc(BuyerShow::getCreateTime));
        enrichShows(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result checkCanPost(Long productId) {
        if (productId == null) {
            return Result.ok(null);
        }
        Long userId = UserHolder.getUser().getId();
        // 查找当前用户对该商品的已完成订单
        Order order = orderService.query()
                .eq("buyer_id", userId)
                .eq("product_id", productId)
                .eq("status", 3)
                .one();
        if (order == null) {
            return Result.ok(null);
        }
        // 已发布过买家秀则不可再发
        long count = query().eq("order_id", order.getId()).count();
        if (count > 0) {
            return Result.ok(null);
        }
        return Result.ok(order.getId());
    }

    /**
     * 批量填充买家昵称、头像
     */
    private void enrichShows(List<BuyerShow> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream().map(BuyerShow::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (BuyerShow s : list) {
            User u = userMap.get(s.getUserId());
            if (u != null) {
                s.setNickName(u.getNickName());
                s.setIcon(u.getIcon());
            }
        }
    }
}
