package com.girlshop;
import com.girlshop.service.*;
import com.girlshop.dto.*;
import com.girlshop.utils.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Uses a disposable MySQL database and a fake model; never calls a paid model or changes business data. */
@EnabledIfSystemProperty(named="priority.integration",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"shop.ai.worker.enabled=false","flash.worker.enabled=false","spring.rabbitmq.listener.simple.auto-startup=false","orders.expiry-scan-ms=3600000"})
class AiListingIntegrationTest {
 static String database,baseUrl,url,user,password;static java.nio.file.Path imageRoot;
 static synchronized void prepare(){if(database!=null)return;try{org.springframework.beans.factory.config.YamlPropertiesFactoryBean yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();yaml.setResources(new org.springframework.core.io.ClassPathResource("application.yaml"));Properties props=yaml.getObject();baseUrl=props.getProperty("spring.datasource.url");user=props.getProperty("spring.datasource.username");password=props.getProperty("spring.datasource.password");database="girl_shop_ai_it_"+System.currentTimeMillis();if(!database.matches("girl_shop_ai_it_[0-9]+"))throw new IllegalStateException("Unsafe database");try(java.sql.Connection c=java.sql.DriverManager.getConnection(baseUrl,user,password);java.sql.Statement q=c.createStatement()){q.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4");}url=baseUrl.replace("/girl_shop?","/"+database+"?");org.springframework.jdbc.datasource.DriverManagerDataSource ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,user,password);for(String resource:Arrays.asList("db/girl-shop.sql","db/new-features.sql")){String sql=new String(new org.springframework.core.io.ClassPathResource(resource).getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);sql=sql.replaceAll("(?im)^CREATE DATABASE[^;]+;","").replaceAll("(?im)^USE[^;]+;","");new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(ds);}imageRoot=java.nio.file.Files.createTempDirectory("girl-shop-ai-it-");}catch(Exception e){throw new IllegalStateException("隔离测试数据库初始化失败",e);}}
 @DynamicPropertySource static void configure(DynamicPropertyRegistry r){prepare();r.add("spring.datasource.url",()->url);r.add("images.root",()->imageRoot.toString());}
 @MockBean(answer=Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redis;
 @MockBean RedissonClient redisson;
 @MockBean RabbitTemplate rabbit;
 @MockBean RedisIdWorker ids;
 @MockBean AiListingClient client;
 @Autowired AiListingService ai;
 @Autowired TradeExperienceService trade;
 @Autowired UploadAssetService assets;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 String image;
 static void login(long id){UserDTO u=new UserDTO();u.setId(id);UserHolder.saveUser(u);}
 @BeforeEach void seed()throws Exception{
  for(String table:Arrays.asList("tb_ai_listing_job","tb_draft_publication","tb_product_draft","tb_product","tb_upload_asset"))jdbc.execute("DELETE FROM "+table);
  jdbc.update("INSERT IGNORE INTO tb_user(id,phone,nick_name) VALUES(10,'13800000010','AI测试卖家')");
  login(10);when(client.configured()).thenReturn(true);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  image=assets.save(10L,new MockMultipartFile("files","test.png","image/png",bytes.toByteArray()));
 }
 ObjectNode request(){ObjectNode n=json.createObjectNode();n.put("requestId",UUID.randomUUID().toString());n.put("consent",true);n.put("text","M码，想卖60元，袖口轻微起球");n.putArray("images").add(image);return n;}
 Map<?,?> create(ObjectNode input){return (Map<?,?>)ai.create(input).getData();}
 ObjectNode model(){ObjectNode n=json.createObjectNode();for(String key:Arrays.asList("brand","measurements","defects"))n.put(key,"");n.put("title","针织上衣");n.put("description","袖口轻微起球");n.put("size","M");n.put("categoryId","1");n.put("price","60");n.putArray("warnings");return n;}
 void execute(String id)throws Exception{jdbc.update("UPDATE tb_ai_listing_job SET status='RUNNING' WHERE id=?",id);Map<String,Object> job=jdbc.queryForMap("SELECT * FROM tb_ai_listing_job WHERE id=?",id);ReflectionTestUtils.invokeMethod(ai,"run",job);login(10);}
 @Test void draftCardsContainSafeSummaryAndOwnerOnlyFields(){Map<?,?> job=create(request());java.util.List<?> list=(java.util.List<?>)trade.drafts().getData();assertEquals(1,list.size());Map<?,?> card=(Map<?,?>)list.get(0);assertEquals(job.get("draftId"),card.get("id"));assertEquals(image,card.get("image"));assertEquals("QUEUED",card.get("aiStatus"));assertFalse(card.containsKey("payload"));assertTrue(((java.util.List<?>)card.get("missing")).contains("价格"));login(99);assertTrue(((java.util.List<?>)trade.drafts().getData()).isEmpty());}
 @Test void duplicateCreateAndOwnershipChecks(){ObjectNode input=request();Map<?,?> first=create(input),second=create(input);assertEquals(first.get("id"),second.get("id"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_ai_listing_job",Integer.class));input.put("text","不同的商品");assertThrows(IllegalArgumentException.class,()->ai.create(input));login(99);assertThrows(IllegalArgumentException.class,()->ai.status(first.get("id").toString()));assertThrows(IllegalArgumentException.class,()->trade.draft(Long.parseLong(first.get("draftId").toString())));}
 @Test void consentAndForeignImagesRejected(){ObjectNode input=request();input.put("consent",false);assertThrows(IllegalArgumentException.class,()->ai.create(input));input.put("consent",true);input.putArray("images").add(image.replace("/10/","/99/"));assertThrows(IllegalArgumentException.class,()->ai.create(input));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_ai_listing_job",Integer.class));}
 @Test void unconfiguredProviderDoesNotCreateJobOrDraft()throws Exception{when(client.configured()).thenReturn(false);when(client.configurationError()).thenReturn("AI接口地址不完整");assertThrows(IllegalArgumentException.class,()->create(request()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_ai_listing_job",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_product_draft",Integer.class));verify(client,never()).generate(anyLong(),anyString(),anyList(),anyList());}
 @Test void failedRetryReusesDraftAndProtectsManualChanges()throws Exception{
  ObjectNode input=request();Map<?,?> first=create(input);jdbc.update("UPDATE tb_ai_listing_job SET status='FAILED' WHERE id=?",first.get("id"));
  ObjectNode retry=request();retry.put("retryJobId",first.get("id").toString());Map<?,?> next=create(retry);assertEquals(first.get("draftId"),next.get("draftId"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_product_draft",Integer.class));
  jdbc.update("UPDATE tb_ai_listing_job SET status='FAILED' WHERE id=?",next.get("id"));long draft=Long.parseLong(next.get("draftId").toString());ObjectNode edit=(ObjectNode)json.readTree(jdbc.queryForObject("SELECT payload FROM tb_product_draft WHERE id=?",String.class,draft));edit.put("title","人工标题");trade.saveDraft(draft,edit);
  ObjectNode third=request();third.put("retryJobId",next.get("id").toString());assertThrows(IllegalArgumentException.class,()->create(third));assertEquals("人工标题",jdbc.queryForObject("SELECT title FROM tb_product_draft WHERE id=?",String.class,draft));third.put("overwriteConfirmed",true);assertEquals(next.get("draftId"),create(third).get("draftId"));
 }
 @Test void generationProducesPrivatePreviewAndRequiresConditionConfirmation()throws Exception{when(client.generate(anyLong(),anyString(),anyList(),anyList())).thenReturn(model());Map<?,?> job=create(request());execute(job.get("id").toString());assertEquals("READY",((Map<?,?>)ai.status(job.get("id").toString()).getData()).get("status"));String payload=jdbc.queryForObject("SELECT payload FROM tb_product_draft WHERE id=?",String.class,job.get("draftId"));assertTrue(json.readTree(payload).path("condition").isNull());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tb_product",Integer.class));assertThrows(IllegalArgumentException.class,()->ai.publish(Long.parseLong(job.get("draftId").toString())));}
 @Test void manualEditsSurviveLateModelResult()throws Exception{Map<?,?> job=create(request());long draft=Long.parseLong(job.get("draftId").toString());Map<?,?> previous=(Map<?,?>)trade.draft(draft).getData();ObjectNode changed=(ObjectNode)json.readTree(previous.get("payload").toString());changed.put("title","我的手动标题");trade.saveDraft(draft,changed);when(client.generate(anyLong(),anyString(),anyList(),anyList())).thenReturn(model());execute(job.get("id").toString());assertEquals("我的手动标题",jdbc.queryForObject("SELECT title FROM tb_product_draft WHERE id=?",String.class,draft));assertEquals("FAILED",((Map<?,?>)ai.status(job.get("id").toString()).getData()).get("status"));}
 @Test void providerFailureRetainsInputAndHidesInternalError()throws Exception{when(client.generate(anyLong(),anyString(),anyList(),anyList())).thenThrow(new IllegalStateException("secret-internal-detail"));Map<?,?> job=create(request());execute(job.get("id").toString());Map<?,?> status=(Map<?,?>)ai.status(job.get("id").toString()).getData();assertEquals("FAILED",status.get("status"));assertFalse(status.toString().contains("secret-internal-detail"));assertTrue(jdbc.queryForObject("SELECT payload FROM tb_product_draft WHERE id=?",String.class,job.get("draftId")).contains("袖口轻微起球"));}
 @Test void concurrentPublicationCreatesOneProduct()throws Exception{ObjectNode draft=model();draft.put("condition",2);draft.putArray("images").add(image);long id=Long.parseLong(trade.saveDraft(null,draft).getData().toString());ExecutorService pool=Executors.newFixedThreadPool(2);try{Callable<Object> publish=()->{login(10);try{return ai.publish(id).getData();}finally{UserHolder.removeUser();}};Future<Object> a=pool.submit(publish),b=pool.submit(publish);assertEquals(a.get(10,TimeUnit.SECONDS).toString(),b.get(10,TimeUnit.SECONDS).toString());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_product",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tb_draft_publication",Integer.class));}finally{pool.shutdownNow();}}
 @Test void sellerConditionIsSavedAndRemovedFromMissingFields()throws Exception{
  ObjectNode input=request();input.put("text","L码，白T,九成新，阿迪的，100出无瑕疵");
  ObjectNode generated=model();generated.put("condition","");
  when(client.generate(anyLong(),anyString(),anyList(),anyList())).thenReturn(generated);
  Map<?,?> job=create(input);execute(job.get("id").toString());
  Map<?,?> status=(Map<?,?>)ai.status(job.get("id").toString()).getData();
  assertEquals("READY",status.get("status"));
  assertFalse(((List<?>)status.get("missing")).contains("成色"));
  Map<?,?> saved=(Map<?,?>)trade.draft(Long.parseLong(job.get("draftId").toString())).getData();
  JsonNode preview=json.readTree(saved.get("payload").toString());
  assertEquals(2,preview.path("condition").asInt());
  assertEquals(input.path("text").asText(),preview.path("aiSourceText").asText());
 }
 @AfterEach void clear(){UserHolder.removeUser();}
 @AfterAll static void cleanup()throws Exception{if(database!=null&&database.matches("girl_shop_ai_it_[0-9]+")){try(java.sql.Connection c=java.sql.DriverManager.getConnection(baseUrl,user,password);java.sql.Statement q=c.createStatement()){q.execute("DROP DATABASE "+database);}}}
}
