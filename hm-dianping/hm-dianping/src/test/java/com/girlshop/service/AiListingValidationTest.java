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
 @Test void unknownFactsRemainEmpty(){ObjectNode n=output();n.put("brand","Gucci");n.put("size","XL");n.put("defects","无瑕疵");n.put("measurements","胸围90cm");ObjectNode result=service.normalize(n,input("一件上衣"),categories());assertEquals("",result.path("brand").asText());assertEquals("",result.path("size").asText());assertEquals("",result.path("defects").asText());assertTrue(result.path("condition").isNull());assertTrue(result.path("price").isNull());}
 @Test void explicitPriceAndSizeAreRetained(){ObjectNode n=output();n.put("size","M");ObjectNode result=service.normalize(n,input("M码，想卖60.50元"),categories());assertEquals("M",result.path("size").asText());assertEquals(0,result.path("price").decimalValue().compareTo(new java.math.BigDecimal("60.50")));assertTrue(result.path("condition").isNull());}
 @Test void measurementNumberIsNotAPrice(){ObjectNode n=output();n.put("price","62");assertTrue(service.normalize(n,input("衣长62cm"),categories()).path("price").isNull());}
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
