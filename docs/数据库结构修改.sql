-- 数据库结构修改脚本
-- 为仿真运行表添加引擎参数和智能体参数字段

-- 添加引擎参数字段
ALTER TABLE simulation_runs 
ADD COLUMN engine_parameters TEXT COMMENT '引擎参数JSON字符串';

-- 添加智能体参数字段  
ALTER TABLE simulation_runs 
ADD COLUMN agent_parameters TEXT COMMENT '智能体参数JSON字符串';

-- 查看表结构确认修改
DESCRIBE simulation_runs;

-- 示例数据插入（可选）
-- INSERT INTO simulation_runs (model_name, start_date, status, engine_parameters, agent_parameters, description) 
-- VALUES (
--     'NanJingDong',
--     NOW(),
--     'PENDING',
--     '{"startDate": "2025-05-31 15:30:00", "stopDate": "2025-05-31 15:40:00", "realTimeScale": 1000}',
--     '{"simulTargetTime": "2025-05-31 15:30:00"}',
--     '测试仿真运行'
-- );
