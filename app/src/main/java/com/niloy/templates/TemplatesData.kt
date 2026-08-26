package com.niloy.templates

import com.niloy.data.entity.SnippetEntity

data class CTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val initialCode: String
)

object TemplatesData {

    val ALL_TEMPLATES = listOf(
        CTemplate(
            id = "hello_world",
            title = "Hello World",
            description = "Standard C starter program with formatted output",
            category = "Basics",
            initialCode = """#include <stdio.h>

int main(void) {
    printf("Hello, World!\n");
    return 0;
}
"""
        ),
        CTemplate(
            id = "basic_input",
            title = "Interactive Input (scanf)",
            description = "Reads strings and numbers from standard input",
            category = "Basics",
            initialCode = """#include <stdio.h>

int main(void) {
    char name[50];
    int age;

    printf("Enter your name: ");
    scanf("%s", name);

    printf("Enter your age: ");
    scanf("%d", &age);

    printf("\nWelcome, %s! Next year you will be %d years old.\n", name, age + 1);
    return 0;
}
"""
        ),
        CTemplate(
            id = "calculator",
            title = "Arithmetic Calculator",
            description = "Performs addition, subtraction, multiplication, and division with switch",
            category = "Control Flow",
            initialCode = """#include <stdio.h>

int main(void) {
    double num1 = 24.5;
    double num2 = 3.5;
    char op = '*';

    printf("Calculating: %.2f %c %.2f\n", num1, op, num2);

    switch (op) {
        case '+':
            printf("Result: %.2f\n", num1 + num2);
            break;
        case '-':
            printf("Result: %.2f\n", num1 - num2);
            break;
        case '*':
            printf("Result: %.2f\n", num1 * num2);
            break;
        case '/':
            if (num2 != 0) {
                printf("Result: %.2f\n", num1 / num2);
            } else {
                printf("Error: Division by zero!\n");
            }
            break;
        default:
            printf("Error: Unsupported operator.\n");
    }

    return 0;
}
"""
        ),
        CTemplate(
            id = "bubble_sort",
            title = "Bubble Sort & Search",
            description = "Sorts an array of integers and displays comparisons",
            category = "Algorithms",
            initialCode = """#include <stdio.h>

void bubbleSort(int arr[], int n) {
    int i, j, temp;
    for (i = 0; i < n - 1; i++) {
        for (j = 0; j < n - i - 1; j++) {
            if (arr[j] > arr[j + 1]) {
                temp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = temp;
            }
        }
    }
}

void printArray(int arr[], int n) {
    for (int i = 0; i < n; i++) {
        printf("%d ", arr[i]);
    }
    printf("\n");
}

int main(void) {
    int data[] = {64, 34, 25, 12, 22, 11, 90};
    int n = 7;

    printf("Unsorted array:\n");
    printArray(data, n);

    bubbleSort(data, n);

    printf("Sorted array:\n");
    printArray(data, n);

    return 0;
}
"""
        ),
        CTemplate(
            id = "pointers_memory",
            title = "Pointers & Memory Swap",
            description = "Demonstrates pointer dereferencing and pass-by-reference swap",
            category = "Pointers",
            initialCode = """#include <stdio.h>

void swap(int *a, int *b) {
    int temp = *a;
    *a = *b;
    *b = temp;
}

int main(void) {
    int x = 42;
    int y = 99;

    printf("Before swap: x = %d, y = %d\n", x, y);
    swap(&x, &y);
    printf("After swap:  x = %d, y = %d\n", x, y);

    int *ptr = &x;
    printf("Pointer address: %p, dereferenced value: %d\n", ptr, *ptr);

    return 0;
}
"""
        ),
        CTemplate(
            id = "dynamic_memory",
            title = "Dynamic Memory (malloc/free)",
            description = "Allocates heap memory dynamically and frees it safely",
            category = "Pointers",
            initialCode = """#include <stdio.h>
#include <stdlib.h>

int main(void) {
    int n = 5;
    int *arr = (int *)malloc(n * sizeof(int));

    if (arr == NULL) {
        printf("Memory allocation failed!\n");
        return 1;
    }

    printf("Allocated %d integers on the heap.\n", n);
    for (int i = 0; i < n; i++) {
        arr[i] = (i + 1) * 10;
        printf("arr[%d] = %d\n", i, arr[i]);
    }

    free(arr);
    printf("Memory successfully deallocated.\n");

    return 0;
}
"""
        ),
        CTemplate(
            id = "structures",
            title = "Structures & Records",
            description = "Defines and manages structured student records",
            category = "Data Structures",
            initialCode = """#include <stdio.h>
#include <string.h>

struct Student {
    char name[30];
    int rollNumber;
    float gpa;
};

void displayStudent(struct Student s) {
    printf("Student: %s | Roll: %d | GPA: %.2f\n", s.name, s.rollNumber, s.gpa);
}

int main(void) {
    struct Student s1;
    strcpy(s1.name, "Niloy Mitra");
    s1.rollNumber = 101;
    s1.gpa = 3.95;

    struct Student s2;
    strcpy(s2.name, "Alex Chen");
    s2.rollNumber = 102;
    s2.gpa = 3.88;

    printf("=== Student Records ===\n");
    displayStudent(s1);
    displayStudent(s2);

    return 0;
}
"""
        ),
        CTemplate(
            id = "recursion",
            title = "Recursion & Fibonacci",
            description = "Calculates factorials and Fibonacci sequences recursively",
            category = "Algorithms",
            initialCode = """#include <stdio.h>

long factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

int fibonacci(int n) {
    if (n <= 0) return 0;
    if (n == 1) return 1;
    return fibonacci(n - 1) + fibonacci(n - 2);
}

int main(void) {
    printf("Factorials:\n");
    for (int i = 1; i <= 6; i++) {
        printf("%d! = %ld\n", i, factorial(i));
    }

    printf("\nFibonacci sequence (first 8 terms):\n");
    for (int i = 0; i < 8; i++) {
        printf("%d ", fibonacci(i));
    }
    printf("\n");

    return 0;
}
"""
        ),
        CTemplate(
            id = "string_manipulation",
            title = "String Manipulation",
            description = "Uses strlen, strcpy, strcmp, strcat from string.h",
            category = "Standard Library",
            initialCode = """#include <stdio.h>
#include <string.h>

int main(void) {
    char str1[50] = "C Code";
    char str2[] = " Engine";
    char combined[100];

    printf("Length of '%s': %lu\n", str1, strlen(str1));

    strcpy(combined, str1);
    strcat(combined, str2);
    printf("Concatenated string: %s\n", combined);

    int cmp = strcmp(str1, "C Code");
    printf("Comparison result: %d (0 means identical)\n", cmp);

    return 0;
}
"""
        ),
        CTemplate(
            id = "math_functions",
            title = "Math & Trigonometry",
            description = "Demonstrates sqrt, pow, sin, cos, and hypot from math.h",
            category = "Standard Library",
            initialCode = """#include <stdio.h>
#include <math.h>

int main(void) {
    double x = 16.0;
    double angle = 0.523599; // 30 degrees in radians

    printf("sqrt(%.1f) = %.2f\n", x, sqrt(x));
    printf("pow(2.0, 8.0) = %.1f\n", pow(2.0, 8.0));
    printf("sin(30 deg) = %.4f\n", sin(angle));
    printf("cos(30 deg) = %.4f\n", cos(angle));
    printf("hypot(3.0, 4.0) = %.2f\n", hypot(3.0, 4.0));

    return 0;
}
"""
        ),
        CTemplate(
            id = "matrix_mult",
            title = "Matrix Multiplication (2D Arrays)",
            description = "Multiplies two 2x2 integer matrices",
            category = "Algorithms",
            initialCode = """#include <stdio.h>

int main(void) {
    int a[2][2] = {{1, 2}, {3, 4}};
    int b[2][2] = {{5, 6}, {7, 8}};
    int c[2][2] = {{0, 0}, {0, 0}};

    for (int i = 0; i < 2; i++) {
        for (int j = 0; j < 2; j++) {
            for (int k = 0; k < 2; k++) {
                c[i][j] += a[i][k] * b[k][j];
            }
        }
    }

    printf("Result Matrix C (2x2):\n");
    for (int i = 0; i < 2; i++) {
        for (int j = 0; j < 2; j++) {
            printf("%4d", c[i][j]);
        }
        printf("\n");
    }

    return 0;
}
"""
        ),
        CTemplate(
            id = "character_classifier",
            title = "Character Classifier (ctype.h)",
            description = "Inspects character properties and transformations",
            category = "Standard Library",
            initialCode = """#include <stdio.h>
#include <ctype.h>

void inspectChar(char c) {
    printf("'%c' -> ", c);
    if (isalpha(c)) printf("[Letter] ");
    if (isdigit(c)) printf("[Digit] ");
    if (isspace(c)) printf("[Whitespace] ");
    if (isupper(c)) printf("[Uppercase -> %c] ", tolower(c));
    if (islower(c)) printf("[Lowercase -> %c] ", toupper(c));
    printf("\n");
}

int main(void) {
    inspectChar('N');
    inspectChar('7');
    inspectChar(' ');
    inspectChar('z');

    return 0;
}
"""
        )
    )

    val DEFAULT_SNIPPETS = listOf(
        SnippetEntity(
            title = "For Loop",
            prefix = "for",
            description = "Standard counting loop",
            code = "for (int i = 0; i < count; i++) {\n    // code\n}"
        ),
        SnippetEntity(
            title = "While Loop",
            prefix = "while",
            description = "Condition controlled loop",
            code = "while (condition) {\n    // code\n}"
        ),
        SnippetEntity(
            title = "If-Else Branch",
            prefix = "ifelse",
            description = "Conditional statement with else block",
            code = "if (condition) {\n    // then\n} else {\n    // else\n}"
        ),
        SnippetEntity(
            title = "Switch Statement",
            prefix = "switch",
            description = "Multi-way branch switch",
            code = "switch (value) {\n    case 1:\n        // code\n        break;\n    default:\n        break;\n}"
        ),
        SnippetEntity(
            title = "printf formatted",
            prefix = "printf",
            description = "Print formatted string to stdout",
            code = "printf(\"Value: %d\\n\", value);"
        ),
        SnippetEntity(
            title = "scanf input",
            prefix = "scanf",
            description = "Read formatted input into variable",
            code = "scanf(\"%d\", &variable);"
        ),
        SnippetEntity(
            title = "Struct Declaration",
            prefix = "struct",
            description = "Define a new struct type",
            code = "struct TypeName {\n    int id;\n    char name[50];\n};"
        ),
        SnippetEntity(
            title = "Function Definition",
            prefix = "func",
            description = "Standard C function block",
            code = "int functionName(int param1, int param2) {\n    return param1 + param2;\n}"
        ),
        SnippetEntity(
            title = "Dynamic Allocation Check",
            prefix = "malloc",
            description = "malloc with NULL safety check",
            code = "int *ptr = (int *)malloc(size * sizeof(int));\nif (ptr == NULL) {\n    printf(\"Memory allocation failed!\\n\");\n    return 1;\n}"
        ),
        SnippetEntity(
            title = "Main Function",
            prefix = "main",
            description = "Standard C main entry point",
            code = "int main(void) {\n    // Your code here\n    return 0;\n}"
        )
    )
}
