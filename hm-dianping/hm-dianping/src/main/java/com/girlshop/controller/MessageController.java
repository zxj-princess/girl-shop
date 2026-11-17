package com.girlshop.controller;

import com.girlshop.dto.MessageFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.service.IMessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商品留言咨询控制器
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Resource
    private IMessageService messageService;

    /**
     * 留言咨询
     */
    @PostMapping
    public Result add(@RequestBody MessageFormDTO form) {
        return messageService.add(form);
    }

    /**
     * 商品留言列表
     */
    @GetMapping("/list/{productId}")
    public Result list(@PathVariable("productId") Long productId) {
        return messageService.listByProduct(productId);
    }

    /**
     * 卖家回复
     */
    @PostMapping("/{id}/reply")
    public Result reply(@PathVariable("id") Long id, @RequestParam("reply") String reply) {
        return messageService.reply(id, reply);
    }
}