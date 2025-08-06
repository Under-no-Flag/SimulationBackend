package com.simulation.demo.entity;

public enum SimulationStatus {
    PENDING("待执行"),
    RUNNING("运行中"), 
    COMPLETED("已完成"),
    FAILED("执行失败"),
    CANCELLED("已取消");
    
    private final String description;
    
    SimulationStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    // 添加从数据库字符串转换的方法
    public static SimulationStatus fromDatabaseValue(String dbValue) {
        if (dbValue == null) return null;
        
        switch (dbValue.toLowerCase()) {
            case "pending": return PENDING;
            case "running": return RUNNING;
            case "completed": return COMPLETED;
            case "failed": return FAILED;
            case "cancelled": return CANCELLED;
            default: return null;
        }
    }
    
    // 转换为数据库值的方法
    public String toDatabaseValue() {
        return this.name().toLowerCase();
    }
}
