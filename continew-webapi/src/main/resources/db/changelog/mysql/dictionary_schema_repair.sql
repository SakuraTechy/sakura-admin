-- liquibase formatted sql

-- changeset codex:dictionary-schema-repair-20260824
-- comment 字典项字段按生产导出扩容，保证自动化操作的 JSON 描述可以完整入库。
ALTER TABLE `sys_dict_item`
    MODIFY COLUMN `value` varchar(60) NOT NULL COMMENT '值',
    MODIFY COLUMN `description` varchar(2000) DEFAULT NULL COMMENT '描述';

-- rollback ALTER TABLE `sys_dict_item` MODIFY COLUMN `value` varchar(30) NOT NULL COMMENT '值', MODIFY COLUMN `description` varchar(200) DEFAULT NULL COMMENT '描述';

-- changeset codex:dictionary-item-unique-key-baseline-20260824
-- comment 字典项允许不同标签复用同一值；建表完成后先修正唯一索引，再插入数据库类型字典。
SET @drop_dict_item_index_sql = (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE `sys_dict_item` DROP INDEX `uk_value_dict_id`',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_dict_item'
      AND index_name = 'uk_value_dict_id'
);
PREPARE drop_dict_item_index_stmt FROM @drop_dict_item_index_sql;
EXECUTE drop_dict_item_index_stmt;
DEALLOCATE PREPARE drop_dict_item_index_stmt;

SET @add_dict_item_index_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_dict_item` ADD UNIQUE INDEX `uk_label_value_dict_id` (`label`, `value`, `dict_id`)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_dict_item'
      AND index_name = 'uk_label_value_dict_id'
);
PREPARE add_dict_item_index_stmt FROM @add_dict_item_index_sql;
EXECUTE add_dict_item_index_stmt;
DEALLOCATE PREPARE add_dict_item_index_stmt;

-- rollback ALTER TABLE `sys_dict_item` DROP INDEX `uk_label_value_dict_id`, ADD UNIQUE INDEX `uk_value_dict_id` (`value`, `dict_id`);
