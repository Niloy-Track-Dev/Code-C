package com.example.compiler.parser

import com.example.compiler.lexer.TokenType

sealed class CType {
    object IntType : CType() { override fun toString() = "int" }
    object CharType : CType() { override fun toString() = "char" }
    object FloatType : CType() { override fun toString() = "float" }
    object DoubleType : CType() { override fun toString() = "double" }
    object VoidType : CType() { override fun toString() = "void" }
    object BoolType : CType() { override fun toString() = "bool" }
    data class PointerType(val baseType: CType) : CType() { override fun toString() = "$baseType*" }
    data class ArrayType(val baseType: CType, val size: Int) : CType() { override fun toString() = "$baseType[$size]" }
    data class StructType(val name: String, val fields: Map<String, CType> = emptyMap()) : CType() { override fun toString() = "struct $name" }
    data class CustomType(val name: String) : CType() { override fun toString() = name }
}

sealed interface CASTNode {
    val line: Int
    val column: Int
}

// Program Unit
data class TranslationUnit(
    val declarations: List<TopLevelDeclaration>,
    val file: String = "main.c",
    override val line: Int = 1,
    override val column: Int = 1
) : CASTNode

sealed interface TopLevelDeclaration : CASTNode

data class FunctionDefinition(
    val returnType: CType,
    val name: String,
    val params: List<ParamDeclaration>,
    val body: CompoundStatement,
    val isStatic: Boolean = false,
    val isInline: Boolean = false,
    override val line: Int,
    override val column: Int
) : TopLevelDeclaration

data class GlobalVarDeclaration(
    val type: CType,
    val name: String,
    val arraySize: Int? = null,
    val initializer: Expression? = null,
    val isConst: Boolean = false,
    override val line: Int,
    override val column: Int
) : TopLevelDeclaration

data class StructDeclaration(
    val name: String,
    val fields: List<ParamDeclaration>,
    override val line: Int,
    override val column: Int
) : TopLevelDeclaration

data class TypedefDeclaration(
    val originalType: CType,
    val aliasName: String,
    override val line: Int,
    override val column: Int
) : TopLevelDeclaration

data class ParamDeclaration(
    val type: CType,
    val name: String,
    override val line: Int,
    override val column: Int
) : CASTNode

// Statements
sealed interface Statement : CASTNode

data class CompoundStatement(
    val statements: List<Statement>,
    override val line: Int,
    override val column: Int
) : Statement

data class LocalVarDeclaration(
    val type: CType,
    val name: String,
    val arraySize: Int? = null,
    val initializer: Expression? = null,
    val isConst: Boolean = false,
    override val line: Int,
    override val column: Int
) : Statement

data class MultiVarDeclaration(
    val declarations: List<LocalVarDeclaration>,
    override val line: Int,
    override val column: Int
) : Statement

data class IfStatement(
    val condition: Expression,
    val thenBranch: Statement,
    val elseBranch: Statement? = null,
    override val line: Int,
    override val column: Int
) : Statement

data class WhileStatement(
    val condition: Expression,
    val body: Statement,
    override val line: Int,
    override val column: Int
) : Statement

data class DoWhileStatement(
    val body: Statement,
    val condition: Expression,
    override val line: Int,
    override val column: Int
) : Statement

data class ForStatement(
    val init: Statement?,
    val condition: Expression?,
    val update: Expression?,
    val body: Statement,
    override val line: Int,
    override val column: Int
) : Statement

data class SwitchStatement(
    val condition: Expression,
    val body: Statement,
    override val line: Int,
    override val column: Int
) : Statement

data class CaseStatement(
    val value: Expression,
    val statement: Statement?,
    override val line: Int,
    override val column: Int
) : Statement

data class DefaultStatement(
    val statement: Statement?,
    override val line: Int,
    override val column: Int
) : Statement

data class ReturnStatement(
    val value: Expression? = null,
    override val line: Int,
    override val column: Int
) : Statement

data class BreakStatement(override val line: Int, override val column: Int) : Statement
data class ContinueStatement(override val line: Int, override val column: Int) : Statement
data class ExpressionStatement(val expression: Expression, override val line: Int, override val column: Int) : Statement
data class EmptyStatement(override val line: Int, override val column: Int) : Statement

// Expressions
sealed interface Expression : CASTNode

data class IntLiteral(val value: Long, override val line: Int, override val column: Int) : Expression
data class FloatLiteral(val value: Double, override val line: Int, override val column: Int) : Expression
data class StringLiteral(val value: String, override val line: Int, override val column: Int) : Expression
data class CharLiteral(val value: Char, override val line: Int, override val column: Int) : Expression
data class IdentifierExpr(val name: String, override val line: Int, override val column: Int) : Expression

data class BinaryOpExpr(
    val left: Expression,
    val op: TokenType,
    val right: Expression,
    override val line: Int,
    override val column: Int
) : Expression

data class UnaryOpExpr(
    val op: TokenType,
    val expr: Expression,
    val isPrefix: Boolean = true,
    override val line: Int,
    override val column: Int
) : Expression

data class AssignmentExpr(
    val target: Expression,
    val op: TokenType,
    val value: Expression,
    override val line: Int,
    override val column: Int
) : Expression

data class FunctionCallExpr(
    val name: String,
    val args: List<Expression>,
    override val line: Int,
    override val column: Int
) : Expression

data class ArrayAccessExpr(
    val array: Expression,
    val index: Expression,
    override val line: Int,
    override val column: Int
) : Expression

data class MemberAccessExpr(
    val obj: Expression,
    val member: String,
    val isArrow: Boolean,
    override val line: Int,
    override val column: Int
) : Expression

data class TernaryExpr(
    val condition: Expression,
    val trueExpr: Expression,
    val falseExpr: Expression,
    override val line: Int,
    override val column: Int
) : Expression

data class CastExpr(
    val targetType: CType,
    val expr: Expression,
    override val line: Int,
    override val column: Int
) : Expression

data class SizeofExpr(
    val targetType: CType? = null,
    val targetExpr: Expression? = null,
    override val line: Int,
    override val column: Int
) : Expression

data class ArrayInitializerExpr(
    val elements: List<Expression>,
    override val line: Int,
    override val column: Int
) : Expression
