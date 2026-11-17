package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.entity.Follow;
import com.girlshop.entity.User;
import com.girlshop.mapper.FollowMapper;
import com.girlshop.service.IFollowService;
import com.girlshop.service.IProductService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 关注服务实现类
 * - 关注/取消关注（切换），禁止自关注，唯一索引防重复
 * - 查询是否关注
 * - 我关注的卖家列表（分页）
 */
@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private IUserService userService;

    @Resource
    private IProductService productService;

    @Override
    public Result follow(Long sellerId) {
        if (sellerId == null) {
            return Result.fail("参数错误");
        }
        Long userId = UserHolder.getUser().getId();
        // 禁止自己关注自己
        if (sellerId.equals(userId)) {
            return Result.fail("不能关注自己哦～");
        }
        // 校验被关注者是否存在
        User seller = userService.getById(sellerId);
        if (seller == null) {
            return Result.fail("用户不存在");
        }
        // 查询是否已关注
        Follow exist = query().eq("user_id", userId).eq("follow_user_id", sellerId).one();
        if (exist != null) {
            // 已关注 → 取消关注
            removeById(exist.getId());
            return Result.ok(false);
        }
        // 未关注 → 新增关注
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(sellerId);
        try {
            save(follow);
        } catch (DuplicateKeyException e) {
            // 并发场景下唯一索引兜底
            return Result.fail("请勿重复关注");
        }
        return Result.ok(true);
    }

    @Override
    public Result isFollowing(Long sellerId) {
        if (sellerId == null) {
            return Result.ok(false);
        }
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id", userId).eq("follow_user_id", sellerId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result myFollows(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<Follow> p = new Page<>(current, ps);
        long total=query().eq("user_id",userId).count();
        if(total>0 && current>(total+ps-1)/ps)p.setCurrent((total+ps-1)/ps);
        page(p, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .orderByDesc(Follow::getCreateTime));
        enrichFollows(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    /**
     * 批量填充被关注卖家的昵称、头像、在售商品数
     */
    private void enrichFollows(List<Follow> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> sellerIds = list.stream().map(Follow::getFollowUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        java.util.Map<Long,Integer> counts=new java.util.HashMap<>();
        for(Map<String,Object> row:productService.listMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.girlshop.entity.Product>()
                .select("user_id","COUNT(*) AS product_count").in("user_id",sellerIds).eq("status",1).gt("stock",0).groupBy("user_id")))
            counts.put(((Number)row.get("user_id")).longValue(),((Number)row.get("product_count")).intValue());
        for (Follow f : list) {
            User u = userMap.get(f.getFollowUserId());
            if (u != null) {
                f.setFollowNickName(u.getNickName());
                f.setFollowIcon(u.getIcon());
            }
            // 统计该卖家在售商品数
            f.setProductCount(counts.getOrDefault(f.getFollowUserId(),0));
        }
    }

    /**
     * 我的粉丝列表：谁关注了我（follow_user_id = 我）
     */
    @Override
    public Result myFans(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<Follow> p = new Page<>(current, ps);
        long total=query().eq("follow_user_id",userId).count();
        if(total>0 && current>(total+ps-1)/ps)p.setCurrent((total+ps-1)/ps);
        page(p, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowUserId, userId)
                .orderByDesc(Follow::getCreateTime));
        // 填充粉丝（关注者）的昵称、头像
        List<Follow> records = p.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> fanIds = records.stream().map(Follow::getUserId).distinct().collect(Collectors.toList());
            Map<Long, User> fanMap = userService.listByIds(fanIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            for (Follow f : records) {
                User u = fanMap.get(f.getUserId());
                if (u != null) {
                    f.setFanNickName(u.getNickName());
                    f.setFanIcon(u.getIcon());
                }
            }
        }
        return Result.ok(records, p.getTotal());
    }

    /**
     * 关注数 / 粉丝数（实时统计 tb_follow，关注/取关后自动增减）
     */
    @Override
    public Result counts() {
        Long userId = UserHolder.getUser().getId();
        // 我关注了多少人
        long followCount = query().eq("user_id", userId).count();
        // 多少人关注了我
        long fanCount = query().eq("follow_user_id", userId).count();
        java.util.Map<String, Object> data = new java.util.HashMap<>(4);
        data.put("followCount", followCount);
        data.put("fanCount", fanCount);
        return Result.ok(data);
    }
}
