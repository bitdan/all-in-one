package com.linger.module.toolhub.post;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linger.module.toolhub.post.entity.PostEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPersistenceMappingTest {

    @Test
    void shouldMapInheritedAuditFields() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "post-test");

        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, PostEntity.class);
        Set<String> properties = tableInfo.getFieldList().stream()
                .map(TableFieldInfo::getProperty)
                .collect(Collectors.toSet());

        assertEquals("post_posts", tableInfo.getTableName());
        assertTrue(properties.contains("createdBy"));
        assertTrue(properties.contains("updatedBy"));
        assertTrue(properties.contains("createdAt"));
        assertTrue(properties.contains("updatedAt"));
    }

    @Test
    void shouldParseCustomPostMapperXml() throws Exception {
        String resource = "mapper/post/PostMapper.xml";
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement(
                "com.linger.module.toolhub.post.mapper.PostMapper.selectPostPage"));
    }
}
