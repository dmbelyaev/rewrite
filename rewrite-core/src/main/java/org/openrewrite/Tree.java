/*
 * Copyright 2020 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.openrewrite.internal.MetricsHelper;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Markers;

import java.lang.reflect.Method;
import java.util.UUID;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@c")
public interface Tree {
    @SuppressWarnings("unused")
    @JsonProperty("@c")
    default String getJacksonPolymorphicTypeTag() {
        return getClass().getName();
    }

    static UUID randomId() {
        //noinspection ConstantConditions
        return MetricsHelper.UUID_TIMER.record(UUID::randomUUID);
    }

    /**
     * An id that can be used to identify a particular AST element, even after transformations have taken place on it
     *
     * @return A unique identifier
     */
    UUID getId();

    <T extends Tree> T withId(UUID id);

    /**
     * Supports polymorphic visiting via {@link TreeVisitor#visit(Tree, Object)}. This is useful in cases where an AST
     * type contains a field that is of a type with a hierarchy. The visitor doesn't have to figure out which visit
     * method to call by using instanceof.
     *
     * @param v   visitor
     * @param p   visit context
     * @param <R> visitor return type
     * @param <P> visit context type
     * @return visitor result
     */
    @Nullable
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        return v.defaultValue(this, p);
    }

    /**
     * Checks the supplied argument to see if the supplied visitor and its context would be valid arguments
     * to accept().
     * Typically this involves checking that the visitor is of a type that operates on this kind of tree.
     * e.g.: A Java Tree implementation would return true for JavaVisitors and false for MavenVisitors
     *
     * @param <P> the visitor's context argument
     * @return 'true' if the arguments to this function would be valid arguments to accept()
     */
    <P> boolean isAcceptable(TreeVisitor<?, P> v, P p);

    default <P> TreeVisitor<?, PrintOutputCapture<P>> printer(Cursor cursor) {
        return cursor.firstEnclosingOrThrow(SourceFile.class).printer(cursor);
    }

    default <P> String print(P p, Cursor cursor) {
        PrintOutputCapture<P> outputCapture = new PrintOutputCapture<>(p);
        this.<P>printer(cursor).visit(this, outputCapture, cursor);
        return outputCapture.out.toString();
    }

    default <P> String print(P p, TreeVisitor<?, PrintOutputCapture<P>> printer) {
        PrintOutputCapture<P> outputCapture = new PrintOutputCapture<>(p);
        printer.visit(this, outputCapture);
        return outputCapture.out.toString();
    }

    default String print(Cursor cursor) {
        return print(0, cursor);
    }

    default String print(TreeVisitor<?, PrintOutputCapture<Integer>> printer) {
        return print(0, printer);
    }

    default <P> String printTrimmed(P p, Cursor cursor) {
        return StringUtils.trimIndent(print(p, cursor));
    }

    default String printTrimmed(Cursor cursor) {
        return StringUtils.trimIndent(print(cursor));
    }

    default String printTrimmed(TreeVisitor<?, PrintOutputCapture<Integer>> printer) {
        return StringUtils.trimIndent(print(printer));
    }

    default boolean isScope(@Nullable Tree tree) {
        return tree != null && tree.getId().equals(getId());
    }

    default <T2 extends Tree> T2 cast() {
        //noinspection unchecked
        return (T2) this;
    }

    @Nullable
    default <T2 extends Tree> T2 safeCast() {
        try {
            return cast();
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    default <T extends Tree> T withException(Throwable throwable, @Nullable ExecutionContext ctx) {
        if (ctx != null) {
            ctx.getOnError().accept(throwable);
            ctx.putMessage(Recipe.PANIC, "true");
        }
        RecipeRunException rre;
        if (throwable instanceof RecipeRunException) {
            rre = (RecipeRunException) throwable;
        } else {
            rre = new RecipeRunException(throwable);
        }

        try {
            Method getMarkers = this.getClass().getDeclaredMethod("getMarkers");
            Method withMarkers = this.getClass().getDeclaredMethod("withMarkers", Markers.class);
            Markers markers = (Markers) getMarkers.invoke(this);
            return (T) withMarkers.invoke(this, markers
                    .computeByType(new RecipeRunExceptionResult(rre), (s1, s2) -> s1 == null ? s2 : s1));
        } catch (Throwable ignored) {
            return (T) this;
        }
    }


}
