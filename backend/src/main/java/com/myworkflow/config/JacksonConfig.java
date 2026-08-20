package com.myworkflow.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 雪花 ID 超出 JavaScript Number 安全范围（2^53-1），
 * 直接下发会丢精度导致前端回传的 ID 与库中不一致，故统一序列化为字符串。
 * <p>
 * 只处理包装类型 Long：实体主键均为 Long，而分页总数等纯数值统一用基本类型 long，
 * 这样计数类字段仍以数字下发，前端无需额外转换。
 * <p>
 * LocalDateTime 默认会序列化成带 T 的 ISO 字符串（例如 2026-08-20T13:51:06），
 * 列表页直接展示不好看；这里和 application.yml 的 date-format 对齐。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }
}
