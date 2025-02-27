/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.search;

import lombok.Getter;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;

@Getter
public class FindDeprecatedUses extends Recipe {
    @Option(displayName = "Type pattern",
            description = "A type pattern that is used to find deprecations from certain types.",
            example = "org.springframework..*",
            required = false)
    @Nullable
    private final String typePattern;

    @Option(displayName = "Match inherited",
            description = "When enabled, find types that inherit from a deprecated type.",
            required = false)
    @Nullable
    private final Boolean matchInherited;

    @Option(displayName = "Ignore deprecated scopes",
            description = "When a deprecated type is used in a deprecated method or class, ignore it.",
            required = false)
    @Nullable
    private final Boolean ignoreDeprecatedScopes;

    public FindDeprecatedUses(@Nullable String typePattern, @Nullable Boolean matchInherited, @Nullable Boolean ignoreDeprecatedScopes) {
        this.typePattern = typePattern;
        this.matchInherited = matchInherited;
        this.ignoreDeprecatedScopes = ignoreDeprecatedScopes;
        doNext(new FindDeprecatedMethods((typePattern == null ? "*..*" : typePattern) + " *(..)", ignoreDeprecatedScopes));
        doNext(new FindDeprecatedClasses(typePattern, matchInherited, ignoreDeprecatedScopes));
        doNext(new FindDeprecatedFields(typePattern, ignoreDeprecatedScopes));
    }

    @Override
    public String getDisplayName() {
        return "Find uses of deprecated classes, methods, and fields";
    }

    @Override
    public String getDescription() {
        return "Find deprecated uses of methods, fields, and types. Optionally ignore those classes that are inside of deprecated scopes.";
    }
}
