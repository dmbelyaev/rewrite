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
package org.openrewrite.java;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * A recipe that will rename a package name in package statements, imports, and fully-qualified types (see: NOTE).
 * <p>
 * NOTE: Does not currently transform all possible type references, and accomplishing this would be non-trivial.
 * For example, a method invocation select might refer to field `A a` whose type has now changed to `A2`, and so the type
 * on the select should change as well. But how do we identify the set of all method selects which refer to `a`? Suppose
 * it were prefixed like `this.a`, or `MyClass.this.a`, or indirectly via a separate method call like `getA()` where `getA()`
 * is defined on the super class.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ChangePackage extends Recipe {
    @Option(displayName = "Old package name",
            description = "The package name to replace.",
            example = "com.yourorg.foo")
    String oldPackageName;

    @Option(displayName = "New package name",
            description = "New package name to replace the old package name with.",
            example = "com.yourorg.bar")
    String newPackageName;

    @With
    @Option(displayName = "Recursive",
            description = "Recursively change subpackage names",
            required = false,
            example = "true")
    @Nullable
    Boolean recursive;

    @Override
    public String getDisplayName() {
        return "Rename package name";
    }

    @Override
    public String getDescription() {
        return "A recipe that will rename a package name in package statements, imports, and fully-qualified types.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext executionContext) {
                if (cu.getPackageDeclaration() != null) {
                    String original = cu.getPackageDeclaration().getExpression()
                            .printTrimmed(getCursor()).replaceAll("\\s", "");
                    if (original.startsWith(oldPackageName)) {
                        return cu.withMarkers(cu.getMarkers().searchResult());
                    }
                }
                if (recursive != null && recursive) {
                    doAfterVisit(new UsesType<>(oldPackageName + "..*"));
                } else {
                    doAfterVisit(new UsesType<>(oldPackageName + ".*"));
                }
                return cu;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangePackageVisitor();
    }

    private class ChangePackageVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String RENAME_TO_KEY = "renameTo";
        private static final String RENAME_FROM_KEY = "renameFrom";

        private final Map<String, JavaType> oldNameToChangedType = new HashMap<>();
        private final JavaType.Class newPackageType = JavaType.ShallowClass.build(newPackageName);

        @Override
        public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext ctx) {
            JavaSourceFile c = super.visitJavaSourceFile(cu, ctx);

            String changingTo = getCursor().getMessage(RENAME_TO_KEY);
            if (changingTo != null) {
                String path = ((SourceFile) c).getSourcePath().toString().replace('\\', '/');
                String changingFrom = getCursor().getMessage(RENAME_FROM_KEY);
                assert changingFrom != null;
                c = ((SourceFile) c).withSourcePath(Paths.get(path.replaceFirst(
                        changingFrom.replace('.', '/'),
                        changingTo.replace('.', '/')
                )));

                for (J.Import anImport : c.getImports()) {
                    if (anImport.getPackageName().equals(changingTo)) {
                        c = new RemoveImport<ExecutionContext>(anImport.getTypeName(), true).visitJavaSourceFile(c, ctx);
                    }
                }
            }
            return c;
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);

            if (f.isFullyQualifiedClassReference(oldPackageName)) {
                Cursor parent = getCursor().getParent();
                if (parent != null &&
                        // Ensure the parent isn't a J.FieldAccess OR the parent doesn't match the target package name.
                        (!(parent.getValue() instanceof J.FieldAccess) ||
                        (!(((J.FieldAccess) parent.getValue()).isFullyQualifiedClassReference(newPackageName))))) {

                    f = TypeTree.build(((JavaType.FullyQualified) newPackageType).getFullyQualifiedName())
                            .withPrefix(f.getPrefix());
                }
            }
            return f;
        }

        @Override
        public J.Package visitPackage(J.Package pkg, ExecutionContext context) {
            String original = pkg.getExpression().printTrimmed(getCursor()).replaceAll("\\s", "");
            getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, RENAME_FROM_KEY, original);

            if (original.equals(oldPackageName)) {
                getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, RENAME_TO_KEY, newPackageName);

                if (newPackageName.contains(".")) {
                    pkg = pkg.withTemplate(JavaTemplate.builder(this::getCursor, newPackageName).build(), pkg.getCoordinates().replace());
                } else {
                    // Covers unlikely scenario where the package is removed.
                    getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, "UPDATE_PREFIX", true);
                    pkg = null;
                }
            } else if (isTargetRecursivePackageName(original)) {
                String changingTo = getNewPackageName(original);
                getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, RENAME_TO_KEY, changingTo);
                pkg = pkg.withTemplate(JavaTemplate.builder(this::getCursor, changingTo).build(), pkg.getCoordinates().replace());
            }
            //noinspection ConstantConditions
            return pkg;
        }

        @Override
        public J.Import visitImport(J.Import _import, ExecutionContext executionContext) {
            // Polls message before calling super to change the prefix of the first import if applicable.
            Boolean updatePrefix = getCursor().pollNearestMessage("UPDATE_PREFIX");
            if (updatePrefix != null && updatePrefix) {
                _import = _import.withPrefix(Space.EMPTY);
            }
            return super.visitImport(_import, executionContext);
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            Boolean updatePrefix = getCursor().pollNearestMessage("UPDATE_PREFIX");
            if (updatePrefix != null && updatePrefix) {
                c = c.withPrefix(Space.EMPTY);
            }
            return c;
        }

        @Override
        public @Nullable JavaType visitType(@Nullable JavaType javaType, ExecutionContext executionContext) {
            return updateType(javaType);
        }

        @Override
        public J postVisit(J tree, ExecutionContext executionContext) {
            J j = super.postVisit(tree, executionContext);
            if (j instanceof J.MethodDeclaration) {
                J.MethodDeclaration m = (J.MethodDeclaration) j;
                return m.withMethodType(updateType(m.getMethodType()));
            } else if (j instanceof J.MethodInvocation) {
                J.MethodInvocation m = (J.MethodInvocation) j;
                return m.withMethodType(updateType(m.getMethodType()));
            } else if (j instanceof J.NewClass) {
                J.NewClass n = (J.NewClass) j;
                return n.withConstructorType(updateType(n.getConstructorType()));
            } else if (j instanceof TypedTree) {
                return ((TypedTree) j).withType(updateType(((TypedTree) j).getType()));
            }
            return j;
        }

        @Nullable
        private JavaType updateType(@Nullable JavaType oldType) {
            if (oldType == null || oldType instanceof JavaType.Unknown) {
                return oldType;
            }

            JavaType type = oldNameToChangedType.get(oldType.toString());
            if (type != null) {
                return type;
            }

            if (oldType instanceof JavaType.Parameterized) {
                JavaType.Parameterized pt = (JavaType.Parameterized) oldType;
                pt = pt.withTypeParameters(ListUtils.map(pt.getTypeParameters(), tp -> {
                    if (tp instanceof JavaType.FullyQualified) {
                        JavaType.FullyQualified tpFq = (JavaType.FullyQualified) tp;
                        if (isTargetFullyQualifiedType(tpFq)) {
                            return TypeUtils.asFullyQualified(JavaType.buildType(getNewPackageName(tpFq.getPackageName()) + "." + tpFq.getClassName()));
                        }
                    }
                    return tp;
                }));

                if (isTargetFullyQualifiedType(pt)) {
                    pt = pt.withType((JavaType.FullyQualified) updateType(pt.getType()));
                }

                oldNameToChangedType.put(oldType.toString(), pt);
                return pt;
            } else if (oldType instanceof JavaType.FullyQualified) {
                JavaType.FullyQualified original = TypeUtils.asFullyQualified(oldType);
                if (isTargetFullyQualifiedType(original)) {
                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(JavaType.buildType(getNewPackageName(original.getPackageName()) + "." + original.getClassName()));
                    oldNameToChangedType.put(oldType.toString(), fq);
                    return fq;
                }
            } else if (oldType instanceof JavaType.GenericTypeVariable) {
                JavaType.GenericTypeVariable gtv = (JavaType.GenericTypeVariable) oldType;
                gtv = gtv.withBounds(ListUtils.map(gtv.getBounds(), b -> {
                    if (b instanceof JavaType.FullyQualified && isTargetFullyQualifiedType((JavaType.FullyQualified) b)) {
                        return updateType(b);
                    }
                    return b;
                }));

                oldNameToChangedType.put(oldType.toString(), gtv);
                return gtv;
            } else if (oldType instanceof JavaType.Variable) {
                JavaType.Variable variable = (JavaType.Variable) oldType;
                variable = variable.withType(updateType(variable.getType()));
                oldNameToChangedType.put(oldType.toString(), variable);
                return variable;
            } else if (oldType instanceof JavaType.Array) {
                JavaType.Array array = (JavaType.Array) oldType;
                array = array.withElemType(updateType(array.getElemType()));
                oldNameToChangedType.put(oldType.toString(), array);
                return array;
            }
            return oldType;
        }

        @Nullable
        private JavaType.Method updateType(@Nullable JavaType.Method oldMethodType) {
            if (oldMethodType != null) {
                JavaType.Method method = (JavaType.Method) oldNameToChangedType.get(oldMethodType.toString());
                if (method != null) {
                    return method;
                }

                method = oldMethodType;
                method = method.withDeclaringType((JavaType.FullyQualified) updateType(method.getDeclaringType()))
                        .withReturnType(updateType(method.getReturnType()))
                        .withParameterTypes(ListUtils.map(method.getParameterTypes(), this::updateType));
                oldNameToChangedType.put(oldMethodType.toString(), method);
                return method;
            }
            return null;
        }


        private String getNewPackageName(String packageName) {
            return (recursive == null || recursive) && !newPackageName.endsWith(packageName.substring(oldPackageName.length()))?
                    newPackageName + packageName.substring(oldPackageName.length()) : newPackageName;
        }

        private boolean isTargetFullyQualifiedType(@Nullable JavaType.FullyQualified fq) {
            return fq != null &&
                    (fq.getPackageName().equals(oldPackageName) && !fq.getClassName().isEmpty() ||
                            isTargetRecursivePackageName(fq.getPackageName()));
        }

        private boolean isTargetRecursivePackageName(String packageName) {
            return (recursive == null || recursive) && packageName.startsWith(oldPackageName) && !packageName.startsWith(newPackageName);
        }
    }
}
