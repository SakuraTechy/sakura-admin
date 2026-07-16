SET @parentId = 1933371197518045184;
-- 自动化管理-UI自动化场景管理菜单
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '自动化管理-UI自动化场景管理', 1000, 2, '/automation/automationUiScene', 'AutomationUiScene', 'automation/automationUiScene/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- 自动化管理-UI自动化场景管理按钮
INSERT IGNORE INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (1933371197522239488, '列表', @parentId, 3, 'automation:automationUiScene:list', 1, 1, 1, NOW()),
    (1933371197522239489, '详情', @parentId, 3, 'automation:automationUiScene:get', 2, 1, 1, NOW()),
    (1933371197522239490, '新增', @parentId, 3, 'automation:automationUiScene:create', 3, 1, 1, NOW()),
    (1933371197522239491, '修改', @parentId, 3, 'automation:automationUiScene:update', 4, 1, 1, NOW()),
    (1933371197522239492, '删除', @parentId, 3, 'automation:automationUiScene:delete', 5, 1, 1, NOW()),
    (1933371197522239493, '导出', @parentId, 3, 'automation:automationUiScene:export', 6, 1, 1, NOW()),
    (1933371197522239494, 'Playwright Runner 回放', @parentId, 3, 'automation:automationUiScene:execute', 7, 1, 1, NOW());

