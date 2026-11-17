package com.girlshop.config;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/** Additive, idempotent migration. A missing schema fails startup with a precise error. */
@Component("prioritySchema")
public class PrioritySchema {
    @Resource private JdbcTemplate jdbc;
    @PostConstruct public void migrate() {
        try(java.sql.Connection connection=jdbc.getDataSource().getConnection()) {
            JdbcTemplate migration=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            String lock="girlshop:migration:"+connection.getCatalog();
            if(!Integer.valueOf(1).equals(migration.queryForObject("SELECT GET_LOCK(?,20)",Integer.class,lock)))throw new IllegalStateException("数据库升级锁超时");
            try {
                migration.execute("CREATE TABLE IF NOT EXISTS tb_schema_migration(version VARCHAR(16) PRIMARY KEY,checksum VARCHAR(64) NOT NULL,applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
                String base="CREATE TABLE IF NOT EXISTS tb_order_hidden (order_id BIGINT NOT NULL,user_id BIGINT NOT NULL,PRIMARY KEY(order_id,user_id));\n"+read("db/priority-two.sql");
                apply(migration,"V001",base,()->new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(base.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(migration.getDataSource()));
                apply(migration,"V002","tb_review.images TEXT NULL",()->{
                    if(migration.queryForList("SHOW COLUMNS FROM tb_review LIKE 'images'").isEmpty())migration.execute("ALTER TABLE tb_review ADD COLUMN images TEXT NULL");
                });
                String cache=read("db/V003__cache_eviction.sql");
                apply(migration,"V003",cache,()->new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(cache.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(migration.getDataSource()));
                String trade=read("db/V004__trade_experience.sql");
                apply(migration,"V004",trade,()->new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(trade.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(migration.getDataSource()));
                String realtime=read("db/V005__realtime_messages.sql");
                apply(migration,"V005",realtime,()->{
                    if(migration.queryForList("SHOW COLUMNS FROM tb_private_message LIKE 'client_message_id'").isEmpty())migration.execute("ALTER TABLE tb_private_message ADD COLUMN client_message_id VARCHAR(64) NULL");
                    if(migration.queryForList("SHOW COLUMNS FROM tb_private_message LIKE 'request_hash'").isEmpty())migration.execute("ALTER TABLE tb_private_message ADD COLUMN request_hash VARCHAR(64) NULL");
                    if(migration.queryForList("SHOW INDEX FROM tb_private_message WHERE Key_name='uk_pm_client'").isEmpty())migration.execute("CREATE UNIQUE INDEX uk_pm_client ON tb_private_message(sender_id,client_message_id)");
                    if(migration.queryForList("SHOW INDEX FROM tb_private_message WHERE Key_name='idx_pm_pair'").isEmpty())migration.execute("CREATE INDEX idx_pm_pair ON tb_private_message(sender_id,receiver_id,id)");
                    if(migration.queryForList("SHOW INDEX FROM tb_private_message WHERE Key_name='idx_pm_incoming'").isEmpty())migration.execute("CREATE INDEX idx_pm_incoming ON tb_private_message(receiver_id,sender_id,id)");
                });
                apply(migration,"V006","product measurements varchar500 defects varchar500",()->{
                    if(migration.queryForList("SHOW COLUMNS FROM tb_product LIKE 'measurements'").isEmpty())migration.execute("ALTER TABLE tb_product ADD COLUMN measurements VARCHAR(500) NULL");
                    if(migration.queryForList("SHOW COLUMNS FROM tb_product LIKE 'defects'").isEmpty())migration.execute("ALTER TABLE tb_product ADD COLUMN defects VARCHAR(500) NULL");
                });
                apply(migration,"V007","private message images text",()->{
                    if(migration.queryForList("SHOW COLUMNS FROM tb_private_message LIKE 'images'").isEmpty())migration.execute("ALTER TABLE tb_private_message ADD COLUMN images TEXT NULL");
                });
                String ai=read("db/V008__ai_listing.sql");
                apply(migration,"V008",ai,()->new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(ai.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(migration.getDataSource()));
            } finally {migration.queryForObject("SELECT RELEASE_LOCK(?)",Integer.class,lock);}
        } catch(Exception e){throw new IllegalStateException("数据库版本迁移失败，请检查日志和数据库权限",e);}
    }
    private String read(String path)throws java.io.IOException {
        try(java.io.InputStream in=new ClassPathResource(path).getInputStream()){return org.springframework.util.StreamUtils.copyToString(in,java.nio.charset.StandardCharsets.UTF_8);}
    }
    private void apply(JdbcTemplate db,String version,String sql,Runnable action)throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder checksum=new StringBuilder();for(byte b:digest)checksum.append(String.format("%02x",b));
        java.util.List<String> previous=db.queryForList("SELECT checksum FROM tb_schema_migration WHERE version=?",String.class,version);
        if(!previous.isEmpty()){if(!previous.get(0).equals(checksum.toString()))throw new IllegalStateException("已执行迁移被改动："+version);return;}
        action.run();db.update("INSERT INTO tb_schema_migration(version,checksum) VALUES(?,?)",version,checksum.toString());
    }
}
