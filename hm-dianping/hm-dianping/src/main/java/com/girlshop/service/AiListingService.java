package com.girlshop.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.girlshop.dto.*;
import com.girlshop.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import javax.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;

@Service @DependsOn("prioritySchema") @lombok.extern.slf4j.Slf4j
public class AiListingService {
 @Resource private JdbcTemplate jdbc;
 @Resource private ObjectMapper json;
 @Resource private TradeExperienceService trade;
 @Resource private AiListingClient client;
 @Resource private com.girlshop.realtime.RealtimeHub realtime;
 @Resource private IProductService products;
 @Value("${shop.ai.daily-limit:10}") private int dailyLimit;
 @Value("${shop.ai.worker.enabled:true}") private boolean workerEnabled;
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
 @PreDestroy public void close(){executor.shutdownNow();}
 private long me(){return UserHolder.getUser().getId();}
 public Result config(){Map<String,Object> result=new HashMap<>();result.put("configured",client.configured());result.put("dailyLimit",dailyLimit);result.put("reason",client.configurationError());return Result.ok(result);}
 @Transactional public Result create(JsonNode input){
  if(!client.configured())throw new IllegalArgumentException(client.configurationError());
  long user=me();String text=input.path("text").asText("").trim(),key=input.path("requestId").asText("");
  if(!input.path("consent").asBoolean(false))throw new IllegalArgumentException("请确认同意将商品图片和文字提交给AI服务");
  if(text.isEmpty()||text.length()>2000||!key.matches("[A-Za-z0-9-]{8,64}"))throw new IllegalArgumentException("请填写1至2000字的商品说明");
  JsonNode images=input.path("images");if(!images.isArray()||images.size()<1||images.size()>5)throw new IllegalArgumentException("请选择1至5张商品图片");
  for(JsonNode image:images)if(!image.isTextual())throw new IllegalArgumentException("图片格式错误");
  try{List<String> owned=new ArrayList<>();images.forEach(n->owned.add(n.asText()));client.validateImages(user,owned);}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("图片不可用，请重新上传");}
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(text+"|"+images.toString()+(!input.path("retryJobId").asText("").isEmpty()?"|"+input.path("retryJobId").asText()+"|"+input.path("overwriteConfirmed").asBoolean(false):""));
  jdbc.queryForList("SELECT id FROM tb_user WHERE id=? FOR UPDATE",user);
  List<Map<String,Object>> old=jdbc.queryForList("SELECT id,input_hash FROM tb_ai_listing_job WHERE user_id=? AND request_id=?",user,key);
  if(!old.isEmpty()){if(!hash.equals(old.get(0).get("input_hash")))throw new IllegalArgumentException("请求编号与内容不匹配");return status(old.get(0).get("id").toString());}
  if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_ai_listing_job WHERE user_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 DAY) AND (error_message IS NULL OR (error_message NOT LIKE 'AI接口地址配置错误%' AND error_message NOT LIKE 'AI服务未配置%'))",Integer.class,user)>=dailyLimit)throw new IllegalArgumentException("今日生成次数已达上限，请使用手动发布");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_ai_listing_job WHERE user_id=? AND status IN ('QUEUED','RUNNING')",Integer.class,user)>0)throw new IllegalArgumentException("已有商品正在生成，请先查看原任务");
  ObjectNode draft=json.createObjectNode();draft.put("title","待整理的商品");draft.put("description",text.substring(0,Math.min(500,text.length())));draft.put("aiSourceText",text);draft.set("images",images.deepCopy());draft.put("aiReviewRequired",true);
  Long reuse=null;String retryId=input.path("retryJobId").asText("");
  if(!retryId.isEmpty()){
   List<Map<String,Object>> jobs=jdbc.queryForList("SELECT draft_id,input_payload,status FROM tb_ai_listing_job WHERE id=? AND user_id=? FOR UPDATE",retryId,user);
   if(jobs.isEmpty()||!"FAILED".equals(jobs.get(0).get("status")))throw new IllegalArgumentException("只能重试本人失败的生成任务");
   reuse=((Number)jobs.get(0).get("draft_id")).longValue();
   List<Map<String,Object>> saved=jdbc.queryForList("SELECT payload FROM tb_product_draft WHERE id=? AND user_id=? FOR UPDATE",reuse,user);
   if(saved.isEmpty())throw new IllegalArgumentException("原草稿已删除，请重新创建");
   if(!jdbc.queryForList("SELECT draft_id FROM tb_draft_publication WHERE draft_id=?",reuse).isEmpty())throw new IllegalArgumentException("该草稿已发布，不能重新生成");
   try{String original=json.readTree(jobs.get(0).get("input_payload").toString()).path("originalDraft").asText();if(!saved.get(0).get("payload").toString().equals(original)&&!input.path("overwriteConfirmed").asBoolean(false))throw new IllegalArgumentException("草稿已有修改，请确认覆盖后重新生成");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("原任务数据不可用");}
  }
  long draftId=Long.parseLong(trade.saveDraft(reuse,draft).getData().toString());String id=UUID.randomUUID().toString();ObjectNode payload=json.createObjectNode();payload.put("text",text);payload.set("images",images.deepCopy());payload.put("originalDraft",draft.toString());
  jdbc.update("INSERT INTO tb_ai_listing_job(id,user_id,request_id,input_hash,input_payload,draft_id,status,error_message) VALUES(?,?,?,?,?,?,?,?)",id,user,key,hash,payload.toString(),draftId,"QUEUED",null);return status(id);
 }
 public Result status(String id){
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT j.id,j.status,CAST(j.draft_id AS CHAR) draftId,j.error_message errorMessage,j.input_payload inputPayload,d.payload,d.title FROM tb_ai_listing_job j LEFT JOIN tb_product_draft d ON d.id=j.draft_id AND d.user_id=j.user_id WHERE j.id=? AND j.user_id=?",id,me());
  if(rows.isEmpty())throw new IllegalArgumentException("生成任务不存在");Map<String,Object> result=rows.get(0);
  try{JsonNode draft=json.readTree(Objects.toString(result.remove("payload"),"{}"));JsonNode input=json.readTree(result.remove("inputPayload").toString());result.put("image",draft.path("images").path(0).asText(""));result.put("draftEdited",!draft.toString().equals(json.readTree(input.path("originalDraft").asText("{}")).toString()));List<String> missing=new ArrayList<>();for(String key:Arrays.asList("price","size","condition","categoryId"))if(draft.path(key).isNull()||draft.path(key).isMissingNode()||draft.path(key).asText().isEmpty())missing.add(new HashMap<String,String>(){{put("price","价格");put("size","尺码");put("condition","成色");put("categoryId","分类");}}.get(key));result.put("missing",missing);}catch(Exception e){result.remove("inputPayload");result.remove("payload");}
  if("READY".equals(result.get("status")))result.put("previewUrl","listing-preview.html?draft="+result.get("draftId"));return Result.ok(result);
 }
 @Scheduled(fixedDelay=2000) public void dispatch(){
  if(!workerEnabled)return;
  jdbc.update("UPDATE tb_ai_listing_job SET status='FAILED',error_message='生成超时或服务已重启，原始草稿已保留',updated_at=NOW() WHERE status='RUNNING' AND updated_at<DATE_SUB(NOW(),INTERVAL 3 MINUTE)");
  if(executor.getActiveCount()>=2||!executor.getQueue().isEmpty())return;
  List<Map<String,Object>> queued=jdbc.queryForList("SELECT * FROM tb_ai_listing_job WHERE status='QUEUED' ORDER BY created_at LIMIT 1");if(queued.isEmpty())return;Map<String,Object> job=queued.get(0);String id=job.get("id").toString();
  if(jdbc.update("UPDATE tb_ai_listing_job SET status='RUNNING',updated_at=NOW() WHERE id=? AND status='QUEUED'",id)==0)return;
  try{executor.execute(()->run(job));}catch(RejectedExecutionException e){jdbc.update("UPDATE tb_ai_listing_job SET status='QUEUED' WHERE id=? AND status='RUNNING'",id);}
 }
 void run(Map<String,Object> job){long user=((Number)job.get("user_id")).longValue();String id=job.get("id").toString();UserDTO identity=new UserDTO();identity.setId(user);UserHolder.saveUser(identity);
  try{JsonNode input=json.readTree(job.get("input_payload").toString());List<String> images=new ArrayList<>();input.path("images").forEach(n->images.add(n.asText()));List<Map<String,Object>> categories=jdbc.queryForList("SELECT CAST(id AS CHAR) id,name FROM tb_category ORDER BY sort,id");JsonNode generated=client.generate(user,input.path("text").asText(),images,categories);ObjectNode draft=normalize(generated,input,categories);
   // Never overwrite a draft the user has already edited or published while generation was running.
   List<Map<String,Object>> saved=jdbc.queryForList("SELECT payload FROM tb_product_draft WHERE id=? AND user_id=?",job.get("draft_id"),user);if(saved.isEmpty())throw new IllegalArgumentException("草稿已删除");String previous=saved.get(0).get("payload").toString();JsonNode prior=json.readTree(previous);
   if(!previous.equals(input.path("originalDraft").asText()))throw new IllegalArgumentException("草稿已编辑，未覆盖修改");
   int changed=jdbc.update("UPDATE tb_product_draft SET title=?,payload=?,updated_at=NOW() WHERE id=? AND user_id=? AND BINARY payload=BINARY ? AND NOT EXISTS (SELECT 1 FROM tb_draft_publication WHERE draft_id=?) AND EXISTS (SELECT 1 FROM tb_ai_listing_job WHERE id=? AND status='RUNNING')",draft.path("title").asText(),draft.toString(),job.get("draft_id"),user,previous,job.get("draft_id"),id);if(changed==0)throw new IllegalArgumentException("草稿已变化，未覆盖修改");
   jdbc.update("UPDATE tb_ai_listing_job SET status='READY',updated_at=NOW() WHERE id=? AND status='RUNNING'",id);
  }catch(Exception e){log.warn("AI商品整理失败 job={} type={} cause={}",id,e.getClass().getSimpleName(),failureCode(e));jdbc.update("UPDATE tb_ai_listing_job SET status='FAILED',error_message=?,updated_at=NOW() WHERE id=? AND status='RUNNING'",failureMessage(e),id);}finally{try{realtime.changed(user,"AI_LISTING_CHANGED",Collections.singletonMap("jobId",id));}finally{UserHolder.removeUser();}}
 }
 static String failureCode(Throwable error){
  for(int i=0;error!=null&&i<16;i++,error=error.getCause()){
   if(error instanceof java.net.UnknownHostException)return "DNS_FAILED";
   if(error instanceof javax.net.ssl.SSLException)return "TLS_FAILED";
   if(error instanceof java.net.SocketTimeoutException)return "NETWORK_TIMEOUT";
   if(error instanceof java.net.ConnectException)return "CONNECTION_FAILED";
  }
  return "OTHER";
 }
 static String failureMessage(Exception error){
  String reason="AI整理未完成，请重试或继续手动编辑";
  if(error instanceof org.springframework.web.client.HttpStatusCodeException){int status=((org.springframework.web.client.HttpStatusCodeException)error).getRawStatusCode();switch(status){case 401:reason="AI密钥无效或已过期，请检查服务端配置";break;case 403:reason="AI服务访问被拒绝，请检查模型权限及密钥地域";break;case 404:reason="AI接口或模型不存在，请检查endpoint和model";break;case 400:case 422:reason="AI服务不接受当前请求，请检查模型的图片识别和结构化输出支持";break;case 429:reason="AI服务调用受限或额度不足，请检查配额后重试";break;default:reason="AI服务暂时不可用，请稍后重试";}}
  else if(error instanceof org.springframework.web.client.ResourceAccessException){switch(failureCode(error)){
   case "DNS_FAILED":reason="AI服务域名解析失败，请检查服务端DNS和接口域名";break;
   case "TLS_FAILED":reason="AI服务HTTPS连接失败，请检查Java证书和网络代理";break;
   case "NETWORK_TIMEOUT":reason="AI服务响应超时，本次未生成详情，请稍后重试";break;
   case "CONNECTION_FAILED":reason="无法建立AI服务连接，请检查服务端网络和接口地址";break;
   default:reason="无法连接AI服务或请求超时，请检查网络和endpoint";
  }}
  else if("AI接口必须使用HTTPS".equals(error.getMessage()))reason="AI接口地址配置错误，endpoint应填写完整HTTPS地址，不能只填业务空间ID";
  else if("AI服务未配置".equals(error.getMessage()))reason="AI服务未配置，请检查服务端密钥和模型名称";
  else if("AI生成未完成".equals(error.getMessage()))reason="AI返回未完成的结果，可能达到输出限制，请减少说明或检查模型配置";
  else if("草稿已编辑，未覆盖修改".equals(error.getMessage())||"草稿已变化，未覆盖修改".equals(error.getMessage()))reason="草稿已有手动修改，AI结果未覆盖，请继续编辑";
  return reason+"；原始图片和说明已保留";
 }
 ObjectNode normalize(JsonNode result,JsonNode input,List<Map<String,Object>> categories){
  if(!result.isObject())throw new IllegalArgumentException("AI结果格式错误");ObjectNode draft=json.createObjectNode();
  for(String key:Arrays.asList("title","description","brand","size","measurements","defects")){JsonNode n=result.path(key);if(!n.isTextual())throw new IllegalArgumentException("AI字段格式错误");String value=n.asText().trim();int max="title".equals(key)?100:"size".equals(key)?20:"brand".equals(key)?100:500;if(value.length()>max)throw new IllegalArgumentException("AI字段过长");draft.put(key,value);}
  if(draft.path("title").asText().isEmpty())draft.put("title","待确认的商品");String source=input.path("text").asText();
  for(String key:Arrays.asList("brand","size","measurements","defects"))if(!source.toLowerCase(Locale.ROOT).contains(draft.path(key).asText().toLowerCase(Locale.ROOT)))draft.put(key,"");
  String category=result.path("categoryId").asText("");if(categories.stream().anyMatch(c->category.equals(c.get("id").toString())))draft.put("categoryId",category);else draft.putNull("categoryId");
  Integer condition=com.girlshop.utils.ListingConditionParser.parse(source);
  if(condition==null)draft.putNull("condition");else draft.put("condition",condition);
  draft.putNull("price");String price=result.path("price").asText("");java.util.regex.Matcher resale=java.util.regex.Pattern.compile("(?<![0-9.])([0-9]{1,6}(?:\\.[0-9]{1,2})?)\\s*(?:元|块)?\\s*出(?:售)?(?![0-9])").matcher(source);String resalePrice=null;int resaleCount=0;while(resale.find()){resalePrice=resale.group(1);resaleCount++;}if(resaleCount==1){java.math.BigDecimal amount=new java.math.BigDecimal(resalePrice);if(amount.signum()>0)draft.put("price",amount);}
  if(price.matches("[0-9]{1,6}(\\.[0-9]{1,2})?")){BigDecimal amount=new BigDecimal(price);java.util.regex.Matcher numbers=java.util.regex.Pattern.compile("(?:卖|价格|售价|定价|¥|￥)\\s*([0-9]+(?:\\.[0-9]+)?)|([0-9]+(?:\\.[0-9]+)?)\\s*(?:元|块)").matcher(source);while(numbers.find())if(new BigDecimal(numbers.group(1)!=null?numbers.group(1):numbers.group(2)).compareTo(amount)==0&&amount.signum()>0&&amount.compareTo(new BigDecimal("999999.99"))<=0){draft.put("price",amount);break;}}
  ArrayNode warnings=draft.putArray("aiWarnings");warnings.add("请确认价格、尺码、成色、品牌和瑕疵；图片识别可能有误。");JsonNode notes=result.path("warnings");if(notes.isArray())for(JsonNode n:notes)if(n.isTextual()&&warnings.size()<8)warnings.add(n.asText().substring(0,Math.min(200,n.asText().length())));
  draft.set("images",input.path("images").deepCopy());draft.put("aiSourceText",source);draft.put("aiReviewRequired",true);return draft;
 }
 @Transactional public Result publish(long id){
  long user=me();List<Map<String,Object>> old=jdbc.queryForList("SELECT CAST(product_id AS CHAR) productId FROM tb_draft_publication WHERE draft_id=? AND user_id=?",id,user);if(!old.isEmpty())return Result.ok(old.get(0).get("productId"));
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT payload FROM tb_product_draft WHERE id=? AND user_id=? FOR UPDATE",id,user);if(rows.isEmpty())throw new IllegalArgumentException("草稿不存在");
  old=jdbc.queryForList("SELECT CAST(product_id AS CHAR) productId FROM tb_draft_publication WHERE draft_id=? AND user_id=? FOR UPDATE",id,user);if(!old.isEmpty())return Result.ok(old.get(0).get("productId"));
  try{JsonNode node=json.readTree(rows.get(0).get("payload").toString());if(node.path("images").size()<1||node.path("description").asText().trim().isEmpty())throw new IllegalArgumentException("请补充商品图片和描述");ProductFormDTO form=json.treeToValue(((ObjectNode)json.readTree(rows.get(0).get("payload").toString())).retain("categoryId","title","description","brand","size","condition","price","originalPrice","images","measurements","defects"),ProductFormDTO.class);Result result=products.publish(form);if(!Boolean.TRUE.equals(result.getSuccess()))return result;jdbc.update("INSERT INTO tb_draft_publication(draft_id,user_id,product_id) VALUES(?,?,?)",id,user,result.getData());return result;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("草稿内容格式错误");}
 }
}
