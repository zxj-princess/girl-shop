package com.girlshop.service;

import com.girlshop.dto.Result;
import com.girlshop.entity.Order;
import com.girlshop.mapper.OrderMapper;
import com.girlshop.utils.UserHolder;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;

/** All changes use the same database transaction as their business event. */
@Service @DependsOn("prioritySchema")
public class TradeExperienceService {
 @Resource private JdbcTemplate jdbc;
 @Resource private OrderMapper orders;
 @Resource private UploadAssetService assets;
 @Resource private CacheEvictionService evictions;
 @Resource private com.girlshop.realtime.RealtimeHub realtime;
 private long me(){return UserHolder.getUser().getId();}
 private String text(JsonNode b,String key,int max){String s=b.path(key).asText("").trim();if(s.isEmpty()||s.length()>max){Map<String,String> labels=new HashMap<>();labels.put("reason","原因");labels.put("carrier","快递公司");labels.put("trackingNo","运单号");labels.put("content","消息内容");labels.put("action","操作");labels.put("note","处理说明");labels.put("targetType","举报类型");throw new IllegalArgumentException(labels.getOrDefault(key,"内容")+"不能为空，且不能超过"+max+"字");}return s;}
 private long number(JsonNode b,String key){long n=b.path(key).asLong(0);if(n<=0)throw new IllegalArgumentException("请选择有效对象");return n;}
 private List<String> images(JsonNode b){List<String> list=new ArrayList<>();JsonNode a=b.path("images");if(!a.isMissingNode()&&!a.isArray())throw new IllegalArgumentException("图片格式错误");for(JsonNode i:a){if(!i.isTextual())throw new IllegalArgumentException("图片格式错误");list.add(i.asText());}if(list.size()>5)throw new IllegalArgumentException("最多5张凭证图片");assets.claim(me(),list);return list;}
 private List<Map<String,Object>> rows(String sql,Object...args){return jdbc.queryForList(sql,args);}
 private Order ownOrder(long id){Order o=orders.lockById(id);if(o==null||(!o.getBuyerId().equals(me())&&!o.getSellerId().equals(me())))throw new IllegalArgumentException("订单不存在");return o;}
 public boolean activeCase(long order){return jdbc.queryForObject("SELECT COUNT(*) FROM tb_after_sale WHERE order_id=? AND status IN ('OPEN','RETURN_REQUIRED','RETURN_SENT','DISPUTED')",Integer.class,order)>0;}
 public void notice(long user,String title,String content,Long order){jdbc.update("INSERT INTO tb_trade_notice(user_id,title,content,order_id) VALUES(?,?,?,?)",user,title,content,order);realtime.changed(user);}
 public void productNotice(long user,String title,String content,long product){jdbc.update("INSERT INTO tb_trade_notice(user_id,title,content,product_id) VALUES(?,?,?,?)",user,title,content,product);realtime.changed(user);}
 public void orderEvent(Order o,String title){notice(o.getBuyerId(),title,"订单："+o.getOrderNo(),o.getId());notice(o.getSellerId(),title,"订单："+o.getOrderNo(),o.getId());}
 public Result notices(int page){return Result.ok(rows("SELECT CAST(id AS CHAR) id,title,content,CAST(order_id AS CHAR) orderId,is_read isRead,created_at createdAt FROM tb_trade_notice WHERE user_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",me(),offset(page)),jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice WHERE user_id=?",Long.class,me()));}
 private int offset(int p){return (Math.max(1,Math.min(p,10000))-1)*20;}
 public Result unread(){Map<String,Object> result=new HashMap<>();result.put("notices",jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_notice WHERE user_id=? AND is_read=0",Integer.class,me()));result.put("messages",jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE receiver_id=? AND is_read=0",Integer.class,me()));return Result.ok(result);}
 @Transactional public Result readNotice(Long id){if(id==null)jdbc.update("UPDATE tb_trade_notice SET is_read=1 WHERE user_id=?",me());else jdbc.update("UPDATE tb_trade_notice SET is_read=1 WHERE id=? AND user_id=?",id,me());realtime.changed(me());return Result.ok();}
 public Result contacts(){return Result.ok(rows("SELECT CAST(u.id AS CHAR) id,u.nick_name nickName,u.icon,CASE WHEN latest.content='' THEN CASE WHEN COALESCE(latest.images,'')<>'' THEN '[图片]' ELSE '商品咨询' END ELSE latest.content END lastMessage,latest.created_at lastTime,(SELECT COUNT(*) FROM tb_private_message unread WHERE unread.sender_id=u.id AND unread.receiver_id=? AND unread.is_read=0) unread FROM (SELECT IF(sender_id=?,receiver_id,sender_id) peer,MAX(id) latest FROM tb_private_message WHERE sender_id=? OR receiver_id=? GROUP BY peer) c JOIN tb_user u ON u.id=c.peer JOIN tb_private_message latest ON latest.id=c.latest ORDER BY c.latest DESC LIMIT 100",me(),me(),me(),me()));}
 public Result peer(long id){List<Map<String,Object>> found=rows("SELECT CAST(id AS CHAR) id,nick_name nickName,icon FROM tb_user WHERE id=?",id);if(found.isEmpty()||id==me())throw new IllegalArgumentException("该用户已不可用");Map<String,Object> p=found.get(0);p.put("blocked",jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_block WHERE user_id=? AND target_id=?",Integer.class,me(),id)>0);p.put("cannotSend",jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_block WHERE (user_id=? AND target_id=?) OR (user_id=? AND target_id=?)",Integer.class,me(),id,id,me())>0);return Result.ok(p);}
 public Result inbox(int page){String union="SELECT CAST(id AS CHAR) id,'TRADE' kind,CONVERT(title USING utf8mb4) COLLATE utf8mb4_unicode_ci title,CONVERT(content USING utf8mb4) COLLATE utf8mb4_unicode_ci content,CAST(order_id AS CHAR) orderId,CAST(product_id AS CHAR) productId,is_read isRead,created_at createdAt FROM tb_trade_notice WHERE user_id=? UNION ALL SELECT CAST(id AS CHAR) id,'NEW' kind,'关注卖家上新' title,CONVERT(title USING utf8mb4) COLLATE utf8mb4_unicode_ci content,NULL orderId,CAST(product_id AS CHAR) productId,is_read isRead,create_time createdAt FROM tb_user_notice WHERE user_id=?";List<Map<String,Object>> items=rows("SELECT * FROM ("+union+") n ORDER BY createdAt DESC,kind,id DESC LIMIT 20 OFFSET ?",me(),me(),offset(page));for(Map<String,Object> item:items){if(item.get("orderId")!=null){List<Map<String,Object>> detail=rows("SELECT o.product_title productTitle,o.product_image productImage,s.carrier,s.tracking_no trackingNo FROM tb_order o LEFT JOIN tb_trade_shipment s ON s.order_id=o.id WHERE o.id=? AND (o.buyer_id=? OR o.seller_id=?)",item.get("orderId"),me(),me());if(!detail.isEmpty())item.putAll(detail.get(0));}}return Result.ok(items,jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM tb_trade_notice WHERE user_id=?)+(SELECT COUNT(*) FROM tb_user_notice WHERE user_id=?)",Long.class,me(),me()));}
 @Transactional public Result readInbox(String kind,Long id){if("ALL".equals(kind)){readNotice(null);jdbc.update("UPDATE tb_user_notice SET is_read=1 WHERE user_id=?",me());}else if("TRADE".equals(kind))readNotice(id);else if("NEW".equals(kind))jdbc.update("UPDATE tb_user_notice SET is_read=1 WHERE id=? AND user_id=?",id,me());else throw new IllegalArgumentException("通知类型错误");realtime.changed(me());return Result.ok();}
 public Result messageCounts(){Map<String,Object> result=new HashMap<>((Map<String,Object>)unread().getData());result.put("newProducts",jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_notice WHERE user_id=? AND is_read=0",Integer.class,me()));return Result.ok(result);}
 @Transactional public Result conversation(long peer,long before){
  List<Map<String,Object>> list=rows("SELECT CAST(m.id AS CHAR) id,CAST(sender_id AS CHAR) senderId,content,m.images messageImages,CAST(product_id AS CHAR) productId,p.title productTitle,p.images productImages,m.created_at createdAt FROM tb_private_message m LEFT JOIN tb_product p ON p.id=m.product_id WHERE ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) AND m.id<? ORDER BY m.id DESC LIMIT 50",me(),peer,peer,me(),before<=0?Long.MAX_VALUE:before);
  // Only mark the messages actually returned; a concurrent arrival remains unread.
  if(!list.isEmpty()){
   List<Object> parameters=new ArrayList<>();parameters.add(me());for(Map<String,Object> r:list)parameters.add(r.get("id"));
   String placeholders=String.join(",",Collections.nCopies(list.size(),"?"));
   int changed=jdbc.update("UPDATE tb_private_message SET is_read=1 WHERE receiver_id=? AND is_read=0 AND id IN ("+placeholders+")",parameters.toArray());if(changed>0){realtime.changed(me());realtime.changed(peer);}
  }
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) AND id<?",Long.class,me(),peer,peer,me(),before<=0?Long.MAX_VALUE:before);
  Collections.reverse(list);return Result.ok(list,total);
 }
 public Result conversationPage(long peer,long before,long after){
  if(peer<=0||peer==me()||before<0||after<0||(before>0&&after>0))throw new IllegalArgumentException("会话游标错误");
  boolean forward=after>0;long cursor=forward?after:before>0?before:Long.MAX_VALUE;
  List<Map<String,Object>> list=rows("SELECT CAST(m.id AS CHAR) id,CAST(sender_id AS CHAR) senderId,CAST(receiver_id AS CHAR) receiverId,content,m.images messageImages,CAST(product_id AS CHAR) productId,p.title productTitle,p.images productImages,is_read isRead,m.created_at createdAt FROM tb_private_message m LEFT JOIN tb_product p ON p.id=m.product_id WHERE ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) AND m.id"+(forward?">":"<")+"? ORDER BY m.id "+(forward?"ASC":"DESC")+" LIMIT 51",me(),peer,peer,me(),cursor);
  boolean more=list.size()>50;if(more)list.remove(50);if(!forward)Collections.reverse(list);
  Map<String,Object> result=new HashMap<>();result.put("messages",list);result.put("hasMore",more);return Result.ok(result);
 }
 @Transactional public Result readMessages(long peer,JsonNode b){
  JsonNode ids=b.path("ids");if(!ids.isArray()||ids.size()>100)throw new IllegalArgumentException("已读消息编号格式错误");
  List<String> safe=new ArrayList<>();for(JsonNode id:ids){String value=id.asText();try{if(!value.matches("[0-9]{1,19}")||Long.parseLong(value)<=0)throw new IllegalArgumentException("消息编号错误");}catch(NumberFormatException e){throw new IllegalArgumentException("消息编号错误");}if(!safe.contains(value))safe.add(value);}
  if(safe.isEmpty())return Result.ok();
  String placeholders=String.join(",",Collections.nCopies(safe.size(),"?"));List<Object> parameters=new ArrayList<>();parameters.add(me());parameters.add(peer);parameters.addAll(safe);
  List<Map<String,Object>> visible=rows("SELECT CAST(id AS CHAR) id FROM tb_private_message WHERE receiver_id=? AND sender_id=? AND is_read=0 AND id IN ("+placeholders+") FOR UPDATE",parameters.toArray());
  if(!visible.isEmpty()){
   jdbc.update("UPDATE tb_private_message SET is_read=1 WHERE receiver_id=? AND sender_id=? AND is_read=0 AND id IN ("+placeholders+")",parameters.toArray());
   Map<String,Object> detail=new HashMap<>();detail.put("peerId",String.valueOf(me()));detail.put("ids",visible.stream().map(row->row.get("id").toString()).collect(java.util.stream.Collectors.toList()));
   realtime.changed(peer,"READ_CHANGED",detail);realtime.changed(me());
  }
  return Result.ok();
 }
 @Transactional public Result message(JsonNode b){long peer=number(b,"peerId");if(peer==me()||jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE id=?",Integer.class,peer)==0)throw new IllegalArgumentException("接收人不存在");
  String clientId=b.path("clientMessageId").asText("");if(!clientId.isEmpty()&&!clientId.matches("[A-Za-z0-9-]{8,64}"))throw new IllegalArgumentException("消息编号格式错误");
  String attachmentKey=b.path("images").isArray() && b.path("images").size()>0?b.path("images").toString()+"|":"";
  String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex(attachmentKey+peer+"|"+b.path("productId").asText("")+"|"+b.path("autoProduct").asBoolean(false)+"|"+b.path("content").asText("").trim());
  // Serialize both directions with block/unblock operations using user rows.
  jdbc.queryForList("SELECT id FROM tb_user WHERE id IN (?,?) ORDER BY id FOR UPDATE",me(),peer);
  if(!clientId.isEmpty()){List<Map<String,Object>> existing=rows("SELECT id,request_hash FROM tb_private_message WHERE sender_id=? AND client_message_id=? FOR UPDATE",me(),clientId);if(!existing.isEmpty()){if(!fingerprint.equals(existing.get(0).get("request_hash")))throw new IllegalArgumentException("同一消息编号不能用于不同内容");return Result.ok(Collections.singletonMap("messageId",existing.get(0).get("id").toString()));}}
  if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_block WHERE (user_id=? AND target_id=?) OR (user_id=? AND target_id=?)",Integer.class,me(),peer,peer,me())>0)throw new IllegalArgumentException("双方存在拉黑关系，无法发送消息");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE sender_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 MINUTE)",Integer.class,me())>=20)throw new IllegalArgumentException("发送过于频繁，请稍后重试");
  Long product=b.path("productId").asLong(0)>0?b.path("productId").asLong():null;
  if(product!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM tb_product WHERE id=? AND user_id IN (?,?)",Integer.class,product,me(),peer)==0)throw new IllegalArgumentException("商品不存在或不属于会话双方");
  List<String> attachments=images(b);
  String content=b.path("content").asText("").trim();if(content.length()>1000)throw new IllegalArgumentException("消息最多1000字");
  if(product==null&&content.isEmpty()&&attachments.isEmpty())throw new IllegalArgumentException("请输入消息或选择商品");
  // User row locks serialize both directions; a current read also covers older history.
  if(product!=null&&b.path("autoProduct").asBoolean(false)&&!rows("SELECT id FROM tb_private_message WHERE product_id=? AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) LIMIT 1 FOR UPDATE",product,me(),peer,peer,me()).isEmpty())product=null;
  if(product==null&&content.isEmpty()&&attachments.isEmpty())return Result.ok();
  final Long attached=product;final long sender=me();
  org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
  jdbc.update(conn->{java.sql.PreparedStatement st=conn.prepareStatement("INSERT INTO tb_private_message(sender_id,receiver_id,content,product_id,client_message_id,request_hash,images) VALUES(?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);st.setLong(1,sender);st.setLong(2,peer);st.setString(3,content);st.setObject(4,attached);st.setString(5,clientId.isEmpty()?null:clientId);st.setString(6,fingerprint);st.setString(7,String.join(",",attachments));return st;},key);
  String messageId=String.valueOf(key.getKey().longValue());
  Map<String,Object> toReceiver=new HashMap<>();toReceiver.put("peerId",String.valueOf(sender));toReceiver.put("messageId",messageId);
  Map<String,Object> toSender=new HashMap<>();toSender.put("peerId",String.valueOf(peer));toSender.put("messageId",messageId);
  realtime.changed(peer,"MESSAGE_CHANGED",toReceiver);realtime.changed(sender,"MESSAGE_CHANGED",toSender);
  return Result.ok(Collections.singletonMap("messageId",messageId));
 }
 public Result productContext(long peer,long product){peer(peer);if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_product WHERE id=? AND user_id IN (?,?)",Integer.class,product,me(),peer)==0)throw new IllegalArgumentException("商品不存在或不属于会话双方");Map<String,Object> result=new HashMap<>();result.put("sent",jdbc.queryForObject("SELECT COUNT(*) FROM tb_private_message WHERE product_id=? AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",Integer.class,product,me(),peer,peer,me())>0);return Result.ok(result);}
 public Result pickerOwner(long peer,long product){
  peer(peer);List<Map<String,Object>> found;
  if(product>0){found=rows("SELECT CAST(user_id AS CHAR) ownerId FROM tb_product WHERE id=? AND user_id IN (?,?) AND status IN (0,1,2)",product,me(),peer);if(found.isEmpty())throw new IllegalArgumentException("商品不存在或不属于会话双方");}
  else found=rows("SELECT CAST(p.user_id AS CHAR) ownerId FROM tb_private_message m JOIN tb_product p ON p.id=m.product_id WHERE ((m.sender_id=? AND m.receiver_id=?) OR (m.sender_id=? AND m.receiver_id=?)) AND p.user_id IN (?,?) AND p.status IN (0,1,2) ORDER BY m.id DESC LIMIT 1",me(),peer,peer,me(),me(),peer);
  Map<String,Object> result=new HashMap<>();result.put("ownerId",found.isEmpty()?null:found.get(0).get("ownerId"));return Result.ok(result);
 }
 public Result peerProducts(long peer,int page,int status){return pickerProducts(peer,peer,page,status);}
 public Result pickerProducts(long peer,long owner,int page,int status){peer(peer);if(owner!=me()&&owner!=peer)throw new IllegalArgumentException("商品必须属于会话双方");if(!Arrays.asList(-1,0,1,2).contains(status))throw new IllegalArgumentException("商品状态错误");String filter=status==-1?"":" AND status="+status+(status==1?" AND stock>0":"");String where=" FROM tb_product WHERE user_id=? AND status IN (0,1,2)"+filter;return Result.ok(rows("SELECT CAST(id AS CHAR) id,title,images,price,status,stock"+where+" ORDER BY CASE WHEN status=1 AND stock>0 THEN 0 WHEN status=2 THEN 1 ELSE 2 END,create_time DESC,id DESC LIMIT 12 OFFSET ?",owner,(Math.max(1,Math.min(page,10000))-1)*12),jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,owner));}
 public Result blocks(){return Result.ok(rows("SELECT CAST(b.target_id AS CHAR) id,u.nick_name nickName FROM tb_user_block b LEFT JOIN tb_user u ON u.id=b.target_id WHERE b.user_id=?",me()));}
 @Transactional public Result block(long peer,boolean add){if(peer==me()||jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE id=?",Integer.class,peer)==0)throw new IllegalArgumentException("用户不存在");jdbc.queryForList("SELECT id FROM tb_user WHERE id IN (?,?) ORDER BY id FOR UPDATE",me(),peer);if(add)jdbc.update("INSERT IGNORE INTO tb_user_block(user_id,target_id) VALUES(?,?)",me(),peer);else jdbc.update("DELETE FROM tb_user_block WHERE user_id=? AND target_id=?",me(),peer);realtime.changed(me());realtime.changed(peer);return Result.ok();}
 public Result shipment(long id){Order o=orders.selectById(id);if(o==null||(!o.getBuyerId().equals(me())&&!o.getSellerId().equals(me())))throw new IllegalArgumentException("订单不存在");return Result.ok(rows("SELECT carrier,tracking_no trackingNo,created_at createdAt FROM tb_trade_shipment WHERE order_id=?",id));}
 private String tracking(JsonNode b,String key){String s=text(b,key,64);if(!s.matches("[A-Za-z0-9-]{5,64}"))throw new IllegalArgumentException("运单号需为5至64位字母、数字或连字符");return s;}
 @Transactional public Result ship(long id,JsonNode b){Order o=ownOrder(id);if(!o.getSellerId().equals(me())||o.getStatus()!=1||activeCase(id))throw new IllegalArgumentException("订单不可发货，或有待处理售后");jdbc.update("INSERT INTO tb_trade_shipment(order_id,carrier,tracking_no) VALUES(?,?,?)",id,text(b,"carrier",40),tracking(b,"trackingNo"));jdbc.update("UPDATE tb_order SET status=2,ship_time=NOW() WHERE id=? AND status=1",id);orderEvent(o,"商品已发货");return Result.ok();}
 public Result cases(){return Result.ok(rows("SELECT CAST(id AS CHAR) id,CAST(order_id AS CHAR) orderId,CAST(buyer_id AS CHAR) buyerId,CAST(seller_id AS CHAR) sellerId,reason,images,status,reply,return_carrier returnCarrier,return_tracking returnTracking,updated_at updatedAt FROM tb_after_sale WHERE buyer_id=? OR seller_id=? ORDER BY id DESC LIMIT 100",me(),me()));}
 public Result events(long id){if(rows("SELECT id FROM tb_after_sale WHERE id=? AND (buyer_id=? OR seller_id=?)",id,me(),me()).isEmpty())throw new IllegalArgumentException("售后不存在");return Result.ok(rows("SELECT action,note,created_at createdAt FROM tb_after_sale_event WHERE case_id=? ORDER BY id",id));}
 private void event(long id,String action,String note){jdbc.update("INSERT INTO tb_after_sale_event(case_id,actor_id,action,note) VALUES(?,?,?,?)",id,me(),action,note);}
 @Transactional public Result apply(long order,JsonNode b){Order o=ownOrder(order);if(!o.getBuyerId().equals(me())||o.getStatus()<1||o.getStatus()>3)throw new IllegalArgumentException("只有买家可对已付款订单申请售后");String reason=text(b,"reason",1000);String imgs=String.join(",",images(b));List<Map<String,Object>> prior=rows("SELECT id,status FROM tb_after_sale WHERE order_id=? FOR UPDATE",order);long id;
  if(prior.isEmpty()){jdbc.update("INSERT INTO tb_after_sale(order_id,buyer_id,seller_id,reason,images) VALUES(?,?,?,?,?)",order,me(),o.getSellerId(),reason,imgs);id=jdbc.queryForObject("SELECT id FROM tb_after_sale WHERE order_id=?",Long.class,order);}else{if(!"REJECTED".equals(prior.get(0).get("status")))throw new IllegalArgumentException("该订单已有售后记录");id=((Number)prior.get(0).get("id")).longValue();jdbc.update("UPDATE tb_after_sale SET status='OPEN',reason=?,images=?,reply=NULL,updated_at=NOW() WHERE id=?",reason,imgs,id);}
  event(id,"APPLY",reason);notice(o.getSellerId(),"收到售后申请",reason,order);return Result.ok(String.valueOf(id));
 }
 @Transactional public Result resolve(long id,JsonNode b){List<Map<String,Object>> found=rows("SELECT order_id FROM tb_after_sale WHERE id=? AND (buyer_id=? OR seller_id=?)",id,me(),me());if(found.isEmpty())throw new IllegalArgumentException("售后不存在");long oid=((Number)found.get(0).get("order_id")).longValue();Order o=ownOrder(oid);Map<String,Object> c=rows("SELECT * FROM tb_after_sale WHERE id=? FOR UPDATE",id).get(0);String status=(String)c.get("status"),action=text(b,"action",32),next;String note=text(b,"note",900);
  if("DISPUTE".equals(action)&&o.getBuyerId().equals(me())&&"REJECTED".equals(status)){next="DISPUTED";}
  else if("RETURN".equals(action)&&o.getBuyerId().equals(me())&&"RETURN_REQUIRED".equals(status)){next="RETURN_SENT";jdbc.update("UPDATE tb_after_sale SET return_carrier=?,return_tracking=? WHERE id=?",text(b,"carrier",40),tracking(b,"trackingNo"),id);}
  else if(o.getSellerId().equals(me())&&"REJECT".equals(action)&&"OPEN".equals(status)){next="REJECTED";}
  else if(o.getSellerId().equals(me())&&"ACCEPT".equals(action)&&"OPEN".equals(status)){next=o.getStatus()==1?"RESOLVED":"RETURN_REQUIRED";}
  else if(o.getSellerId().equals(me())&&"RECEIVED_RETURN".equals(action)&&"RETURN_SENT".equals(status)){next="RESOLVED";}
  else throw new IllegalArgumentException("无权操作或售后状态已变化");
  if("RESOLVED".equals(next)){if(o.getStatus()<1||o.getStatus()>3)throw new IllegalArgumentException("订单状态已变化");jdbc.update("UPDATE tb_order SET status=4,close_time=NOW() WHERE id=?",oid);jdbc.update("UPDATE tb_product SET stock=stock+1,sold=GREATEST(sold-1,0),status=IF(status=2,1,status) WHERE id=?",o.getProductId());jdbc.update("UPDATE tb_flash_request SET status='CANCELLED',updated_at=NOW() WHERE id=? AND status='SUCCESS'",oid);evictions.enqueue(o.getProductId());note="[站内模拟结案，未执行真实退款] "+note;}
  jdbc.update("UPDATE tb_after_sale SET status=?,reply=?,updated_at=NOW() WHERE id=?",next,note,id);event(id,action,note);orderEvent(o,"售后进度更新："+next);return Result.ok();
 }
 @Transactional public Result report(JsonNode b){String type=text(b,"targetType",16);long target=number(b,"targetId");String table;if("PRODUCT".equals(type))table="tb_product";else if("USER".equals(type))table="tb_user";else if("MESSAGE".equals(type)){if(rows("SELECT id FROM tb_private_message WHERE id=? AND (sender_id=? OR receiver_id=?)",target,me(),me()).isEmpty())throw new IllegalArgumentException("消息不存在");table="tb_private_message";}else throw new IllegalArgumentException("举报类型错误");if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Integer.class,target)==0)throw new IllegalArgumentException("举报对象不存在");if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_trade_report WHERE user_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 DAY)",Integer.class,me())>=20)throw new IllegalArgumentException("今日举报次数已达上限");jdbc.update("INSERT INTO tb_trade_report(user_id,target_type,target_id,reason,images) VALUES(?,?,?,?,?)",me(),type,target,text(b,"reason",1000),String.join(",",images(b)));return Result.ok();}
 public Result reports(){return Result.ok(rows("SELECT CAST(id AS CHAR) id,target_type targetType,CAST(target_id AS CHAR) targetId,reason,status,result,created_at createdAt FROM tb_trade_report WHERE user_id=? ORDER BY id DESC LIMIT 100",me()));}
 public Result drafts(){
  List<Map<String,Object>> list=rows("SELECT CAST(d.id AS CHAR) id,d.title,d.payload,d.updated_at updatedAt,j.status aiStatus FROM tb_product_draft d LEFT JOIN tb_ai_listing_job j ON j.id=(SELECT x.id FROM tb_ai_listing_job x WHERE x.draft_id=d.id AND x.user_id=d.user_id ORDER BY x.created_at DESC,x.updated_at DESC LIMIT 1) WHERE d.user_id=? AND NOT EXISTS (SELECT 1 FROM tb_draft_publication p WHERE p.draft_id=d.id) ORDER BY d.updated_at DESC LIMIT 100",me());
  com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  for(Map<String,Object> item:list){try{JsonNode data=mapper.readTree(item.remove("payload").toString());item.put("image",data.path("images").path(0).asText(""));item.put("summary",data.path("description").asText(""));List<String> missing=new ArrayList<>();if(data.path("price").asDouble(0)<=0)missing.add("价格");if(data.path("size").asText("").isEmpty())missing.add("尺码");if(data.path("condition").asInt(0)<=0)missing.add("成色");if(data.path("categoryId").asLong(0)<=0)missing.add("分类");item.put("missing",missing);}catch(Exception e){item.remove("payload");item.put("summary","请继续完善商品信息");}}
  return Result.ok(list);
 }
 public Result draft(long id){List<Map<String,Object>> list=rows("SELECT CAST(id AS CHAR) id,payload FROM tb_product_draft WHERE id=? AND user_id=?",id,me());if(list.isEmpty())throw new IllegalArgumentException("草稿不存在");return Result.ok(list.get(0));}
 @Transactional public Result saveDraft(Long id,JsonNode b){if(id!=null&&!jdbc.queryForList("SELECT draft_id FROM tb_draft_publication WHERE draft_id=? AND user_id=?",id,me()).isEmpty())throw new IllegalArgumentException("该草稿已经发布，请从我的商品编辑");if(b.toString().length()>20000||!b.isObject())throw new IllegalArgumentException("草稿内容过长或格式错误");if(id!=null){List<String> previous=jdbc.queryForList("SELECT payload FROM tb_product_draft WHERE id=? AND user_id=? FOR UPDATE",String.class,id,me());if(!previous.isEmpty())try{JsonNode old=new com.fasterxml.jackson.databind.ObjectMapper().readTree(previous.get(0));if(old.has("aiSourceText"))for(String field:Arrays.asList("title","description","categoryId","price","originalPrice","size","condition","brand","measurements","defects"))if(!old.path(field).equals(b.path(field)))jdbc.update("INSERT INTO tb_ai_field_edit(draft_id,field_name) VALUES(?,?) ON DUPLICATE KEY UPDATE edits=edits+1",id,field);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("草稿格式错误");}}
String title=b.path("title").asText("").trim();if(title.length()>100)throw new IllegalArgumentException("标题最多100字");images(b);if(id==null){if(jdbc.queryForObject("SELECT COUNT(*) FROM tb_product_draft WHERE user_id=?",Integer.class,me())>=100)throw new IllegalArgumentException("草稿最多100份");org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();final String t=title.isEmpty()?"未命名草稿":title;jdbc.update(conn->{java.sql.PreparedStatement p=conn.prepareStatement("INSERT INTO tb_product_draft(user_id,title,payload) VALUES(?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);p.setLong(1,me());p.setString(2,t);p.setString(3,b.toString());return p;},key);id=key.getKey().longValue();}else if(jdbc.update("UPDATE tb_product_draft SET title=?,payload=?,updated_at=NOW() WHERE id=? AND user_id=?",title.isEmpty()?"未命名草稿":title,b.toString(),id,me())==0)throw new IllegalArgumentException("草稿不存在");return Result.ok(String.valueOf(id));}
 public Result deleteDraft(long id){jdbc.update("DELETE FROM tb_product_draft WHERE id=? AND user_id=?",id,me());return Result.ok();}
}
