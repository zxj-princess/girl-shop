package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.Result;
import com.girlshop.dto.ReviewFormDTO;
import com.girlshop.entity.Review;

public interface IReviewService extends IService<Review> {

    Result add(ReviewFormDTO form);

    Result listByProduct(Long productId);
}