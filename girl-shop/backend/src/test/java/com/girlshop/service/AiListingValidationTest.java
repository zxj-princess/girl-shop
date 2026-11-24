package com.girlshop.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiListingValidationTest {
 ObjectMapper json=new ObjectMapper();AiListingService service=new AiListingService();
 @BeforeEach void wire(){ReflectionTestUtils.setField(service,"json",json);}
 ObjectNode output(){ObjectNode n=json.createObjectNode();for(String key:Arrays.asList("title","description","brand","size","measurements","defects"))n.put(key,"");n.put("title","针织上衣");n.put("description","简约针织款式");n.put("categoryId","1");n.put("price","60.50");n.putArray("warnings");return n;}
 ObjectNode input(String text){ObjectNode n=json.createObjectNode();n.put("text",text);n.putArray("images").add("/imgs/products/10/example.png");return n;}
 List<Map<String,Object>> categories(){return Collections.singletonList(Collections.singletonMap("id","1"));}
 @Test void colloquialScreenshotCompletesMissingFieldsAndCategory(){
  ObjectNode n=output();n.put("title","卡其色风衣");n.put("categoryId","");n.put("size","");n.put("brand","");
  List<Map<String,Object>> catalog=new ArrayList<>();for(int i=1;i<=2;i++){Map<String,Object> c=new HashMap<>();c.put("id",String.valueOf(i));c.put("name",i==1?"上衣":"外套");catalog.add(c);}
  String text="M码，穿过两次，300买的，100出，九九新，zara的";
  ObjectNode d=service.normalize(n,input(text),catalog);
  assertEquals(300,d.path("originalPrice").intValue());assertEquals(100,d.path("price").intValue());
  assertEquals(6,d.path("condition").asInt());assertEquals("M码",d.path("size").asText());assertEquals("zara",d.path("brand").asText());assertEquals("2",d.path("categoryId").asText());
  assertEquals("",d.path("defects").asText());assertEquals("",d.path("measurements").asText());assertEquals(text,d.path("aiSourceText").asText());
 }
 @Test void unknownFactsRemainEmpty(){ObjectNode n=output();n.put("brand","Gucci");n.put("size","XL");n.put("defects","无瑕疵");n.put("measurements","胸围90cm");ObjectNode result=service.normalize(n,input("一件上衣"),categories());assertEquals("",result.path("brand").asText());assertEquals("",result.path("size").asText());assertEquals("",result.path("defects").asText());assertTrue(result.path("condition").isNull());assertTrue(result.path("price").isNull());}
 @Test void explicitPriceAndSizeAreRetained(){ObjectNode n=output();n.put("size","M");ObjectNode result=service.normalize(n,input("M码，想卖60.50元"),categories());assertEquals("M",result.path("size").asText());assertEquals(0,result.path("price").decimalValue().compareTo(new java.math.BigDecimal("60.50")));assertTrue(result.path("condition").isNull());}
 @Test void measurementNumberIsNotAPrice(){ObjectNode n=output();n.put("price","62");assertTrue(service.normalize(n,input("衣长62cm"),categories()).path("price").isNull());}
 @Test void missingOrRewrittenModelFactsAreRecoveredFromSellerText(){
  ObjectNode n=output();n.put("price","300");n.put("originalPrice","999");n.put("defects","无明显磨损");
  ObjectNode d=service.normalize(n,input("L码风衣，穿过几次，九成新，衣服保存的很好，没什么磨损，原价300，现在80出"),categories());
  assertEquals(80,d.path("price").intValue());assertEquals(300,d.path("originalPrice").intValue());
  assertEquals("没什么磨损",d.path("defects").asText());assertEquals(2,d.path("condition").asInt());
 }
 @Test void modelCannotReplaceOriginalPriceWithSalePriceOrDiscardOtherDamage(){
  ObjectNode n=output();n.put("price","300");n.put("originalPrice","300");n.put("defects","袖口起球");
  ObjectNode d=service.normalize(n,input("原价300元，袖口起球，下摆有污渍"),categories());
  assertTrue(d.path("price").isNull());assertEquals(300,d.path("originalPrice").intValue());
  assertEquals("袖口起球；下摆有污渍",d.path("defects").asText());
  assertTrue(service.normalize(n,input("一件上衣"),categories()).path("originalPrice").isNull());
 }
 @Test void explicitSellerConditionAppearsEvenWhenModelLeavesItEmpty(){
  ObjectNode n=output();n.put("condition","");
  ObjectNode draft=service.normalize(n,input("L码，白T,九成新，阿迪的，100出无瑕疵"),categories());
  assertEquals(2,draft.path("condition").asInt());
  assertEquals("L码，白T,九成新，阿迪的，100出无瑕疵",draft.path("aiSourceText").asText());
  assertTrue(draft.path("aiReviewRequired").asBoolean());
 }
 @Test void modelCannotInventOrOverrideCondition(){
  ObjectNode n=output();n.put("condition","1");
  assertTrue(service.normalize(n,input("穿过两次的白T"),categories()).path("condition").isNull());
  assertEquals(2,service.normalize(n,input("九成新白T"),categories()).path("condition").asInt());
 }
 @Test void conflictingAndUncertainSellerConditionsRemainUnconfirmed(){
  for(String text:Arrays.asList("九成新或者八成新","大概九成新","九成新左右","不是九成新","全新和九成新之间"))
   assertTrue(service.normalize(output(),input(text),categories()).path("condition").isNull(),text);
 }
 @Test void inventedCategoryAndOversizedTextRejected(){ObjectNode n=output();n.put("categoryId","999");assertTrue(service.normalize(n,input("上衣"),categories()).path("categoryId").isNull());n.put("title",String.join("",Collections.nCopies(101,"字")));assertThrows(IllegalArgumentException.class,()->service.normalize(n,input("上衣"),categories()));}
 @Test void nonObjectAndMalformedFieldRejected(){assertThrows(IllegalArgumentException.class,()->service.normalize(json.createArrayNode(),input("上衣"),categories()));ObjectNode n=output();n.putObject("description");assertThrows(IllegalArgumentException.class,()->service.normalize(n,input("上衣"),categories()));}
 @Test void endpointAndProviderErrorsAreActionableWithoutLeakingSecrets(){
  assertTrue(AiListingService.failureMessage(new IllegalStateException("AI接口必须使用HTTPS")).contains("完整HTTPS地址"));
  Exception denied=new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.UNAUTHORIZED,"secret-key-and-provider-body");String message=AiListingService.failureMessage(denied);assertTrue(message.contains("密钥无效"));assertFalse(message.contains("secret"));
  assertTrue(AiListingService.failureMessage(new org.springframework.web.client.ResourceAccessException("secret-network-details")).contains("请求超时"));
 }
 @Test void extractsResalePriceWithoutTreatingSizeAsMoney(){ObjectNode n=output();n.put("price","");assertEquals(80,service.normalize(n,input("L码，穿过两次，七成新，80出"),categories()).path("price").intValue());assertTrue(service.normalize(n,input("38码，穿过两次"),categories()).path("price").isNull());}
 @AfterEach void close(){service.close();}
 @Test void distinguishesNetworkFailuresWithoutExposingCauseDetails(){
  java.io.IOException[] causes={new java.net.UnknownHostException("private-host"),new javax.net.ssl.SSLHandshakeException("private-cert"),new java.net.SocketTimeoutException("private-url"),new java.net.ConnectException("private-ip")};
  String[] codes={"DNS_FAILED","TLS_FAILED","NETWORK_TIMEOUT","CONNECTION_FAILED"};
  String[] hints={"域名解析失败","HTTPS连接失败","响应超时","无法建立"};
  for(int i=0;i<causes.length;i++){Exception e=new org.springframework.web.client.ResourceAccessException("secret-key",causes[i]);assertEquals(codes[i],AiListingService.failureCode(e));assertTrue(AiListingService.failureMessage(e).contains(hints[i]));assertFalse(AiListingService.failureMessage(e).contains("private"));assertFalse(AiListingService.failureMessage(e).contains("secret"));}
 }
}
