package com.girlshop.controller;

import com.girlshop.dto.OrderFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.service.IOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private IOrderService orderService;
    @Resource private com.girlshop.service.FlashRequestService flashRequests;
    @GetMapping("/flash/request/{id}")
    public Result flashStatus(@PathVariable Long id) {
        return flashRequests.status(id,com.girlshop.utils.UserHolder.getUser().getId());
    }
    @GetMapping("/flash/requests")
    public Result flashRequests() { return flashRequests.mine(com.girlshop.utils.UserHolder.getUser().getId()); }

    /**
     * 普通下单
     */
    @PostMapping
    public Result create(@RequestBody OrderFormDTO form) {
        return orderService.createOrder(form);
    }

    /**
     * 捡漏请求（持久预占后返回请求ID，通过请求结果接口查询订单）
     * @param productId 商品id
     * @param addressId 收货地址id（必须属于当前买家）
     */
    @PostMapping("/flash/{productId}")
    public Result flashOrder(@PathVariable("productId") Long productId,
                             @RequestParam(value = "addressId", required = false) Long addressId) {
        return orderService.flashOrder(productId, addressId);
    }

    /**
     * 模拟支付
     */
    @PostMapping("/{id}/pay")
    public Result pay(@PathVariable("id") Long id) {
        return orderService.pay(id);
    }

    /**
     * 卖家发货
     */
    @PostMapping("/{id}/ship")
    public Result ship(@PathVariable("id") Long id) {
        return orderService.ship(id);
    }

    /**
     * 买家确认收货
     */
    @PostMapping("/{id}/receive")
    public Result receive(@PathVariable("id") Long id) {
        return orderService.receive(id);
    }

    /**
     * 取消/关闭订单
     */
    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") Long id) {
        return orderService.cancel(id);
    }

    /**
     * 隐藏当前用户的已完成/已关闭订单，保留原始记录
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Long id) {
        return orderService.delete(id);
    }

    /**
     * 我买到的订单
     */
    @GetMapping("/buy")
    public Result buyList() {
        return orderService.buyList();
    }

    /**
     * 我卖出的订单
     */
    @GetMapping("/sell")
    public Result sellList() {
        return orderService.sellList();
    }

    /**
     * 我的订单（合并买家/卖家双端，分页）
     */
    @GetMapping("/my")
    public Result myList(@RequestParam(value = "page", required = false) Integer page,
                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                         @RequestParam(value = "status", required = false) Integer status) {
        return orderService.myList(page, pageSize, status);
    }

    /**
     * 订单详情
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return orderService.detail(id);
    }
}
