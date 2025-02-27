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
package org.openrewrite.java.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.function.BiPredicate;

public class RemoveUnneededAssertion extends Recipe {
    private static final MethodMatcher JUNIT_JUPITER_ASSERT_TRUE_MATCHER =
            new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(..)");
    private static final MethodMatcher JUNIT_JUPITER_ASSERT_FALSE_MATCHER =
            new MethodMatcher("org.junit.jupiter.api.Assertions assertFalse(..)");
    private static final MethodMatcher JUNIT_ASSERT_TRUE_MATCHER =
            new MethodMatcher("org.junit.Assert assertTrue(boolean)");
    private static final MethodMatcher JUNIT_ASSERT_FALSE_MATCHER =
            new MethodMatcher("org.junit.Assert assertFalse(boolean)");

    private static final MethodMatcher JUNIT_ASSERT_MESSAGE_TRUE_MATCHER =
            new MethodMatcher("org.junit.Assert assertTrue(String, boolean)");
    private static final MethodMatcher JUNIT_ASSERT_MESSAGE_FALSE_MATCHER =
            new MethodMatcher("org.junit.Assert assertFalse(String, boolean)");

    @Override
    public String getDisplayName() {
        return "Remove Unneeded Assertions";
    }

    @Override
    public String getDescription() {
        return "Remove unneeded assertions like `assert true`, `assertTrue(true)`, or `assertFalse(false)`.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesMethod<>(JUNIT_JUPITER_ASSERT_TRUE_MATCHER));
                doAfterVisit(new UsesMethod<>(JUNIT_JUPITER_ASSERT_FALSE_MATCHER));
                doAfterVisit(new UsesMethod<>(JUNIT_ASSERT_TRUE_MATCHER));
                doAfterVisit(new UsesMethod<>(JUNIT_ASSERT_FALSE_MATCHER));
                doAfterVisit(new UsesMethod<>(JUNIT_ASSERT_MESSAGE_TRUE_MATCHER));
                doAfterVisit(new UsesMethod<>(JUNIT_ASSERT_MESSAGE_FALSE_MATCHER));
                return super.visitJavaSourceFile(cu, executionContext);
            }

            @Override
            public J.Assert visitAssert(J.Assert _assert, ExecutionContext executionContext) {
                if (J.Literal.isLiteralValue(_assert.getCondition(), true)) {
                    return _assert.withMarkers(_assert.getMarkers().searchResult());
                }
                return _assert;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveUnneededAssertionVisitor<>();
    }

    private static class RemoveUnneededAssertionVisitor<P> extends JavaIsoVisitor<P> {

        @FunctionalInterface
        private interface InvokeRemoveMethodCallVisitor {
            J.CompilationUnit invoke(
                    J.CompilationUnit cu,
                    MethodMatcher methodMatcher,
                    BiPredicate<Integer, Expression> argumentPredicate
            );
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
            J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, p);
            // We can compute the types in use once, because this logic only removes method calls, never adds them.
            TypesInUse typesInUse = compilationUnit.getTypesInUse();
            InvokeRemoveMethodCallVisitor invokeRemoveMethodCallVisitor = (inputCu, methodMatcher, argumentPredicate) -> {
                if (typesInUse.getUsedMethods().stream().anyMatch(methodMatcher::matches)) {
                    // Only visit the subtree when we know the method is present.
                    return (J.CompilationUnit) new RemoveMethodCallVisitor<>(methodMatcher, argumentPredicate)
                            .visitNonNull(cu, p, getCursor().getParentOrThrow());
                }
                return inputCu;
            };

            // Junit Jupiter
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_JUPITER_ASSERT_TRUE_MATCHER,
                    (arg, expr) -> !arg.equals(0) || J.Literal.isLiteralValue(expr, true)
            );
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_JUPITER_ASSERT_FALSE_MATCHER,
                    (arg, expr) -> !arg.equals(0) || J.Literal.isLiteralValue(expr, false)
            );

            // Junit 4
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_ASSERT_TRUE_MATCHER,
                    (arg, expr) -> arg.equals(0) && J.Literal.isLiteralValue(expr, true)
            );
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_ASSERT_FALSE_MATCHER,
                    (arg, expr) -> arg.equals(0) && J.Literal.isLiteralValue(expr, false)
            );
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_ASSERT_MESSAGE_TRUE_MATCHER,
                    (arg, expr) -> !arg.equals(1) || J.Literal.isLiteralValue(expr, true)
            );
            compilationUnit = invokeRemoveMethodCallVisitor.invoke(
                    compilationUnit,
                    JUNIT_ASSERT_MESSAGE_FALSE_MATCHER,
                    (arg, expr) -> !arg.equals(1) || J.Literal.isLiteralValue(expr, false)
            );
            return compilationUnit;
        }

        @Override
        public J.Assert visitAssert(J.Assert _assert, P p) {
            if (_assert.getCondition() instanceof J.Literal) {
                if (J.Literal.isLiteralValue(_assert.getCondition(), true)) {
                    //noinspection ConstantConditions
                    return null;
                }
            }
            return super.visitAssert(_assert, p);
        }
    }
}
