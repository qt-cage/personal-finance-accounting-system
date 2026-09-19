package com.finance.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 国际化配置类
 * 加载i18n资源文件，支持中文/英文/日语
 */
@Configuration
public class I18nConfig {

    @Bean(name = "messageSource")
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        // 设置资源文件基础名（对应resources/i18n/messages_*.properties）
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        // 设置默认语言（zh-cn）
        source.setDefaultLocale(java.util.Locale.SIMPLIFIED_CHINESE);
        return source;
    }
}
