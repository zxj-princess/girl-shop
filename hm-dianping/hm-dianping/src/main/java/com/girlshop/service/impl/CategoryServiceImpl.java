package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.entity.Category;
import com.girlshop.mapper.CategoryMapper;
import com.girlshop.service.ICategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类服务实现类
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    @Override
    public Result listCategories() {
        List<Category> list = query().orderByAsc("sort").list();
        return Result.ok(list);
    }
}