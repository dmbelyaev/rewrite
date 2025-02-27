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
package org.openrewrite.java.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

@Suppress("FunctionName")
interface RemoveUnneededAssertionTest: RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(RemoveUnneededAssertion())
    }

    @Test
    fun `assert true`() = rewriteRun(
        java(
            """
                public class A {
                    public void m() {
                        System.out.println("Hello");
                        assert true;
                        System.out.println("World");
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                        System.out.println("Hello");
                        System.out.println("World");
                    }
                }
            """
        )
    )

    @Test
    fun `assert false`() = rewriteRun(
        java(
            """
                public class A {
                    public void m() {
                        System.out.println("Hello");
                        assert false;
                        System.out.println("World");
                    }
                }
            """
        )
    )

    @Test
    fun `junit jupiter assertTrue(true)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit-jupiter-api") )},
        java(
            """
                import static org.junit.jupiter.api.Assertions.assertTrue;
                public class A {
                    public void m() {
                        assertTrue(true);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit jupiter assertFalse(false)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit-jupiter-api") )},
        java(
            """
                import static org.junit.jupiter.api.Assertions.assertFalse;
                public class A {
                    public void m() {
                        assertFalse(false);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit jupiter assertTrue(true, message)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit-jupiter-api") )},
        java(
            """
                import static org.junit.jupiter.api.Assertions.assertTrue;
                public class A {
                    public void m() {
                        assertTrue(true, "message");
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit jupiter assertFalse(false, message)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit-jupiter-api") )},
        java(
            """
                import static org.junit.jupiter.api.Assertions.assertFalse;
                public class A {
                    public void m() {
                        assertFalse(false, "message");
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit4 assertTrue(true)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit") )},
        java(
            """
                import static org.junit.Assert.assertTrue;
                public class A {
                    public void m() {
                        assertTrue(true);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit4 assertFalse(false)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit") )},
        java(
            """
                import static org.junit.Assert.assertFalse;
                public class A {
                    public void m() {
                        assertFalse(false);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit4 assertTrue(message, true)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit") )},
        java(
            """
                import static org.junit.Assert.assertTrue;
                public class A {
                    public void m() {
                        assertTrue("message", true);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )

    @Test
    fun `junit4 assertFalse(message, false)`() = rewriteRun(
        { spec -> spec.parser( JavaParser.fromJavaVersion().classpath("junit") )},
        java(
            """
                import static org.junit.Assert.assertFalse;
                public class A {
                    public void m() {
                        assertFalse("message", false);
                    }
                }
            """,
            """
                public class A {
                    public void m() {
                    }
                }
            """
        )
    )
}
