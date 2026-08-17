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

package top.continew.admin.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.continew.admin.project.model.entity.ProjectResourceSlotDO;
import top.continew.starter.data.mp.base.BaseMapper;

/** 项目自动化资源角色 Mapper。 */
@Mapper
public interface ProjectResourceSlotMapper extends BaseMapper<ProjectResourceSlotDO> {
}
