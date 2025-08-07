-- 创建数据库触发器，在插入新的行人数据时自动调用坐标转换
-- 注意：这需要在数据库中创建存储过程和触发器

-- 1. 创建坐标转换的存储过程
DELIMITER $$

CREATE PROCEDURE ConvertCoordinates(
    IN p_id BIGINT,
    IN p_pos_x DECIMAL(10,3),
    IN p_pos_y DECIMAL(10,3)
)
BEGIN
    DECLARE v_lat DOUBLE;
    DECLARE v_lon DOUBLE;
    
    -- 这里需要实现坐标转换逻辑
    -- 由于MySQL无法直接调用Java代码，这个方案需要：
    -- 1. 将GeoUtil的转换逻辑用MySQL函数重写，或
    -- 2. 使用外部程序调用
    
    -- 临时示例（需要根据实际转换公式修改）
    -- 校准参数
    SET @a1 = 0.000001; -- 这些需要根据GeoUtil.calibrate()的结果设置
    SET @b1 = 0.000001;
    SET @c1 = 31.0;
    SET @a2 = 0.000001;
    SET @b2 = 0.000001;
    SET @c2 = 121.0;
    SET @imgH = 1521;
    
    -- 转换坐标（简化版本，实际需要完整的转换逻辑）
    SET p_pos_y = @imgH - p_pos_y;
    SET v_lat = @a1 * p_pos_x + @b1 * p_pos_y + @c1;
    SET v_lon = @a2 * p_pos_x + @b2 * p_pos_y + @c2;
    
    -- 更新记录
    UPDATE pedestrian_data 
    SET lat = v_lat, lon = v_lon 
    WHERE id = p_id;
END$$

DELIMITER ;

-- 2. 创建触发器，在插入数据后自动调用转换
DELIMITER $$

CREATE TRIGGER tr_pedestrian_data_after_insert
AFTER INSERT ON pedestrian_data
FOR EACH ROW
BEGIN
    -- 如果插入的数据有pos_x和pos_y但没有lat和lon，则进行转换
    IF NEW.pos_x IS NOT NULL AND NEW.pos_y IS NOT NULL AND 
       (NEW.lat IS NULL OR NEW.lon IS NULL) THEN
        CALL ConvertCoordinates(NEW.id, NEW.pos_x, NEW.pos_y);
    END IF;
END$$

DELIMITER ;

-- 注意：这个方案需要将Java的GeoUtil转换逻辑移植到MySQL中
-- 或者使用UDF（用户定义函数）调用外部程序
