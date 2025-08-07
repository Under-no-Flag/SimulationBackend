package com.simulation.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 参数类型转换工具类
 * 用于将字符串参数转换为各种目标类型
 */
public class ParameterConversionUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(ParameterConversionUtils.class);

    /**
     * 参数类型转换主方法
     */
    public static Object convertParameterValue(Class<?> targetType, Object value) {
        if (value == null) return null;

        // 如果类型已经匹配
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // 类型转换
        String valueStr = value.toString();

        if (targetType == String.class) {
            return valueStr;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.valueOf(valueStr);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.valueOf(valueStr);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.valueOf(valueStr);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.valueOf(valueStr);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.valueOf(valueStr);
        } else if (targetType == Date.class) {
            return convertStringToDate(valueStr);
        } else if (targetType == LocalDateTime.class) {
            return convertStringToLocalDateTime(valueStr);
        } else if (targetType == LocalDate.class) {
            return convertStringToLocalDate(valueStr);
        } else if (targetType == LocalTime.class) {
            return convertStringToLocalTime(valueStr);
        }

        logger.warn("不支持的参数类型转换: {} -> {}", value.getClass(), targetType);
        return value;
    }

    /**
     * 字符串转Date
     */
    public static Date convertStringToDate(String dateStr) {
        try {
            // 尝试多种日期格式
            String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd"
            };

            for (String pattern : patterns) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                    sdf.setLenient(false);
                    return sdf.parse(dateStr);
                } catch (ParseException e) {
                    // 继续尝试下一个格式
                }
            }
            
            throw new IllegalArgumentException("无法解析日期字符串: " + dateStr);
        } catch (Exception e) {
            logger.error("日期转换失败: {} -> {}", dateStr, e.getMessage());
            throw new RuntimeException("日期转换失败: " + dateStr, e);
        }
    }

    /**
     * 字符串转LocalDateTime
     */
    public static LocalDateTime convertStringToLocalDateTime(String dateStr) {
        try {
            // 尝试多种日期时间格式
            String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm"
            };

            for (String pattern : patterns) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                    return LocalDateTime.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // 继续尝试下一个格式
                }
            }
            
            throw new IllegalArgumentException("无法解析日期时间字符串: " + dateStr);
        } catch (Exception e) {
            logger.error("LocalDateTime转换失败: {} -> {}", dateStr, e.getMessage());
            throw new RuntimeException("LocalDateTime转换失败: " + dateStr, e);
        }
    }

    /**
     * 字符串转LocalDate
     */
    public static LocalDate convertStringToLocalDate(String dateStr) {
        try {
            // 尝试多种日期格式
            String[] patterns = {
                "yyyy-MM-dd",
                "yyyy/MM/dd"
            };

            for (String pattern : patterns) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                    return LocalDate.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // 继续尝试下一个格式
                }
            }
            
            throw new IllegalArgumentException("无法解析日期字符串: " + dateStr);
        } catch (Exception e) {
            logger.error("LocalDate转换失败: {} -> {}", dateStr, e.getMessage());
            throw new RuntimeException("LocalDate转换失败: " + dateStr, e);
        }
    }

    /**
     * 字符串转LocalTime
     */
    public static LocalTime convertStringToLocalTime(String timeStr) {
        try {
            // 尝试多种时间格式
            String[] patterns = {
                "HH:mm:ss",
                "HH:mm"
            };

            for (String pattern : patterns) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                    return LocalTime.parse(timeStr, formatter);
                } catch (DateTimeParseException e) {
                    // 继续尝试下一个格式
                }
            }
            
            throw new IllegalArgumentException("无法解析时间字符串: " + timeStr);
        } catch (Exception e) {
            logger.error("LocalTime转换失败: {} -> {}", timeStr, e.getMessage());
            throw new RuntimeException("LocalTime转换失败: " + timeStr, e);
        }
    }
}
