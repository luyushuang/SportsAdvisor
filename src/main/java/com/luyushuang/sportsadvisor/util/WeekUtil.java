package com.luyushuang.sportsadvisor.util;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class WeekUtil {
    private static final int DEFAULT_WEEKS = 12;
    private static final int MIN_WEEKS = 4;
    private static final int MAX_WEEKS = 24;

    /**
     * 中文数字映射（覆盖 1-20，按长度降序排列以避免"十一"被"十"先匹配）
     */
    private static final Map<String, Integer> CN_NUMBERS = new LinkedHashMap<>() {{
        put("二十", 20);
        put("十九", 19);
        put("十八", 18);
        put("十七", 17);
        put("十六", 16);
        put("十五", 15);
        put("十四", 14);
        put("十三", 13);
        put("十二", 12);
        put("十一", 11);
        put("十", 10);
        put("九", 9);
        put("八", 8);
        put("七", 7);
        put("六", 6);
        put("五", 5);
        put("四", 4);
        put("三", 3);
        put("两", 2);
        put("二", 2);
        put("一", 1);
    }};

    /**
     * 阿拉伯数字 + 周/星期/weeks，如 "3周"、"3 个星期"、"3 weeks"
     */
    private static final Pattern ARABIC_WEEKS = Pattern.compile(
            "(\\d+)\\s*(?:周|个?星期|weeks?)", Pattern.CASE_INSENSITIVE);

    /**
     * 中文数字 + 周/星期，如 "三周"、"十二个星期"
     */
    private static final Pattern CN_WEEKS = Pattern.compile(
            "(二十|十[一二三四五六七八九]|[一二两三四五六七八九十])\\s*(?:周|个?星期)");

    /**
     * 特殊表达：半个月 ≈ 2 周，一个月 ≈ 4 周
     */
    private static final Pattern HALF_MONTH = Pattern.compile("半个月");
    private static final Pattern ONE_MONTH = Pattern.compile("[一1１]\\s*个?月");


    /**
     * 从用户请求中解析明确指定的训练周数。
     * <p>
     * 返回值语义：
     * - 返回具体数字：用户在请求中明确提到了周数
     * - 返回 null：用户没有指定周数，调用方应使用默认值
     * <p>
     * 解析优先级：阿拉伯数字 > 中文数字 > 特殊表达（半个月/一个月）
     */
    public static Integer parseRequestedWeeks(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return null;
        }

        // 1. 阿拉伯数字：3周、3 周、3个星期、3 weeks
        Matcher arabicMatcher = ARABIC_WEEKS.matcher(userRequest);
        if (arabicMatcher.find()) {
            try {
                int weeks = Integer.parseInt(arabicMatcher.group(1));
                log.debug("从请求中解析到周数（阿拉伯数字）：{}", weeks);
                return weeks;
            } catch (NumberFormatException e) {
                log.warn("周数解析失败，原始文本：{}", arabicMatcher.group(1));
            }
        }

        // 2. 中文数字：三周、十二个星期
        Matcher cnMatcher = CN_WEEKS.matcher(userRequest);
        if (cnMatcher.find()) {
            Integer weeks = CN_NUMBERS.get(cnMatcher.group(1));
            if (weeks != null) {
                log.debug("从请求中解析到周数（中文数字）：{} -> {}", cnMatcher.group(1), weeks);
                return weeks;
            }
        }

        // 3. 特殊表达
        if (HALF_MONTH.matcher(userRequest).find()) {
            log.debug("从请求中解析到特殊表达：半个月 -> 2 周");
            return 2;
        }
        if (ONE_MONTH.matcher(userRequest).find()) {
            log.debug("从请求中解析到特殊表达：一个月 -> 4 周");
            return 4;
        }

        // 4. 未识别到周数
        log.debug("请求中未识别到明确周数，将使用默认值 {}", DEFAULT_WEEKS);
        return null;
    }

    /**
     * 将用户请求的周数限制到合理范围内。
     * <p>
     * 规则：
     * - 未指定（null）：使用默认 12 周
     * - 低于最小阈值（4 周）：提升到 4 周
     * - 超过最大阈值（24 周）：截断到 24 周
     * - 范围内：原样返回
     */
    public static int clampWeeks(Integer requestedWeeks) {
        if (requestedWeeks == null) {
            return DEFAULT_WEEKS;
        }

        if (requestedWeeks < MIN_WEEKS) {
            log.warn("请求周数 {} 低于最小值 {}，已提升至 {}", requestedWeeks, MIN_WEEKS, MIN_WEEKS);
            return MIN_WEEKS;
        }

        if (requestedWeeks > MAX_WEEKS) {
            log.warn("请求周数 {} 超过最大值 {}，已截断至 {}", requestedWeeks, MAX_WEEKS, MAX_WEEKS);
            return MAX_WEEKS;
        }

        return requestedWeeks;
    }
}
