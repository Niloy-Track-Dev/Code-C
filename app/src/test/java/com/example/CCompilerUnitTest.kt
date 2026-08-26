package com.example

import com.example.compiler.CCompilerService
import com.example.compiler.lexer.CLexer
import com.example.compiler.lexer.TokenType
import com.example.compiler.model.CompilerConfig
import com.example.compiler.model.DiagnosticSeverity
import com.example.editor.CCodeFormatter
import com.example.editor.CodeStatistics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CCompilerUnitTest {

    private val compilerService = CCompilerService()

    @Test
    fun testLexerTokenization() {
        val code = """
            #include <stdio.h>
            int main() {
                int a = 42;
                float b = 3.14;
                char* s = "Hello World";
                return 0;
            }
        """.trimIndent()

        val lexer = CLexer(code)
        val tokens = lexer.tokenize()

        assertTrue(tokens.any { it.type == TokenType.KEYWORD_INT })
        assertTrue(tokens.any { it.type == TokenType.IDENTIFIER && it.text == "main" })
        assertTrue(tokens.any { it.type == TokenType.LITERAL_INT && it.text == "42" })
        assertTrue(tokens.any { it.type == TokenType.LITERAL_FLOAT && it.text == "3.14" })
        assertTrue(tokens.any { it.type == TokenType.LITERAL_STRING && it.stringValue == "Hello World" })
        assertTrue(tokens.any { it.type == TokenType.KEYWORD_RETURN })
    }

    @Test
    fun testHelloWorldExecution() = runBlocking {
        val code = """
            #include <stdio.h>
            int main() {
                printf("Hello, 100%% Offline C!\n");
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("Hello, 100% Offline C!"))
    }

    @Test
    fun testArithmeticAndLoops() = runBlocking {
        val code = """
            #include <stdio.h>
            int main() {
                int sum = 0;
                for (int i = 1; i <= 10; i++) {
                    sum += i;
                }
                printf("Sum = %d\n", sum);
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.isSuccess)
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("Sum = 55"))
    }

    @Test
    fun testPointerOperations() = runBlocking {
        val code = """
            #include <stdio.h>
            void swap(int *a, int *b) {
                int temp = *a;
                *a = *b;
                *b = temp;
            }
            int main() {
                int x = 10, y = 20;
                swap(&x, &y);
                printf("x=%d, y=%d\n", x, y);
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.isSuccess)
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("x=20, y=10"))
    }

    @Test
    fun testDynamicMemoryMallocFree() = runBlocking {
        val code = """
            #include <stdio.h>
            #include <stdlib.h>
            int main() {
                int *arr = (int*)malloc(5 * sizeof(int));
                if (arr == NULL) return 1;
                for (int i = 0; i < 5; i++) {
                    arr[i] = (i + 1) * 10;
                }
                printf("arr[2]=%d\n", arr[2]);
                free(arr);
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.isSuccess)
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("arr[2]=30"))
    }

    @Test
    fun testStructMemberAccess() = runBlocking {
        val code = """
            #include <stdio.h>
            struct Point {
                int x;
                int y;
            };
            int main() {
                struct Point p;
                p.x = 42;
                p.y = 99;
                printf("Point: (%d, %d)\n", p.x, p.y);
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.isSuccess)
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("Point: (42, 99)"))
    }

    @Test
    fun testStandardLibraryMathAndString() = runBlocking {
        val code = """
            #include <stdio.h>
            #include <string.h>
            #include <math.h>
            int main() {
                char s[] = "offline";
                printf("len=%d\n", (int)strlen(s));
                double root = sqrt(49.0);
                printf("sqrt=%.1f\n", root);
                return 0;
            }
        """.trimIndent()

        val compRes = compilerService.compile(code)
        assertTrue(compRes.isSuccess)
        assertNotNull(compRes.executable)

        val execRes = compilerService.execute(compRes.executable!!)
        assertEquals(0, execRes.exitCode)
        assertTrue(execRes.stdout.contains("len=7"))
        assertTrue(execRes.stdout.contains("sqrt=7.0"))
    }

    @Test
    fun testCodeFormatterAndStatistics() {
        val rawCode = """
int main() {
int a = 5;
if (a > 0) {
printf("positive");
}
return 0;
}
        """.trimIndent()

        val formatted = CCodeFormatter.format(rawCode, tabSize = 4)
        assertTrue(formatted.contains("    int a = 5;"))
        assertTrue(formatted.contains("    if (a > 0) {"))
        assertTrue(formatted.contains("        printf(\"positive\");"))

        val stats = CodeStatistics.calculate(formatted)
        assertEquals(7, stats.totalLines)
        assertTrue(stats.codeLines >= 6)
        assertEquals(1, stats.estimatedFunctions)
    }
}
