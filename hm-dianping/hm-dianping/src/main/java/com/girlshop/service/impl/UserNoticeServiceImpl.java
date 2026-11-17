package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.NewProductNoticeMessage;
import com.girlshop.dto.Result;
import com.girlshop.entity.Follow;
import com.girlshop.entity.User;
import com.girlshop.entity.UserNotice;
import com.girlshop.mapper.UserNoticeMapper;
import com.girlshop.service.IFollowService;
import com.girlshop.service.IUserNoticeService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息通知服务实现类
 * - 我的通知列表（分页）
 * - 标记已读 / 全部已读 / 未读数量
 * - MQ 消费者调用：为卖家所有粉丝批量生成上新通知
 */
@Slf4j
@Service
public class UserNoticeServiceImpl extends ServiceImpl<UserNoticeMapper, UserNotice> implements IUserNoticeService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;
    @Resource private com.girlshop.realtime.RealtimeHub realtime;

    @Override
    public Result myNotices(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<UserNotice> p = new Page<>(current, ps);
        page(p, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserId, userId)
                .orderByDesc(UserNotice::getCreateTime));
        enrichNotices(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result read(Long id) {
        if (id == null) {
            return Result.fail("参数错误");
        }
        Long userId = UserHolder.getUser().getId();
        // 仅能标记属于自己的通知为已读
        update().eq("id", id).eq("user_id", userId).set("is_read", 1).update();
        realtime.changed(userId);
        return Result.ok();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result readAll() {
        Long userId = UserHolder.getUser().getId();
        update().eq("user_id", userId).eq("is_read", 0).set("is_read", 1).update();
        realtime.changed(userId);
        return Result.ok();
    }

    @Override
    public Result unreadCount() {
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id", userId).eq("is_read", 0).count();
        return Result.ok(count);
    }

    /**
     * MQ 消费者调用：查询该卖家的所有粉丝，批量写入上新通知
     * 此方法在消费者线程中执行，不阻塞发布商品接口
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void createNoticesForFollowers(NewProductNoticeMessage message) {
        if (message == null || message.getSellerId() == null || message.getProductId() == null) {
            log.warn("上新通知消息参数缺失：{}", message);
            return;
        }
        // 查询该卖家的所有粉丝
        List<Follow> follows = followService.query()
                .eq("follow_user_id", message.getSellerId()).list();
        if (follows.isEmpty()) {
            log.info("卖家{}暂无粉丝，跳过上新通知", message.getSellerId());
            return;
        }
        // 批量构建通知记录
        List<UserNotice> notices = new ArrayList<>();
        for (Follow f : follows) {
            UserNotice notice = new UserNotice();
            notice.setUserId(f.getUserId());
            notice.setSellerId(message.getSellerId());
            notice.setProductId(message.getProductId());
            notice.setTitle(message.getProductTitle());
            notice.setProductImage(message.getProductImage());
            notice.setIsRead(0);
            notices.add(notice);
        }
        // 批量保存
        saveBatch(notices);
        for(UserNotice notice:notices)realtime.changed(notice.getUserId());
        log.info("卖家上新通知已发送：卖家={}, 商品={}, 粉丝数={}",
                message.getSellerId(), message.getProductId(), notices.size());
    }

    /**
     * 批量填充卖家昵称、头像
     */
    private void enrichNotices(List<UserNotice> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> sellerIds = list.stream().map(UserNotice::getSellerId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (UserNotice n : list) {
            User u = userMap.get(n.getSellerId());
            if (u != null) {
                n.setSellerNickName(u.getNickName());
                n.setSellerIcon(u.getIcon());
            }
        }
    }
}
