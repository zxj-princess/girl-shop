package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 关注控制器
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;
    @Resource private com.girlshop.service.IUserService users;
    @PutMapping("/{sellerId}")
    public Result ensureFollow(@PathVariable Long sellerId) {
        Long userId=com.girlshop.utils.UserHolder.getUser().getId();
        if(userId.equals(sellerId)||users.getById(sellerId)==null)return Result.fail("不能关注自己或不存在的用户");
        try{followService.save(new com.girlshop.entity.Follow().setUserId(userId).setFollowUserId(sellerId));}
        catch(org.springframework.dao.DuplicateKeyException alreadyFollowing){}
        return Result.ok(true);
    }
    @DeleteMapping("/{sellerId}")
    public Result unfollow(@PathVariable Long sellerId) {
        followService.remove(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.girlshop.entity.Follow>()
                .eq("user_id",com.girlshop.utils.UserHolder.getUser().getId()).eq("follow_user_id",sellerId));
        return Result.ok(false);
    }

    /**
     * 关注/取消关注卖家（切换状态）
     * 返回 data=true 表示已关注，data=false 表示已取消
     */
    @PostMapping("/{sellerId}")
    public Result follow(@PathVariable("sellerId") Long sellerId) {
        return followService.follow(sellerId);
    }

    /**
     * 查询当前用户是否已关注某卖家
     */
    @GetMapping("/status/{sellerId}")
    public Result isFollowing(@PathVariable("sellerId") Long sellerId) {
        return followService.isFollowing(sellerId);
    }

    /**
     * 我关注的卖家列表（分页）
     */
    @GetMapping("/my")
    public Result myFollows(@RequestParam(value = "page", required = false) Integer page,
                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return followService.myFollows(page, pageSize);
    }

    /**
     * 我的粉丝列表（分页）：关注了我的人
     */
    @GetMapping("/fans")
    public Result myFans(@RequestParam(value = "page", required = false) Integer page,
                         @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return followService.myFans(page, pageSize);
    }

    /**
     * 我的关注数 / 粉丝数
     */
    @GetMapping("/counts")
    public Result counts() {
        return followService.counts();
    }
}
