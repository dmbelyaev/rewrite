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
@file:Suppress("DuplicatedCode", "ConstantConditions", "JUnitMalformedDeclaration", "UnusedAssignment",
    "InstantiationOfUtilityClass", "StatementWithEmptyBody", "StringOperationCanBeSimplified"
)

package org.openrewrite.java

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.ExecutionContext
import org.openrewrite.Issue
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.java.tree.Space
import org.openrewrite.java.tree.TypeUtils
import org.openrewrite.test.RewriteTest
import java.util.Comparator.comparing

@Suppress("Convert2MethodRef", "UnnecessaryBoxing")
interface JavaTemplateTest : RewriteTest, JavaRecipeTest {

    @Issue("https://github.com/openrewrite/rewrite/issues/1339")
    @Test
    fun templateStatementIsWithinTryWithResourcesBlock() = assertChanged(
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                override fun visitNewClass(newClass: J.NewClass, p: ExecutionContext): J {
                    var nc = super.visitNewClass(newClass, p)
                    val md: J.MethodDeclaration? = cursor.firstEnclosing(J.MethodDeclaration::class.java)
                    if (md != null && md.simpleName.equals("createBis")) {
                        return nc
                    }
                    if (newClass.type != null && (newClass.type as JavaType.Class).fullyQualifiedName.equals("java.io.ByteArrayInputStream")
                        && newClass.arguments.isNotEmpty()
                    ) {
                        nc = nc.withTemplate(
                            JavaTemplate.builder({ this.cursor }, "createBis(#{anyArray()})").build(),
                            newClass.coordinates.replace(), newClass.arguments[0]
                        )
                    }
                    return nc
                }
            }
        },
        before = """
            import java.io.*;
            import java.nio.charset.StandardCharsets;
            
            class Test {
                ByteArrayInputStream createBis(byte[] bytes) {
                    return new ByteArrayInputStream(bytes);
                }
                
                void doSomething() {
                    String sout = "";
                    try (BufferedReader br = new BufferedReader(new FileReader(null))) {
                        new ByteArrayInputStream("bytes".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        """,
        after = """
            import java.io.*;
            import java.nio.charset.StandardCharsets;
            
            class Test {
                ByteArrayInputStream createBis(byte[] bytes) {
                    return new ByteArrayInputStream(bytes);
                }
                
                void doSomething() {
                    String sout = "";
                    try (BufferedReader br = new BufferedReader(new FileReader(null))) {
                        createBis("bytes".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1796")
    @Test
    fun replaceIdentifierWithMethodInvocation() = assertChanged(
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    return method.withBody(visit(method.body, p) as J.Block)
                }

                override fun visitIdentifier(identifier: J.Identifier, p: ExecutionContext): J {
                    return if (identifier.simpleName == "f") {
                        identifier.withTemplate(
                            JavaTemplate.builder({ this.cursor }, "#{any(java.io.File)}.getCanonicalFile().toPath()").build(),
                            identifier.coordinates.replace(),
                            identifier
                        )
                    } else {
                        identifier
                    }
                }
            }
        },
        before = """
            import java.io.File;
            class Test {
                void test(File f) {
                    System.out.println(f);
                }
            }
        """,
        after = """
            import java.io.File;
            class Test {
                void test(File f) {
                    System.out.println(f.getCanonicalFile().toPath());
                }
            }
        """,
        expectedCyclesThatMakeChanges = 1,
        cycles = 1
    )

    @Suppress("UnaryPlus", "UnusedAssignment")
    @Test
    fun replaceExpressionWithAnotherExpression() = assertChanged(
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                override fun visitUnary(unary: J.Unary, p: ExecutionContext): J {
                    return unary.withTemplate(
                        JavaTemplate.builder({ this.cursor }, "#{any()}++").build(),
                        unary.coordinates.replace(),
                        unary.expression
                    )
                }
            }
        },
        before = """
            class Test {
                void test(int i) {
                    int n = +i;
                }
            }
        """,
        after = """
            class Test {
                void test(int i) {
                    int n = i++;
                }
            }
        """,
        expectedCyclesThatMakeChanges = 1,
        cycles = 1
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1796")
    @Test
    fun replaceFieldAccessWithMethodInvocation() = assertChanged(
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    return method.withBody(visit(method.body, p) as J.Block)
                }

                override fun visitFieldAccess(fa: J.FieldAccess, p: ExecutionContext): J {
                    return if (fa.simpleName == "f") {
                        fa.withTemplate(
                            JavaTemplate.builder({ this.cursor }, "#{any(java.io.File)}.getCanonicalFile().toPath()").build(),
                            fa.coordinates.replace(),
                            fa
                        )
                    } else {
                        fa
                    }
                }
            }
        },
        before = """
            import java.io.File;
            class Test {
                File f;
                void test() {
                    System.out.println(this.f);
                }
            }
        """,
        after = """
            import java.io.File;
            class Test {
                File f;
                void test() {
                    System.out.println(this.f.getCanonicalFile().toPath());
                }
            }
        """,
        expectedCyclesThatMakeChanges = 1,
        cycles = 1
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1092")
    @Test
    fun methodInvocationReplacementHasContextAboutLocalVariables() = assertChanged(
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                override fun visitMethodInvocation(
                    method: J.MethodInvocation,
                    ctx: ExecutionContext
                ): J.MethodInvocation {
                    return if (method.simpleName == "clear") {
                        method.withTemplate(
                            JavaTemplate.builder({ this.cursor }, """words.add("jon");""")
                                .build(),
                            method.coordinates.replace()
                        )
                    } else method
                }
            }
        },
        before = """
            import java.util.List;
            class Test {
                List<String> words;
                void test() {
                    words.clear();
                }
            }
        """,
        after = """
            import java.util.List;
            class Test {
                List<String> words;
                void test() {
                    words.add("jon");
                }
            }
        """
    )

    @Test
    fun innerEnumWithStaticMethod(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "new A()").build()

                override fun visitNewClass(newClass: J.NewClass, p: ExecutionContext): J =
                    when (newClass.arguments[0]) {
                        is J.Empty -> newClass
                        else -> newClass.withTemplate(t, newClass.coordinates.replace())
                    }
            }
        },
        typeValidation = {
            identifiers = false
        },
        before = """
            class A {
                public enum Type {
                    One;
            
                    public Type(String t) {
                    }
            
                    String t;
            
                    public static Type fromType(String type) {
                        return null;
                    }
                }
            
                public A(Type type) {}
                public A() {}
            
                public void method(Type type) {
                    new A(type);
                }
            }
        """,
        after = """
            class A {
                public enum Type {
                    One;
            
                    public Type(String t) {
                    }
            
                    String t;
            
                    public static Type fromType(String type) {
                        return null;
                    }
                }
            
                public A(Type type) {}
                public A() {}
            
                public void method(Type type) {
                    new A();
                }
            }
        """
    )

    @Test
    fun replacePackage(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "b").build()

                override fun visitPackage(pkg: J.Package, p: ExecutionContext): J.Package {
                    if (pkg.expression.printTrimmed(cursor) == "a") {
                        return pkg.withTemplate(t, pkg.coordinates.replace())
                    }
                    return super.visitPackage(pkg, p)
                }

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext,
                ): J.ClassDeclaration {
                    var cd = super.visitClassDeclaration(classDecl, p)
                    if (classDecl.type!!.packageName == "a") {
                        cd = cd.withType(cd.type!!.withFullyQualifiedName("b.${cd.simpleName}"))
                    }
                    return cd
                }
            }
        },
        before = """
            package a;
            class Test {
            }
        """,
        after = """
            package b;
            class Test {
            }
        """
    )

    @Test
    fun replaceMethod(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int test2(int n) { return n; }").build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext,
                ): J.MethodDeclaration {
                    if (method.simpleName == "test") {
                        return method.withTemplate(t, method.coordinates.replace())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                void test() {
                }
            }
        """,
        after = """
            class Test {
            
                int test2(int n) {
                    return n;
                }
            }
        """,
        afterConditions = { cu ->
            val methodType = (cu.classes.first().body.statements.first() as J.MethodDeclaration).methodType!!
            assertThat(methodType.returnType).isEqualTo(JavaType.Primitive.Int)
            assertThat(methodType.parameterTypes).containsExactly(JavaType.Primitive.Int)
        }
    )

    @Test
    fun replaceLambdaWithMethodReference(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "Object::toString").build()

                override fun visitLambda(lambda: J.Lambda, p: ExecutionContext): J {
                    return lambda.withTemplate(t, lambda.coordinates.replace())
                }
            }
        },
        before = """
            import java.util.function.Function;

            class Test {
                Function<Object, String> toString = it -> it.toString();
            }
        """,
        after = """
            import java.util.function.Function;

            class Test {
                Function<Object, String> toString = Object::toString;
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1120")
    @Suppress("UnusedAssignment", "ResultOfMethodCallIgnored", "CodeBlock2Expr")
    fun replaceStatementInLambdaBodySingleStatementBlock(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "return n == 1;").build()

                override fun visitReturn(retrn: J.Return, p: ExecutionContext): J {
                    if (retrn.expression is J.Binary) {
                        val binary = retrn.expression as J.Binary
                        if (binary.right is J.Literal && Integer.valueOf(0) == (binary.right as J.Literal).value) {
                            return retrn.withTemplate(t, retrn.coordinates.replace())
                        }
                    }
                    return retrn
                }
            }
        },
        before = """
            import java.util.stream.Stream;

            class Test {
                int n;

                void method(Stream<Object> obj) {
                    obj.filter(o -> {
                        return n == 0;
                    });
                }
            }
        """,
        after = """
            import java.util.stream.Stream;

            class Test {
                int n;

                void method(Stream<Object> obj) {
                    obj.filter(o -> {
                        return n == 1;
                    });
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1120")
    @Suppress("UnusedAssignment", "ResultOfMethodCallIgnored", "ConstantConditions")
    fun replaceStatementInLambdaBodyWithVariableDeclaredInBlock(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "return n == 1;").build()

                override fun visitReturn(retrn: J.Return, p: ExecutionContext): J {
                    if (retrn.expression is J.Binary) {
                        val binary = retrn.expression as J.Binary
                        if (binary.right is J.Literal && Integer.valueOf(0) == (binary.right as J.Literal).value) {
                            return retrn.withTemplate(t, retrn.coordinates.replace())
                        }
                    }
                    return retrn
                }
            }
        },
        before = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<Object> obj) {
                    obj.filter(o -> {
                        int n = 0;
                        return n == 0;
                    });
                }
            }
        """,
        after = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<Object> obj) {
                    obj.filter(o -> {
                        int n = 0;
                        return n == 1;
                    });
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1120")
    @Suppress("ResultOfMethodCallIgnored", "UnusedAssignment")
    fun replaceStatementInLambdaBodyMultiStatementBlock(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "#{any(java.lang.String)}.toUpperCase()").build()

                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    if (method.simpleName.equals("toLowerCase")) {
                        return method.withTemplate(t, method.coordinates.replace(), method.select)
                    }
                    return super.visitMethodInvocation(method, p)
                }
            }
        },
        before = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<String> obj) {
                    obj.map(o -> {
                        String str = o;
                        str = o.toLowerCase();
                        return str;
                    });
                }
            }
        """,
        after = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<String> obj) {
                    obj.map(o -> {
                        String str = o;
                        str = o.toUpperCase();
                        return str;
                    });
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1120")
    @Suppress("ResultOfMethodCallIgnored", "SizeReplaceableByIsEmpty")
    fun replaceSingleExpressionInLambdaBody(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "#{any(java.lang.String)}.toUpperCase()").build()

                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    if (method.simpleName.equals("toLowerCase")) {
                        return method.withTemplate(t, method.coordinates.replace(), method.select)
                    }
                    return super.visitMethodInvocation(method, p)
                }
            }
        },
        before = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<String> obj) {
                    obj.filter(o -> o.toLowerCase().length() > 0);
                }
            }
        """,
        after = """
            import java.util.stream.Stream;

            class Test {
                static void method(Stream<String> obj) {
                    obj.filter(o -> o.toUpperCase().length() > 0);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/2176")
    @Test
    fun replaceSingleExpressionInLambdaBodyWithExpression(jp: JavaParser.Builder<*, *>) = assertChanged (
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val ENUM_EQUALS = MethodMatcher("java.lang.Enum equals(java.lang.Object)")
                val t = JavaTemplate.builder({ cursor }, "#{any()} == #{any()}").build()

                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    if (ENUM_EQUALS.matches(method)) {
                        return method.withTemplate(t, method.coordinates.replace(), method.select, method.arguments[0])
                    }
                    return super.visitMethodInvocation(method, p)
                }
            }
        },
        before = """
            import java.util.stream.Stream;

            class Test {
                enum Abc {A,B,C}
                static void method(Stream<Abc> obj) {
                    Object a = obj.filter(o -> o.equals(Abc.A));
                }
            }
        """,
        after = """
            import java.util.stream.Stream;

            class Test {
                enum Abc {A,B,C}
                static void method(Stream<Abc> obj) {
                    Object a = obj.filter(o -> o == Abc.A);
                }
            }
        """
    )

    @Suppress("ClassInitializerMayBeStatic")
    @Test
    fun replaceMethodNameAndArgumentsSimultaneously(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "acceptString(#{any()}.toString())")
                    .javaParser {
                        JavaParser.fromJavaVersion()
                            .dependsOn(
                                """
                            package org.openrewrite;
                            public class A {
                                public A acceptInteger(Integer i) { return this; }
                                public A acceptString(String s) { return this; }
                                public A someOtherMethod() { return this; }
                            }
                        """
                            )
                            .build()
                    }
                    .build()

                override fun visitMethodInvocation(
                    method: J.MethodInvocation,
                    p: ExecutionContext
                ): J.MethodInvocation {
                    var m: J.MethodInvocation = super.visitMethodInvocation(method, p)
                    if (m.simpleName.equals("acceptInteger")) {
                        m = m.withTemplate(t, m.coordinates.replaceMethod(), m.arguments[0])
                    }
                    return m
                }
            }
        },
        dependsOn = arrayOf(
            """
                package org.openrewrite;
                public class A {
                    public A acceptInteger(Integer i) { return this; }
                    public A acceptString(String s) { return this; }
                    public A someOtherMethod() { return this; }
                }
            """
        ),
        before = """
            package org.openrewrite;
            
            public class Foo {
                {
                    Integer i = 1;
                    new A().someOtherMethod()
                            .acceptInteger(i)
                            .someOtherMethod();
                }
            }
        """,
        after = """
            package org.openrewrite;
            
            public class Foo {
                {
                    Integer i = 1;
                    new A().someOtherMethod()
                            .acceptString(i.toString())
                            .someOtherMethod();
                }
            }
        """
    )

    @Test
    fun replaceMethodInvocationWithArray(jp: JavaParser) = assertChanged(
        jp,
        dependsOn = arrayOf(
            """
            package org.openrewrite;
            public class Test {
                public void method(int[] val) {}
                public void method(int[] val1, String val2) {}
            }
        """.trimIndent()
        ),
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "#{anyArray(int)}").build()

                override fun visitMethodInvocation(
                    method: J.MethodInvocation,
                    p: ExecutionContext
                ): J.MethodInvocation {
                    var m: J.MethodInvocation = super.visitMethodInvocation(method, p)
                    if (m.simpleName.equals("method") && m.arguments.size == 2) {
                        m = m.withTemplate(t, m.coordinates.replaceArguments(), m.arguments[0])
                    }
                    return m
                }
            }
        },
        typeValidation = {
            identifiers = false
        },
        before = """
            import org.openrewrite.Test;
            class A {
                public void method() {
                    Test test = new Test();
                    int[] arr = new int[]{};
                    test.method(arr, null);
                }
            }
        """,
        after = """
            import org.openrewrite.Test;
            class A {
                public void method() {
                    Test test = new Test();
                    int[] arr = new int[]{};
                    test.method(arr);
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/602")
    @Test
    fun replaceMethodInvocationWithMethodReference(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "Object::toString").build()

                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    return method.withTemplate(t, method.coordinates.replace())
                }

            }
        },
        before = """
            import java.util.function.Function;

            class Test {
                Function<Object, String> toString = getToString();
                
                static Function<Object, String> getToString() {
                    return Object::toString;
                } 
            }
        """,
        after = """
            import java.util.function.Function;

            class Test {
                Function<Object, String> toString = Object::toString;
                
                static Function<Object, String> getToString() {
                    return Object::toString;
                } 
            }
        """
    )

    @Test
    fun replaceMethodParameters(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int m, java.util.List<String> n")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.simpleName == "test" && method.parameters.size == 1) {
                        // insert in outer method
                        val m: J.MethodDeclaration = method.withTemplate(t, method.coordinates.replaceParameters())
                        val newRunnable = (method.body!!.statements[0] as J.NewClass)

                        // insert in inner method
                        val innerMethod = (newRunnable.body!!.statements[0] as J.MethodDeclaration)
                        return m.withTemplate(t, innerMethod.coordinates.replaceParameters())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                void test() {
                    new Runnable() {
                        void inner() {
                        }
                    };
                }
            }
        """,
        after = """
            class Test {
                void test(int m, java.util.List<String> n) {
                    new Runnable() {
                        void inner(int m, java.util.List<String> n) {
                        }
                    };
                }
            }
        """,
        afterConditions = { cu ->
            val type = (cu.classes.first().body.statements.first() as J.MethodDeclaration).methodType!!

            assertThat(type.parameterNames)
                .`as`("Changing the method's parameters should have also updated its type's parameter names")
                .containsExactly("m", "n")
            assertThat(type.parameterTypes[0])
                .`as`("Changing the method's parameters should have resulted in the first parameter's type being 'int'")
                .isEqualTo(JavaType.Primitive.Int)
            assertThat(type.parameterTypes[1])
                .matches(
                    {
                        it is JavaType.Parameterized
                                && it.type.fullyQualifiedName == "java.util.List"
                                && it.typeParameters.size == 1
                                && it.typeParameters.first()
                            .asFullyQualified()!!.fullyQualifiedName == "java.lang.String"
                    },
                    "Changing the method's parameters should have resulted in the second parameter's type being 'List<String>'"
                )
        }
    )

    @Test
    fun replaceMethodParametersVariadicArray(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "Object[]... values")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.simpleName == "test" && method.parameters.firstOrNull() is J.Empty) {
                        // insert in outer method
                        val m: J.MethodDeclaration = method.withTemplate(t, method.coordinates.replaceParameters())
                        val newRunnable = (method.body!!.statements[0] as J.NewClass)

                        // insert in inner method
                        val innerMethod = (newRunnable.body!!.statements[0] as J.MethodDeclaration)
                        return m.withTemplate(t, innerMethod.coordinates.replaceParameters())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                void test() {
                    new Runnable() {
                        void inner() {
                        }
                    };
                }
            }
        """,
        after = """
            class Test {
                void test(Object[]... values) {
                    new Runnable() {
                        void inner(Object[]... values) {
                        }
                    };
                }
            }
        """,
        afterConditions = { cu ->
            val type = (cu.classes.first().body.statements.first() as J.MethodDeclaration).methodType!!

            assertThat(type.parameterNames)
                .`as`("Changing the method's parameters should have also updated its type's parameter names")
                .containsExactly("values")
            val param = type.parameterTypes[0]
            assertThat(param.asArray()!!.elemType)
                .`as`("Changing the method's parameters should have resulted in the first parameter's type being 'Object[]'")
                .matches { it.asArray()!!.elemType.asFullyQualified()?.fullyQualifiedName == "java.lang.Object" }
        }
    )

    @Test
    fun replaceAndInterpolateMethodParameters(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int n, #{}")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.simpleName == "test" && method.parameters.size == 1) {
                        return method.withTemplate(
                            t,
                            method.coordinates.replaceParameters(),
                            method.parameters[0]
                        )
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                void test(String s) {
                }
            }
        """,
        after = """
            class Test {
                void test(int n, String s) {
                }
            }
        """,
        afterConditions = { cu ->
            val type = (cu.classes.first().body.statements.first() as J.MethodDeclaration).methodType!!

            assertThat(type.parameterNames)
                .`as`("Changing the method's parameters should have also updated its type's parameter names")
                .containsExactly("n", "s")
            assertThat(type.parameterTypes[0])
                .`as`("Changing the method's parameters should have resulted in the first parameter's type being 'int'")
                .isEqualTo(JavaType.Primitive.Int)
            assertThat(type.parameterTypes[1])
                .`as`("Changing the method's parameters should have resulted in the second parameter's type being 'List<String>'")
                .matches { it.asFullyQualified()!!.fullyQualifiedName == "java.lang.String" }
        }
    )

    @Test
    fun replaceLambdaParameters(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int m, int n")
                    .build()

                override fun visitLambda(lambda: J.Lambda, p: ExecutionContext): J.Lambda =
                    if (lambda.parameters.parameters.size == 1) {
                        lambda.withTemplate(t, lambda.parameters.coordinates.replace())
                    } else {
                        super.visitLambda(lambda, p)
                    }
            }
        },
        before = """
            class Test {
                void test() {
                    Object o = () -> 1;
                }
            }
        """,
        after = """
            class Test {
                void test() {
                    Object o = (int m, int n) -> 1;
                }
            }
        """
    )

    @Test
    fun replaceSingleStatement(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder(
                    { cursor },
                    "if(n != 1) {\n" +
                            "  n++;\n" +
                            "}"
                )
                    .build()

                override fun visitAssert(_assert: J.Assert, p: ExecutionContext): J =
                    _assert.withTemplate(t, _assert.coordinates.replace())
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    if (n != 1) {
                        n++;
                    }
                }
            }
        """
    )

    @Suppress("UnusedAssignment")
    @Test
    fun replaceStatementInBlock(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "n = 2;\nn = 3;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    val statement = method.body!!.statements[1]
                    if (statement is J.Unary) {
                        return method.withTemplate(t, statement.coordinates.replace())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    n = 1;
                    n++;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    n = 1;
                    n = 2;
                    n = 3;
                }
            }
        """
    )

    @Test
    fun beforeStatementInBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "assert n == 0;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    val statement = method.body!!.statements[0]
                    if (statement is J.Assignment) {
                        return method.withTemplate(t, statement.coordinates.before())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    n = 1;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                    n = 1;
                }
            }
        """
    )

    @Test
    fun afterStatementInBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "n = 1;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    if (method.body!!.statements.size == 1) {
                        return method.withTemplate(t, method.body!!.statements[0].coordinates.after())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                    n = 1;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1093")
    @Test
    fun firstStatementInClassBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int m;")
                    .build()

                override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J {
                    if (classDecl.body.statements.size == 1) {
                        return classDecl.withTemplate(t, classDecl.body.coordinates.firstStatement())
                    }
                    return classDecl
                }
            }
        },
        before = """
            class Test {
                // comment
                int n;
            }
        """,
        after = """
            class Test {
                int m;
                // comment
                int n;
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1093")
    @Test
    fun firstStatementInMethodBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int m = 0;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    if (method.body!!.statements.size == 1) {
                        return method.withTemplate(t, method.body!!.coordinates.firstStatement())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    // comment
                    int n = 1;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    int m = 0;
                    // comment
                    int n = 1;
                }
            }
        """
    )

    @Test
    fun lastStatementInClassBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "int n;")
                    .build()

                override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J {
                    if (classDecl.body.statements.isEmpty()) {
                        return classDecl.withTemplate(t, classDecl.body.coordinates.lastStatement())
                    }
                    return classDecl
                }
            }
        },
        before = """
            class Test {
            }
        """,
        after = """
            class Test {
                int n;
            }
        """
    )

    @Test
    fun lastStatementInMethodBlock(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "n = 1;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    if (method.body!!.statements.size == 1) {
                        return method.withTemplate(t, method.body!!.coordinates.lastStatement())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                    n = 1;
                }
            }
        """
    )

    @Test
    fun replaceStatementRequiringNewImport(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "List<String> s = null;")
                    .imports("java.util.List")
                    .build()

                override fun visitAssert(_assert: J.Assert, p: ExecutionContext): J {
                    maybeAddImport("java.util.List")
                    return _assert.withTemplate(t, _assert.coordinates.replace())
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    assert n == 0;
                }
            }
        """,
        after = """
            import java.util.List;
            
            class Test {
                int n;
                void test() {
                    List<String> s = null;
                }
            }
        """
    )

    @Suppress("UnnecessaryBoxing")
    @Test
    fun replaceArguments(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "m, Integer.valueOf(n), \"foo\"")
                    .build()

                override fun visitMethodInvocation(
                    method: J.MethodInvocation,
                    p: ExecutionContext
                ): J.MethodInvocation {
                    if (method.arguments.size == 1) {
                        return method.withTemplate(t, method.coordinates.replaceArguments())
                    }
                    return method
                }
            }
        },
        before = """
            abstract class Test {
                abstract void test();
                abstract void test(int m, int n, String foo);
                void fred(int m, int n, String foo) {
                    test();
                }
            }
        """,
        after = """
            abstract class Test {
                abstract void test();
                abstract void test(int m, int n, String foo);
                void fred(int m, int n, String foo) {
                    test(m, Integer.valueOf(n), "foo");
                }
            }
        """,
        afterConditions = { cu ->
            val m = (cu.classes[0].body.statements[2] as J.MethodDeclaration).body!!.statements[0] as J.MethodInvocation
            val type = m.methodType!!
            assertThat(type.parameterTypes[0]).isEqualTo(JavaType.Primitive.Int)
            assertThat(type.parameterTypes[1]).isEqualTo(JavaType.Primitive.Int)
            assertThat(type.parameterTypes[2]).matches { it.asFullyQualified()!!.fullyQualifiedName.equals("java.lang.String") }
        }
    )

    @Test
    fun replaceClassAnnotation(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@Deprecated")
                    .build()

                override fun visitAnnotation(annotation: J.Annotation, p: ExecutionContext): J.Annotation {
                    if (annotation.simpleName == "SuppressWarnings") {
                        return annotation.withTemplate(t, annotation.coordinates.replace())
                    }
                    return super.visitAnnotation(annotation, p)
                }
            }
        },
        before = "@SuppressWarnings(\"ALL\") class Test {}",
        after = "@Deprecated class Test {}"
    )

    @Test
    fun replaceMethodAnnotations(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"other\")")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.leadingAnnotations.size == 0) {
                        return method.withTemplate(t, method.coordinates.replaceAnnotations())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                static final String WARNINGS = "ALL";
            
                public @SuppressWarnings(WARNINGS) Test() {
                }
            
                public void test1() {
                }
            
                public @SuppressWarnings(WARNINGS) void test2() {
                }
            }
        """,
        after = """
            class Test {
                static final String WARNINGS = "ALL";
            
                @SuppressWarnings("other")
                public Test() {
                }
            
                @SuppressWarnings("other")
                public void test1() {
                }
            
                @SuppressWarnings("other")
                public void test2() {
                }
            }
        """
    )

    @Test
    fun replaceClassAnnotations(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"other\")")
                    .build()

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext
                ): J.ClassDeclaration {
                    if (classDecl.leadingAnnotations.size == 0 && classDecl.simpleName != "Test") {
                        return classDecl.withTemplate(t, classDecl.coordinates.replaceAnnotations())
                    }
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            class Test {
                static final String WARNINGS = "ALL";
                
                class Inner1 {
                }
            }
        """,
        after = """
            class Test {
                static final String WARNINGS = "ALL";
            
                @SuppressWarnings("other")
                class Inner1 {
                }
            }
        """
    )

    @Test
    fun replaceVariableAnnotations(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"other\")")
                    .build()

                override fun visitVariableDeclarations(
                    multiVariable: J.VariableDeclarations,
                    p: ExecutionContext,
                ): J.VariableDeclarations {
                    if (multiVariable.leadingAnnotations.size == 0) {
                        return multiVariable.withTemplate(t, multiVariable.coordinates.replaceAnnotations())
                    }
                    return super.visitVariableDeclarations(multiVariable, p)
                }
            }
        },
        before = """
            class Test {
                void test() {
                    // the m
                    int m;
                    final @SuppressWarnings("ALL") int n;
                }
            }
        """,
        after = """
            class Test {
                void test() {
                    // the m
                    @SuppressWarnings("other")
                    int m;
                    @SuppressWarnings("other")
                    final int n;
                }
            }
        """
    )

    @Test
    fun addVariableAnnotationsToVariableAlreadyAnnotated(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@Deprecated")
                    .build()

                override fun visitVariableDeclarations(
                    multiVariable: J.VariableDeclarations,
                    p: ExecutionContext,
                ): J.VariableDeclarations {
                    if (multiVariable.leadingAnnotations.size == 1) {
                        return multiVariable.withTemplate(t, multiVariable.coordinates.addAnnotation(comparing { 0 }))
                    }
                    return super.visitVariableDeclarations(multiVariable, p)
                }
            }
        },
        before = """
            class Test {
                @SuppressWarnings("ALL") private final int m, a;
                void test() {
                    @SuppressWarnings("ALL") /* hello */
                    Boolean z;
                    // comment n
                    @SuppressWarnings("ALL")
                    int n;
                    @SuppressWarnings("ALL") final Boolean b;
                    @SuppressWarnings("ALL")
                    // comment x, y
                    private Boolean x, y;
                }
            }
        """,
        after = """
            class Test {
                @SuppressWarnings("ALL")
                @Deprecated
                private final int m, a;
                void test() {
                    @SuppressWarnings("ALL")
                    @Deprecated /* hello */
                    Boolean z;
                    // comment n
                    @SuppressWarnings("ALL")
                    @Deprecated
                    int n;
                    @SuppressWarnings("ALL")
                    @Deprecated
                    final Boolean b;
                    @SuppressWarnings("ALL")
                    @Deprecated
                    // comment x, y
                    private Boolean x, y;
                }
            }
        """
    )

    @Test
    fun addVariableAnnotationsToVariableNotAnnotated(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"ALL\")")
                    .build()

                override fun visitVariableDeclarations(
                    multiVariable: J.VariableDeclarations,
                    p: ExecutionContext,
                ): J.VariableDeclarations {
                    if (multiVariable.leadingAnnotations.size == 0) {
                        return multiVariable.withTemplate(
                            t,
                            multiVariable.coordinates.addAnnotation(comparing { it.simpleName })
                        )
                    }
                    return super.visitVariableDeclarations(multiVariable, p)
                }
            }
        },
        before = """
            class Test {
                void test() {
                    final int m;
                    int n;
                }
            }
        """,
        after = """
            class Test {
                void test() {
                    @SuppressWarnings("ALL")
                    final int m;
                    @SuppressWarnings("ALL")
                    int n;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1111")
    @Test
    fun addMethodAnnotations(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"other\")")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.leadingAnnotations.size == 0) {
                        return method.withTemplate(t, method.coordinates.addAnnotation(comparing { it.simpleName }))
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                public void test0() {
                }

                static final String WARNINGS = "ALL";

                void test1() {
                }
            }
        """,
        after = """
            class Test {
                @SuppressWarnings("other")
                public void test0() {
                }

                static final String WARNINGS = "ALL";

                @SuppressWarnings("other")
                void test1() {
                }
            }
        """
    )

    @Test
    fun addClassAnnotations(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "@SuppressWarnings(\"other\")")
                    .build()

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext
                ): J.ClassDeclaration {
                    if (classDecl.leadingAnnotations.size == 0 && classDecl.simpleName != "Test") {
                        return classDecl.withTemplate(
                            t,
                            classDecl.coordinates.addAnnotation(comparing { it.simpleName })
                        )
                    }
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            class Test {
                class Inner1 {
                }
            }
        """,
        after = """
            class Test {
                @SuppressWarnings("other")
                class Inner1 {
                }
            }
        """
    )

    @Test
    fun replaceClassImplements(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "Serializable, Closeable")
                    .imports("java.io.*")
                    .build()

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext
                ): J.ClassDeclaration {
                    if (classDecl.implements == null) {
                        maybeAddImport("java.io.Closeable")
                        maybeAddImport("java.io.Serializable")
                        return classDecl.withTemplate(t, classDecl.coordinates.replaceImplementsClause())
                    }
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            class Test {
            }
        """,
        after = """
            import java.io.Closeable;
            import java.io.Serializable;
            
            class Test implements Serializable, Closeable {
            }
        """
    )

    @Test
    fun replaceClassExtends(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "List<String>")
                    .imports("java.util.*")
                    .build()

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext
                ): J.ClassDeclaration {
                    if (classDecl.extends == null) {
                        maybeAddImport("java.util.List")
                        return classDecl.withTemplate(t, classDecl.coordinates.replaceExtendsClause())
                    }
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            class Test {
            }
        """,
        after = """
            import java.util.List;
            
            class Test extends List<String> {
            }
        """
    )

    @Suppress("RedundantThrows")
    @Test
    fun replaceThrows(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "Exception")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.throws == null) {
                        return method.withTemplate(t, method.coordinates.replaceThrows())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            class Test {
                void test() {}
            }
        """,
        after = """
            class Test {
                void test() throws Exception {}
            }
        """,
        afterConditions = { cu ->
            val testMethodDecl = cu.classes.first().body.statements.first() as J.MethodDeclaration
            assertThat(testMethodDecl.methodType!!.thrownExceptions.map { it.fullyQualifiedName })
                .containsExactly("java.lang.Exception")
        }
    )

    @Disabled
    @Test
    fun replaceMethodTypeParameters(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val typeParamsTemplate = JavaTemplate.builder({ cursor }, "T, U")
                    .build()

                val methodArgsTemplate = JavaTemplate.builder({ cursor }, "List<T> t, U u")
                    .imports("java.util.List")
                    .build()

                override fun visitMethodDeclaration(
                    method: J.MethodDeclaration,
                    p: ExecutionContext
                ): J.MethodDeclaration {
                    if (method.typeParameters == null) {
                        return method.withTemplate<J.MethodDeclaration>(
                            typeParamsTemplate,
                            method.coordinates.replaceTypeParameters()
                        )
                            .withTemplate(methodArgsTemplate, method.coordinates.replaceParameters())
                    }
                    return super.visitMethodDeclaration(method, p)
                }
            }
        },
        before = """
            import java.util.List;
            
            class Test {
            
                void test() {
                }
            }
        """,
        after = """
            import java.util.List;
            
            class Test {
            
                <T, U> void test(List<T> t, U u) {
                }
            }
        """,
        typeValidation = {
            identifiers = false
        },
        afterConditions = { cu ->
            val type = (cu.classes.first().body.statements.first() as J.MethodDeclaration).methodType!!
            assertThat(type).isNotNull
            val paramTypes = type.parameterTypes

            assertThat(paramTypes[0])
                .`as`("The method declaration's type's genericSignature first argument should have have type 'java.util.List'")
                .matches { tType ->
                    tType is JavaType.FullyQualified && tType.fullyQualifiedName == "java.util.List"
                }

            assertThat(paramTypes[1])
                .`as`("The method declaration's type's genericSignature second argument should have type 'U' with bound 'java.lang.Object'")
                .matches { uType ->
                    uType is JavaType.GenericTypeVariable &&
                            uType.name == "U" &&
                            uType.bounds.isEmpty()
                }
        }
    )

    @Test
    fun replaceClassTypeParameters(jp: JavaParser) = assertChanged(
        jp,
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "T, U")
                    .build()

                override fun visitClassDeclaration(
                    classDecl: J.ClassDeclaration,
                    p: ExecutionContext
                ): J.ClassDeclaration {
                    if (classDecl.typeParameters == null) {
                        return classDecl.withTemplate(t, classDecl.coordinates.replaceTypeParameters())
                    }
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            class Test {
            }
        """,
        after = """
            class Test<T, U> {
            }
        """
    )

    @Test
    fun replaceBody(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "n = 1;")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    val statement = method.body!!.statements[0]
                    if (statement is J.Unary) {
                        return method.withTemplate(t, method.coordinates.replaceBody())
                    }
                    return method
                }
            }
        },
        before = """
            class Test {
                int n;
                void test() {
                    n++;
                }
            }
        """,
        after = """
            class Test {
                int n;
                void test() {
                    n = 1;
                }
            }
        """
    )

    @Test
    fun replaceMissingBody(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val t = JavaTemplate.builder({ cursor }, "")
                    .build()

                override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J {
                    var m = method
                    if (!m.isAbstract) {
                        return m
                    }
                    m = m.withReturnTypeExpression(m.returnTypeExpression!!.withPrefix(Space.EMPTY))
                    m = m.withModifiers(emptyList())

                    m = m.withTemplate(t, m.coordinates.replaceBody())

                    return m
                }
            }
        },
        before = """
            abstract class Test {
                abstract void test();
            }
        """,
        after = """
            abstract class Test {
                void test(){
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1198")
    @Test
    @Suppress(
        "UnnecessaryBoxing",
        "UnnecessaryLocalVariable",
        "CachedNumberConstructorCall",
        "UnnecessaryTemporaryOnConversionToString",
        "ResultOfMethodCallIgnored"
    )
    fun replaceNamedVariableInitializerMethodInvocation(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val matcher = MethodMatcher("Integer valueOf(..)")
                val t = JavaTemplate.builder({ cursor }, "new Integer(#{any()})").build()
                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    if (matcher.matches(method)) {
                        return method.withTemplate(t, method.coordinates.replace(), method.arguments[0])
                    }
                    return super.visitMethodInvocation(method, p)
                }
            }
        },
        before = """
            import java.util.Arrays;
            import java.util.List;
            import java.util.function.Function;
            class Test {
                void t() {
                    List<String> nums = Arrays.asList("1", "2", "3");
                    nums.forEach(s -> Integer.valueOf(s));
                }
                void inLambda(int i) {
                    Function<String, Integer> toString = it -> {
                        try {
                            return Integer.valueOf(it);
                        }catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                        return 0;
                    };
                }
                String inClassDeclaration(int i) {
                    return new Object() {
                        void foo() {
                            Integer.valueOf(i);
                        }
                    }.toString();
                }
            }
        """,
        after = """
            import java.util.Arrays;
            import java.util.List;
            import java.util.function.Function;
            class Test {
                void t() {
                    List<String> nums = Arrays.asList("1", "2", "3");
                    nums.forEach(s -> new Integer(s));
                }
                void inLambda(int i) {
                    Function<String, Integer> toString = it -> {
                        try {
                            return new Integer(it);
                        }catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                        return 0;
                    };
                }
                String inClassDeclaration(int i) {
                    return new Object() {
                        void foo() {
                            new Integer(i);
                        }
                    }.toString();
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1198")
    @Test
    @Suppress(
        "UnnecessaryBoxing",
        "UnnecessaryLocalVariable",
        "CachedNumberConstructorCall",
        "UnnecessaryTemporaryOnConversionToString"
    )
    fun lambdaIsVariableInitializer(jp: JavaParser.Builder<*, *>) = assertChanged(
        jp.logCompilationWarningsAndErrors(true).build(),
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                val matcher = MethodMatcher("Integer valueOf(..)")
                val t = JavaTemplate.builder({ cursor }, "new Integer(#{any()})").build()
                override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                    if (matcher.matches(method)) {
                        return method.withTemplate(t, method.coordinates.replace(), method.arguments[0])
                    }
                    return super.visitMethodInvocation(method, p)
                }
            }
        },
        before = """
            import java.util.function.Function;
            class Test {
                Function<String, Integer> asInteger = it -> Integer.valueOf(it);
            }
        """,
        after = """
            import java.util.function.Function;
            class Test {
                Function<String, Integer> asInteger = it -> new Integer(it);
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1505")
    @Test
    fun methodDeclarationWithComment() = assertChanged(
        recipe = toRecipe {
            object : JavaVisitor<ExecutionContext>() {
                override fun visitClassDeclaration(classDeclaration: J.ClassDeclaration, p: ExecutionContext): J {
                    var cd = classDeclaration
                    if (cd.body.statements.isEmpty()) {
                        cd = cd.withBody(
                            cd.body.withTemplate(
                                JavaTemplate.builder(
                                    this::getCursor, """
                                /**
                                 * comment
                                 */
                                void foo() {
                                }
                            """
                                )
                                    .build(),
                                cd.body.coordinates.firstStatement()
                            )
                        )
                    }
                    return cd
                }
            }
        },
        before = """
            class A {
            
            }
        """,
        after = """
            class A {
                /**
                 * comment
                 */
                void foo() {
                }

            }
        """
    )

    @Suppress("UnusedAssignment")
    @Issue("https://github.com/openrewrite/rewrite/issues/1821")
    @Test
    fun assignmentNotPartOfVariableDeclaration() = assertChanged(
        recipe = toRecipe {
            object : JavaIsoVisitor<ExecutionContext>() {
                override fun visitAssignment(assignment: J.Assignment, p: ExecutionContext): J.Assignment {
                    var a = assignment

                    if(a.assignment is J.MethodInvocation) {
                        val mi = a.assignment as J.MethodInvocation
                        a = a.withAssignment(mi.withTemplate(
                            JavaTemplate.builder(this::getCursor, "1")
                                .build(),
                            mi.coordinates.replace()
                        ))
                    }
                    return a
                }
            }
        },
        before = """
            class A {
                void foo() {
                    int i;
                    i = Integer.valueOf(1);
                }
            }
        """,
        after = """
            class A {
                void foo() {
                    int i;
                    i = 1;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/2090")
    @Test
    fun assignmentWithinIfPredicate() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaIsoVisitor<ExecutionContext>() {
                    override fun visitAssignment(assignment: J.Assignment, p: ExecutionContext): J.Assignment {
                        if((assignment.assignment is J.Literal) && "1" == (assignment.assignment as J.Literal).valueSource) {
                            return assignment.withTemplate(
                                JavaTemplate.builder(this::getCursor, "value = 0").build(),
                                assignment.coordinates.replace()
                            )
                        }
                        return assignment
                    }
                }
            })
        },
        java("""
            class A {
                void foo() {
                    double value = 0;
                    if ((value = 1) == 0) {}
                }
            }
        """,
        """
            class A {
                void foo() {
                    double value = 0;
                    if ((value = 0) == 0) {}
                }
            }
        """)
    )

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/66")
    @Test
    fun lambdaIsNewClass() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaIsoVisitor<ExecutionContext>() {
                    override fun visitAssignment(assignment: J.Assignment, p: ExecutionContext): J.Assignment {
                        var a = assignment
                        if(a.assignment is J.MethodInvocation) {
                            val mi = a.assignment as J.MethodInvocation
                            a = a.withAssignment(mi.withTemplate(
                                JavaTemplate.builder(this::getCursor, "1").build(), mi.coordinates.replace()
                            ))
                        }
                        return a
                    }
                }
            })
        },
        java(
            """
            class T {
                public T (int a, Runnable r, String s) { }
                static void method() {
                    new T(1, () -> {
                        int i;
                        i = Integer.valueOf(1);
                    }, "hello" );
                }
            }
            """,
            """
            class T {
                public T (int a, Runnable r, String s) { }
                static void method() {
                    new T(1, () -> {
                        int i;
                        i = 1;
                    }, "hello" );
                }
            }
            """
        )
    )

    @Suppress("RedundantOperationOnEmptyContainer", "RedundantOperationOnEmptyContainer")
    @Test
    fun replaceForEachControlVariable() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaIsoVisitor<ExecutionContext>() {
                    override fun visitVariableDeclarations(
                        multiVariable: J.VariableDeclarations,
                        p: ExecutionContext
                    ): J.VariableDeclarations {
                        var mv = super.visitVariableDeclarations(multiVariable, p)
                        if (mv.variables[0].initializer == null && TypeUtils.isOfType(mv.typeExpression!!.type, JavaType.Primitive.String)) {
                            mv = multiVariable.withTemplate(
                                JavaTemplate.builder(this::getCursor, "Object #{}").build(),
                                multiVariable.coordinates.replace(),
                                multiVariable.variables[0].simpleName
                            )
                        }
                        return mv
                    }
                }
            })
        },
        java(
            """
            import java.util.ArrayList;
            class T {
                void m() {
                    for (String s : new ArrayList<String>()) {}
                }
            }
            """,
            """
            import java.util.ArrayList;
            class T {
                void m() {
                    for (Object s : new ArrayList<String>()) {}
                }
            }
            """
        )
    )

    @Suppress("StatementWithEmptyBody", "RedundantOperationOnEmptyContainer")
    @Test
    fun replaceForEachControlIterator() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaVisitor<ExecutionContext>() {
                    override fun visitNewClass(newClass: J.NewClass, p: ExecutionContext): J {
                        var nc = super.visitNewClass(newClass, p)
                        if (TypeUtils.isOfClassType(newClass.type, "java.util.ArrayList")) {
                            nc = nc.withTemplate(JavaTemplate.builder(this::getCursor,"Collections.emptyList()")
                                .imports("java.util.Collections").build(),
                                newClass.coordinates.replace())
                        }
                        return nc
                    }
                }
            })
        },
        java(
            """
            import java.util.ArrayList;
            import java.util.Collections;
            class T {
                void m() {
                    for (String s : new ArrayList<String>()) {}
                }
            }
            """,
            """
            import java.util.ArrayList;
            import java.util.Collections;
            class T {
                void m() {
                    for (String s : Collections.emptyList()) {}
                }
            }
            """
        )
    )

    @Suppress("StringOperationCanBeSimplified")
    @Issue("https://github.com/openrewrite/rewrite/issues/2185")
    @Test
    fun chainedMethodInvocationsAsNewClassArgument() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaVisitor<ExecutionContext>() {
                    private var TO_STRING = MethodMatcher("java.lang.String toString()")
                    private val t = JavaTemplate.builder({ cursor }, "#{any(java.lang.String)}").build()

                    override fun visitMethodInvocation(method: J.MethodInvocation, ctx: ExecutionContext): J {
                        val mi = super.visitMethodInvocation(method, ctx) as J
                        if (mi is J.MethodInvocation && TO_STRING.matches(mi)) {
                            return mi.withTemplate(t, mi.coordinates.replace(), mi.select)
                        }
                        return mi
                    }
                }
            })
        },
        java(
            """
            import java.util.ArrayList;
            import java.util.Collections;
            public class T {
                void m(String arg) {
                    U u = new U(arg.toString().toCharArray());
                }
                class U {
                    U(char[] chars){}
                }
            }
            """,
            """
            import java.util.ArrayList;
            import java.util.Collections;
            public class T {
                void m(String arg) {
                    U u = new U(arg.toCharArray());
                }
                class U {
                    U(char[] chars){}
                }
            }
            """
        )
    )

    @Test
    fun chainedMethodInvocationsAsNewClassArgument2() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaVisitor<ExecutionContext>() {
                    private var TO_STRING = MethodMatcher("java.lang.String toString()")
                    private val t = JavaTemplate.builder({ cursor }, "#{any(java.lang.String)}").build()

                    override fun visitMethodInvocation(method: J.MethodInvocation, ctx: ExecutionContext): J {
                        val mi = super.visitMethodInvocation(method, ctx) as J
                        if (mi is J.MethodInvocation && TO_STRING.matches(mi)) {
                            return mi.withTemplate(t, mi.coordinates.replace(), mi.select)
                        }
                        return mi
                    }
                }
            })
        },
        java(
            """
            class T {
                void m(String jsonPayload) {
                    HttpEntity entity = new HttpEntity(jsonPayload.toString(), 0);
                }
                class HttpEntity {
                    HttpEntity(String s, int i){}
                }
            }
            """,
            """
            class T {
                void m(String jsonPayload) {
                    HttpEntity entity = new HttpEntity(jsonPayload, 0);
                }
                class HttpEntity {
                    HttpEntity(String s, int i){}
                }
            }
            """
        )
    )

    @Suppress("LoopConditionNotUpdatedInsideLoop")
    @Test
    fun templatingWhileLoopCondition() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaVisitor<ExecutionContext>() {
                    override fun visitBinary(binary: J.Binary, p: ExecutionContext): J {
                        if (binary.left is J.MethodInvocation) {
                            val mi = binary.left as J.MethodInvocation
                            return binary.withTemplate(
                                JavaTemplate.builder(this::getCursor, "!#{any(java.util.List)}.isEmpty()")
                                    .build(), mi.coordinates.replace(), mi.select
                            )
                        } else if (binary.left is J.Unary) {
                            return binary.left
                        }
                        return binary
                    }
                }
            })
            spec.expectedCyclesThatMakeChanges(2)
        },
        java("""
            import java.util.List;
            class T {
                void m(List<?> l) {
                    while (l.size() != 0) {}
                }
            }
        """,
        """
            import java.util.List;
            class T {
                void m(List<?> l) {
                    while (!l.isEmpty()) {}
                }
            }
        """)
    )

    @Suppress("BigDecimalLegacyMethod")
    @Test
    fun javaTemplateControlsSemiColons() = rewriteRun(
        { spec ->
            spec.recipe(toRecipe {
                object : JavaVisitor<ExecutionContext>() {
                    var BIG_DECIMAL_SET_SCALE = MethodMatcher("java.math.BigDecimal setScale(int, int)")
                    var twoArgScale = JavaTemplate.builder({ cursor }, "#{any(int)}, #{}")
                        .imports("java.math.RoundingMode").build()

                    override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J {
                        var mi = super.visitMethodInvocation(method, p) as J.MethodInvocation
                        if (BIG_DECIMAL_SET_SCALE.matches(mi)) {
                            mi = mi.withTemplate(
                                twoArgScale, mi.getCoordinates().replaceArguments(),
                                mi.getArguments().get(0), "RoundingMode.HALF_UP"
                            )
                        }
                        return mi
                    }
                }
            })
        },
        java("""
            import java.math.BigDecimal;
            import java.math.RoundingMode;
            
            class A {
                void m() {
                    StringBuilder sb = new StringBuilder();
                    sb.append((new BigDecimal(0).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue())).append("|");
                }
            }
        """,
            """
            import java.math.BigDecimal;
            import java.math.RoundingMode;
            
            class A {
                void m() {
                    StringBuilder sb = new StringBuilder();
                    sb.append((new BigDecimal(0).setScale(1, RoundingMode.HALF_UP).doubleValue())).append("|");
                }
            }
        """)
    )
}
