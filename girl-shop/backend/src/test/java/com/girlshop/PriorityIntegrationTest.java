package com.girlshop;

import com.girlshop.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.girlshop.entity.*;
import com.girlshop.entity.Order;
import com.girlshop.mapper.*;
import com.girlshop.service.*;
import com.girlshop.utils.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.Answers;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in: real MySQL transactions in a fresh database; never seed or clear girl_shop. */
@EnabledIfSystemProperty(named="priority.integration",matches="true")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"platform.workers.enabled=false","flash.worker.enabled=false","spring.rabbitmq.listener.simple.auto-startup=false","orders.expiry-scan-ms=3600000"})
class PriorityIntegrationTest {
    static String database,url,baseUrl,user,password;
    static Path imageRoot;
    static synchronized void prepare() {
        if(database!=null)return;
        try {
            YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yaml"));
            Properties props=yaml.getObject();
            baseUrl=props.getProperty("spring.datasource.url");user=props.getProperty("spring.datasource.username");password=props.getProperty("spring.datasource.password");
            // Read-only diagnosis of the existing application's new table.
            try(Connection c=DriverManager.getConnection(baseUrl,user,password);Statement q=c.createStatement();ResultSet rs=q.executeQuery("SHOW TABLES LIKE 'tb_order_hidden'")) {
                System.out.println("LIVE_DIAGNOSIS: tb_order_hidden="+(rs.next()?"present":"MISSING"));
            }
            database="girl_shop_priority_it_"+System.currentTimeMillis();
            if(!database.matches("girl_shop_priority_it_[0-9]+"))throw new IllegalStateException("Unsafe test database name");
            try(Connection c=DriverManager.getConnection(baseUrl,user,password);Statement q=c.createStatement()) {q.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4");}
            url=baseUrl.replace("/girl_shop?","/"+database+"?");
            DriverManagerDataSource ds=new DriverManagerDataSource(url,user,password);
            for(String resource:Arrays.asList("db/girl-shop.sql","db/new-features.sql")) {
                String sql=new String(new ClassPathResource(resource).getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                sql=sql.replaceAll("(?im)^CREATE DATABASE[^;]+;","").replaceAll("(?im)^USE[^;]+;","");
                new ResourceDatabasePopulator(false,false,"UTF-8",new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(ds);
            }
            imageRoot=Files.createTempDirectory("girl-shop-priority-images-");
        }catch(Exception e){throw new IllegalStateException("无法创建隔离测试数据库",e);}
    }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        prepare();r.add("spring.datasource.url",()->url);r.add("images.root",()->imageRoot.toString());
    }
    @MockBean(answer=Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redis;
    @MockBean RedissonClient redisson;
    @MockBean RabbitTemplate rabbit;
    @MockBean RedisIdWorker ids;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.web.server.LocalServerPort int socketPort;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired FlashRequestService flash;
    @Autowired IOrderService orders;
    @Autowired ProductMapper products;
    @Autowired AddressMapper addresses;
    @Autowired UploadAssetService assets;
    @Autowired IProductService productService;
    @Autowired IReviewService reviews;
    @Autowired com.girlshop.service.CacheEvictionService evictions;
    @Autowired com.girlshop.config.PrioritySchema schema;
    @Autowired IFollowService follows;
    @Autowired TradeExperienceService trade;
    @Autowired IMessageService productMessages;
    @Autowired IFavoriteService favorites;
    @Autowired ProductMetricsService metrics;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired PlatformImprovementService improvements;
    @Autowired NoticeDeliveryService delivery;
    @Autowired IUserNoticeService userNotices;
    @Test void reliableNoticeRollbackAndConsumerDeduplication(){
        NewProductNoticeMessage notice=new NewProductNoticeMessage(20L,2L,"上新","test.png");
        assertThrows(IllegalStateException.class,()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{delivery.enqueue(notice);throw new IllegalStateException();}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_notice_outbox",Integer.class));
        delivery.enqueue(notice);delivery.enqueue(notice);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_notice_outbox",Integer.class));
        jdbc.update("INSERT INTO tb_follow(user_id,follow_user_id) VALUES(10,20)");
        userNotices.createNoticesForFollowers(notice);userNotices.createNoticesForFollowers(notice);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_notice WHERE user_id=10 AND product_id=2",Integer.class));
    }
    @Test void adminPermissionAndAuditedReportHandling() throws Exception {
        assertThrows(IllegalArgumentException.class,()->improvements.dashboard());
        org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","99");
        try{trade.report(json.readTree("{\"targetType\":\"PRODUCT\",\"targetId\":2,\"reason\":\"测试举报\"}"));long report=jdbc.queryForObject("SELECT MAX(id) FROM tb_trade_report",Long.class);login(99);improvements.action("reports",report,json.readTree("{\"action\":\"UPHELD\",\"note\":\"核实并反馈\"}"));assertEquals("UPHELD",jdbc.queryForObject("SELECT status FROM tb_trade_report WHERE id=?",String.class,report));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_admin_audit",Integer.class));assertThrows(IllegalArgumentException.class,()->improvements.action("reports",report,json.readTree("{\"action\":\"UPHELD\",\"note\":\"重复处理\"}")));}finally{org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","");}
    }
    @Test void sellerCreditOnlyExposesAggregateData(){Map<?,?> data=(Map<?,?>)improvements.credit(20).getData();assertTrue(data.containsKey("completedOrders"));assertFalse(data.containsKey("phone"));}
    @Test void priceAlertPreferenceValidation() throws Exception {assertEquals(true,((Map<?,?>)improvements.preferences().getData()).get("priceEnabled"));improvements.preferences(json.readTree("{\"priceEnabled\":false}"));assertEquals(false,((Map<?,?>)improvements.preferences().getData()).get("priceEnabled"));assertThrows(IllegalArgumentException.class,()->improvements.preferences(json.readTree("{\"priceEnabled\":\"false\"}")));}
    @Test void alertIsSentOnceAndPreferenceSuppressesNextDrop() throws Exception {
        metrics.favorite(10,2,true);org.springframework.test.util.ReflectionTestUtils.setField(improvements,"improvementWorkers",true);
        try{jdbc.update("UPDATE tb_product SET price=40 WHERE id=2");improvements.alerts();improvements.alerts();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice WHERE title='收藏商品降价'",Integer.class));improvements.preferences(json.readTree("{\"priceEnabled\":false}"));jdbc.update("UPDATE tb_product SET price=30 WHERE id=2");improvements.alerts();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice WHERE title='收藏商品降价'",Integer.class));}finally{org.springframework.test.util.ReflectionTestUtils.setField(improvements,"improvementWorkers",false);}
    }
    @Test void synonymSearchAndRelevantPagination() {
        products.updateById(products.selectById(2L).setTitle("白色T恤"));
        assertFalse(((List<?>)productService.listProducts(1,12,null,null,null,null,"短袖","relevance").getData()).isEmpty());
    }
    @Test void notificationFailureIsArchivedAndRetryRequiresAdministrator() throws Exception {
        delivery.enqueue(new NewProductNoticeMessage(20L,2L,"上新",""));delivery.failed(2,8,"CONSUMER_FAILED");assertEquals("DEAD",jdbc.queryForObject("SELECT status FROM tb_notice_outbox WHERE product_id=2",String.class));
        assertThrows(IllegalArgumentException.class,()->improvements.action("delivery",2,json.readTree("{\"action\":\"RETRY\",\"note\":\"测试重试\"}")));
        org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","10");try{improvements.action("delivery",2,json.readTree("{\"action\":\"RETRY\",\"note\":\"检查后重试\"}"));assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM tb_notice_outbox WHERE product_id=2",String.class));}finally{org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","");}
    }
    @Test void restrictionBlocksProtectedRequestsAndProtectsAdmin() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","99");
        try{login(99);improvements.action("users",10,json.readTree("{\"action\":\"BAN\",\"note\":\"测试限制\"}"));login(10);org.springframework.mock.web.MockHttpServletResponse response=new org.springframework.mock.web.MockHttpServletResponse();assertFalse(new LoginInterceptor(jdbc).preHandle(new org.springframework.mock.web.MockHttpServletRequest(),response,new Object()));assertEquals(403,response.getStatus());login(99);assertThrows(IllegalArgumentException.class,()->improvements.action("users",99,json.readTree("{\"action\":\"BAN\",\"note\":\"不可限制管理员\"}")));improvements.action("users",10,json.readTree("{\"action\":\"UNBAN\",\"note\":\"测试解除\"}"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_account_restriction",Integer.class));}finally{org.springframework.test.util.ReflectionTestUtils.setField(improvements,"admins","");}
    }
    static AtomicLong sequence=new AtomicLong(900000L);
    @BeforeEach void seed() {
        for(String table:Arrays.asList("tb_ai_field_edit","tb_account_restriction","tb_ai_feedback","tb_admin_audit","tb_notice_consumed","tb_notice_outbox","tb_product_alert","tb_notice_preference"))jdbc.execute("DELETE FROM "+table);
        for(String table:Arrays.asList("tb_message","tb_favorite","tb_trade_notice","tb_private_message","tb_user_block","tb_trade_shipment","tb_after_sale_event","tb_after_sale","tb_trade_report","tb_product_draft"))jdbc.execute("DELETE FROM "+table);
        for(String table:Arrays.asList("tb_cache_eviction","tb_review","tb_order_hidden","tb_flash_request","tb_order","tb_buyer_show","tb_upload_asset","tb_product","tb_address"))jdbc.execute("DELETE FROM "+table);
        when(ids.nextId("order")).thenAnswer(i->sequence.incrementAndGet());
        when(redis.opsForHash().entries("login:token:it-buyer")).thenReturn(new HashMap<Object,Object>(){{put("id","10");put("nickName","测试买家");}});
        when(redis.opsForHash().entries("login:token:it-other")).thenReturn(new HashMap<Object,Object>(){{put("id","99");put("nickName","测试用户");}});
        Product p=new Product().setId(2L).setUserId(20L).setCategoryId(1L).setTitle("隔离测试商品").setSize("M").setCondition(2)
            .setPrice(new BigDecimal("50.00")).setFlashPrice(new BigDecimal("20.00")).setStatus(1).setIsFlash(1).setStock(1).setSold(0)
            .setFlashBeginTime(LocalDateTime.now().minusHours(1)).setFlashEndTime(LocalDateTime.now().plusHours(1));
        products.insert(p);
        addresses.insert(new Address().setId(3L).setUserId(10L).setReceiverName("测试").setReceiverPhone("13800000000").setProvince("省").setCity("市").setDistrict("区").setDetail("测试地址"));
        when(redis.opsForHash().get("login:token:it-buyer","id")).thenReturn("10");
        when(redis.opsForHash().get("login:token:it-seller","id")).thenReturn("20");
        login(10L);
    }
    static void login(long id){UserDTO u=new UserDTO();u.setId(id);UserHolder.saveUser(u);}
    @Test void productCommentsNotifySellerAndRepliesNotifyBuyerWithScopedReads() {
        MessageFormDTO form=new MessageFormDTO();form.setProductId(2L);form.setContent("有腰带吗");
        assertTrue(productMessages.add(form).getSuccess());
        long message=jdbc.queryForObject("SELECT MAX(id) FROM tb_message",Long.class);
        login(20L);
        assertEquals(1,((Number)((Map<?,?>)trade.messageCounts().getData()).get("notices")).intValue());
        Map<?,?> notification=(Map<?,?>)((List<?>)trade.inbox(1).getData()).get(0);
        assertEquals("2",notification.get("productId"));assertTrue(notification.get("content").toString().contains("有腰带吗"));
        long notice=Long.parseLong(notification.get("id").toString());
        login(99L);trade.readInbox("TRADE",notice);
        assertEquals(0,jdbc.queryForObject("SELECT is_read FROM tb_trade_notice WHERE id=?",Integer.class,notice));
        login(20L);trade.readInbox("TRADE",notice);
        assertEquals(0,((Number)((Map<?,?>)trade.messageCounts().getData()).get("notices")).intValue());
        assertTrue(productMessages.reply(message,"有原配腰带").getSuccess());
        assertTrue(productMessages.reply(message,"有原配腰带").getSuccess());
        login(10L);assertEquals(1,((Number)((Map<?,?>)trade.messageCounts().getData()).get("notices")).intValue());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice WHERE user_id=10",Integer.class));
        trade.readInbox("ALL",null);
        assertEquals(0,((Number)((Map<?,?>)trade.messageCounts().getData()).get("notices")).intValue());
        schema.migrate();schema.migrate(); // upgrade remains safe to repeat with existing notices
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice",Integer.class));
    }
    @Test void productCommentsRollbackTogetherAndSelfCommentsDoNotNotify() {
        MessageFormDTO form=new MessageFormDTO();form.setProductId(2L);form.setContent("咨询尺码");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
            assertTrue(productMessages.add(form).getSuccess());status.setRollbackOnly();return null;
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_message",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice",Integer.class));
        login(20L);assertTrue(productMessages.add(form).getSuccess());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice",Integer.class));
        form.setContent(" ");assertFalse(productMessages.add(form).getSuccess());
        long id=jdbc.queryForObject("SELECT MAX(id) FROM tb_message",Long.class);
        login(99L);assertFalse(productMessages.reply(id,"非卖家回复").getSuccess());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice",Integer.class));
    }
    @Test void detailViewsAdvanceEvenWhenDescriptionCacheKeepsZero() {
        Product cached=products.selectById(2L);cached.setViews(0);
        when(redis.opsForSet().isMember(com.girlshop.utils.RedisConstants.FLASH_LIST_KEY,"2")).thenReturn(false);
        String key=com.girlshop.utils.RedisConstants.CACHE_PRODUCT_KEY+"2";
        when(redis.opsForValue().get(key)).thenReturn(cn.hutool.json.JSONUtil.toJsonStr(cached));
        try {
            jdbc.update("UPDATE tb_product SET views=0 WHERE id=2");
            assertEquals(1,((Product)productService.detail(2L).getData()).getViews());
            assertEquals(2,((Product)productService.detail(2L).getData()).getViews());
            assertEquals(2,((Number)metrics.snapshot(2L).get("views")).intValue());
            assertEquals(0L,((Product)productService.detail(2L).getData()).getFavoriteCount());
        } finally {when(redis.opsForValue().get(key)).thenReturn(null);}
    }
    @Test void concurrentViewsAndFavoriteRetriesKeepAccurateTotals()throws Exception {
        jdbc.update("UPDATE tb_product SET views=0 WHERE id=2");
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Map<String,Object>>> visits=new ArrayList<>();for(int i=0;i<12;i++)visits.add(()->metrics.recordView(2L));
            for(Future<Map<String,Object>> visit:pool.invokeAll(visits))visit.get();
            assertEquals(12,((Number)metrics.snapshot(2L).get("views")).intValue());
            List<Callable<Map<String,Object>>> repeated=new ArrayList<>();for(int i=0;i<8;i++)repeated.add(()->metrics.favorite(10L,2L,true));
            for(Future<Map<String,Object>> favorite:pool.invokeAll(repeated))favorite.get();
            assertEquals(1L,((Number)metrics.snapshot(2L).get("favoriteCount")).longValue());
            login(20L);assertEquals(2L,((Number)((Map<?,?>)favorites.add(2L).getData()).get("favoriteCount")).longValue());
            login(10L);assertEquals(1L,((Number)((Map<?,?>)favorites.remove(2L).getData()).get("favoriteCount")).longValue());
            assertEquals(1L,((Number)((Map<?,?>)favorites.remove(2L).getData()).get("favoriteCount")).longValue());
            assertEquals(12,((Number)metrics.snapshot(2L).get("views")).intValue(),"favorite changes must not add visits");
        } finally {pool.shutdownNow();}
    }
    @AfterEach void clear(){UserHolder.removeUser();}
    long reserve(){Result r=flash.reserve(2L,3L,10L);assertTrue(r.getSuccess(),r.getErrorMsg());return Long.parseLong(r.getData().toString());}
    int stock(){return jdbc.queryForObject("SELECT stock FROM tb_product WHERE id=2",Integer.class);}
    @Test void asyncSnapshotAndDuplicateDelivery() {
        long id=reserve();assertEquals(0,stock());assertEquals(0,orders.count());
        assertEquals("PENDING",((Map<?,?>)flash.status(id,10L).getData()).get("status"));
        flash.complete(id);flash.complete(id);assertEquals(1,orders.count());assertEquals(0,stock());
        assertEquals(new BigDecimal("20.00"),orders.getById(id).getPayAmount());
        assertFalse(flash.status(id,99L).getSuccess());
    }
    static class SocketProbe extends org.springframework.web.socket.handler.TextWebSocketHandler {
        final BlockingQueue<String> events=new LinkedBlockingQueue<>();
        final CountDownLatch closed=new CountDownLatch(1);volatile org.springframework.web.socket.CloseStatus status;
        @Override protected void handleTextMessage(org.springframework.web.socket.WebSocketSession s,org.springframework.web.socket.TextMessage m){events.add(m.getPayload());}
        @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession s,org.springframework.web.socket.CloseStatus status){this.status=status;closed.countDown();}
    }
    org.springframework.web.socket.WebSocketSession socket(long user,String token,SocketProbe probe)throws Exception {
        String ticket=UUID.randomUUID().toString();
        doReturn(user+"\n"+token,null).when(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),eq(Collections.singletonList("ws:ticket:"+ticket)));
        org.springframework.web.socket.WebSocketHttpHeaders headers=new org.springframework.web.socket.WebSocketHttpHeaders();headers.setOrigin("http://localhost:8090");
        org.springframework.web.socket.WebSocketSession session=new org.springframework.web.socket.client.standard.StandardWebSocketClient().doHandshake(probe,headers,java.net.URI.create("ws://127.0.0.1:"+socketPort+"/ws/messages?ticket="+ticket)).get(5,TimeUnit.SECONDS);
        // Finish server-side authentication before another test ticket is stubbed on shared Redis.
        String ready=probe.events.poll(5,TimeUnit.SECONDS);if(ready==null){session.close();throw new IllegalStateException("WebSocket READY timeout");}probe.events.add(ready);return session;
    }
    @Test void realSocketsOnlyNotifyAfterCommitAndNeverAfterRollback()throws Exception {
        chatUsers();SocketProbe buyer=new SocketProbe(),seller=new SocketProbe();
        try(org.springframework.web.socket.WebSocketSession a=socket(10L,"it-buyer",buyer);org.springframework.web.socket.WebSocketSession b=socket(20L,"it-seller",seller)){
            assertTrue(buyer.events.poll(3,TimeUnit.SECONDS).contains("READY"));assertTrue(seller.events.poll(3,TimeUnit.SECONDS).contains("READY"));
            org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            assertThrows(IllegalStateException.class,()->tx.execute(status->{try{trade.message(json.readTree("{\"peerId\":20,\"content\":\"回滚消息\"}"));}catch(Exception e){throw new IllegalStateException(e);}throw new IllegalStateException("rollback");}));
            assertNull(seller.events.poll(300,TimeUnit.MILLISECONDS));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message",Integer.class));
            Result saved=trade.message(json.readTree("{\"peerId\":20,\"content\":\"提交消息\",\"clientMessageId\":\"test-socket-message\"}"));
            assertTrue(seller.events.poll(3,TimeUnit.SECONDS).contains("MESSAGE_CHANGED"));assertTrue(buyer.events.poll(3,TimeUnit.SECONDS).contains("MESSAGE_CHANGED"));
            String id=((Map<?,?>)saved.getData()).get("messageId").toString();login(20L);trade.readMessages(10L,json.readTree("{\"ids\":[\""+id+"\"]}"));
            assertTrue(buyer.events.poll(3,TimeUnit.SECONDS).contains("READ_CHANGED"));
            assertTrue(seller.events.poll(3,TimeUnit.SECONDS).contains("STATE_CHANGED"));
        }
    }
    @Test void messageClientIdsAreIdempotentAndContentBound()throws Exception {
        chatUsers();JsonNode body=json.readTree("{\"peerId\":20,\"content\":\"网络重试\",\"clientMessageId\":\"same-client-id-123\"}");
        Result first=trade.message(body),retry=trade.message(body);assertEquals(first.getData(),retry.getData());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message",Integer.class));
        assertThrows(IllegalArgumentException.class,()->trade.message(json.readTree("{\"peerId\":20,\"content\":\"另一条内容\",\"clientMessageId\":\"same-client-id-123\"}")));
    }
    @Test void socketsRejectMissingCredentialsAndCrossOrigin()throws Exception {
        org.springframework.web.socket.client.standard.StandardWebSocketClient client=new org.springframework.web.socket.client.standard.StandardWebSocketClient();
        assertThrows(Exception.class,()->client.doHandshake(new SocketProbe(),"ws://127.0.0.1:"+socketPort+"/ws/messages").get(3,TimeUnit.SECONDS));
        String ticket=UUID.randomUUID().toString();doReturn("10\nit-buyer",null).when(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),eq(Collections.singletonList("ws:ticket:"+ticket)));
        org.springframework.web.socket.WebSocketHttpHeaders headers=new org.springframework.web.socket.WebSocketHttpHeaders();headers.setOrigin("https://untrusted.invalid");
        assertThrows(Exception.class,()->client.doHandshake(new SocketProbe(),headers,java.net.URI.create("ws://127.0.0.1:"+socketPort+"/ws/messages?ticket="+ticket)).get(3,TimeUnit.SECONDS));
    }
    @Test void socketDoesNotAcceptSubscriptionsAndChecksSessionRevocation()throws Exception {
        SocketProbe unauthorized=new SocketProbe();try(org.springframework.web.socket.WebSocketSession session=socket(10L,"it-buyer",unauthorized)){
            unauthorized.events.poll(3,TimeUnit.SECONDS);session.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"SUBSCRIBE\",\"userId\":20}"));assertTrue(unauthorized.closed.await(3,TimeUnit.SECONDS));assertEquals(1003,unauthorized.status.getCode());
        }
        SocketProbe revoked=new SocketProbe();try(org.springframework.web.socket.WebSocketSession session=socket(10L,"it-buyer",revoked)){
            revoked.events.poll(3,TimeUnit.SECONDS);when(redis.opsForHash().get("login:token:it-buyer","id")).thenReturn(null);session.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"PING\"}"));assertTrue(revoked.closed.await(3,TimeUnit.SECONDS));assertEquals(1008,revoked.status.getCode());
        }
    }
    @Test void incrementalReadsHaveNoSideEffectsAndReadIdsAreScoped()throws Exception {
        for(int i=0;i<55;i++)jdbc.update("INSERT INTO tb_private_message(sender_id,receiver_id,content) VALUES(20,10,?)","补拉"+i);
        Map<?,?> latest=(Map<?,?>)trade.conversationPage(20L,0,0).getData();List<?> list=(List<?>)latest.get("messages");assertEquals(50,list.size());assertEquals(true,latest.get("hasMore"));
        assertEquals(55,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE is_read=0",Integer.class));
        String last=((Map<?,?>)list.get(49)).get("id").toString();jdbc.update("INSERT INTO tb_private_message(sender_id,receiver_id,content) VALUES(20,10,'离线新消息')");
        Map<?,?> delta=(Map<?,?>)trade.conversationPage(20L,0,Long.parseLong(last)).getData();assertEquals(1,((List<?>)delta.get("messages")).size());
        login(99L);trade.readMessages(20L,json.readTree("{\"ids\":[\""+last+"\"]}"));assertEquals(56,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE is_read=0",Integer.class));
        login(10L);trade.readMessages(20L,json.readTree("{\"ids\":[\""+last+"\"]}"));assertEquals(55,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE is_read=0",Integer.class));
    }
    @Test void tradeNoticesAreScopedAndCannotMarkOthersRead() {
        trade.notice(10L,"买家消息","测试",null);trade.notice(20L,"卖家消息","测试",null);
        assertEquals(1,((Number)((Map<?,?>)trade.unread().getData()).get("notices")).intValue());
        long other=jdbc.queryForObject("SELECT id FROM tb_trade_notice WHERE user_id=20",Long.class);
        trade.readNotice(other);assertEquals(0,jdbc.queryForObject("SELECT is_read FROM tb_trade_notice WHERE id=?",Integer.class,other));
        trade.readNotice(null);assertEquals(0,((Number)((Map<?,?>)trade.unread().getData()).get("notices")).intValue());
    }
    private void chatUsers(){jdbc.update("INSERT IGNORE INTO tb_user(id,phone,nick_name) VALUES(10,'13800000010','买家'),(20,'13800000020','卖家'),(99,'13800000099','其他')");}
    @Test void messageCenterProfilesAndContactPreviews()throws Exception {
        chatUsers();Map<?,?> first=(Map<?,?>)trade.peer(20L).getData();assertEquals(jdbc.queryForObject("SELECT nick_name FROM tb_user WHERE id=20",String.class),first.get("nickName"));assertFalse(first.containsKey("phone"));assertFalse(first.containsKey("password"));
        assertThrows(IllegalArgumentException.class,()->trade.peer(999999L));
        trade.message(json.readTree("{\"peerId\":20,\"content\":\"首条咨询\"}"));
        login(20L);List<?> list=(List<?>)trade.contacts().getData();assertEquals(1,list.size());Map<?,?> contact=(Map<?,?>)list.get(0);assertEquals("买家",contact.get("nickName"));assertEquals("首条咨询",contact.get("lastMessage"));assertEquals(1,((Number)contact.get("unread")).intValue());assertNotNull(contact.get("lastTime"));
        trade.block(10L,true);login(10L);assertEquals(true,((Map<?,?>)trade.peer(20L).getData()).get("cannotSend"));assertEquals(false,((Map<?,?>)trade.peer(20L).getData()).get("blocked"));
    }
    @Test void unifiedInboxHasTypesAndReadOwnership() {
        trade.notice(10L,"订单通知","测试",null);trade.notice(20L,"其他用户","私有",null);
        jdbc.update("INSERT INTO tb_user_notice(user_id,seller_id,product_id,title,is_read) VALUES(10,20,2,'卖家上新商品',0),(20,10,2,'其他用户上新',0)");
        List<?> inbox=(List<?>)trade.inbox(1).getData();assertEquals(2,inbox.size());Set<Object> kinds=new HashSet<>();for(Object n:inbox)kinds.add(((Map<?,?>)n).get("kind"));assertTrue(kinds.containsAll(Arrays.asList("TRADE","NEW")));
        Map<?,?> counts=(Map<?,?>)trade.messageCounts().getData();assertEquals(1,((Number)counts.get("notices")).intValue());assertEquals(1,((Number)counts.get("newProducts")).intValue());
        long foreign=jdbc.queryForObject("SELECT id FROM tb_user_notice WHERE user_id=20",Long.class);trade.readInbox("NEW",foreign);assertEquals(0,jdbc.queryForObject("SELECT is_read FROM tb_user_notice WHERE id=?",Integer.class,foreign));
        trade.readInbox("ALL",null);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_notice WHERE user_id=10 AND is_read=0",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_notice WHERE user_id=20 AND is_read=0",Integer.class));
        jdbc.update("DELETE FROM tb_user_notice");
    }
    @Test void conversationPaginationMarksOnlyVisibleMessagesRead() {
        for(int i=0;i<55;i++)jdbc.update("INSERT INTO tb_private_message(sender_id,receiver_id,content) VALUES(20,10,?)","历史消息"+i);
        Result latest=trade.conversation(20L,0);List<?> messages=(List<?>)latest.getData();assertEquals(50,messages.size());assertEquals(55L,latest.getTotal());
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE receiver_id=10 AND is_read=0",Integer.class));
        long first=Long.parseLong(((Map<?,?>)messages.get(0)).get("id").toString());Result older=trade.conversation(20L,first);assertEquals(5,((List<?>)older.getData()).size());assertEquals(5L,older.getTotal());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE receiver_id=10 AND is_read=0",Integer.class));
    }
    @Test void productCardsAutoAttachOnceAcrossFullHistoryButAllowExplicitResend()throws Exception {
        chatUsers();trade.message(json.readTree("{\"peerId\":20,\"content\":\"首次咨询\",\"productId\":2,\"autoProduct\":true}"));
        assertEquals(true,((Map<?,?>)trade.productContext(20L,2L).getData()).get("sent"));
        for(int i=0;i<55;i++)jdbc.update("INSERT INTO tb_private_message(sender_id,receiver_id,content) VALUES(20,10,?)","后续消息"+i);
        trade.message(json.readTree("{\"peerId\":20,\"content\":\"刷新后继续问\",\"productId\":2,\"autoProduct\":true}"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE product_id=2",Integer.class));
        trade.message(json.readTree("{\"peerId\":20,\"content\":\"\",\"productId\":2,\"autoProduct\":false}"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE product_id=2",Integer.class));
        assertEquals("商品咨询",((Map<?,?>)((List<?>)trade.contacts().getData()).get(0)).get("lastMessage"));
        assertThrows(IllegalArgumentException.class,()->trade.message(json.readTree("{\"peerId\":20,\"content\":\" \"}")));
        assertThrows(IllegalArgumentException.class,()->trade.productContext(99L,2L));
    }
    @Test void concurrentAutomaticCardsDoNotDuplicate()throws Exception {
        chatUsers();ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            List<Future<Result>> tasks=new ArrayList<>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{login(10L);try{return trade.message(json.readTree("{\"peerId\":20,\"content\":\"并发咨询\",\"productId\":2,\"autoProduct\":true}"));}finally{UserHolder.removeUser();}}));
            for(Future<Result> task:tasks)assertTrue(task.get(20,TimeUnit.SECONDS).getSuccess());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE product_id=2",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void bothSidesResolveSellerAndCanSendTheirProducts()throws Exception {
        chatUsers();assertNull(((Map<?,?>)trade.pickerOwner(20L,0).getData()).get("ownerId"));
        assertEquals("20",((Map<?,?>)trade.pickerOwner(20L,2L).getData()).get("ownerId"));
        trade.message(json.readTree("{\"peerId\":20,\"productId\":2}"));
        assertEquals("20",((Map<?,?>)trade.pickerOwner(20L,0).getData()).get("ownerId"));
        login(20L);assertEquals("20",((Map<?,?>)trade.pickerOwner(10L,0).getData()).get("ownerId"));
        assertEquals(1L,trade.pickerProducts(10L,20L,1,1).getTotal());
        trade.message(json.readTree("{\"peerId\":10,\"productId\":2}"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE product_id=2",Integer.class));
        assertThrows(IllegalArgumentException.class,()->trade.pickerProducts(10L,99L,1,1));
        products.insert(new Product().setId(8L).setUserId(10L).setCategoryId(1L).setTitle("买家的商品").setPrice(BigDecimal.TEN).setStatus(1).setStock(1));
        assertEquals("10",((Map<?,?>)trade.pickerOwner(10L,8L).getData()).get("ownerId"));
        assertThrows(IllegalArgumentException.class,()->trade.pickerOwner(99L,8L));
    }
    @Test void pickerProductsArePeerScopedAndExcludeDrafts()throws Exception {
        chatUsers();Product own=new Product().setId(8L).setUserId(10L).setCategoryId(1L).setTitle("我的商品").setPrice(new BigDecimal("30")).setStatus(1).setStock(1);products.insert(own);
        products.insert(new Product().setId(9L).setUserId(20L).setCategoryId(1L).setTitle("卖家已下架").setPrice(new BigDecimal("25")).setStatus(0).setStock(1));
        trade.saveDraft(null,json.readTree("{\"title\":\"不应公开的草稿\"}"));
        assertEquals(1L,trade.peerProducts(20L,1,1).getTotal());assertEquals(2L,trade.peerProducts(20L,1,-1).getTotal());assertEquals(1L,trade.peerProducts(20L,1,0).getTotal());
        for(Object p:(List<?>)trade.peerProducts(20L,1,-1).getData())assertNotEquals("8",((Map<?,?>)p).get("id"));
        assertThrows(IllegalArgumentException.class,()->trade.peerProducts(20L,1,9));
    }
    @Test void unifiedInboxSupportsHistoricalColumnCollations() {
        String collation=jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_trade_notice' AND COLUMN_NAME='content'",String.class);
        assertTrue(collation.matches("[A-Za-z0-9_]+"));
        try{
            jdbc.execute("ALTER TABLE tb_trade_notice MODIFY content VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL");
            trade.notice(10L,"交易消息","兼容旧数据",null);assertTrue(trade.inbox(1).getSuccess());
        }finally{jdbc.execute("ALTER TABLE tb_trade_notice MODIFY content VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE "+collation+" NOT NULL");}
    }
    @Test void privateMessagesCardsBlockingAndReadIsolation()throws Exception {
        chatUsers();trade.message(json.readTree("{\"peerId\":20,\"productId\":2,\"content\":\"请问商品尺寸？\"}"));
        assertEquals(1,((List<?>)trade.conversation(20L,0).getData()).size());
        login(99L);assertTrue(((List<?>)trade.conversation(10L,0).getData()).isEmpty());
        login(20L);assertEquals(1,((Number)((Map<?,?>)trade.unread().getData()).get("messages")).intValue());trade.conversation(10L,0);
        assertEquals(0,((Number)((Map<?,?>)trade.unread().getData()).get("messages")).intValue());
        trade.block(10L,true);login(10L);assertThrows(IllegalArgumentException.class,()->trade.message(json.readTree("{\"peerId\":20,\"content\":\"再次发送\"}")));
        login(20L);trade.block(10L,false);login(10L);assertTrue(trade.message(json.readTree("{\"peerId\":20,\"content\":\"现在可以发送\"}")).getSuccess());
    }
    @Test void shipmentRequiresSellerValidTrackingAndNoActiveCase()throws Exception {
        long id=reserve();flash.complete(id);orders.pay(id);
        assertThrows(IllegalArgumentException.class,()->trade.ship(id,json.readTree("{\"carrier\":\"快递\",\"trackingNo\":\"AB12345\"}")));
        login(20L);assertThrows(IllegalArgumentException.class,()->trade.ship(id,json.readTree("{\"carrier\":\"快递\",\"trackingNo\":\"x\"}")));
        assertEquals(1,orders.getById(id).getStatus());assertFalse(orders.ship(id).getSuccess());
        trade.ship(id,json.readTree("{\"carrier\":\"测试快递\",\"trackingNo\":\"AB12345\"}"));
        assertThrows(IllegalArgumentException.class,()->trade.ship(id,json.readTree("{\"carrier\":\"测试快递\",\"trackingNo\":\"AB12345\"}")));
        login(99L);assertThrows(IllegalArgumentException.class,()->trade.shipment(id));
        login(10L);assertEquals(1,((List<?>)trade.shipment(id).getData()).size());
    }
    @Test void paidAfterSaleClosesOnlyOnceAndFreezesShipment()throws Exception {
        long id=reserve();flash.complete(id);orders.pay(id);assertFalse(orders.cancel(id).getSuccess());
        long cid=Long.parseLong(trade.apply(id,json.readTree("{\"reason\":\"拍错商品\"}")).getData().toString());
        assertThrows(IllegalArgumentException.class,()->trade.apply(id,json.readTree("{\"reason\":\"重复申请\"}")));
        login(20L);assertThrows(IllegalArgumentException.class,()->trade.ship(id,json.readTree("{\"carrier\":\"测试\",\"trackingNo\":\"ABC123\"}")));
        trade.resolve(cid,json.readTree("{\"action\":\"ACCEPT\",\"note\":\"同意\"}"));assertEquals(1,stock());assertEquals(4,orders.getById(id).getStatus());
        assertThrows(IllegalArgumentException.class,()->trade.resolve(cid,json.readTree("{\"action\":\"ACCEPT\",\"note\":\"重复同意\"}")));assertEquals(1,stock());
        assertEquals(2,((List<?>)trade.events(cid).getData()).size());login(99L);assertThrows(IllegalArgumentException.class,()->trade.events(cid));
    }
    @Test void shippedAfterSaleRequiresReturnBeforeStockRestoration()throws Exception {
        long id=reserve();flash.complete(id);orders.pay(id);login(20L);trade.ship(id,json.readTree("{\"carrier\":\"快递\",\"trackingNo\":\"SHIP123\"}"));login(10L);
        long cid=Long.parseLong(trade.apply(id,json.readTree("{\"reason\":\"存在瑕疵\"}")).getData().toString());assertFalse(orders.receive(id).getSuccess());
        login(20L);trade.resolve(cid,json.readTree("{\"action\":\"ACCEPT\",\"note\":\"同意退货\"}"));assertEquals(0,stock());
        assertThrows(IllegalArgumentException.class,()->trade.resolve(cid,json.readTree("{\"action\":\"RECEIVED_RETURN\",\"note\":\"提前确认\"}")));
        login(10L);trade.resolve(cid,json.readTree("{\"action\":\"RETURN\",\"note\":\"已寄回\",\"carrier\":\"退货快递\",\"trackingNo\":\"RETURN123\"}"));
        login(20L);trade.resolve(cid,json.readTree("{\"action\":\"RECEIVED_RETURN\",\"note\":\"收到退货\"}"));assertEquals(1,stock());assertEquals(4,orders.getById(id).getStatus());
    }
    @Test void rejectedAfterSaleCanBeDisputedButNotResolvedByBuyer()throws Exception {
        long id=reserve();flash.complete(id);orders.pay(id);long cid=Long.parseLong(trade.apply(id,json.readTree("{\"reason\":\"商品问题\"}")).getData().toString());
        assertThrows(IllegalArgumentException.class,()->trade.resolve(cid,json.readTree("{\"action\":\"ACCEPT\",\"note\":\"买家越权\"}")));
        login(20L);trade.resolve(cid,json.readTree("{\"action\":\"REJECT\",\"note\":\"请补充凭证\"}"));login(10L);trade.resolve(cid,json.readTree("{\"action\":\"DISPUTE\",\"note\":\"请求人工核查\"}"));assertEquals("DISPUTED",jdbc.queryForObject("SELECT status FROM tb_after_sale WHERE id=?",String.class,cid));assertEquals(0,stock());
    }
    @Test void draftOwnershipAndUploadedEvidenceAreValidated()throws Exception {
        String img=upload();long id=Long.parseLong(trade.saveDraft(null,json.readTree("{\"title\":\"未完成商品\",\"images\":[\""+img+"\"]}")).getData().toString());
        assertThrows(IllegalArgumentException.class,()->assets.delete(10L,img));login(20L);
        assertThrows(IllegalArgumentException.class,()->trade.draft(id));assertThrows(IllegalArgumentException.class,()->trade.saveDraft(id,json.readTree("{\"title\":\"越权\"}")));
        trade.deleteDraft(id);login(10L);assertTrue(trade.draft(id).getSuccess());assertThrows(IllegalArgumentException.class,()->trade.saveDraft(null,json.readTree("{\"images\":[\"https://invalid/image.jpg\"]}")));
        trade.deleteDraft(id);assertTrue(((List<?>)trade.drafts().getData()).isEmpty());
    }
    @Test void reportsRejectForeignMessagesAndAnonymousTradeApi()throws Exception {
        chatUsers();trade.message(json.readTree("{\"peerId\":20,\"content\":\"商品咨询\"}"));long mid=jdbc.queryForObject("SELECT id FROM tb_private_message LIMIT 1",Long.class);
        login(99L);assertThrows(IllegalArgumentException.class,()->trade.report(json.readTree("{\"targetType\":\"MESSAGE\",\"targetId\":"+mid+",\"reason\":\"越权举报\"}")));
        login(10L);assertTrue(trade.report(json.readTree("{\"targetType\":\"PRODUCT\",\"targetId\":2,\"reason\":\"描述不实\"}")).getSuccess());assertEquals(1,((List<?>)trade.reports().getData()).size());
        UserHolder.removeUser();mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/trade/drafts")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
    @Test void threeFailuresCompensateExactlyOnceAndAllowRetry() {
        long id=reserve();for(int i=0;i<4;i++)flash.failedAttempt(id,"测试失败");
        assertEquals(1,stock());assertEquals("FAILED",((Map<?,?>)flash.status(id,10L).getData()).get("status"));
        flash.complete(id);assertEquals(0,orders.count());assertNotEquals(id,reserve());
    }
    @Test void lostMessageExpiresAndLateDeliveryCannotCreateOrder() {
        long id=reserve();jdbc.update("UPDATE tb_flash_request SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);
        flash.expire(id);flash.expire(id);flash.complete(id);assertEquals(1,stock());assertEquals(0,orders.count());
    }
    @Test void pendingCannotBeBypassedByOrdinaryPurchase() {
        jdbc.update("UPDATE tb_product SET stock=2 WHERE id=2");long id=reserve();
        OrderFormDTO form=new OrderFormDTO();form.setProductId(2L);form.setAddressId(3L);
        assertFalse(orders.createOrder(form).getSuccess());assertEquals(1,stock());flash.complete(id);
    }
    @Test void concurrentRequestsReserveOnlyOne()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<Result>> fs=new ArrayList<>();
            for(int i=0;i<8;i++)fs.add(pool.submit(()->flash.reserve(2L,3L,10L)));
            Set<Object> ids=new HashSet<>();for(Future<Result> f:fs){Result r=f.get(20,TimeUnit.SECONDS);assertTrue(r.getSuccess());ids.add(r.getData());}
            assertEquals(1,ids.size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_flash_request",Integer.class));assertEquals(0,stock());
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentPayCancelAndHideRetainOtherParty()throws Exception {
        long id=reserve();flash.complete(id);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> a=pool.submit(()->{login(10L);try{orders.pay(id);}finally{UserHolder.removeUser();}});
            Future<?> b=pool.submit(()->{login(10L);try{orders.cancel(id);}finally{UserHolder.removeUser();}});
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        if(orders.getById(id).getStatus()==1){assertEquals(0,stock());assertFalse(orders.cancel(id).getSuccess());long cid=Long.parseLong(trade.apply(id,json.readTree("{\"reason\":\"取消购买\"}")).getData().toString());login(20L);trade.resolve(cid,json.readTree("{\"action\":\"ACCEPT\",\"note\":\"同意模拟结案\"}"));login(10L);}
        assertEquals(4,orders.getById(id).getStatus());assertEquals(1,stock());assertEquals(0,jdbc.queryForObject("SELECT sold FROM tb_product WHERE id=2",Integer.class));
        assertFalse(orders.cancel(id).getSuccess());assertTrue(orders.delete(id).getSuccess());
        assertTrue(((List<?>)orders.buyList().getData()).isEmpty());login(20L);assertEquals(1,((List<?>)orders.sellList().getData()).size());
        assertTrue(orders.myList(1,10,null).getSuccess());
    }
    @Test void ordinaryAndFlashCompeteForLastItem()throws Exception {
        addresses.insert(new Address().setId(4L).setUserId(11L).setReceiverName("测试2").setReceiverPhone("13900000000").setProvince("省").setCity("市").setDistrict("区").setDetail("测试地址"));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<Result> a=pool.submit(()->{login(10L);try{OrderFormDTO f=new OrderFormDTO();f.setProductId(2L);f.setAddressId(3L);return orders.createOrder(f);}finally{UserHolder.removeUser();}});
            Future<Result> b=pool.submit(()->flash.reserve(2L,4L,11L));
            boolean ordinary=a.get(20,TimeUnit.SECONDS).getSuccess(),reserved=b.get(20,TimeUnit.SECONDS).getSuccess();
            assertNotEquals(ordinary,reserved);
            for(Long id:jdbc.queryForList("SELECT id FROM tb_flash_request WHERE status='PENDING'",Long.class))flash.complete(id);
            assertEquals(1,orders.count());assertEquals(0,stock());
        }finally{pool.shutdownNow();}
    }
    @Test void paymentRacesExpiryWithoutInconsistentInventory()throws Exception {
        long id=reserve();flash.complete(id);jdbc.update("UPDATE tb_order SET create_time=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?",id);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> a=pool.submit(()->{login(10L);try{orders.pay(id);}finally{UserHolder.removeUser();}});
            Future<?> b=pool.submit(()->orders.expireUnpaid(id,LocalDateTime.now().minusMinutes(30)));
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        int state=orders.getById(id).getStatus();assertTrue(state==1||state==4);
        assertEquals(state==1?0:1,stock());assertEquals(state==1?1:0,jdbc.queryForObject("SELECT sold FROM tb_product WHERE id=2",Integer.class));
    }
    @Test void ordinaryInsertFailureRestoresDatabaseStock() {
        jdbc.execute("CREATE TRIGGER priority_fail_insert BEFORE INSERT ON tb_order FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated test failure'");
        try{
            OrderFormDTO form=new OrderFormDTO();form.setProductId(2L);form.setAddressId(3L);
            assertThrows(RuntimeException.class,()->orders.createOrder(form));assertEquals(1,stock());assertEquals(0,orders.count());
        }finally{jdbc.execute("DROP TRIGGER priority_fail_insert");}
    }
    @Test void orderInsertFailureRollsBackBeforeCompensation() {
        long id=reserve();String snapshot=jdbc.queryForObject("SELECT order_json FROM tb_flash_request WHERE id=?",String.class,id);
        jdbc.update("UPDATE tb_flash_request SET order_json=REPLACE(order_json,'20.00','null') WHERE id=?",id);
        assertThrows(RuntimeException.class,()->flash.complete(id));assertEquals(0,orders.count());assertEquals(0,stock());
        assertEquals("PENDING",((Map<?,?>)flash.status(id,10L).getData()).get("status"));
        jdbc.update("UPDATE tb_flash_request SET order_json=? WHERE id=?",snapshot,id);flash.complete(id);assertEquals(1,orders.count());
    }
    @Test void reconcileUsesReservedDatabaseStock() {
        reserve();flash.reconcile(2L);verify(redis.opsForValue()).set("flash:stock:2","0");
    }
    String upload()throws Exception {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        return assets.save(10L,new org.springframework.mock.web.MockMultipartFile("files","a.png","image/png",bytes.toByteArray()));
    }
    @Test void uploadOwnershipReferencesAndDeletedAssets()throws Exception {
        String image=upload();
        assertThrows(IllegalArgumentException.class,()->assets.delete(20L,image));
        assets.claim(10L,Collections.singletonList(image));
        assertThrows(IllegalArgumentException.class,()->assets.delete(10L,image));
        String staged=upload();assets.delete(10L,staged);
        assertThrows(IllegalArgumentException.class,()->assets.claim(10L,Collections.singletonList(staged)));
    }
    @Test void concurrentImageClaimAndDeleteHaveOneWinner()throws Exception {
        String image=upload();ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Boolean> claim=()->{try{assets.claim(10L,Collections.singletonList(image));return true;}catch(IllegalArgumentException e){return false;}};
            Callable<Boolean> delete=()->{try{assets.delete(10L,image);return true;}catch(IllegalArgumentException e){return false;}};
            Future<Boolean> a=pool.submit(claim),b=pool.submit(delete);
            boolean claimed=a.get(20,TimeUnit.SECONDS),deleted=b.get(20,TimeUnit.SECONDS);
            assertNotEquals(claimed,deleted);
            assertEquals(claimed,Files.exists(imageRoot.resolve(image.substring(6))));
        }finally{pool.shutdownNow();}
    }
    @Test void invalidProductPriceAndForeignImageRejected()throws Exception {
        ProductFormDTO form=new ProductFormDTO();form.setTitle("测试");form.setCategoryId(1L);form.setSize("M");form.setCondition(2);form.setPrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,()->productService.publish(form));
        String image=upload();login(20L);form.setPrice(BigDecimal.TEN);form.setImages(Collections.singletonList(image));
        assertThrows(IllegalArgumentException.class,()->productService.publish(form));
    }
    @Test void realRabbitConfirmAndDuplicateDelivery()throws Exception {
        long id=reserve();
        YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yaml"));Properties p=yaml.getObject();
        org.springframework.amqp.rabbit.connection.CachingConnectionFactory cf=new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(p.getProperty("spring.rabbitmq.host"),Integer.parseInt(p.getProperty("spring.rabbitmq.port")));
        cf.setUsername(p.getProperty("spring.rabbitmq.username"));cf.setPassword(p.getProperty("spring.rabbitmq.password"));
        cf.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);cf.setPublisherReturns(true);
        String queue="girlshop.priority.it."+database;
        org.springframework.amqp.rabbit.core.RabbitAdmin admin=new org.springframework.amqp.rabbit.core.RabbitAdmin(cf);
        try{
            admin.declareQueue(new org.springframework.amqp.core.Queue(queue,true));
            RabbitTemplate real=new RabbitTemplate(cf);real.setMandatory(true);real.setMessageConverter(new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter());
            FlashOutboxWorker worker=new FlashOutboxWorker();
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"jdbc",jdbc);
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"rabbit",real);
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"requests",flash);
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"queue",queue);
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"queue",queue+".missing");
            worker.dispatch();assertEquals(0,orders.count());assertEquals(0,stock());
            assertEquals("PENDING",((Map<?,?>)flash.status(id,10L).getData()).get("status"));
            jdbc.update("UPDATE tb_flash_request SET next_publish_at=NOW() WHERE id=?",id);
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"queue",queue);
            worker.dispatch();assertEquals(Long.toString(id),real.receiveAndConvert(queue,3000));
            flash.complete(id);flash.complete(id);assertEquals(1,orders.count());
        }finally{admin.deleteQueue(queue);cf.destroy();}
    }
    @Test void realRedisReconciliationDoesNotTouchApplicationKeys() {
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory cf=new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory("127.0.0.1",6379);
        cf.afterPropertiesSet();StringRedisTemplate real=new StringRedisTemplate(cf);
        String prefix="priority-it:"+database+":";
        org.springframework.test.util.ReflectionTestUtils.setField(flash,"redis",real);
        org.springframework.test.util.ReflectionTestUtils.setField(flash,"redisPrefix",prefix);
        try{
            long id=reserve();flash.reconcile(2L);assertEquals("0",real.opsForValue().get(prefix+"flash:stock:2"));
            for(int i=0;i<3;i++)flash.failedAttempt(id,"测试失败");
            flash.reconcile(2L);assertEquals("1",real.opsForValue().get(prefix+"flash:stock:2"));
        }finally{
            real.delete(Arrays.asList(prefix+"flash:stock:2",prefix+"flash:order:2",prefix+"cache:product:2"));
            org.springframework.test.util.ReflectionTestUtils.setField(flash,"redis",redis);
            org.springframework.test.util.ReflectionTestUtils.setField(flash,"redisPrefix","");cf.destroy();
        }
    }
    @Test void realOrderHttpEndpointsAndRequestOwnership()throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/order/flash/2").param("addressId","3").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true));
        long id=jdbc.queryForObject("SELECT id FROM tb_flash_request LIMIT 1",Long.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/order/flash/request/"+id).header("authorization","it-other"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));
        flash.complete(id);
        for(String path:Arrays.asList("/order/buy","/order/sell","/order/my?page=1&pageSize=10","/order/flash/requests"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true));
    }
    @Test void uploadHttpRejectsFakeImageAndForeignDeletion()throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/upload/product")
            .file(new org.springframework.mock.web.MockMultipartFile("files","fake.jpg","image/jpeg","fake".getBytes())).header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));
        String image=upload();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/upload/product").param("name",image).header("authorization","it-other"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));
    }
    @Test void sellerStorefrontFiltersSellerAndHidesPrivateFields()throws Exception {
        jdbc.update("INSERT IGNORE INTO tb_user(id,phone,nick_name) VALUES(20,'13800000020','测试卖家')");
        products.insert(new Product().setId(30L).setUserId(21L).setCategoryId(1L).setTitle("其他卖家商品").setPrice(BigDecimal.TEN).setStatus(1).setStock(1));
        products.insert(new Product().setId(31L).setUserId(20L).setCategoryId(1L).setTitle("已下架商品").setPrice(BigDecimal.TEN).setStatus(0).setStock(1));
        products.insert(new Product().setId(32L).setUserId(20L).setCategoryId(1L).setTitle("售罄商品").setPrice(BigDecimal.TEN).setStatus(1).setStock(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/20").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.phone").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/20/products").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].userId").value("20"));
        jdbc.update("UPDATE tb_product SET status=0 WHERE user_id=20");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/20"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.phone").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/20/products"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(0));
        products.insert(new Product().setId(33L).setUserId(10L).setCategoryId(1L).setTitle("我的商品").setPrice(BigDecimal.TEN).setStatus(1).setStock(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/product/my").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].userId").value("10"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/product/my"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/20/products").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/seller/999999999").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));
    }
    @Test void onlyBuyerCanReviewAfterReceiptAndReviewIsProductScoped()throws Exception {
        long id=reserve();flash.complete(id);
        ReviewFormDTO form=new ReviewFormDTO();form.setOrderId(id);form.setScore(5);form.setContent("收到商品，质量很好");
        assertFalse(reviews.add(form).getSuccess());
        orders.pay(id);login(20L);trade.ship(id,json.readTree("{\"carrier\":\"测试快递\",\"trackingNo\":\"TEST123456\"}"));login(10L);
        assertFalse(reviews.add(form).getSuccess());orders.receive(id);
        login(20L);assertFalse(reviews.add(form).getSuccess());login(10L);
        String photo=upload();form.setImages(Collections.singletonList(photo));
        assertTrue(reviews.add(form).getSuccess());assertFalse(reviews.add(form).getSuccess());
        assertEquals(1,((List<?>)reviews.listByProduct(2L).getData()).size());
        assertTrue(((List<?>)reviews.listByProduct(99L).getData()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->assets.delete(10L,photo));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/review/order/"+id).header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.reviewed").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.canReview").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/review/eligible/2").header("authorization","it-buyer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.reviewed").value(true));
    }
    @Test void simultaneousReviewSubmissionsCreateOnlyOne()throws Exception {
        long id=reserve();flash.complete(id);jdbc.update("UPDATE tb_order SET status=3 WHERE id=?",id);
        ReviewFormDTO form=new ReviewFormDTO();form.setOrderId(id);form.setScore(4);form.setContent("评价并发测试");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Result> action=()->{login(10L);try{return reviews.add(form);}finally{UserHolder.removeUser();}};
            Future<Result> a=pool.submit(action),b=pool.submit(action);
            assertNotEquals(a.get(20,TimeUnit.SECONDS).getSuccess(),b.get(20,TimeUnit.SECONDS).getSuccess());
            assertEquals(1,reviews.count());
        }finally{pool.shutdownNow();}
    }
    @Test void cacheFailureIsDurableAndRetryClearsIt() {
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("isolated failure")).when(redis).delete(org.mockito.ArgumentMatchers.<String>anyCollection());
        try {login(20L);assertTrue(productService.setStatus(2L,0).getSuccess());assertEquals(0,products.selectById(2L).getStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_cache_eviction WHERE product_id=2",Integer.class));}
        finally {doReturn(0L).when(redis).delete(org.mockito.ArgumentMatchers.<String>anyCollection());}
        evictions.flushOne(2L);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_cache_eviction WHERE product_id=2",Integer.class));
    }
    @Test void requestTraceIsReturnedAndMigrationTamperingRejected()throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/category/list").header("X-Request-Id","qa-trace-1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Request-Id","qa-trace-1"));
        String checksum=jdbc.queryForObject("SELECT checksum FROM tb_schema_migration WHERE version='V003'",String.class);
        try{jdbc.update("UPDATE tb_schema_migration SET checksum='changed' WHERE version='V003'");assertThrows(IllegalStateException.class,()->schema.migrate());}
        finally{jdbc.update("UPDATE tb_schema_migration SET checksum=? WHERE version='V003'",checksum);}
    }
    @Test void ordinaryAndFlashCacheUseDifferentEncodings() {
        when(redis.opsForSet().isMember("flash:list","2")).thenReturn(false);
        assertTrue(productService.detail(2L).getSuccess());
        verify(redis.opsForValue()).get("cache:product:v2:2");
        when(redis.opsForSet().isMember("flash:list","2")).thenReturn(true);
        assertTrue(productService.detail(2L).getSuccess());
        verify(redis.opsForValue()).get("cache:flash-product:v2:2");
    }
    @Test void migrationHistoryAndBatchedReviewFlagsAreConsistent() {
        schema.migrate();schema.migrate();assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM tb_schema_migration",Integer.class));
        long id=reserve();flash.complete(id);jdbc.update("UPDATE tb_order SET status=3 WHERE id=?",id);
        Order before=(Order)((List<?>)orders.myList(1,10,null).getData()).get(0);assertFalse(before.getReviewed());assertTrue(before.getCanReview());
        ReviewFormDTO form=new ReviewFormDTO();form.setOrderId(id);form.setScore(5);form.setContent("批量状态测试");assertTrue(reviews.add(form).getSuccess());
        Order after=(Order)((List<?>)orders.myList(1,10,null).getData()).get(0);assertTrue(after.getReviewed());assertFalse(after.getCanReview());
        assertFalse(((List<?>)orders.myList(999,10,null).getData()).isEmpty());
    }
    @Test void followPaginationAndRepeatedUnfollowRemainCorrect()throws Exception {
        jdbc.update("INSERT IGNORE INTO tb_user(id,phone,nick_name) VALUES(20,'13800000020','测试卖家')");
        jdbc.update("DELETE FROM tb_follow");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/follow/20").header("authorization","it-buyer"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/follow/20").header("authorization","it-buyer"));
        login(10L);assertEquals(1,((List<?>)follows.myFollows(999,10).getData()).size());
        com.girlshop.entity.Follow followed=(com.girlshop.entity.Follow)((List<?>)follows.myFollows(1,10).getData()).get(0);
        assertEquals(1,followed.getProductCount());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/follow/20").header("authorization","it-buyer"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/follow/20").header("authorization","it-buyer"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_follow",Integer.class));
    }
    @AfterAll static void cleanupDatabase(@Autowired org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler)throws Exception {
        scheduler.shutdown();
        if(database!=null && database.matches("girl_shop_priority_it_[0-9]+")) {
            try(Connection c=DriverManager.getConnection(baseUrl,user,password);Statement q=c.createStatement()){q.execute("DROP DATABASE "+database);}
        }
    }
}
