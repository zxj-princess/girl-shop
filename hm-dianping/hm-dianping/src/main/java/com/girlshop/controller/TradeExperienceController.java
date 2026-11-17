package com.girlshop.controller;
import com.girlshop.dto.Result;
import com.girlshop.service.TradeExperienceService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController @RequestMapping("/trade")
public class TradeExperienceController {
 @Resource private TradeExperienceService trade;
 @Resource private com.girlshop.realtime.RealtimeTickets tickets;
 @PostMapping("/realtime-ticket") public Result ticket(@RequestHeader("authorization") String token){return Result.ok(tickets.issue(com.girlshop.utils.UserHolder.getUser().getId(),token));}
 @GetMapping("/conversation/{peer}") public Result conversation(@PathVariable long peer,@RequestParam(defaultValue="0") long before,@RequestParam(defaultValue="0") long after){return trade.conversationPage(peer,before,after);}
 @PutMapping("/conversation/{peer}/read") public Result readConversation(@PathVariable long peer,@RequestBody JsonNode b){return trade.readMessages(peer,b);}
 @GetMapping("/peers/{id}") public Result peer(@PathVariable long id){return trade.peer(id);}
 @GetMapping("/peers/{id}/products") public Result products(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="1") int status,@RequestParam(required=false) Long owner){return owner==null?trade.peerProducts(id,page,status):trade.pickerProducts(id,owner,page,status);}
 @GetMapping("/peers/{id}/product-owner") public Result pickerOwner(@PathVariable long id,@RequestParam(defaultValue="0") long product){return trade.pickerOwner(id,product);}
 @GetMapping("/peers/{id}/product-context/{product}") public Result productContext(@PathVariable long id,@PathVariable long product){return trade.productContext(id,product);}
 @GetMapping("/message-counts") public Result counts(){return trade.messageCounts();}
 @GetMapping("/inbox") public Result inbox(@RequestParam(defaultValue="1") int page){return trade.inbox(page);}
 @PutMapping("/inbox/read-all") public Result readInbox(){return trade.readInbox("ALL",null);}
 @PutMapping("/inbox/{kind}/{id}/read") public Result readInbox(@PathVariable String kind,@PathVariable long id){return trade.readInbox(kind,id);}
 @GetMapping("/notices") public Result notices(@RequestParam(defaultValue="1") int page){return trade.notices(page);}
 @GetMapping("/unread") public Result unread(){return trade.unread();}
 @PutMapping("/notices/read-all") public Result readAll(){return trade.readNotice(null);}
 @PutMapping("/notices/{id}/read") public Result read(@PathVariable long id){return trade.readNotice(id);}
 @GetMapping("/contacts") public Result contacts(){return trade.contacts();}
 @GetMapping("/messages/{peer}") public Result messages(@PathVariable long peer,@RequestParam(defaultValue="0") long before){return trade.conversation(peer,before);}
 @PostMapping("/messages") public Result message(@RequestBody JsonNode b){return trade.message(b);}
 @GetMapping("/blocks") public Result blocks(){return trade.blocks();}
 @PutMapping("/blocks/{id}") public Result block(@PathVariable long id){return trade.block(id,true);}
 @DeleteMapping("/blocks/{id}") public Result unblock(@PathVariable long id){return trade.block(id,false);}
 @GetMapping("/orders/{id}/shipment") public Result shipment(@PathVariable long id){return trade.shipment(id);}
 @PostMapping("/orders/{id}/shipment") public Result ship(@PathVariable long id,@RequestBody JsonNode b){return trade.ship(id,b);}
 @GetMapping("/cases") public Result cases(){return trade.cases();}
 @GetMapping("/cases/{id}/events") public Result events(@PathVariable long id){return trade.events(id);}
 @PostMapping("/orders/{id}/cases") public Result apply(@PathVariable long id,@RequestBody JsonNode b){return trade.apply(id,b);}
 @PostMapping("/cases/{id}/action") public Result action(@PathVariable long id,@RequestBody JsonNode b){return trade.resolve(id,b);}
 @GetMapping("/reports") public Result reports(){return trade.reports();}
 @PostMapping("/reports") public Result report(@RequestBody JsonNode b){return trade.report(b);}
 @GetMapping("/drafts") public Result drafts(){return trade.drafts();}
 @GetMapping("/drafts/{id}") public Result draft(@PathVariable long id){return trade.draft(id);}
 @PostMapping("/drafts") public Result save(@RequestBody JsonNode b){return trade.saveDraft(null,b);}
 @PutMapping("/drafts/{id}") public Result update(@PathVariable long id,@RequestBody JsonNode b){return trade.saveDraft(id,b);}
 @DeleteMapping("/drafts/{id}") public Result delete(@PathVariable long id){return trade.deleteDraft(id);}
}
