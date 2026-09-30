-- liquibase formatted sql

-- changeset codex:user-contact-data-repair-20260928
-- comment 修复默认管理员联系方式误使用密文导致个人中心无法正常显示的问题。
UPDATE `sys_user`
SET `email` = '123456789@qq.com',
    `phone` = '13811111111'
WHERE `id` = 1
  AND `username` = 'admin'
  AND `email` = '42190c6c5639d2ca4edb4150a35e058559ccf8270361a23745a2fd285a273c28'
  AND `phone` = '5bda89a4609a65546422ea56bfe5eab4';

-- rollback UPDATE `sys_user`
-- rollback SET `email` = '42190c6c5639d2ca4edb4150a35e058559ccf8270361a23745a2fd285a273c28',
-- rollback     `phone` = '5bda89a4609a65546422ea56bfe5eab4'
-- rollback WHERE `id` = 1 AND `username` = 'admin';
