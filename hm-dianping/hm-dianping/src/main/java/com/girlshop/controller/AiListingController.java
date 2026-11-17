package com.girlshop.controller;
import com.girlshop.dto.Result;
import com.girlshop.service.AiListingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
@RestController @RequestMapping("/ai/listings")
public class AiListingController {
 @Resource private AiListingService service;
 @GetMapping("/config") public Result config(){return service.config();}
 @PostMapping public Result create(@RequestBody JsonNode input){return service.create(input);}
 @GetMapping("/{id}") public Result status(@PathVariable String id){return service.status(id);}
 @PostMapping("/drafts/{id}/publish") public Result publish(@PathVariable long id,@RequestBody JsonNode body){if(!body.path("confirmed").asBoolean(false))return Result.fail("请先确认商品信息真实完整");return service.publish(id);}
}
