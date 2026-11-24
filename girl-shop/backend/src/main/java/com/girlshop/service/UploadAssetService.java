package com.girlshop.service;

import com.girlshop.utils.ImageStorage;
import com.girlshop.utils.SystemConstants;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Service @DependsOn("prioritySchema") @Slf4j
public class UploadAssetService {
 @org.springframework.beans.factory.annotation.Value("${platform.workers.enabled:true}") private boolean improvementWorkers=true;

    @Resource private JdbcTemplate jdbc;
    @Value("${images.root:"+SystemConstants.IMAGE_UPLOAD_DIR+"}") private String root;
    @Transactional @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${uploads.cleanup-ms:3600000}")
    public void cleanupStaged(){if(!improvementWorkers)return;for(Map<String,Object> asset:jdbc.queryForList("SELECT url,user_id FROM tb_upload_asset WHERE status='STAGED' AND created_at<DATE_SUB(NOW(),INTERVAL 7 DAY) ORDER BY created_at LIMIT 50 FOR UPDATE")){
        String url=asset.get("url").toString();Long user=((Number)asset.get("user_id")).longValue();
        Integer refs=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM tb_product WHERE FIND_IN_SET(?,images)>0)+(SELECT COUNT(*) FROM tb_product_draft WHERE LOCATE(?,payload)>0)+(SELECT COUNT(*) FROM tb_private_message WHERE FIND_IN_SET(?,images)>0)+(SELECT COUNT(*) FROM tb_review WHERE FIND_IN_SET(?,images)>0)+(SELECT COUNT(*) FROM tb_buyer_show WHERE FIND_IN_SET(?,images)>0)+(SELECT COUNT(*) FROM tb_order WHERE product_image=?)",Integer.class,url,url,url,url,url,url);
        if(refs!=null&&refs>0){jdbc.update("UPDATE tb_upload_asset SET status='ACTIVE' WHERE url=?",url);continue;}
        Path path=own(user,url);jdbc.update("UPDATE tb_upload_asset SET status='DELETED' WHERE url=? AND status='STAGED'",url);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){try{Files.deleteIfExists(path);}catch(IOException e){log.warn("临时图片清理未完成");}}});
    }}
    public String save(Long userId,MultipartFile file)throws IOException {
        String url=ImageStorage.save(Paths.get(root),userId,file);
        try{jdbc.update("INSERT INTO tb_upload_asset(url,user_id,status) VALUES(?,?,'STAGED')",url,userId);}
        catch(RuntimeException e){Files.deleteIfExists(ImageStorage.ownedPath(Paths.get(root),userId,url));throw e;}
        return url;
    }
    public void cleanup(Long userId,String url) {
        try{Files.deleteIfExists(ImageStorage.ownedPath(Paths.get(root),userId,url));}
        catch(IOException e){log.warn("未完成上传的文件清理失败",e);}
    }
    private Path own(Long userId,String url) {
        try{return ImageStorage.ownedPath(Paths.get(root),userId,url);}
        catch(IOException e){throw new IllegalArgumentException("图片不存在");}
    }
    private Map<String,Object> lock(Long userId,String url) {
        Path path=own(userId,url);
        if(!Files.isRegularFile(path))throw new IllegalArgumentException("图片不存在");
        // Import images created by the previous version, only after path/owner verification.
        jdbc.update("INSERT IGNORE INTO tb_upload_asset(url,user_id,status) VALUES(?,?,'STAGED')",url,userId);
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM tb_upload_asset WHERE url=? FOR UPDATE",url);
        if(((Number)row.get("user_id")).longValue()!=userId || "DELETED".equals(row.get("status")))
            throw new IllegalArgumentException("图片不属于当前用户或已删除");
        return row;
    }
    @Transactional
    public void claim(Long userId,List<String> images) {
        if(images==null)return;
        if(images.size()>9)throw new IllegalArgumentException("最多上传9张图片");
        // Stable lock order avoids deadlocks when two forms reuse multiple images.
        List<String> sorted=new ArrayList<>(images);
        if(sorted.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("图片地址无效");
        Collections.sort(sorted);
        for(String url:sorted){lock(userId,url);jdbc.update("UPDATE tb_upload_asset SET status='ACTIVE' WHERE url=?",url);}
    }
    @Transactional
    public void delete(Long userId,String filename) {
        Path path=own(userId,filename);
        String url="/imgs/"+Paths.get(root).toAbsolutePath().normalize().relativize(path).toString().replace('\\','/');
        Map<String,Object> asset=lock(userId,url);
        Integer references=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM tb_product WHERE FIND_IN_SET(?,images)>0) + (SELECT COUNT(*) FROM tb_order WHERE product_image=?) + (SELECT COUNT(*) FROM tb_buyer_show WHERE FIND_IN_SET(?,images)>0)",Integer.class,url,url,url);
        if("ACTIVE".equals(asset.get("status")) || (references!=null && references>0))throw new IllegalArgumentException("图片已被使用，不能删除");
        jdbc.update("UPDATE tb_upload_asset SET status='DELETED' WHERE url=?",url);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){try{Files.deleteIfExists(path);}catch(IOException e){log.warn("已注销图片文件清理失败",e);}}
        });
    }
}
