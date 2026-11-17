package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.Result;
import com.girlshop.entity.Address;
import com.girlshop.mapper.AddressMapper;
import com.girlshop.service.IAddressService;
import com.girlshop.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收货地址服务实现类
 */
@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements IAddressService {

    @Override
    public Result listAddress() {
        Long userId = UserHolder.getUser().getId();
        List<Address> list = query().eq("user_id", userId)
                .orderByDesc("is_default").orderByDesc("id").list();
        return Result.ok(list);
    }

    @Override
    public Result saveAddress(Address address) {
        Long userId = UserHolder.getUser().getId();
        address.setUserId(userId);
        address.setId(null);
        // 首个地址自动设为默认
        long count = query().eq("user_id", userId).count();
        if (count == 0) {
            address.setIsDefault(1);
        }
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        save(address);
        return Result.ok(address.getId());
    }

    @Override
    public Result updateAddress(Address address) {
        Long userId = UserHolder.getUser().getId();
        Address exist = getById(address.getId());
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.fail("地址不存在");
        }
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        address.setUserId(userId);
        address.setCreateTime(null);
        address.setUpdateTime(null);
        updateById(address);
        return Result.ok();
    }

    @Override
    public Result deleteAddress(Long id) {
        Long userId = UserHolder.getUser().getId();
        Address exist = getById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.fail("地址不存在");
        }
        removeById(id);
        return Result.ok();
    }

    @Override
    public Result setDefault(Long id) {
        Long userId = UserHolder.getUser().getId();
        Address exist = getById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.fail("地址不存在");
        }
        clearDefault(userId);
        update().eq("id", id).set("is_default", 1).update();
        return Result.ok();
    }

    private void clearDefault(Long userId) {
        update().eq("user_id", userId).set("is_default", 0).update();
    }
}