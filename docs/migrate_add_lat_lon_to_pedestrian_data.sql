-- 数据库迁移脚本：为 pedestrian_data 表添加经纬度字段
-- 执行前请确认表中尚未有 lat/lon 字段

ALTER TABLE pedestrian_data
  ADD COLUMN lat DOUBLE(10,6) DEFAULT NULL,
  ADD COLUMN lon DOUBLE(10,6) DEFAULT NULL;
