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

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.test.model.entity.TestTimedTaskRunDO;
import top.continew.starter.data.mp.base.BaseMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TestTimedTaskRunMapper extends BaseMapper<TestTimedTaskRunDO> {

    @Select("""
        <script>
        SELECT run.*
        FROM test_timed_task_run run
        INNER JOIN (
            SELECT timed_task_id, MAX(id) AS latest_id
            FROM test_timed_task_run
            WHERE del_flag = 3
              AND timed_task_id IN
              <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">
                  #{taskId}
              </foreach>
            GROUP BY timed_task_id
        ) latest ON latest.latest_id = run.id
        ORDER BY run.start_time DESC, run.id DESC
        </script>
        """)
    List<TestTimedTaskRunDO> selectLatestByTaskIds(@Param("taskIds") Collection<Long> taskIds);

    @Delete("""
        DELETE FROM test_timed_task_run
        WHERE status IN ('PASSED', 'FAILED', 'CANCELLED', 'SKIPPED')
          AND COALESCE(end_time, start_time, create_time) < #{cutoff}
        ORDER BY id
        LIMIT #{batchSize}
        """)
    int deleteExpiredRuns(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") Integer batchSize);

    @Update("""
        UPDATE test_timed_task_run
        SET status = #{status},
            end_time = #{endTime},
            run_time = #{runTime},
            test_report_id = COALESCE(#{testReportId}, test_report_id),
            build_number = COALESCE(#{buildNumber}, build_number),
            console_url = COALESCE(#{consoleUrl}, console_url),
            report_url = COALESCE(#{reportUrl}, report_url),
            failure_reason = #{failureReason}
        WHERE id = #{id} AND status = 'RUNNING'
        """)
    int finishRunning(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("endTime") LocalDateTime endTime,
                      @Param("runTime") Long runTime,
                      @Param("testReportId") Long testReportId,
                      @Param("buildNumber") String buildNumber,
                      @Param("consoleUrl") String consoleUrl,
                      @Param("reportUrl") String reportUrl,
                      @Param("failureReason") String failureReason);

    @Update("""
        UPDATE test_timed_task_run
        SET notification_status = 'SENDING', notification_error = NULL
        WHERE id = #{id} AND notification_status = 'PENDING'
        """)
    int claimNotification(@Param("id") Long id);

    @Update("""
        UPDATE test_timed_task_run
        SET notification_status = #{status}, notification_error = #{error}
        WHERE id = #{id} AND notification_status = 'SENDING'
        """)
    int finishNotification(@Param("id") Long id, @Param("status") String status, @Param("error") String error);
}
