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

package top.continew.admin.common.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserReg {

    /**
     * 
     * 验证用户名，支持中英文(包括全角字符)、数字、下划线和减号 (全角及汉字算两位),长度为4-20位,中文按二位计数
     * 
     * @author www.zuidaima.com
     * 
     * @param userName
     * 
     * @return
     * 
     */

    public static boolean validateUserName(String userName) {

        String validateStr = "^[\\w\\-－＿[０-９]\u4e00-\u9fa5\uFF21-\uFF3A\uFF41-\uFF5A]+$";

        boolean rs = false;

        rs = matcher(validateStr, userName);

        if (rs) {

            int strLenth = getStrLength(userName);

            if (strLenth < 4 || strLenth > 20) {

                rs = false;

            }

        }

        return rs;

    }

    /**
     * 
     * 获取字符串的长度，对双字符(包括汉字)按两位计数
     *
     * 
     * 
     * @param value
     * 
     * @return
     * 
     */

    public static int getStrLength(String value) {

        int valueLength = 0;

        String chinese = "[\u0391-\uFFE5]";

        for (int i = 0; i < value.length(); i++) {

            String temp = value.substring(i, i + 1);

            if (temp.matches(chinese)) {

                valueLength += 2;

            } else {

                valueLength += 1;

            }

        }

        return valueLength;

    }

    private static boolean matcher(String reg, String string) {

        boolean tem = false;

        Pattern pattern = Pattern.compile(reg);

        Matcher matcher = pattern.matcher(string);

        tem = matcher.matches();

        return tem;

    }

    public static void main(String[] args) {

        String str = "０－＿ｆ９ｚｄ中22";

        String st = "Ａ-ｄｑ_!!！！去符号标号！ノチセたのひちぬ！当然。!!..**半角";

        System.out.println(validateUserName(str));

        System.out.println(st.replaceAll("[\\pP&&[^-_]]", ""));

        System.out.println(st.replaceAll("[\\w\\-一-龥Ａ-Ｚａ-ｚ]", ""));

    }

}