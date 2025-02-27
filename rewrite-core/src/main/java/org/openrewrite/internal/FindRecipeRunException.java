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
package org.openrewrite.internal;

import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.RecipeRunException;
import org.openrewrite.RecipeRunExceptionResult;
import org.openrewrite.marker.Marker;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

public class FindRecipeRunException extends TreeVisitor<Tree, Integer> {

    private static final TreeVisitor<Tree, AtomicBoolean> hasRecipeRunExceptionVisitor = new TreeVisitor<Tree, AtomicBoolean>() {
        @Override
        public <M extends Marker> M visitMarker(Marker marker, AtomicBoolean atomicBoolean) {
            if (marker instanceof RecipeRunExceptionResult) {
                atomicBoolean.set(true);
            }
            return super.visitMarker(marker,atomicBoolean);
        }
    };

    private final RecipeRunException vt;
    private final Tree nearestTree;

    public FindRecipeRunException(RecipeRunException rre) {
        this.vt = rre;
        this.nearestTree = (Tree) requireNonNull(rre.getCursor()).getPath(Tree.class::isInstance).next();
    }

    @Override
    public Tree preVisit(Tree tree, Integer integer) {
        if (tree == nearestTree) {
            return tree.withException(vt, null);
        }
        return tree;
    }

    public static boolean hasRecipeRunException(Tree tree) {
        AtomicBoolean hasException = new AtomicBoolean(false);
        hasRecipeRunExceptionVisitor.visit(tree, hasException);
        return hasException.get();
    }
}
