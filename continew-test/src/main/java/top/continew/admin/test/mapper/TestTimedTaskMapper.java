/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.starter.data.mp.base.BaseMapper;

@Mapper
public interface TestTimedTaskMapper extends BaseMapper<TestTimedTaskDO> {

    /**
     * 锁定任务行，保证同一任务的重叠判断和运行记录创建是原子的。
     */
    @Select("SELECT * FROM test_timed_task WHERE id = #{id} FOR UPDATE")
    TestTimedTaskDO selectByIdForUpdate(Long id);
}
