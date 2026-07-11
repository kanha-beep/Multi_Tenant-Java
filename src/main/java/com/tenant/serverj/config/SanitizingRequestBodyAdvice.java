package com.tenant.serverj.config;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice
public class SanitizingRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return sanitize(body);
    }

    private Object sanitize(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?, ?>) {
            Map<String, Object> sanitized = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Object rawKey = entry.getKey();
                if (rawKey == null) {
                    continue;
                }

                String key = rawKey.toString();
                if (key.startsWith("$") || key.contains(".")) {
                    continue;
                }

                sanitized.put(key, sanitize(entry.getValue()));
            }
            return sanitized;
        }

        if (value instanceof List<?>) {
            List<Object> sanitized = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }

        return value;
    }
}
