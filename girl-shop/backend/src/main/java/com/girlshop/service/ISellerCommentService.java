package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.entity.SellerComment;
import com.girlshop.dto.Result;
import com.girlshop.dto.SellerCommentFormDTO;

/**
 * 卖家评价服务接口
 */
public interface ISellerCommentService extends IService<SellerComment> {

    /**
     * 评价卖家（需该卖家的已完成订单，一个订单只能评价一次）
     */
    Result add(SellerCommentFormDTO form);

    /**
     * 卖家评价列表（分页）
     */
    Result listBySeller(Long sellerId, Integer page, Integer pageSize);

    /**
     * 卖家评分汇总（平均分 + 评价数）
     */
    Result summaryBySeller(Long sellerId);

    /**
     * 检查当前用户是否可评价该卖家
     * 返回 data=orderId（可评价）或 data=null（不可评价）
     */
    Result checkCanPost(Long sellerId);
}
