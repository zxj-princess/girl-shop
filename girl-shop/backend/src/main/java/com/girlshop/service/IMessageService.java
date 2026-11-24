package com.girlshop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.girlshop.dto.MessageFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Message;

public interface IMessageService extends IService<Message> {

    Result add(MessageFormDTO form);

    Result listByProduct(Long productId);

    Result reply(Long id, String reply);
}