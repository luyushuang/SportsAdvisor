package com.luyushuang.sportsadvisor.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * JSON 序列化/反序列化工具类（基于 Jackson 3）。
 * <p>
 * 设计要点：
 * 1. 单例 JsonMapper，避免每次 new 带来的性能开销
 * 2. 关闭 FAIL_ON_UNKNOWN_PROPERTIES，容忍模型多返回字段
 * 3. 不输出 null 字段，减小存储体积（可按需调整）
 * 4. 统一的异常包装，便于上层处理
 */
public final class JsonUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            // 反序列化：忽略未知字段，维持不变
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // 序列化：全局忽略 null 值（字段和 Map 的 value）
            // 这是 Jackson 3 中替代 WRITE_NULL_MAP_VALUES 的正确方式
            .changeDefaultPropertyInclusion(incl -> incl
                    .withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL)
            )
            .build();

    private JsonUtils() {
    }

    /**
     * 对象 → JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new JsonSerializeException("JSON 序列化失败: " + obj.getClass().getName(), e);
        }
    }

    /**
     * JSON 字符串 → 对象
     */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new JsonSerializeException("JSON 反序列化失败: " + type.getName(), e);
        }
    }

    /**
     * JSON 字符串 → 泛型对象（如 List<String>、Map<String, Object>）
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new JsonSerializeException("JSON 反序列化失败: " + typeRef.getType(), e);
        }
    }

    /**
     * JSON 字符串 → List<String>（TypeHandler 场景最常用）
     */
    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new JsonSerializeException("JSON 反序列化为 List<String> 失败", e);
        }
    }

    /**
     * 对象 → JsonNode（需要动态遍历时使用）
     */
    public static JsonNode toJsonNode(Object obj) {
        return MAPPER.valueToTree(obj);
    }

    /**
     * 对象 → 格式化 JSON 字符串（日志调试用，带缩进）
     */
    public static String toPrettyJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new JsonSerializeException("JSON 格式化序列化失败", e);
        }
    }

    /**
     * 浅层深拷贝（基于 JSON 序列化）
     */
    public static <T> T deepCopy(T obj, Class<T> type) {
        return fromJson(toJson(obj), type);
    }

    /**
     * 自定义异常，区分 JSON 处理错误
     */
    public static class JsonSerializeException extends RuntimeException {
        public JsonSerializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}