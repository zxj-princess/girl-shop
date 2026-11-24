package com.girlshop.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.MessageFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Message;
import com.girlshop.entity.Product;
import com.girlshop.entity.User;
import com.girlshop.mapper.MessageMapper;
import com.girlshop.service.IMessageService;
import com.girlshop.service.IProductService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.SensitiveWordFilter;
import com.girlshop.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品留言咨询服务实现类
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    @Resource
    private IProductService productService;

    @Resource
    private IUserService userService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private com.girlshop.service.TradeExperienceService trade;

    @Override
    @Transactional
    public Result add(MessageFormDTO form) {
        if (form == null || form.getProductId() == null || form.getContent() == null) {
            return Result.fail("参数错误");
        }
        String content = form.getContent().trim();
        if (content.isEmpty() || content.length() > 500) return Result.fail("留言不能为空，且不能超过500字");
        if (sensitiveWordFilter.contains(content)) {
            return Result.fail("留言包含敏感词");
        }
        Product product = productService.getById(form.getProductId());
        if (product == null) {
            return Result.fail("商品不存在");
        }
        Message message = new Message();
        message.setProductId(form.getProductId());
        message.setUserId(UserHolder.getUser().getId());
        message.setContent(content);
        if (!save(message)) throw new IllegalStateException("留言保存失败");
        if (!product.getUserId().equals(message.getUserId())) {
            trade.productNotice(product.getUserId(), "商品收到新留言", "《" + product.getTitle() + "》：" + content, product.getId());
        }
        return Result.ok();
    }

    @Override
    public Result listByProduct(Long productId) {
        List<Message> list = query().eq("product_id", productId).orderByAsc("create_time").list();
        enrich(list);
        return Result.ok(list);
    }

    @Override
    @Transactional
    public Result reply(Long id, String reply) {
        if (id == null || reply == null || reply.trim().isEmpty() || reply.trim().length() > 500) return Result.fail("回复不能为空，且不能超过500字");
        reply = reply.trim();
        if (sensitiveWordFilter.contains(reply)) {
            return Result.fail("回复包含敏感词");
        }
        Message message = query().eq("id", id).last("FOR UPDATE").one();
        if (message == null) {
            return Result.fail("留言不存在");
        }
        Product product = productService.getById(message.getProductId());
        if (product == null || !product.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("仅卖家可回复");
        }
        if (reply.equals(message.getReply())) return Result.ok();
        if (!update().eq("id", id).set("reply", reply).set("reply_time", LocalDateTime.now()).update()) throw new IllegalStateException("回复保存失败");
        if (!product.getUserId().equals(message.getUserId())) {
            trade.productNotice(message.getUserId(), "卖家回复了你的留言", "《" + product.getTitle() + "》：" + reply, product.getId());
        }
        return Result.ok();
    }

    private void enrich(List<Message> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Message m : list) {
            User u = userService.getById(m.getUserId());
            if (u != null) {
                m.setNickName(u.getNickName());
                m.setIcon(u.getIcon());
            }
        }
    }
}
