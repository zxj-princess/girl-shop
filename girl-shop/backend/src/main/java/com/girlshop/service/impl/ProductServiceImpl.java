package com.girlshop.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.girlshop.config.MqConstants;
import com.girlshop.dto.FlashSaleFormDTO;
import com.girlshop.dto.NewProductNoticeMessage;
import com.girlshop.dto.ProductFormDTO;
import com.girlshop.dto.Result;
import com.girlshop.entity.Category;
import com.girlshop.entity.FlashSale;
import com.girlshop.entity.Product;
import com.girlshop.entity.User;
import com.girlshop.mapper.FlashSaleMapper;
import com.girlshop.mapper.ProductMapper;
// FlashSaleMapper 用于捡漏库存表落库
import com.girlshop.service.ICategoryService;
import com.girlshop.service.IProductService;
import com.girlshop.service.IUserService;
import com.girlshop.utils.CacheClient;
import com.girlshop.utils.SensitiveWordFilter;
import com.girlshop.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.girlshop.utils.RedisConstants.*;
import static com.girlshop.utils.SystemConstants.*;

/**
 * 商品服务实现类
 * - 发布商品、编辑、上下架
 * - 搜索筛选排序（分页）
 * - 详情缓存：穿透（空值缓存）、击穿（逻辑过期+互斥锁重建）、雪崩（TTL随机）
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Resource private com.girlshop.service.CacheEvictionService evictions;

    @Resource
    private IUserService userService;

    @Resource
    private ICategoryService categoryService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private FlashSaleMapper flashSaleMapper;
    @Resource
    private com.girlshop.mapper.OrderMapper orderMapper;
    @Resource private com.girlshop.service.UploadAssetService assets;
    @Resource private com.girlshop.service.FlashRequestService requests;

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource private com.girlshop.service.NoticeDeliveryService noticeDelivery;

    @Resource private com.girlshop.service.ProductMetricsService metrics;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result publish(ProductFormDTO form) {
        validateForm(form);
        // 1. 敏感词风控
        if (sensitiveWordFilter.contains(form.getTitle()) || sensitiveWordFilter.contains(form.getDescription())) {
            return Result.fail("内容包含敏感词，请修改后重试");
        }
        // 2. 构建商品
        Product product = BeanUtil.copyProperties(form, Product.class);
        product.setUserId(UserHolder.getUser().getId());
        product.setImages(form.getImages() == null ? "" : String.join(",", form.getImages()));
        product.setStatus(1);
        product.setIsFlash(0);
        product.setStock(1);
        product.setSold(0);
        product.setViews(0);
        // 3. 保存
        save(product);
        // 4. 事务提交后推送上新通知，回滚的商品不发送通知。
        String firstImage = "";
        String imgs = product.getImages();
        if (imgs != null && !imgs.isEmpty()) {
            firstImage = imgs.split(",")[0].trim();
        }
        NewProductNoticeMessage notice=new NewProductNoticeMessage(product.getUserId(),product.getId(),product.getTitle(),firstImage);
        noticeDelivery.enqueue(notice);
        return Result.ok(product.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result updateProduct(Long id, ProductFormDTO form) {
        Product exist = getById(id);
        if (exist == null || !exist.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("商品不存在或无权操作");
        }
        validateForm(form);
        if (sensitiveWordFilter.contains(form.getTitle()) || sensitiveWordFilter.contains(form.getDescription())) {
            return Result.fail("内容包含敏感词，请修改后重试");
        }
        Product product = BeanUtil.copyProperties(form, Product.class);
        product.setId(id);
        product.setImages(form.getImages() == null ? "" : String.join(",", form.getImages()));
        updateById(product);
        // 删除缓存
        evictions.enqueue(id);
        return Result.ok();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result setStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) return Result.fail("只允许上架或下架");
        Product exist = getById(id);
        if (exist == null || !exist.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("商品不存在或无权操作");
        }
        if (status == 1 && (exist.getStock() == null || exist.getStock() < 1)) return Result.fail("库存不足，不能上架");
        boolean changed = update().eq("id", id).gt(status == 1, "stock", 0).set("status", status).update();
        if (!changed) return Result.fail("商品状态已变化");
        evictions.enqueue(id);
        return Result.ok();
    }

    @Override
    public Result listProducts(Integer page, Integer pageSize, Long categoryId, String brand,
                               String size, Integer condition, String keyword, String orderBy) {
        int current = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<Product> p = new Page<>(current, ps);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(StrUtil.isNotBlank(brand), Product::getBrand, brand)
                .eq(StrUtil.isNotBlank(size), Product::getSize, size)
                .eq(condition != null, Product::getCondition, condition);
        if (StrUtil.isNotBlank(keyword)) {
            String normalized=keyword.trim().replaceAll("[\\s　]+", " ").replace("温柔风","温柔");
            java.util.List<String> terms=new java.util.ArrayList<>();
            for(String term:normalized.split(" ")) {
                java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("上衣|外套|连衣裙|裙装|裤装|鞋靴|配饰").matcher(term);
                if(matcher.find() && matcher.start()>0){terms.add(term.substring(0,matcher.start()));terms.add(term.substring(matcher.start()));}else terms.add(term);
            }
            for(String term:terms){java.util.List<String> aliases=com.girlshop.utils.SearchTerms.aliases(term);wrapper.and(q->{for(String alias:aliases)q.or(x->x.like(Product::getTitle,alias).or().like(Product::getBrand,alias).or().like(Product::getDescription,alias));});}
        }
        // 排序：new 最新(默认,发布时间倒序) / sold 销量(销量倒序) / priceAsc 价格升 / priceDesc 价格降
        if ("relevance".equals(orderBy)&&StrUtil.isNotBlank(keyword)) {
            String value=keyword.trim();if(value.length()>100)value=value.substring(0,100);
            StringBuilder hex=new StringBuilder();for(byte b:value.getBytes(java.nio.charset.StandardCharsets.UTF_8))hex.append(String.format("%02x",b&255));
            // Only hexadecimal bytes enter the fixed ORDER BY expression; user text cannot introduce SQL syntax.
            wrapper.last("ORDER BY CASE WHEN LOCATE(UNHEX('"+hex+"'),title)>0 THEN 0 ELSE 1 END,create_time DESC,id DESC");
        } else if ("sold".equals(orderBy)) {
            wrapper.orderByDesc(Product::getSold).orderByDesc(Product::getCreateTime);
        } else if ("priceAsc".equals(orderBy)) {
            wrapper.orderByAsc(Product::getPrice).orderByDesc(Product::getCreateTime);
        } else if ("priceDesc".equals(orderBy)) {
            wrapper.orderByDesc(Product::getPrice).orderByDesc(Product::getCreateTime);
        } else {
            wrapper.orderByDesc(Product::getCreateTime);
        }
        page(p, wrapper);
        enrich(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result detail(Long id) {
        // 判断是否为捡漏热商品（击穿防护走逻辑过期）
        boolean isFlash = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(FLASH_LIST_KEY, id.toString()));
        long ttl = CACHE_PRODUCT_TTL + ThreadLocalRandom.current().nextLong(5); // 雪崩：TTL 随机
        Product product;
        if (isFlash) {
            product = cacheClient.queryWithLogicalExpire(CACHE_FLASH_PRODUCT_KEY, id, Product.class,
                    this::dbQueryById, ttl, TimeUnit.MINUTES);
            if (product == null) {
                // 逻辑过期缓存未命中，回源并建立缓存
                product = dbQueryById(id);
                if (product == null) {
                    return Result.fail("商品不存在");
                }
                cacheClient.setWithLogicalExpire(CACHE_FLASH_PRODUCT_KEY + id, product, ttl, TimeUnit.MINUTES);
            }
        } else {
            // 穿透防护：空值缓存
            product = cacheClient.queryWithPassThrough(CACHE_PRODUCT_KEY, id, Product.class,
                    this::dbQueryById, ttl, TimeUnit.MINUTES);
        }
        if (product == null) {
            return Result.fail("商品不存在");
        }
        enrichOne(product);
        // Cached descriptions must not freeze live counters; include this successful visit in the response.
        Map<String,Object> current=metrics.recordView(id);
        product.setViews(((Number)current.get("views")).intValue());
        product.setFavoriteCount(((Number)current.get("favoriteCount")).longValue());
        return Result.ok(product);
    }

    @Override
    public Result myList() {
        Long userId = UserHolder.getUser().getId();
        List<Product> list = query().eq("user_id", userId).orderByDesc("create_time").list();
        enrich(list);
        return Result.ok(list);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result setFlash(Long id, FlashSaleFormDTO form) {
        if (form == null || form.getFlashPrice() == null || form.getFlashPrice().signum() <= 0
                || form.getFlashPrice().scale() > 2 || form.getBeginTime() == null || form.getEndTime() == null
                || !form.getEndTime().isAfter(form.getBeginTime()) || !form.getEndTime().isAfter(java.time.LocalDateTime.now()))
            return Result.fail("请填写有效的捡漏价格和活动时间");
        Product product = baseMapper.lockById(id);
        if (product == null || !product.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("商品不存在或无权操作");
        }
        if (product.getStatus() != 1) {
            return Result.fail("仅上架中的商品可设置捡漏");
        }
        if (form.getStock() == null || form.getStock() < 1 || form.getStock() > 10000) {
            return Result.fail("库存必须大于0");
        }
        if (orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.girlshop.entity.Order>()
                .eq("product_id", id).in("status", 0, 1, 2)) > 0)
            return Result.fail("商品有进行中的订单，不能重设捡漏库存");
        if (form.getFlashPrice().compareTo(product.getPrice()) > 0) return Result.fail("捡漏价不能高于原售价");
        if (requests.pendingForProduct(id)) return Result.fail("有捡漏请求正在处理，不能重设库存");
        // 更新商品捡漏字段
        product.setIsFlash(1);
        product.setStock(form.getStock());
        product.setFlashPrice(form.getFlashPrice());
        product.setFlashBeginTime(form.getBeginTime());
        product.setFlashEndTime(form.getEndTime());
        updateById(product);
        // 落库捡漏表
        FlashSale flashSale = new FlashSale();
        flashSale.setId(id);
        flashSale.setStock(form.getStock());
        flashSale.setBeginTime(form.getBeginTime());
        flashSale.setEndTime(form.getEndTime());
        // 保存或更新捡漏库存表（主键与商品 id 一致）
        if (flashSaleMapper.selectById(id) == null) {
            flashSaleMapper.insert(flashSale);
        } else {
            flashSaleMapper.updateById(flashSale);
        }
        evictions.enqueue(id);
        return Result.ok();
    }

    @Override
    public Result flashList() {
        List<Product> list = query().eq("is_flash", 1).eq("status", 1)
                .orderByAsc("flash_begin_time").list();
        enrich(list);
        return Result.ok(list);
    }

    @Override
    public Product getProductById(Long id) {
        return getById(id);
    }

    /**
     * 回源查询 DB
     */
    private Product dbQueryById(Long id) {
        Product product = getById(id);
        if (product != null) {
            enrichOne(product);
        }
        return product;
    }

    /**
     * 批量填充分类名与卖家信息
     */
    private void enrich(List<Product> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 分类 map
        Map<Long, String> categoryMap = categoryService.list().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        // 卖家 map
        List<Long> userIds = list.stream().map(Product::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (Product p : list) {
            p.setCategoryName(categoryMap.get(p.getCategoryId()));
            User u = userMap.get(p.getUserId());
            if (u != null) {
                p.setSellerNickName(u.getNickName());
                p.setSellerIcon(u.getIcon());
            }
        }
    }

    private void validateForm(ProductFormDTO form) {
        if (form == null || form.getTitle() == null || form.getTitle().trim().isEmpty() || form.getTitle().length() > 100
                || form.getCategoryId() == null || categoryService.getById(form.getCategoryId()) == null
                || form.getSize() == null || form.getSize().trim().isEmpty() || form.getSize().length() > 20
                || form.getCondition() == null || form.getCondition() < 1 || form.getCondition() > 7
                || form.getPrice() == null || form.getPrice().signum() <= 0 || form.getPrice().scale() > 2
                || form.getPrice().compareTo(new java.math.BigDecimal("999999.99")) > 0
                || (form.getOriginalPrice() != null && (form.getOriginalPrice().signum() < 0 || form.getOriginalPrice().scale() > 2))
                || (form.getImages() != null && form.getImages().size() > 9))
            throw new IllegalArgumentException("商品标题、分类、尺码、成色或价格不合法");
        if ((form.getMeasurements()!=null && form.getMeasurements().length()>500)
                || (form.getDefects()!=null && form.getDefects().length()>500))
            throw new IllegalArgumentException("实测信息和瑕疵说明最多500字");
        if (sensitiveWordFilter.contains(form.getMeasurements()==null?"":form.getMeasurements())
                || sensitiveWordFilter.contains(form.getDefects()==null?"":form.getDefects()))
            throw new IllegalArgumentException("实测信息或瑕疵说明包含敏感词");
        if (form.getImages() != null) {
            java.util.List<String> uploaded=new java.util.ArrayList<>();
            for (String image : form.getImages()) {
                if (image == null || image.contains(",")) throw new IllegalArgumentException("图片地址不合法");
                Long userId = UserHolder.getUser().getId();
                if (image.startsWith("/imgs/products/" + userId + "/")) {
                    uploaded.add(image);
                } else if (query().eq("user_id", userId).apply("FIND_IN_SET({0}, images)>0", image).count() == 0) {
                    throw new IllegalArgumentException("只能使用自己上传的图片");
                }
            }
            assets.claim(UserHolder.getUser().getId(),uploaded);
        }
    }

    private void enrichOne(Product product) {
        Category category = categoryService.getById(product.getCategoryId());
        if (category != null) {
            product.setCategoryName(category.getName());
        }
        User user = userService.getById(product.getUserId());
        if (user != null) {
            product.setSellerNickName(user.getNickName());
            product.setSellerIcon(user.getIcon());
        }
    }

    }
