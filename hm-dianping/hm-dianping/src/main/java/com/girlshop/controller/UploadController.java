package com.girlshop.controller;

import com.girlshop.dto.Result;
import com.girlshop.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片上传控制器（商品多图）
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {
    @javax.annotation.Resource
    private com.girlshop.service.UploadAssetService assets;

    /**
     * 上传商品图片（支持多张），返回可访问地址列表
     */
    @PostMapping("/product")
    @org.springframework.transaction.annotation.Transactional
    public Result uploadProduct(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0 || files.length > 9) {
            return Result.fail("请选择图片");
        }
        List<String> urls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String url = saveImage(file);
                urls.add(url);
            }
        } catch (IOException | RuntimeException e) {
            for (String url : urls) assets.cleanup(com.girlshop.utils.UserHolder.getUser().getId(),url);
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException)e;
            throw new RuntimeException("图片上传失败", e);
        }
        return Result.ok(urls);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/product")
    public Result deleteImage(@RequestParam("name") String filename) {
        assets.delete(com.girlshop.utils.UserHolder.getUser().getId(),filename);
        return Result.ok();
    }

    private String saveImage(MultipartFile image) throws IOException {
        return assets.save(com.girlshop.utils.UserHolder.getUser().getId(),image);
    }
}
