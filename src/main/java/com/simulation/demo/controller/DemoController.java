package com.simulation.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 演示控制器 - 用于检查系统状态和依赖项
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/check-dependencies")
    public Map<String, Object> checkDependencies() {
        Map<String, Object> result = new HashMap<>();
        
        // 检查模型JAR文件
        File modelJar = new File("model.jar");
        result.put("model.jar.exists", modelJar.exists());
        if (modelJar.exists()) {
            result.put("model.jar.size", modelJar.length());
            result.put("model.jar.path", modelJar.getAbsolutePath());
        }
        
        // 检查数据集文件
        File datasetFile = new File("dataset/selected_cameras_20250531.xlsx");
        result.put("dataset.exists", datasetFile.exists());
        if (datasetFile.exists()) {
            result.put("dataset.size", datasetFile.length());
            result.put("dataset.path", datasetFile.getAbsolutePath());
        }
        
        // 检查lib目录
        File libDir = new File("lib");
        result.put("lib.directory.exists", libDir.exists());
        if (libDir.exists()) {
            String[] files = libDir.list();
            result.put("lib.files.count", files != null ? files.length : 0);
            result.put("lib.files", files);
        }
        
        // 检查关键的AnyLogic库文件
        String[] criticalLibs = {
            "lib/com.anylogic.engine.jar",
            "lib/com.anylogic.engine.sa.jar", 
            "lib/Core.jar"
        };
        
        Map<String, Boolean> libStatus = new HashMap<>();
        for (String libPath : criticalLibs) {
            File libFile = new File(libPath);
            libStatus.put(libPath, libFile.exists());
        }
        result.put("critical.libs", libStatus);
        
        // 系统信息
        result.put("java.version", System.getProperty("java.version"));
        result.put("os.name", System.getProperty("os.name"));
        result.put("working.directory", System.getProperty("user.dir"));
        
        return result;
    }

    @GetMapping("/test-reflection")
    public Map<String, Object> testReflection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 尝试加载仿真类
            String[] possibleClassNames = {
                "nanjingdong.Simulation",
                "nanjingdong.Main", 
                "Simulation",
                "Main"
            };

            Map<String, String> classStatus = new HashMap<>();
            for (String className : possibleClassNames) {
                try {
                    Class<?> clazz = Class.forName(className);
                    classStatus.put(className, "找到类: " + clazz.getName());
                    
                    // 列出一些关键方法
                    java.lang.reflect.Method[] methods = clazz.getMethods();
                    Map<String, String> methodInfo = new HashMap<>();
                    
                    for (java.lang.reflect.Method method : methods) {
                        String methodName = method.getName();
                        if (methodName.equals("run") || methodName.equals("setStopTime") || 
                            methodName.equals("getTime") || methodName.equals("stop")) {
                            methodInfo.put(methodName, java.util.Arrays.toString(method.getParameterTypes()));
                        }
                    }
                    result.put(className + ".methods", methodInfo);
                    
                } catch (ClassNotFoundException e) {
                    classStatus.put(className, "类不存在");
                }
            }
            result.put("class.status", classStatus);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
