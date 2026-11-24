package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.Result;
import com.girlshop.entity.Favorite;

public interface IFavoriteService extends IService<Favorite> {

    Result add(Long productId);

    Result remove(Long productId);

    Result myList();

    Result status(Long productId);
}