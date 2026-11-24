package com.girlshop.controller;
import com.girlshop.dto.Result;import com.girlshop.service.PlatformImprovementService;import com.fasterxml.jackson.databind.JsonNode;import org.springframework.web.bind.annotation.*;import javax.annotation.Resource;
@RestController @RequestMapping("/platform") public class PlatformImprovementController {
 @Resource private PlatformImprovementService service;
 @GetMapping("/access") public Result access(){return service.access();}
 @GetMapping("/preferences") public Result preferences(){return service.preferences();}
 @PutMapping("/preferences") public Result preferences(@RequestBody JsonNode body){return service.preferences(body);}
 @PostMapping("/ai-feedback/{draft}") public Result feedback(@PathVariable long draft,@RequestBody JsonNode body){return service.feedback(draft,body);}
 @GetMapping("/health") public Result health(){return service.health();}
 @GetMapping("/dashboard") public Result dashboard(){return service.dashboard();}
 @GetMapping("/admin/{kind}") public Result list(@PathVariable String kind,@RequestParam(defaultValue="1") int page){return service.list(kind,page);}
 @PostMapping("/admin/{kind}/{id}") public Result action(@PathVariable String kind,@PathVariable long id,@RequestBody JsonNode body){return service.action(kind,id,body);}
}
