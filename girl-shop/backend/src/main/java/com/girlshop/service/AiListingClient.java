package com.girlshop.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.girlshop.utils.ImageStorage;
import com.girlshop.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import javax.annotation.Resource;
import java.nio.file.*;
import java.util.*;

/** Only server-configured endpoints receive validated, owned image bytes. No remote user URLs. */
@Component
public class AiListingClient {
 @Value("${shop.ai.api-key:}") private String apiKey;
 @Value("${shop.ai.model:}") private String model;
 @Value("${shop.ai.base-url:${shop.ai.endpoint:https://api.openai.com/v1}}") private String endpoint;
 @Value("${images.root:"+SystemConstants.IMAGE_UPLOAD_DIR+"}") private String imageRoot;
 @Resource private ObjectMapper json;
 private final RestTemplate http;
 private final String listingSkill=loadListingSkill();
 private static String loadListingSkill(){
  try(java.io.InputStream stream=new org.springframework.core.io.ClassPathResource("skills/product-listing/SKILL.md").getInputStream()){
   String document=org.springframework.util.StreamUtils.copyToString(stream,java.nio.charset.StandardCharsets.UTF_8);
   return document.replaceFirst("(?s)^---\\r?\\n.*?\\r?\\n---\\r?\\n", "").trim();
  }catch(java.io.IOException e){throw new IllegalStateException("商品生成Skill加载失败",e);}
 }
 public AiListingClient(){SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(120000);http=new RestTemplate(factory);}

 public String requestEndpoint(){String url=endpoint.trim();while(url.endsWith("/"))url=url.substring(0,url.length()-1);return url.endsWith("/v1")?url+"/responses":url;}
 public String configurationError(){
  if(apiKey.trim().isEmpty())return "AI服务未就绪：尚未配置服务端密钥";
  if(model.trim().isEmpty())return "AI服务未就绪：尚未配置模型名称";
  try{java.net.URI uri=java.net.URI.create(requestEndpoint());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||!uri.getPath().endsWith("/responses"))return "AI接口地址不完整，请填写HTTPS基础地址（以/v1结尾）或完整的/responses地址";}catch(Exception e){return "AI接口地址格式错误，请检查服务端endpoint配置";}
  return "";
 }
 public boolean configured(){return configurationError().isEmpty();}
 public void validateImages(long user,List<String> images)throws Exception {
  long total=0;for(String url:images){Path path=ImageStorage.ownedPath(Paths.get(imageRoot),user,url);long size=Files.size(path);total+=size;if(size<=0||size>5*1024*1024||total>20*1024*1024)throw new IllegalArgumentException("AI图片每张最多5MB，总计最多20MB");}
 }
 public JsonNode generate(long user,String text,List<String> images,List<Map<String,Object>> categories)throws Exception {
  if(!configured())throw new IllegalStateException(configurationError());
  validateImages(user,images);
  if(!endpoint.trim().toLowerCase(Locale.ROOT).startsWith("https://"))throw new IllegalStateException("AI接口必须使用HTTPS");
  ObjectNode request=json.createObjectNode();request.put("model",model.trim());request.put("store",false);request.put("max_output_tokens",1800);
  String instructions=listingSkill+"\n本次可选分类（id必须原样取自此清单）："+json.writeValueAsString(categories);
  ArrayNode content=json.createArrayNode();content.addObject().put("type","input_text").put("text",text);
  long total=0;
  for(String url:images){Path path=ImageStorage.ownedPath(Paths.get(imageRoot),user,url);long size=Files.size(path);total+=size;if(size>5*1024*1024||total>20*1024*1024)throw new IllegalArgumentException("图片超过AI处理大小限制");byte[] data=Files.readAllBytes(path);String mime=url.endsWith(".png")?"image/png":"image/jpeg";content.addObject().put("type","input_image").put("detail","low").put("image_url","data:"+mime+";base64,"+Base64.getEncoder().encodeToString(data));}
  ArrayNode input=request.putArray("input");input.addObject().put("role","system").put("content",instructions);input.addObject().put("role","user").set("content",content);
  ObjectNode schema=json.createObjectNode();schema.put("type","object");schema.put("additionalProperties",false);ObjectNode props=schema.putObject("properties");ArrayNode required=schema.putArray("required");
  for(String field:Arrays.asList("title","description","categoryId","brand","size","condition","price","originalPrice","measurements","defects")){props.putObject(field).put("type","string");required.add(field);}
  ArrayNode categoryIds=((ObjectNode)props.get("categoryId")).putArray("enum");categoryIds.add("");for(Map<String,Object> category:categories)categoryIds.add(category.get("id").toString());
  ((ObjectNode)props.get("condition")).putArray("enum").add("");
  for(String field:Arrays.asList("brand","size","measurements","defects"))((ObjectNode)props.get(field)).put("description","仅提取卖家说明中连续出现的原文片段；未明确提供则为空字符串，禁止依据图片或常识补全。");
  ((ObjectNode)props.get("price")).put("description","仅提取卖家明确售价的数字，否则为空字符串；尺码和使用次数不是售价。");
  ((ObjectNode)props.get("originalPrice")).put("description","仅提取卖家明确原价、购入价或入手价的数字；与售价分别填写，未提供则为空字符串。");
  ((ObjectNode)props.get("defects")).put("description","完整保留卖家对瑕疵和磨损的自述，包括没什么磨损、无瑕疵等否定描述和多处瑕疵；使用连续原文，不把没有说明改为无瑕疵。");
  props.putObject("warnings").put("type","array").putObject("items").put("type","string");required.add("warnings");
  ObjectNode format=request.putObject("text").putObject("format");format.put("type","json_schema");ObjectNode schemaConfig=format;
  String host=java.net.URI.create(requestEndpoint()).getHost();if(host!=null&&host.endsWith(".aliyuncs.com"))schemaConfig=format.putObject("json_schema");
  schemaConfig.put("name","listing_draft");schemaConfig.put("strict",true);schemaConfig.set("schema",schema);
  HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(apiKey.trim());
  String body=http.postForObject(requestEndpoint(),new HttpEntity<>(request.toString(),headers),String.class);
  if(body==null||body.length()>200000)throw new IllegalArgumentException("AI返回内容异常");
  JsonNode response=json.readTree(body);if(!"completed".equals(response.path("status").asText()))throw new IllegalArgumentException("AI生成未完成");
  for(JsonNode output:response.path("output"))for(JsonNode item:output.path("content"))if("output_text".equals(item.path("type").asText())){String result=item.path("text").asText();if(result.length()>12000)throw new IllegalArgumentException("AI内容过长");JsonNode parsed=json.readTree(result);if(parsed.isArray()&&parsed.size()==1)parsed=parsed.get(0);return parsed;}
  throw new IllegalArgumentException("AI未返回可用商品信息");
 }
}
