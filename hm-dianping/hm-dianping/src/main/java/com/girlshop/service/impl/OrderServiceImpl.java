package com.girlshop.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.dto.FlashOrderMessage;
import com.girlshop.dto.OrderFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Address;
import com.girlshop.entity.Order;
import com.girlshop.entity.Product;
import com.girlshop.mapper.OrderMapper;
import com.girlshop.service.IAddressService;
import com.girlshop.service.IOrderService;
import com.girlshop.service.IProductService;
import com.girlshop.utils.RedisIdWorker;
import com.girlshop.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单服务实现类
 * - 普通下单（乐观锁防超卖）
 * - 普通与捡漏下单共用数据库事务和商品行锁，旧消息入口保留幂等处理
 * - 状态流转：待付款 0 -> 待发货 1 -> 待收货 2 -> 已完成 3 / 已关闭 4
 * - 买家/卖家双端视图
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Resource
    private IProductService productService;

    @Resource
    private com.girlshop.mapper.ProductMapper productMapper;

    @Resource
    private IAddressService addressService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource private com.girlshop.service.FlashRequestService flashRequests;
    @Resource private com.girlshop.mapper.ReviewMapper reviews;
    @Resource private com.girlshop.service.CacheEvictionService evictions;
    @Resource private com.girlshop.service.TradeExperienceService trade;

    @Override
    @Transactional
    public Result createOrder(OrderFormDTO form) {
        if (form == null) return Result.fail("参数错误");
        return placeOrder(form.getProductId(), form.getAddressId(), false, null, UserHolder.getUser().getId());
    }

    @Override
    @Transactional
    public Result flashOrder(Long productId, Long addressId) {
        return flashRequests.reserve(productId,addressId,UserHolder.getUser().getId());
    }

    private Result placeOrder(Long productId, Long addressId, boolean flash, Long orderId, Long userId) {
        if (productId == null || addressId == null) return Result.fail("请选择商品和收货地址");
        Product product = productMapper.lockById(productId);
        if (product == null) return Result.fail("商品不存在");
        // 锁住商品后进行当前读，普通购买与捡漏共用同一串行化边界。
        if (orderId != null && getById(orderId) != null) return Result.ok(orderId);
        if (product.getStatus() == null || product.getStatus() != 1 || product.getStock() == null || product.getStock() < 1)
            return Result.fail("商品已下架或库存不足");
        if (product.getUserId().equals(userId)) return Result.fail("不能购买自己发布的商品");
        if (flash) {
            LocalDateTime now = LocalDateTime.now();
            if (!Integer.valueOf(1).equals(product.getIsFlash()) || product.getFlashPrice() == null
                    || product.getFlashPrice().signum() <= 0 || product.getFlashBeginTime() == null
                    || product.getFlashEndTime() == null || now.isBefore(product.getFlashBeginTime())
                    || !now.isBefore(product.getFlashEndTime())) return Result.fail("捡漏活动未开始或已结束");
        }
        Order existing = query().eq("buyer_id", userId).eq("product_id", productId)
                .ne("status", 4).last("LIMIT 1 FOR UPDATE").one();
        if (existing != null) return Result.fail("每人每商品限购一单");
        if (flashRequests != null && flashRequests.hasPending(productId,userId)) return Result.fail("已有捡漏订单正在处理");
        Address address = addressService.getById(addressId);
        if (address == null || !userId.equals(address.getUserId())) return Result.fail("收货地址无效");
        boolean deducted = productService.update().setSql("stock = stock - 1")
                .eq("id", productId).eq("status", 1).gt("stock", 0).update();
        if (!deducted) return Result.fail("库存不足");
        productService.update().eq("id", productId).eq("stock", 0).set("status", 2).update();
        Order order = buildOrder(userId, product, address, flash ? product.getFlashPrice() : product.getPrice());
        if (orderId != null) order.setId(orderId);
        if (!save(order)) throw new IllegalStateException("订单保存失败");
        trade.orderEvent(order,"新订单已创建");
        invalidateAfterCommit(productId);
        return Result.ok(order.getId());
    }

    @Override
    @Transactional
    public void createFlashOrder(FlashOrderMessage message) {
        if (message == null || message.getOrderId() == null || message.getUserId() == null)
            throw new IllegalArgumentException("旧消息参数不完整");
        Result result = placeOrder(message.getProductId(), message.getAddressId(), true,
                message.getOrderId(), message.getUserId());
        if (!Boolean.TRUE.equals(result.getSuccess())) throw new IllegalArgumentException(result.getErrorMsg());
    }

    private void invalidateAfterCommit(Long productId) {
        evictions.enqueue(productId);
    }

    @Override
    @Transactional
    public Result pay(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = baseMapper.lockById(id);
        if (order == null || !order.getBuyerId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 0) {
            return Result.fail("订单状态异常，无法支付");
        }
        // 模拟支付：待付款 -> 待发货
        if (!update().eq("id", id).eq("status", 0).set("status", 1).set("pay_time", LocalDateTime.now()).update())
            return Result.fail("订单状态已变化");
        invalidateAfterCommit(order.getProductId());
        // 累加销量
        if (!productService.update().eq("id", order.getProductId()).setSql("sold = sold + 1").update())
            throw new IllegalStateException("销量更新失败");
        trade.orderEvent(order,"订单已付款（模拟支付）");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result ship(Long id) {
        return Result.fail("请填写快递公司和运单号，通过物流发货入口操作");

    }

    @Override
    @Transactional
    public Result receive(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = baseMapper.lockById(id);
        if (order == null || !order.getBuyerId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 2) {
            return Result.fail("订单状态异常，无法确认收货");
        }
        if(trade.activeCase(id))return Result.fail("请先处理售后申请");
        // 待收货 -> 已完成
        if (!update().eq("id", id).eq("status", 2).set("status", 3)
                .set("receive_time", LocalDateTime.now())
                .set("finish_time", LocalDateTime.now()).update()) return Result.fail("订单状态已变化");
        trade.orderEvent(order,"买家已确认收货，可以评价");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result cancel(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = baseMapper.lockById(id);
        if (order == null || (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId))) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            return Result.fail("当前状态不可取消");
        }
        if(trade.activeCase(id))return Result.fail("请通过售后流程处理该订单");
        if(order.getStatus()==1)return Result.fail("已付款订单请申请售后，由卖家处理");
        // 待发货(已付款)订单只有买家可以申请取消；待付款订单买家/卖家均可关闭
        if (order.getStatus() == 1 && !order.getBuyerId().equals(userId)) {
            return Result.fail("已付款订单需买家申请取消");
        }
        // 关闭订单
        if (!update().eq("id", id).eq("status", order.getStatus()).set("status", 4).set("close_time", LocalDateTime.now()).update())
            return Result.fail("订单状态已变化");
        // 取消后商品退回卖家：回补库存并重新上架；已支付订单同时回退销量
        if (!productService.update().setSql("stock = stock + 1").eq("id", order.getProductId()).update())
            throw new IllegalStateException("库存恢复失败");
        productService.update().eq("id", order.getProductId()).eq("status", 2).set("status", 1).update();
        if (order.getStatus() == 1) {
            productService.update().setSql("sold = sold - 1").eq("id", order.getProductId()).gt("sold", 0).update();
        }
        invalidateAfterCommit(order.getProductId());
        flashRequests.orderClosed(id);
        trade.orderEvent(order,"订单已关闭");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result delete(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = baseMapper.lockById(id);
        if (order == null || (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId))) {
            return Result.fail("订单不存在");
        }
        // 仅已完成(3) / 已关闭(4) 的终态订单允许删除，避免进行中的订单被误删
        if (order.getStatus() != 3 && order.getStatus() != 4) {
            return Result.fail("请先取消或完成订单后再删除");
        }
        if(trade.activeCase(id))return Result.fail("售后处理中，暂不能隐藏订单");
        baseMapper.hide(id, userId);
        return Result.ok();
    }

    @Override
    public Result buyList() {
        List<Order> list = query().eq("buyer_id", UserHolder.getUser().getId()).apply("NOT EXISTS (SELECT 1 FROM tb_order_hidden h WHERE h.order_id=tb_order.id AND h.user_id={0})", UserHolder.getUser().getId())
                .orderByDesc("create_time").list();
        fillReviewState(list);
        return Result.ok(list);
    }

    @Override
    public Result sellList() {
        List<Order> list = query().eq("seller_id", UserHolder.getUser().getId()).apply("NOT EXISTS (SELECT 1 FROM tb_order_hidden h WHERE h.order_id=tb_order.id AND h.user_id={0})", UserHolder.getUser().getId())
                .orderByDesc("create_time").list();
        fillReviewState(list);
        return Result.ok(list);
    }

    @Override
    public Result myList(Integer page, Integer pageSize, Integer status) {
        Long userId = UserHolder.getUser().getId();
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        Page<Order> p = new Page<>(current, ps);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        // 买家或卖家 = 当前用户（双端合并视图）
        wrapper.and(w -> w.eq(Order::getBuyerId, userId).or().eq(Order::getSellerId, userId));
        if (status != null) wrapper.eq(Order::getStatus, status);
        wrapper.apply("NOT EXISTS (SELECT 1 FROM tb_order_hidden h WHERE h.order_id=tb_order.id AND h.user_id={0})", userId);
        wrapper.orderByDesc(Order::getCreateTime);
        page(p, wrapper);
        if(p.getTotal()>0 && p.getCurrent()>p.getPages()) {p.setCurrent(p.getPages());page(p,wrapper);}
        fillReviewState(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result detail(Long id) {
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(order);
    }

    @Override
    @Transactional
    public void expireUnpaid(Long id, LocalDateTime cutoff) {
        Order order = baseMapper.lockById(id);
        if (order == null || order.getStatus() != 0 || order.getCreateTime() == null
                || order.getCreateTime().isAfter(cutoff)) return;
        if (!update().eq("id", id).eq("status", 0).le("create_time", cutoff)
                .set("status", 4).set("close_time", LocalDateTime.now()).update()) return;
        if (!productService.update().eq("id", order.getProductId()).setSql("stock = stock + 1").update())
            throw new IllegalStateException("库存恢复失败");
        productService.update().eq("id", order.getProductId()).eq("status", 2).set("status", 1).update();
        invalidateAfterCommit(order.getProductId());
        flashRequests.orderClosed(id);
        trade.orderEvent(order,"订单超时未付款，已关闭");
    }

    private Order buildOrder(Long buyerId, Product product, Address address, java.math.BigDecimal payAmount) {
        Order order = new Order();
        order.setId(redisIdWorker.nextId("order"));
        order.setOrderNo(buildOrderNo());
        order.setBuyerId(buyerId);
        order.setSellerId(product.getUserId());
        order.setProductId(product.getId());
        order.setProductTitle(product.getTitle());
        order.setProductImage(firstImage(product.getImages()));
        order.setPrice(payAmount);
        order.setPayAmount(payAmount);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
        order.setStatus(0);
        return order;
    }
    private void fillReviewState(List<Order> list) {
        if(list.isEmpty())return;
        java.util.Set<Long> reviewedIds=reviews.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.girlshop.entity.Review>()
                .select("order_id").in("order_id",list.stream().map(Order::getId).collect(java.util.stream.Collectors.toList())))
                .stream().map(com.girlshop.entity.Review::getOrderId).collect(java.util.stream.Collectors.toSet());
        Long userId=UserHolder.getUser().getId();
        for(Order o:list){o.setReviewed(reviewedIds.contains(o.getId()));o.setCanReview(o.getBuyerId().equals(userId)&&o.getStatus()==3&&!o.getReviewed());}
    }

    private String buildOrderNo() {
        return "GS" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);
    }

    private String firstImage(String images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        int i = images.indexOf(",");
        return i == -1 ? images : images.substring(0, i);
    }
}
