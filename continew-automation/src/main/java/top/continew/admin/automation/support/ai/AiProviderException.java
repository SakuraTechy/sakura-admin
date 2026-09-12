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

package top.continew.admin.automation.support.ai;

/**
 * AI 供应商调用异常。
 *
 * <p>封装 AI 服务调用过程中的各类错误：网络超时、鉴权失败、限流、模型错误等。</p>
 */
public class AiProviderException extends RuntimeException {

    private final String provider;
    private final String errorCode;
    private final boolean retryable;

    public AiProviderException(String provider, String message) {
        this(provider, message, null, null, false);
    }

    public AiProviderException(String provider, String message, Throwable cause) {
        this(provider, message, cause, null, false);
    }

    public AiProviderException(String provider, String message, String errorCode, boolean retryable) {
        this(provider, message, null, errorCode, retryable);
    }

    public AiProviderException(String provider, String message, Throwable cause, String errorCode, boolean retryable) {
        super(String.format("[%s] %s", provider, message), cause);
        this.provider = provider;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getProvider() {
        return provider;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
