package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.entity.Address;
import com.girlshop.service.IAddressService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 收货地址控制器
 */
@RestController
@RequestMapping("/address")
public class AddressController {

    @Resource
    private IAddressService addressService;

    @GetMapping("/list")
    public Result list() {
        return addressService.listAddress();
    }

    @PostMapping
    public Result save(@RequestBody Address address) {
        return addressService.saveAddress(address);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id, @RequestBody Address address) {
        address.setId(id);
        return addressService.updateAddress(address);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Long id) {
        return addressService.deleteAddress(id);
    }

    @PutMapping("/default/{id}")
    public Result setDefault(@PathVariable("id") Long id) {
        return addressService.setDefault(id);
    }
}