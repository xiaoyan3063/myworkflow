package com.myworkflow.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 超出 JavaScript Number 安全范围（2^53-1），
 * 直接下发会丢精度导致前端回传的 ID 与库中不一致，故统一序列化为字符串。
 * <p>
 * 只处理包装类型 Long：实体主键均为 Long，而分页总数等纯数值统一用基本类型 long，
 * 这样计数类字段仍以数字下发，前端无需额外转换。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
