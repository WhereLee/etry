package com.wherelee.etry.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "etry.storage.type", havingValue = "cos")
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(StorageProperties properties) {
        BasicCOSCredentials credentials = new BasicCOSCredentials(
                properties.getCos().getSecretId(),
                properties.getCos().getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getCos().getRegion()));
        clientConfig.setConnectionTimeout(5_000);
        clientConfig.setSocketTimeout(30_000);
        return new COSClient(credentials, clientConfig);
    }
}
