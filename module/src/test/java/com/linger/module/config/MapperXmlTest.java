package com.linger.module.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyActivityMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyDelayTaskMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyGroupMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyInventoryLedgerMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyMemberMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyOrderMapper;
import com.linger.module.groupbuy.transaction.mapper.GroupBuyOutboxEventMapper;
import com.linger.module.toolhub.auth.mapper.UserMapper;
import com.linger.module.toolhub.post.mapper.PostMapper;
import com.linger.module.toolhub.post.mapper.PostTagMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlTest {

    @Test
    void shouldParseXmlAndBindEveryCustomMapperMethod() throws Exception {
        assertMapperXml("mapper/post/PostMapper.xml", PostMapper.class);
        assertMapperXml("mapper/post/PostTagMapper.xml", PostTagMapper.class);
        assertMapperXml("mapper/auth/UserMapper.xml", UserMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyActivityMapper.xml", GroupBuyActivityMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyDelayTaskMapper.xml", GroupBuyDelayTaskMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyGroupMapper.xml", GroupBuyGroupMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyInventoryLedgerMapper.xml", GroupBuyInventoryLedgerMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyMemberMapper.xml", GroupBuyMemberMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyOrderMapper.xml", GroupBuyOrderMapper.class);
        assertMapperXml("mapper/groupbuy/GroupBuyOutboxEventMapper.xml", GroupBuyOutboxEventMapper.class);
    }

    private void assertMapperXml(String resource, Class<?> mapperType) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        for (Method method : mapperType.getDeclaredMethods()) {
            String statementId = mapperType.getName() + "." + method.getName();
            assertTrue(configuration.hasStatement(statementId),
                    () -> resource + " is missing statement " + statementId);
        }
    }
}
