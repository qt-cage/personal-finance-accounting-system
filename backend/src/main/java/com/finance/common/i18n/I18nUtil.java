package com.finance.common.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * 国际化工具类
 * 根据请求头lang参数解析语言，返回对应语言的提示信息
 * 如果lang为空或无法识别，默认返回中文（zh-cn）
 */
@Component
public class I18nUtil {

    @Autowired
    private MessageSource messageSource;

    /**
     * 根据当前请求的语言获取消息
     *
     * @param key 消息key
     * @return 翻译后的消息
     */
    public String getMessage(String key) {
        return getMessage(key, resolveLocale());
    }

    /**
     * 根据指定语言获取消息
     *
     * @param key    消息key
     * @param locale 语言区域
     * @return 翻译后的消息
     */
    public String getMessage(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 解析当前请求的语言
     * 优先级：请求头lang → 默认zh-cn
     */
    public Locale resolveLocale() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String lang = request.getHeader("lang");
                if (lang != null && !lang.isEmpty()) {
                    return parseLocale(lang);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

    /**
     * 将语言标识字符串解析为Locale对象
     * 支持：zh-cn / en / ja
     */
    public static Locale parseLocale(String lang) {
        if (lang == null || lang.isEmpty()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        switch (lang) {
            case "en":
                return Locale.US;
            case "ja":
                return Locale.JAPANESE;
            case "zh-cn":
            default:
                return Locale.SIMPLIFIED_CHINESE;
        }
    }
}
