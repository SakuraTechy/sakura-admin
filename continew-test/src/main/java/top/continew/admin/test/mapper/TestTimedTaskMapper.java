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
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.test.model.entity.TestTimedTaskDO;
import top.continew.starter.data.mp.base.BaseMapper;

import java.time.LocalDateTime;

@Mapper
public interface TestTimedTaskMapper extends BaseMapper<TestTimedTaskDO> {

    /**
     * 锁定任务行，保证同一任务的重叠判断和运行记录创建是原子的。
     */
    @Select("SELECT * FROM test_timed_task WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_TestTimedTaskDO")
    TestTimedTaskDO selectByIdForUpdate(Long id);

    @Update("""
        UPDATE test_timed_task
        SET schedule_sync_status = 'SYNCING', schedule_sync_time = #{syncTime}
        WHERE id = #{id}
          AND schedule_sync_version = #{version}
          AND (schedule_sync_status <> 'SYNCING' OR schedule_sync_time IS NULL OR schedule_sync_time < #{staleBefore})
        """)
    int claimScheduleSync(@Param("id") Long id,
                          @Param("version") Long version,
                          @Param("syncTime") LocalDateTime syncTime,
                          @Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
        UPDATE test_timed_task
        SET schedule_job_id = #{jobId},
            next_execute_time = #{nextExecuteTime},
            schedule_sync_status = 'SYNCED',
            schedule_sync_error = NULL,
            schedule_sync_time = #{syncTime},
            schedule_sync_retry_count = 0,
            schedule_sync_next_retry_time = NULL
        WHERE id = #{id} AND schedule_sync_version = #{version}
        """)
    int markScheduleSynced(@Param("id") Long id,
                           @Param("version") Long version,
                           @Param("jobId") Long jobId,
                           @Param("nextExecuteTime") LocalDateTime nextExecuteTime,
                           @Param("syncTime") LocalDateTime syncTime);

    @Update("""
        UPDATE test_timed_task
        SET schedule_sync_status = 'FAILED',
            schedule_sync_error = #{error},
            schedule_sync_time = #{syncTime},
            schedule_sync_retry_count = #{retryCount},
            schedule_sync_next_retry_time = #{nextRetryTime}
        WHERE id = #{id} AND schedule_sync_version = #{version}
        """)
    int markScheduleSyncFailed(@Param("id") Long id,
                               @Param("version") Long version,
                               @Param("error") String error,
                               @Param("syncTime") LocalDateTime syncTime,
                               @Param("retryCount") Integer retryCount,
                               @Param("nextRetryTime") LocalDateTime nextRetryTime);

    @Update("""
        UPDATE test_timed_task
        SET del_flag = 4,
            schedule_job_id = NULL,
            next_execute_time = NULL,
            schedule_sync_status = 'SYNCED',
            schedule_sync_error = NULL,
            schedule_sync_time = #{syncTime},
            schedule_sync_retry_count = 0,
            schedule_sync_next_retry_time = NULL
        WHERE id = #{id} AND schedule_sync_version = #{version}
        """)
    int markScheduleDeleted(@Param("id") Long id,
                            @Param("version") Long version,
                            @Param("syncTime") LocalDateTime syncTime);
}
