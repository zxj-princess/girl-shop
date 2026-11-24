package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.entity.Product;
import com.girlshop.entity.User;
import com.girlshop.service.IProductService;
import com.girlshop.service.IUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/** Seller storefront: public profile fields only, never phone numbers or private account details. */
@RestController
@RequestMapping("/seller")
public class SellerController {
    @Resource private IUserService users;
    @Resource private com.girlshop.service.PlatformImprovementService improvements;
    @GetMapping("/{id}/credit") public Result credit(@PathVariable long id){return improvements.credit(id);}
    @Resource private IProductService products;
    @GetMapping("/{id}")
    public Result profile(@PathVariable Long id) {
        User user=users.getById(id);
        if(user==null)return Result.fail("卖家不存在");
        Map<String,Object> profile=new LinkedHashMap<>();
        profile.put("id",user.getId());profile.put("nickName",user.getNickName());profile.put("icon",user.getIcon());
        profile.put("productCount",products.count(available(id)));
        return Result.ok(profile);
    }
    @GetMapping("/{id}/products")
    public Result products(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize) {
        if(users.getById(id)==null)return Result.fail("卖家不存在");
        Page<Product> result=products.page(new Page<>(Math.max(1,page),Math.max(1,Math.min(50,pageSize))),
                available(id).orderByDesc(Product::getCreateTime).orderByDesc(Product::getId));
        return Result.ok(result.getRecords(),result.getTotal());
    }
    private LambdaQueryWrapper<Product> available(Long id) {
        return new LambdaQueryWrapper<Product>().eq(Product::getUserId,id).eq(Product::getStatus,1).gt(Product::getStock,0);
    }
}
