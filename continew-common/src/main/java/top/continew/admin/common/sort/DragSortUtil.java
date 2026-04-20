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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DragSortUtil {
    /**
     * 交换位置排序
     * 
     * @param list      排序列表
     * @param fromIndex 起始位置
     * @param toIndex   目标位置
     */
    public static <T> void swap(List<T> list, int fromIndex, int toIndex) {
        if (list == null || list.isEmpty()) {
            return;
        }
        T fromData = list.get(fromIndex);
        T toData = list.get(toIndex);
        list.set(fromIndex, toData);
        list.set(toIndex, fromData);
    }

    /**
     * 移动位置排序
     * 
     * @param list      排序列表
     * @param fromIndex 起始位置
     * @param toIndex   目标位置
     */
    public static <T> void move(List<T> list, int fromIndex, int toIndex) {
        if (list == null || list.isEmpty()) {
            return;
        }
        if (fromIndex > toIndex) {
            // 往上移动，需先移除再插入
            T data = list.remove(fromIndex);
            list.add(toIndex, data);
        } else if (fromIndex < toIndex) {
            // 往下移动，需先插入再移除
            T data = list.get(fromIndex);
            list.add(toIndex + 1, data);
            list.remove(fromIndex);
        }
    }

    public static <T> Consumer<T> getIndex(BiConsumer<T, Integer> consumer) {
        class IndexObject {
            int index;
        }
        IndexObject indexObject = new IndexObject();
        return i -> {
            consumer.accept(i, indexObject.index++);
        };
    }

    // 可执行示例
    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        list.add("{\"name\":\"一级模块\",\"sort\":\"1\"}");
        list.add("{\"name\":\"二级模块\",\"sort\":\"2\"}");
        list.add("{\"name\":\"三级模块\",\"sort\":\"3\"}");
        list.add("{\"name\":\"四级模块\",\"sort\":\"4\"}");

        //        List<String> list = new ArrayList<>();
        //        list.add("{\"cancel\":\"true\",\"copyId\":\"\",\"id\":\"D1\",\"name\":\"D1\",\"order\":1,\"remark\":\"\",\"stepList\":[{\"action\":\"wait-forced\",\"config\":[{\"paramsName\":\"value\",\"paramsValue\":\"20000\"}],\"copyId\":\"\",\"copyPid\":\"\",\"id\":\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\",\"name\":\"1\",\"operationName\":\"强制等待\",\"operationType\":\"等待操作\",\"order\":1,\"pid\":\"D1\",\"setting\":\"\"}],\"stepMsg\":\"[{\\\"action\\\":\\\"wait-forced\\\",\\\"config\\\":[{\\\"paramsName\\\":\\\"value\\\",\\\"paramsValue\\\":\\\"20000\\\"}],\\\"copyId\\\":\\\"\\\",\\\"copyPid\\\":\\\"\\\",\\\"id\\\":\\\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\\\",\\\"name\\\":\\\"1\\\",\\\"operationName\\\":\\\"强制等待\\\",\\\"operationType\\\":\\\"等待操作\\\",\\\"order\\\":1,\\\"pid\\\":\\\"D1\\\",\\\"setting\\\":\\\"\\\"}]\"}");
        //        list.add("{\"cancel\":\"true\",\"copyId\":\"\",\"id\":\"D2\",\"name\":\"D2\",\"order\":2,\"remark\":\"\",\"stepList\":[{\"action\":\"wait-forced\",\"config\":[{\"paramsName\":\"value\",\"paramsValue\":\"20000\"}],\"copyId\":\"\",\"copyPid\":\"\",\"id\":\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\",\"name\":\"1\",\"operationName\":\"强制等待\",\"operationType\":\"等待操作\",\"order\":1,\"pid\":\"D1\",\"setting\":\"\"}],\"stepMsg\":\"[{\\\"action\\\":\\\"wait-forced\\\",\\\"config\\\":[{\\\"paramsName\\\":\\\"value\\\",\\\"paramsValue\\\":\\\"20000\\\"}],\\\"copyId\\\":\\\"\\\",\\\"copyPid\\\":\\\"\\\",\\\"id\\\":\\\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\\\",\\\"name\\\":\\\"1\\\",\\\"operationName\\\":\\\"强制等待\\\",\\\"operationType\\\":\\\"等待操作\\\",\\\"order\\\":1,\\\"pid\\\":\\\"D1\\\",\\\"setting\\\":\\\"\\\"}]\"}");
        //        list.add("{\"cancel\":\"true\",\"copyId\":\"\",\"id\":\"D3\",\"name\":\"D3\",\"order\":3,\"remark\":\"\",\"stepList\":[{\"action\":\"wait-forced\",\"config\":[{\"paramsName\":\"value\",\"paramsValue\":\"20000\"}],\"copyId\":\"\",\"copyPid\":\"\",\"id\":\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\",\"name\":\"1\",\"operationName\":\"强制等待\",\"operationType\":\"等待操作\",\"order\":1,\"pid\":\"D1\",\"setting\":\"\"}],\"stepMsg\":\"[{\\\"action\\\":\\\"wait-forced\\\",\\\"config\\\":[{\\\"paramsName\\\":\\\"value\\\",\\\"paramsValue\\\":\\\"20000\\\"}],\\\"copyId\\\":\\\"\\\",\\\"copyPid\\\":\\\"\\\",\\\"id\\\":\\\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\\\",\\\"name\\\":\\\"1\\\",\\\"operationName\\\":\\\"强制等待\\\",\\\"operationType\\\":\\\"等待操作\\\",\\\"order\\\":1,\\\"pid\\\":\\\"D1\\\",\\\"setting\\\":\\\"\\\"}]\"}");
        //        list.add("{\"cancel\":\"true\",\"copyId\":\"\",\"id\":\"D4\",\"name\":\"D4\",\"order\":4,\"remark\":\"\",\"stepList\":[{\"action\":\"wait-forced\",\"config\":[{\"paramsName\":\"value\",\"paramsValue\":\"20000\"}],\"copyId\":\"\",\"copyPid\":\"\",\"id\":\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\",\"name\":\"1\",\"operationName\":\"强制等待\",\"operationType\":\"等待操作\",\"order\":1,\"pid\":\"D1\",\"setting\":\"\"}],\"stepMsg\":\"[{\\\"action\\\":\\\"wait-forced\\\",\\\"config\\\":[{\\\"paramsName\\\":\\\"value\\\",\\\"paramsValue\\\":\\\"20000\\\"}],\\\"copyId\\\":\\\"\\\",\\\"copyPid\\\":\\\"\\\",\\\"id\\\":\\\"7ca19a10-f90d-11ed-9020-fd19d38e1c1e\\\",\\\"name\\\":\\\"1\\\",\\\"operationName\\\":\\\"强制等待\\\",\\\"operationType\\\":\\\"等待操作\\\",\\\"order\\\":1,\\\"pid\\\":\\\"D1\\\",\\\"setting\\\":\\\"\\\"}]\"}");

        System.out.println("排序前：" + list);
        // 交换位置排序
        swap(list, 0, 2);
        System.out.println("交换位置排序：" + list);
        // 移动位置排序
        move(list, 1, 3);
        System.out.println("移动位置排序：" + list);

        // 用法
        list.forEach(getIndex((item, index) -> {
            System.out.println(item + "下标为：" + index);
        }));

    }
}
