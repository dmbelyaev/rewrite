/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import lombok.Getter;
import org.openrewrite.internal.lang.Nullable;

import java.util.StringJoiner;
import java.util.UUID;

public class RecipeRunException extends RuntimeException {
    @Getter
    private final UUID id = UUID.randomUUID();

    /**
     * Null if the exception occurs outside a visitor in an applicable test, etc.
     */
    @Getter
    @Nullable
    private final Cursor cursor;

    public RecipeRunException(Throwable cause, @Nullable Cursor cursor) {
        super(cause);
        this.cursor = cursor;
    }

    public RecipeRunException(Throwable cause) {
        this(cause, null);
    }

    public String getSanitizedStackTrace() {
        StringJoiner sanitized = new StringJoiner("\n");
        sanitized.add(getCause().getClass().getName() + ": " + getCause().getLocalizedMessage());

        int i = 0;
        for (StackTraceElement stackTraceElement : getCause().getStackTrace()) {
            if (stackTraceElement.getClassName().equals(RecipeScheduler.class.getName())) {
                break;
            }
            if (i++ >= 8) {
                sanitized.add("  ...");
                break;
            }
            sanitized.add("  " + stackTraceElement);
        }
        return sanitized.toString();
    }
}
