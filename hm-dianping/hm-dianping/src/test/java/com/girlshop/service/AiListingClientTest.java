package com.girlshop.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class AiListingClientTest {
 @TempDir Path root;ObjectMapper json=new ObjectMapper();AiListingClient client;MockRestServiceServer server;String url;
 @BeforeEach void setup()throws Exception{client=new AiListingClient();ReflectionTestUtils.setField(client,"apiKey","test-only-key");ReflectionTestUtils.setField(client,"model","test-vision");ReflectionTestUtils.setField(client,"endpoint","https://example.invalid/v1/responses");ReflectionTestUtils.setField(client,"imageRoot",root.toString());ReflectionTestUtils.setField(client,"json",json);Path parent=root.resolve("products/10");Files.createDirectories(parent);String name=UUID.randomUUID()+".png";javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",parent.resolve(name).toFile());url="/imgs/products/10/"+name;server=MockRestServiceServer.createServer((RestTemplate)ReflectionTestUtils.getField(client,"http"));}
 @Test void incompleteEndpointFailsPreflight(){ReflectionTestUtils.setField(client,"endpoint","workspace.example.com");assertFalse(client.configured());assertTrue(client.configurationError().contains("HTTPS基础地址"));}
 @Test void acceptsSdkBaseUrlAndAddsResponsesOnce(){ReflectionTestUtils.setField(client,"endpoint","https://example.invalid/compatible-mode/v1/");assertTrue(client.configured());assertEquals("https://example.invalid/compatible-mode/v1/responses",client.requestEndpoint());ReflectionTestUtils.setField(client,"endpoint","https://example.invalid/compatible-mode/v1/responses");assertEquals("https://example.invalid/compatible-mode/v1/responses",client.requestEndpoint());}
 @Test void sendsOwnedImageAndStrictSchemaWithoutStoringResponse()throws Exception{ObjectNode response=json.createObjectNode();response.put("status","completed");response.putArray("output").addObject().putArray("content").addObject().put("type","output_text").put("text","{\"title\":\"上衣\"}");server.expect(requestTo("https://example.invalid/v1/responses")).andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.text.format.strict").value(true)).andExpect(jsonPath("$.input[1].content[1].image_url").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,"))).andRespond(withSuccess(response.toString(),MediaType.APPLICATION_JSON));assertEquals("上衣",client.generate(10L,"上衣",Collections.singletonList(url),Collections.emptyList()).path("title").asText());server.verify();}
 @Test void dashscopeUsesDocumentedNestedSchema()throws Exception{ReflectionTestUtils.setField(client,"endpoint","https://test.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/responses");server.expect(anything()).andExpect(jsonPath("$.text.format.json_schema.strict").value(true)).andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"[{\\\"title\\\":\\\"上衣\\\"}]\"}]}]}",MediaType.APPLICATION_JSON));assertTrue(client.generate(10L,"上衣",Collections.singletonList(url),Collections.emptyList()).isObject());server.verify();}
 @Test void refusesForeignImageBeforeNetworkCall(){assertThrows(IllegalArgumentException.class,()->client.generate(20L,"上衣",Collections.singletonList(url),Collections.emptyList()));server.verify();}
 @Test void incompleteModelOutputIsFailure(){server.expect(anything()).andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}",MediaType.APPLICATION_JSON));assertThrows(IllegalArgumentException.class,()->client.generate(10L,"上衣",Collections.singletonList(url),Collections.emptyList()));server.verify();}
}
