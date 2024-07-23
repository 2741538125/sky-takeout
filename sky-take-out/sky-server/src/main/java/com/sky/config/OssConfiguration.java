package com.sky.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sky.properties.AliOssProperties;
import com.sky.utils.AliOssUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 配置类，用于创建ALiOssUtil对象
 */
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean //加入Bean注解后，当项目启动时，就会创建这个对象，然后交给spring容器去管理
    @ConditionalOnMissingBean //保证整个spring容器中只有一个这个对象，当没有这个bean时再创建
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        log.info("开始创建阿里云文件上传工具类对象：{}", aliOssProperties);
        return new AliOssUtil(aliOssProperties.getEndpoint(), 
                aliOssProperties.getAccessKeyId(), 
                aliOssProperties.getAccessKeySecret(), 
                aliOssProperties.getBucketName());
    }
}
