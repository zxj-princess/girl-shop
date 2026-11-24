package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.FlashOrderMessage;
import com.girlshop.dto.OrderFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Order;

public interface IOrderService extends IService<Order> {

    Result createOrder(OrderFormDTO form);

    Result flashOrder(Long productId, Long addressId);

    void createFlashOrder(FlashOrderMessage message);

    Result pay(Long id);

    Result ship(Long id);

    Result receive(Long id);

    Result cancel(Long id);

    /**
     * 隐藏当前用户的终态订单；保留另一方视图和交易记录。
     */
    Result delete(Long id);

    Result buyList();

    Result sellList();

    /**
     * 我的订单（分页，买家/卖家双端合并视图）
     */
    Result myList(Integer page, Integer pageSize, Integer status);

    Result detail(Long id);
    void expireUnpaid(Long id, java.time.LocalDateTime cutoff);
}
