package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.Result;
import com.girlshop.entity.Category;

public interface ICategoryService extends IService<Category> {

    Result listCategories();
}