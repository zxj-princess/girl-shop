package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.entity.BuyerShow;
import com.girlshop.dto.BuyerShowFormDTO;
import com.girlshop.dto.Result;

/**
 * 买家秀服务接口
 */
public interface IBuyerShowService extends IService<BuyerShow> {

    /**
     * 发布买家秀（需已完成对应订单，一个订单只能发一次）
     */
    Result add(BuyerShowFormDTO form);

    /**
     * 商品买家秀列表（分页）
     */
    Result listByProduct(Long productId, Integer page, Integer pageSize);

    /**
     * 检查当前用户是否可发布该商品的买家秀
     * 返回 data=orderId（可发布）或 data=null（不可发布）
     */
    Result checkCanPost(Long productId);
}
