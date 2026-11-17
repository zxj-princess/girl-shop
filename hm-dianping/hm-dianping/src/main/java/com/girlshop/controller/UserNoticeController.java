package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.service.IUserNoticeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 消息通知控制器
 */
@RestController
@RequestMapping("/notice")
public class UserNoticeController {

    @Resource
    private IUserNoticeService userNoticeService;

    /**
     * 我的通知列表（分页）
     */
    @GetMapping("/my")
    public Result myNotices(@RequestParam(value = "page", required = false) Integer page,
                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return userNoticeService.myNotices(page, pageSize);
    }

    /**
     * 标记单条通知为已读
     */
    @PutMapping("/{id}/read")
    public Result read(@PathVariable("id") Long id) {
        return userNoticeService.read(id);
    }

    /**
     * 全部已读
     */
    @PutMapping("/read-all")
    public Result readAll() {
        return userNoticeService.readAll();
    }

    /**
     * 未读通知数量
     */
    @GetMapping("/unread-count")
    public Result unreadCount() {
        return userNoticeService.unreadCount();
    }
}
