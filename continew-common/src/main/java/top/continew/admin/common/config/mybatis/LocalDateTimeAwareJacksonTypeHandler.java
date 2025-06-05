package top.continew.admin.common.config.mybatis;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class LocalDateTimeAwareJacksonTypeHandler extends JacksonTypeHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public LocalDateTimeAwareJacksonTypeHandler(Class<?> type) {
        super(type);
    }
}
