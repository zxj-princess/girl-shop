package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.entity.Follow;
import com.girlshop.dto.Result;

/**
 * 关注服务接口
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取消关注卖家（切换状态）
     */
    Result follow(Long sellerId);

    /**
     * 查询当前用户是否已关注某卖家
     */
    Result isFollowing(Long sellerId);

    /**
     * 我关注的卖家列表（分页）
     */
    Result myFollows(Integer page, Integer pageSize);

    /**
     * 我的粉丝列表（分页）：关注了我的人
     */
    Result myFans(Integer page, Integer pageSize);

    /**
     * 我的关注数 + 粉丝数
     */
    Result counts();
}
