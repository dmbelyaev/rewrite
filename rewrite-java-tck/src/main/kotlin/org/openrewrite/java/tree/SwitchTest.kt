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
package org.openrewrite.java.tree

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.JavaTreeTest.NestingLevel.Block

interface SwitchTest : JavaTreeTest {

    @Test
    fun singleCase(jp: JavaParser) = assertParsePrintAndProcess(
        jp, Block, """
            int n;
            switch(n) {
               case 0: break;
            }
        """
    )

    @Test
    fun default(jp: JavaParser) = assertParsePrintAndProcess(
        jp, Block, """
            int n;
            switch(n) {
                default: System.out.println("default!");
            }
        """
    )

    @Test
    fun noCases(jp: JavaParser) = assertParsePrintAndProcess(
        jp, Block, """
            int n;
            switch(n) {}
        """
    )

    @Test
    fun multipleCases(jp: JavaParser) = assertParsePrintAndProcess(
        jp, Block, """
            int n;
            switch(n) {
                case 0: {
                   break;
                }
                case 1: {
                   break;
                }
            }
        """
    )
}
