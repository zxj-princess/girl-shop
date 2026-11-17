package com.girlshop.service;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;
import java.nio.file.*;
import java.util.*;
/** Opt-in real provider test with a synthetic garment image, no customer data or product creation. */
@EnabledIfSystemProperty(named="ai.live",matches="true")
class AiListingLiveTest {
 @TempDir Path root;
 @Test void realVisionAndStructuredOutput()throws Exception{
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.INFO);
  YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yaml"));Properties props=yaml.getObject();StandardEnvironment env=new StandardEnvironment();String key=env.resolveRequiredPlaceholders(props.getProperty("shop.ai.api-key",""));
  AiListingClient client=new AiListingClient();ReflectionTestUtils.setField(client,"apiKey",key);ReflectionTestUtils.setField(client,"model",env.resolveRequiredPlaceholders(props.getProperty("shop.ai.model","")));ReflectionTestUtils.setField(client,"endpoint",env.resolveRequiredPlaceholders(props.getProperty("shop.ai.endpoint","")));ReflectionTestUtils.setField(client,"imageRoot",root.toString());ReflectionTestUtils.setField(client,"json",new ObjectMapper());
  Assertions.assertTrue(client.configured(),client.configurationError());Path folder=root.resolve("products/10");Files.createDirectories(folder);String name=UUID.randomUUID()+".png";
  java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(360,360,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,360,360);g.setColor(java.awt.Color.BLACK);g.fillPolygon(new int[]{110,150,210,250,310,275,235,235,125,125,85,50},new int[]{80,65,65,80,150,185,150,305,305,150,185,150},12);g.dispose();javax.imageio.ImageIO.write(image,"png",folder.resolve(name).toFile());
  try{JsonNode result=client.generate(10L,"接口验收用示意图：黑色上衣，M码，售价60元。请仅返回商品草稿，不进行实际发布。",Collections.singletonList("/imgs/products/10/"+name),Collections.singletonList(new HashMap<String,Object>(){{put("id","1");put("name","上衣");}}));System.out.println("AI_LIVE_SYNTHETIC_RESULT="+result.toString().substring(0,Math.min(2000,result.toString().length())));Assertions.assertTrue(result.isObject());Assertions.assertTrue(result.path("title").isTextual());Assertions.assertEquals("1",result.path("categoryId").asText());Assertions.assertEquals("",result.path("condition").asText());Assertions.assertTrue(Arrays.asList("M","M码").contains(result.path("size").asText()));Assertions.assertEquals("60",result.path("price").asText());Assertions.assertEquals("",result.path("brand").asText());Assertions.assertEquals("",result.path("defects").asText());System.out.println("AI_LIVE_SUCCESS: real image response parsed; no product published");}
  catch(org.springframework.web.client.HttpStatusCodeException e){String message=e.getResponseBodyAsString().replace(key,"[redacted]").replaceAll("sk-[A-Za-z0-9._-]+","[redacted]");message=message.substring(0,Math.min(1600,message.length()));System.out.println("AI_LIVE_PROVIDER_ERROR status="+e.getRawStatusCode()+" detail="+message);Assertions.fail("Real provider rejected request; see sanitized diagnostic");}
  catch(Exception e){System.out.println("AI_LIVE_FAILURE type="+e.getClass().getSimpleName()+" cause="+AiListingService.failureCode(e)+" reason="+AiListingService.failureMessage(e));Assertions.fail("Real provider check failed; see sanitized diagnostic");}
 }
}
