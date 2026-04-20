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

package top.continew.admin.common.sort;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@AllArgsConstructor
class User1 {
    @OrderProperty
    private Integer order;
    @IdProperty
    private Integer id;
    private String username;
    private String password;

    public static void main(String[] args) {
        ArrayList<User1> users = new ArrayList<>();
        users.add(new User1(1, 1, "乔峰", "降龙十八掌"));
        users.add(new User1(2, 2, "段誉", "六脉神剑"));
        users.add(new User1(3, 3, "虚竹", "北冥神功"));
        users.add(new User1(4, 4, "鸠摩智", "小无相功"));
        users.add(new User1(5, 5, "慕容复", "斗转星移"));
        users.add(new User1(6, 6, "丁春秋", "化功大法"));
        List<User1> sort = SortUtil.sort(users, 1, 4, SortType.BUBBLE);
        sort.sort(Comparator.comparingInt(User1::getOrder));
        sort.forEach(System.out::println);
    }
}
