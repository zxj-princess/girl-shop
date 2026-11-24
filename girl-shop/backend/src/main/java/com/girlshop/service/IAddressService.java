package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.Result;
import com.girlshop.entity.Address;

public interface IAddressService extends IService<Address> {

    Result listAddress();

    Result saveAddress(Address address);

    Result updateAddress(Address address);

    Result deleteAddress(Long id);

    Result setDefault(Long id);
}