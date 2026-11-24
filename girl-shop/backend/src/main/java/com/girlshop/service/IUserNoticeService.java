package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.entity.UserNotice;
import com.girlshop.dto.NewProductNoticeMessage;
import com.girlshop.dto.Result;

/**
 * 消息通知服务接口
 */
public interface IUserNoticeService extends IService<UserNotice> {

    /**
     * 我的通知列表（分页）
     */
    Result myNotices(Integer page, Integer pageSize);

    /**
     * 标记单条通知为已读
     */
    Result read(Long id);

    /**
     * 全部已读
     */
    Result readAll();

    /**
     * 未读通知数量
     */
    Result unreadCount();

    /**
     * MQ 消费者调用：为卖家的所有粉丝批量生成上新通知
     */
    void createNoticesForFollowers(NewProductNoticeMessage message);
}
