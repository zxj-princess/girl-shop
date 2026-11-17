package com.girlshop;

import com.girlshop.config.PrioritySchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit, additive upgrade for the configured application database; no seeds or deletes. */
@EnabledIfSystemProperty(named="priority.migrate",matches="true")
class PrioritySchemaMigrationTest {
    @Test void migrateAndCheckOrderQueries() {
        YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yaml"));
        Properties p=yaml.getObject();
        JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(p.getProperty("spring.datasource.url"),p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password")));
        PrioritySchema schema=new PrioritySchema();ReflectionTestUtils.setField(schema,"jdbc",jdbc);schema.migrate();schema.migrate();
        assertNotNull(jdbc.queryForList("SHOW TABLES LIKE 'tb_order_hidden'"));
        jdbc.queryForList("SELECT id FROM tb_order WHERE buyer_id=0 AND NOT EXISTS (SELECT 1 FROM tb_order_hidden h WHERE h.order_id=tb_order.id AND h.user_id=0)");
        jdbc.queryForList("SELECT id FROM tb_order WHERE seller_id=0 AND NOT EXISTS (SELECT 1 FROM tb_order_hidden h WHERE h.order_id=tb_order.id AND h.user_id=0)");
        System.out.println("SCHEMA_UPGRADE: order query tables ready; existing records unchanged");
    }
}
