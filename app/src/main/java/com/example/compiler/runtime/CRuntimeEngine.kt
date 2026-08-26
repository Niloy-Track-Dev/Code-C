package com.example.compiler.runtime

import com.example.compiler.lexer.TokenType
import com.example.compiler.model.CompilerConfig
import com.example.compiler.model.CompiledExecutable
import com.example.compiler.model.ExecutionResult
import com.example.compiler.parser.*
import java.util.*
import kotlin.math.*

class CExecutable(
    override val programName: String,
    val ast: TranslationUnit
) : CompiledExecutable

sealed class CValue {
    data class IntVal(val value: Long) : CValue() {
        override fun toString() = value.toString()
    }
    data class FloatVal(val value: Double) : CValue() {
        override fun toString() = value.toString()
    }
    data class StringVal(val value: String) : CValue() {
        override fun toString() = value
    }
    data class PointerVal(val address: Long, val targetVarName: String? = null, val offset: Int = 0) : CValue() {
        override fun toString() = "0x${address.toString(16)}"
    }
    data class ArrayVal(val elements: MutableList<CValue>) : CValue() {
        override fun toString() = elements.toString()
    }
    data class StructVal(val typeName: String, val fields: MutableMap<String, CValue>) : CValue() {
        override fun toString() = "struct $typeName $fields"
    }
    object VoidVal : CValue() {
        override fun toString() = "void"
    }

    fun toLong(): Long = when (this) {
        is IntVal -> value
        is FloatVal -> value.toLong()
        is PointerVal -> address
        is StringVal -> value.toLongOrNull() ?: 0L
        is ArrayVal -> if (elements.isNotEmpty()) elements[0].toLong() else 0L
        is StructVal -> 0L
        is VoidVal -> 0L
    }

    fun toDouble(): Double = when (this) {
        is FloatVal -> value
        is IntVal -> value.toDouble()
        is PointerVal -> address.toDouble()
        is StringVal -> value.toDoubleOrNull() ?: 0.0
        is ArrayVal -> if (elements.isNotEmpty()) elements[0].toDouble() else 0.0
        is StructVal -> 0.0
        is VoidVal -> 0.0
    }

    fun isTruthy(): Boolean = when (this) {
        is IntVal -> value != 0L
        is FloatVal -> value != 0.0
        is PointerVal -> address != 0L
        is StringVal -> value.isNotEmpty()
        is ArrayVal -> elements.isNotEmpty()
        is StructVal -> true
        is VoidVal -> false
    }
}

class CRuntimeException(val msg: String, val line: Int = 1) : Exception("Runtime Error at line $line: $msg")
class CExecutionTimeoutException(val timeMs: Long) : Exception("Execution timed out after ${timeMs}ms")
class CExecutionCancelledException : Exception("Execution stopped by user")

class CRuntimeEngine(
    private val config: CompilerConfig,
    private val stdinProvider: () -> String = { "" }
) {
    private val globalScope = mutableMapOf<String, CValue>()
    private val functions = mutableMapOf<String, FunctionDefinition>()
    private val structs = mutableMapOf<String, StructDeclaration>()

    // Memory Heap Simulator: address -> ByteArray / CValue
    private var nextHeapAddress = 0x100000L
    private val heapMemory = mutableMapOf<Long, CValue>()
    private val rawMemoryBlocks = mutableMapOf<Long, Int>() // address -> size

    private val stdout = StringBuilder()
    private val stderr = StringBuilder()
    private var instructionCount = 0L
    private val maxInstructions = 10_000_000L
    private var startTimeMs = 0L

    @Volatile
    private var isCancelled = false

    private var stdinScanner: Scanner? = null
    private var rawStdin: String = ""

    fun cancel() {
        isCancelled = true
    }

    fun execute(executable: CExecutable, stdinInput: String = ""): ExecutionResult {
        startTimeMs = System.currentTimeMillis()
        instructionCount = 0L
        isCancelled = false
        stdout.clear()
        stderr.clear()
        globalScope.clear()
        functions.clear()
        structs.clear()
        heapMemory.clear()
        rawMemoryBlocks.clear()
        nextHeapAddress = 0x100000L

        rawStdin = if (stdinInput.isNotEmpty()) stdinInput else stdinProvider()
        stdinScanner = Scanner(rawStdin)

        // Load AST declarations
        for (decl in executable.ast.declarations) {
            when (decl) {
                is FunctionDefinition -> functions[decl.name] = decl
                is StructDeclaration -> structs[decl.name] = decl
                is GlobalVarDeclaration -> {
                    val initVal = if (decl.initializer != null) {
                        evalExpression(decl.initializer, mutableListOf(globalScope))
                    } else {
                        getDefaultValue(decl.type, decl.arraySize)
                    }
                    globalScope[decl.name] = initVal
                }
                is TypedefDeclaration -> {}
            }
        }

        val mainFunc = functions["main"]
        if (mainFunc == null) {
            return ExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = "Error: 'main' function not defined in program.",
                executionTimeMs = System.currentTimeMillis() - startTimeMs
            )
        }

        return try {
            val exitVal = callFunction("main", emptyList(), mutableListOf(globalScope))
            val exitCode = (exitVal as? CValue.IntVal)?.value?.toInt() ?: 0
            val execTime = System.currentTimeMillis() - startTimeMs
            ExecutionResult(
                exitCode = exitCode,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                executionTimeMs = max(1L, execTime),
                memoryUsageBytes = heapMemory.size * 8L + 4096L
            )
        } catch (e: CExecutionCancelledException) {
            val execTime = System.currentTimeMillis() - startTimeMs
            ExecutionResult(
                exitCode = 130, // SIGINT
                stdout = stdout.toString(),
                stderr = "Program execution stopped by user.",
                executionTimeMs = execTime,
                isCancelled = true
            )
        } catch (e: CExecutionTimeoutException) {
            val execTime = System.currentTimeMillis() - startTimeMs
            ExecutionResult(
                exitCode = 124, // Timeout
                stdout = stdout.toString(),
                stderr = "Execution timed out (limit: ${config.timeoutMs}ms). Possible infinite loop.",
                executionTimeMs = execTime,
                isTimeout = true
            )
        } catch (e: CRuntimeException) {
            val execTime = System.currentTimeMillis() - startTimeMs
            ExecutionResult(
                exitCode = 139, // Runtime error / SIGSEGV
                stdout = stdout.toString(),
                stderr = e.msg,
                executionTimeMs = execTime
            )
        } catch (e: Exception) {
            val execTime = System.currentTimeMillis() - startTimeMs
            ExecutionResult(
                exitCode = 1,
                stdout = stdout.toString(),
                stderr = "Runtime exception: ${e.message ?: "Unknown error"}",
                executionTimeMs = execTime
            )
        }
    }

    private fun checkExecutionLimits() {
        if (isCancelled) {
            throw CExecutionCancelledException()
        }
        instructionCount++
        if (instructionCount % 1000 == 0L) {
            val elapsed = System.currentTimeMillis() - startTimeMs
            if (elapsed > config.timeoutMs) {
                throw CExecutionTimeoutException(config.timeoutMs)
            }
        }
        if (instructionCount > maxInstructions) {
            throw CExecutionTimeoutException(config.timeoutMs)
        }
        if (stdout.length > config.maxOutputChars) {
            stdout.append("\n...[Output truncated: maximum output buffer limit reached]...")
            throw CRuntimeException("Maximum output size (${config.maxOutputChars} bytes) exceeded.")
        }
    }

    private fun callFunction(
        name: String,
        args: List<CValue>,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        checkExecutionLimits()

        // Check Built-in Standard Library functions first
        if (isStandardLibraryFunction(name)) {
            return executeStandardLibraryFunction(name, args, scopes)
        }

        val func = functions[name] ?: throw CRuntimeException("Call to undefined function '$name'")

        val localScope = mutableMapOf<String, CValue>()
        for (i in func.params.indices) {
            val param = func.params[i]
            val argVal = if (i < args.size) args[i] else getDefaultValue(param.type)
            localScope[param.name] = argVal
        }

        scopes.add(localScope)
        try {
            val result = executeStatement(func.body, scopes)
            return when (result) {
                is StatementResult.Return -> result.value
                else -> if (func.returnType == CType.VoidType) CValue.VoidVal else CValue.IntVal(0)
            }
        } finally {
            scopes.removeAt(scopes.lastIndex)
        }
    }

    private sealed class StatementResult {
        object Next : StatementResult()
        data class Return(val value: CValue) : StatementResult()
        object Break : StatementResult()
        object Continue : StatementResult()
    }

    private fun executeStatement(
        stmt: Statement,
        scopes: MutableList<MutableMap<String, CValue>>
    ): StatementResult {
        checkExecutionLimits()

        return when (stmt) {
            is CompoundStatement -> {
                val blockScope = mutableMapOf<String, CValue>()
                scopes.add(blockScope)
                try {
                    for (s in stmt.statements) {
                        val res = executeStatement(s, scopes)
                        if (res !is StatementResult.Next) {
                            return res
                        }
                    }
                    StatementResult.Next
                } finally {
                    scopes.removeAt(scopes.lastIndex)
                }
            }
            is LocalVarDeclaration -> {
                val currentScope = scopes.last()
                val initVal = if (stmt.initializer != null) {
                    if (stmt.initializer is ArrayInitializerExpr) {
                        val elements = stmt.initializer.elements.map { evalExpression(it, scopes) }.toMutableList()
                        CValue.ArrayVal(elements)
                    } else {
                        evalExpression(stmt.initializer, scopes)
                    }
                } else {
                    getDefaultValue(stmt.type, stmt.arraySize)
                }
                currentScope[stmt.name] = initVal
                StatementResult.Next
            }
            is MultiVarDeclaration -> {
                for (decl in stmt.declarations) {
                    val res = executeStatement(decl, scopes)
                    if (res !is StatementResult.Next) return res
                }
                StatementResult.Next
            }
            is ExpressionStatement -> {
                evalExpression(stmt.expression, scopes)
                StatementResult.Next
            }
            is IfStatement -> {
                val cond = evalExpression(stmt.condition, scopes)
                if (cond.isTruthy()) {
                    executeStatement(stmt.thenBranch, scopes)
                } else if (stmt.elseBranch != null) {
                    executeStatement(stmt.elseBranch, scopes)
                } else {
                    StatementResult.Next
                }
            }
            is WhileStatement -> {
                while (evalExpression(stmt.condition, scopes).isTruthy()) {
                    val res = executeStatement(stmt.body, scopes)
                    if (res is StatementResult.Return) return res
                    if (res is StatementResult.Break) break
                    // Continue loops
                }
                StatementResult.Next
            }
            is DoWhileStatement -> {
                do {
                    val res = executeStatement(stmt.body, scopes)
                    if (res is StatementResult.Return) return res
                    if (res is StatementResult.Break) break
                } while (evalExpression(stmt.condition, scopes).isTruthy())
                StatementResult.Next
            }
            is ForStatement -> {
                val forScope = mutableMapOf<String, CValue>()
                scopes.add(forScope)
                try {
                    if (stmt.init != null) {
                        executeStatement(stmt.init, scopes)
                    }
                    while (stmt.condition == null || evalExpression(stmt.condition, scopes).isTruthy()) {
                        val res = executeStatement(stmt.body, scopes)
                        if (res is StatementResult.Return) return res
                        if (res is StatementResult.Break) break

                        if (stmt.update != null) {
                            evalExpression(stmt.update, scopes)
                        }
                    }
                    StatementResult.Next
                } finally {
                    scopes.removeAt(scopes.lastIndex)
                }
            }
            is SwitchStatement -> {
                val condVal = evalExpression(stmt.condition, scopes).toLong()
                var matched = false
                val block = stmt.body as? CompoundStatement ?: CompoundStatement(listOf(stmt.body), stmt.line, stmt.column)

                val blockScope = mutableMapOf<String, CValue>()
                scopes.add(blockScope)
                try {
                    for (s in block.statements) {
                        if (s is CaseStatement) {
                            val caseVal = evalExpression(s.value, scopes).toLong()
                            if (caseVal == condVal || matched) {
                                matched = true
                                if (s.statement != null) {
                                    val res = executeStatement(s.statement, scopes)
                                    if (res is StatementResult.Break) break
                                    if (res is StatementResult.Return) return res
                                }
                            }
                        } else if (s is DefaultStatement) {
                            matched = true
                            if (s.statement != null) {
                                val res = executeStatement(s.statement, scopes)
                                if (res is StatementResult.Break) break
                                if (res is StatementResult.Return) return res
                            }
                        } else if (matched) {
                            val res = executeStatement(s, scopes)
                            if (res is StatementResult.Break) break
                            if (res is StatementResult.Return) return res
                        }
                    }
                    StatementResult.Next
                } finally {
                    scopes.removeAt(scopes.lastIndex)
                }
            }
            is ReturnStatement -> {
                val value = if (stmt.value != null) evalExpression(stmt.value, scopes) else CValue.VoidVal
                StatementResult.Return(value)
            }
            is BreakStatement -> StatementResult.Break
            is ContinueStatement -> StatementResult.Continue
            is EmptyStatement -> StatementResult.Next
            is CaseStatement, is DefaultStatement -> StatementResult.Next
        }
    }

    private fun evalExpression(
        expr: Expression,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        checkExecutionLimits()

        return when (expr) {
            is IntLiteral -> CValue.IntVal(expr.value)
            is FloatLiteral -> CValue.FloatVal(expr.value)
            is StringLiteral -> CValue.StringVal(expr.value)
            is CharLiteral -> CValue.IntVal(expr.value.code.toLong())
            is IdentifierExpr -> {
                lookupVariable(expr.name, scopes)
                    ?: throw CRuntimeException("Undeclared variable '${expr.name}'", expr.line)
            }
            is BinaryOpExpr -> {
                evalBinaryOp(expr, scopes)
            }
            is UnaryOpExpr -> {
                evalUnaryOp(expr, scopes)
            }
            is AssignmentExpr -> {
                evalAssignment(expr, scopes)
            }
            is FunctionCallExpr -> {
                val evaluatedArgs = expr.args.map { evalExpression(it, scopes) }
                callFunction(expr.name, evaluatedArgs, scopes)
            }
            is ArrayAccessExpr -> {
                val arr = evalExpression(expr.array, scopes)
                val idx = evalExpression(expr.index, scopes).toLong().toInt()
                when (arr) {
                    is CValue.ArrayVal -> {
                        if (idx in 0 until arr.elements.size) {
                            arr.elements[idx]
                        } else if (idx >= arr.elements.size && idx < 1000) {
                            // Dynamically expand array if within reasonable bound
                            while (arr.elements.size <= idx) {
                                arr.elements.add(CValue.IntVal(0))
                            }
                            arr.elements[idx]
                        } else {
                            throw CRuntimeException("Array index out of bounds: $idx (size: ${arr.elements.size})", expr.line)
                        }
                    }
                    is CValue.StringVal -> {
                        if (idx in 0 until arr.value.length) {
                            CValue.IntVal(arr.value[idx].code.toLong())
                        } else {
                            CValue.IntVal(0)
                        }
                    }
                    is CValue.PointerVal -> {
                        val targetAddress = arr.address + idx * 8L
                        heapMemory[targetAddress] ?: CValue.IntVal(0)
                    }
                    else -> CValue.IntVal(0)
                }
            }
            is MemberAccessExpr -> {
                val obj = evalExpression(expr.obj, scopes)
                when (obj) {
                    is CValue.StructVal -> {
                        obj.fields[expr.member] ?: CValue.IntVal(0)
                    }
                    is CValue.PointerVal -> {
                        val structVal = heapMemory[obj.address] as? CValue.StructVal
                        structVal?.fields?.get(expr.member) ?: CValue.IntVal(0)
                    }
                    else -> CValue.IntVal(0)
                }
            }
            is TernaryExpr -> {
                val cond = evalExpression(expr.condition, scopes)
                if (cond.isTruthy()) evalExpression(expr.trueExpr, scopes) else evalExpression(expr.falseExpr, scopes)
            }
            is CastExpr -> {
                val orig = evalExpression(expr.expr, scopes)
                when (expr.targetType) {
                    is CType.IntType, is CType.CharType, is CType.BoolType -> CValue.IntVal(orig.toLong())
                    is CType.FloatType, is CType.DoubleType -> CValue.FloatVal(orig.toDouble())
                    is CType.PointerType -> CValue.PointerVal(orig.toLong())
                    else -> orig
                }
            }
            is SizeofExpr -> {
                if (expr.targetType != null) {
                    val size = when (expr.targetType) {
                        is CType.CharType, is CType.BoolType -> 1L
                        is CType.IntType, is CType.FloatType -> 4L
                        is CType.DoubleType, is CType.PointerType -> 8L
                        is CType.ArrayType -> (expr.targetType.size * 4).toLong()
                        else -> 4L
                    }
                    CValue.IntVal(size)
                } else if (expr.targetExpr != null) {
                    val v = evalExpression(expr.targetExpr, scopes)
                    val size = when (v) {
                        is CValue.IntVal -> 4L
                        is CValue.FloatVal -> 8L
                        is CValue.PointerVal -> 8L
                        is CValue.StringVal -> v.value.length.toLong() + 1L
                        is CValue.ArrayVal -> (v.elements.size * 4).toLong()
                        else -> 4L
                    }
                    CValue.IntVal(size)
                } else {
                    CValue.IntVal(4L)
                }
            }
            is ArrayInitializerExpr -> {
                val elems = expr.elements.map { evalExpression(it, scopes) }.toMutableList()
                CValue.ArrayVal(elems)
            }
        }
    }

    private fun evalBinaryOp(
        expr: BinaryOpExpr,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        // Short-circuit logical operators
        if (expr.op == TokenType.LOGICAL_AND) {
            val left = evalExpression(expr.left, scopes)
            if (!left.isTruthy()) return CValue.IntVal(0)
            val right = evalExpression(expr.right, scopes)
            return CValue.IntVal(if (right.isTruthy()) 1 else 0)
        }
        if (expr.op == TokenType.LOGICAL_OR) {
            val left = evalExpression(expr.left, scopes)
            if (left.isTruthy()) return CValue.IntVal(1)
            val right = evalExpression(expr.right, scopes)
            return CValue.IntVal(if (right.isTruthy()) 1 else 0)
        }

        val left = evalExpression(expr.left, scopes)
        val right = evalExpression(expr.right, scopes)

        val isFloatOp = left is CValue.FloatVal || right is CValue.FloatVal

        if (isFloatOp) {
            val l = left.toDouble()
            val r = right.toDouble()
            return when (expr.op) {
                TokenType.PLUS -> CValue.FloatVal(l + r)
                TokenType.MINUS -> CValue.FloatVal(l - r)
                TokenType.STAR -> CValue.FloatVal(l * r)
                TokenType.SLASH -> if (r != 0.0) CValue.FloatVal(l / r) else throw CRuntimeException("Division by zero", expr.line)
                TokenType.EQUAL_EQUAL -> CValue.IntVal(if (l == r) 1 else 0)
                TokenType.NOT_EQUAL -> CValue.IntVal(if (l != r) 1 else 0)
                TokenType.LESS -> CValue.IntVal(if (l < r) 1 else 0)
                TokenType.LESS_EQUAL -> CValue.IntVal(if (l <= r) 1 else 0)
                TokenType.GREATER -> CValue.IntVal(if (l > r) 1 else 0)
                TokenType.GREATER_EQUAL -> CValue.IntVal(if (l >= r) 1 else 0)
                else -> CValue.FloatVal(0.0)
            }
        }

        val l = left.toLong()
        val r = right.toLong()

        // Pointer arithmetic
        if (left is CValue.PointerVal && (expr.op == TokenType.PLUS || expr.op == TokenType.MINUS)) {
            val newAddr = if (expr.op == TokenType.PLUS) left.address + r * 8L else left.address - r * 8L
            return CValue.PointerVal(newAddr, left.targetVarName, left.offset + r.toInt())
        }

        return when (expr.op) {
            TokenType.PLUS -> CValue.IntVal(l + r)
            TokenType.MINUS -> CValue.IntVal(l - r)
            TokenType.STAR -> CValue.IntVal(l * r)
            TokenType.SLASH -> if (r != 0L) CValue.IntVal(l / r) else throw CRuntimeException("Division by zero", expr.line)
            TokenType.PERCENT -> if (r != 0L) CValue.IntVal(l % r) else throw CRuntimeException("Modulo by zero", expr.line)
            TokenType.EQUAL_EQUAL -> CValue.IntVal(if (l == r) 1 else 0)
            TokenType.NOT_EQUAL -> CValue.IntVal(if (l != r) 1 else 0)
            TokenType.LESS -> CValue.IntVal(if (l < r) 1 else 0)
            TokenType.LESS_EQUAL -> CValue.IntVal(if (l <= r) 1 else 0)
            TokenType.GREATER -> CValue.IntVal(if (l > r) 1 else 0)
            TokenType.GREATER_EQUAL -> CValue.IntVal(if (l >= r) 1 else 0)
            TokenType.AMPERSAND -> CValue.IntVal(l and r)
            TokenType.PIPE -> CValue.IntVal(l or r)
            TokenType.CARET -> CValue.IntVal(l xor r)
            TokenType.SHL -> CValue.IntVal(l shl r.toInt())
            TokenType.SHR -> CValue.IntVal(l shr r.toInt())
            TokenType.COMMA -> right
            else -> CValue.IntVal(0)
        }
    }

    private fun evalUnaryOp(
        expr: UnaryOpExpr,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        when (expr.op) {
            TokenType.MINUS -> {
                val v = evalExpression(expr.expr, scopes)
                return if (v is CValue.FloatVal) CValue.FloatVal(-v.value) else CValue.IntVal(-v.toLong())
            }
            TokenType.PLUS -> {
                return evalExpression(expr.expr, scopes)
            }
            TokenType.LOGICAL_NOT -> {
                val v = evalExpression(expr.expr, scopes)
                return CValue.IntVal(if (v.isTruthy()) 0 else 1)
            }
            TokenType.TILDE -> {
                val v = evalExpression(expr.expr, scopes)
                return CValue.IntVal(v.toLong().inv())
            }
            TokenType.AMPERSAND -> {
                // Address-of operator &x
                if (expr.expr is IdentifierExpr) {
                    val varName = expr.expr.name
                    // Synthesize pointer address for variable
                    val addr = varName.hashCode().toLong() and 0xFFFFFFFL or 0x200000L
                    return CValue.PointerVal(addr, varName, 0)
                }
                if (expr.expr is ArrayAccessExpr) {
                    val arr = evalExpression(expr.expr.array, scopes)
                    val idx = evalExpression(expr.expr.index, scopes).toLong().toInt()
                    val addr = if (arr is CValue.PointerVal) arr.address + idx * 8L else (arr.hashCode().toLong() + idx * 8L)
                    return CValue.PointerVal(addr, null, idx)
                }
                return CValue.PointerVal(0x200000L)
            }
            TokenType.STAR -> {
                // Dereference operator *ptr
                val ptr = evalExpression(expr.expr, scopes)
                return when (ptr) {
                    is CValue.PointerVal -> {
                        if (ptr.targetVarName != null) {
                            lookupVariable(ptr.targetVarName, scopes) ?: CValue.IntVal(0)
                        } else {
                            heapMemory[ptr.address] ?: CValue.IntVal(0)
                        }
                    }
                    is CValue.ArrayVal -> {
                        if (ptr.elements.isNotEmpty()) ptr.elements[0] else CValue.IntVal(0)
                    }
                    else -> CValue.IntVal(0)
                }
            }
            TokenType.PLUS_PLUS -> {
                if (expr.expr is IdentifierExpr) {
                    val current = lookupVariable(expr.expr.name, scopes) ?: CValue.IntVal(0)
                    val nextVal = if (current is CValue.FloatVal) CValue.FloatVal(current.value + 1.0) else CValue.IntVal(current.toLong() + 1)
                    assignVariable(expr.expr.name, nextVal, scopes)
                    return if (expr.isPrefix) nextVal else current
                }
                return CValue.IntVal(0)
            }
            TokenType.MINUS_MINUS -> {
                if (expr.expr is IdentifierExpr) {
                    val current = lookupVariable(expr.expr.name, scopes) ?: CValue.IntVal(0)
                    val nextVal = if (current is CValue.FloatVal) CValue.FloatVal(current.value - 1.0) else CValue.IntVal(current.toLong() - 1)
                    assignVariable(expr.expr.name, nextVal, scopes)
                    return if (expr.isPrefix) nextVal else current
                }
                return CValue.IntVal(0)
            }
            else -> return evalExpression(expr.expr, scopes)
        }
    }

    private fun evalAssignment(
        expr: AssignmentExpr,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        val rVal = evalExpression(expr.value, scopes)

        when (expr.target) {
            is IdentifierExpr -> {
                val varName = expr.target.name
                val finalVal = if (expr.op == TokenType.EQUAL) {
                    rVal
                } else {
                    val curVal = lookupVariable(varName, scopes) ?: CValue.IntVal(0)
                    computeCompoundAssignment(curVal, expr.op, rVal, expr.line)
                }
                assignVariable(varName, finalVal, scopes)
                return finalVal
            }
            is ArrayAccessExpr -> {
                val arr = evalExpression(expr.target.array, scopes)
                val idx = evalExpression(expr.target.index, scopes).toLong().toInt()
                when (arr) {
                    is CValue.ArrayVal -> {
                        while (arr.elements.size <= idx) {
                            arr.elements.add(CValue.IntVal(0))
                        }
                        arr.elements[idx] = rVal
                    }
                    is CValue.PointerVal -> {
                        val targetAddr = arr.address + idx * 8L
                        heapMemory[targetAddr] = rVal
                    }
                    else -> {}
                }
                return rVal
            }
            is UnaryOpExpr -> {
                if (expr.target.op == TokenType.STAR) {
                    val ptr = evalExpression(expr.target.expr, scopes)
                    if (ptr is CValue.PointerVal) {
                        if (ptr.targetVarName != null) {
                            assignVariable(ptr.targetVarName, rVal, scopes)
                        } else {
                            heapMemory[ptr.address] = rVal
                        }
                    }
                    return rVal
                }
                return rVal
            }
            is MemberAccessExpr -> {
                val obj = evalExpression(expr.target.obj, scopes)
                when (obj) {
                    is CValue.StructVal -> {
                        obj.fields[expr.target.member] = rVal
                    }
                    is CValue.PointerVal -> {
                        val s = heapMemory[obj.address] as? CValue.StructVal
                        s?.fields?.put(expr.target.member, rVal)
                    }
                    else -> {}
                }
                return rVal
            }
            else -> return rVal
        }
    }

    private fun computeCompoundAssignment(
        left: CValue,
        op: TokenType,
        right: CValue,
        line: Int
    ): CValue {
        val isFloat = left is CValue.FloatVal || right is CValue.FloatVal
        if (isFloat) {
            val l = left.toDouble()
            val r = right.toDouble()
            return when (op) {
                TokenType.PLUS_EQUAL -> CValue.FloatVal(l + r)
                TokenType.MINUS_EQUAL -> CValue.FloatVal(l - r)
                TokenType.STAR_EQUAL -> CValue.FloatVal(l * r)
                TokenType.SLASH_EQUAL -> if (r != 0.0) CValue.FloatVal(l / r) else throw CRuntimeException("Division by zero", line)
                else -> right
            }
        }
        val l = left.toLong()
        val r = right.toLong()
        return when (op) {
            TokenType.PLUS_EQUAL -> CValue.IntVal(l + r)
            TokenType.MINUS_EQUAL -> CValue.IntVal(l - r)
            TokenType.STAR_EQUAL -> CValue.IntVal(l * r)
            TokenType.SLASH_EQUAL -> if (r != 0L) CValue.IntVal(l / r) else throw CRuntimeException("Division by zero", line)
            TokenType.PERCENT_EQUAL -> if (r != 0L) CValue.IntVal(l % r) else throw CRuntimeException("Modulo by zero", line)
            TokenType.AMPERSAND_EQUAL -> CValue.IntVal(l and r)
            TokenType.PIPE_EQUAL -> CValue.IntVal(l or r)
            TokenType.CARET_EQUAL -> CValue.IntVal(l xor r)
            TokenType.SHL_EQUAL -> CValue.IntVal(l shl r.toInt())
            TokenType.SHR_EQUAL -> CValue.IntVal(l shr r.toInt())
            else -> right
        }
    }

    private fun lookupVariable(name: String, scopes: List<Map<String, CValue>>): CValue? {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name)) {
                return scopes[i][name]
            }
        }
        return globalScope[name]
    }

    private fun assignVariable(name: String, value: CValue, scopes: MutableList<MutableMap<String, CValue>>) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name)) {
                scopes[i][name] = value
                return
            }
        }
        globalScope[name] = value
    }

    private fun getDefaultValue(type: CType, arraySize: Int? = null): CValue {
        if (arraySize != null && arraySize > 0) {
            val list = MutableList<CValue>(arraySize) { getDefaultValue(type) }
            return CValue.ArrayVal(list)
        }
        return when (type) {
            is CType.IntType, is CType.CharType, is CType.BoolType -> CValue.IntVal(0)
            is CType.FloatType, is CType.DoubleType -> CValue.FloatVal(0.0)
            is CType.PointerType -> CValue.PointerVal(0L)
            is CType.ArrayType -> {
                val list = MutableList<CValue>(type.size) { getDefaultValue(type.baseType) }
                CValue.ArrayVal(list)
            }
            is CType.StructType -> {
                val fields = mutableMapOf<String, CValue>()
                val structDecl = structs[type.name]
                if (structDecl != null) {
                    for (f in structDecl.fields) {
                        fields[f.name] = getDefaultValue(f.type)
                    }
                }
                CValue.StructVal(type.name, fields)
            }
            else -> CValue.IntVal(0)
        }
    }

    // Standard C Library Built-ins
    private fun isStandardLibraryFunction(name: String): Boolean {
        return setOf(
            "printf", "scanf", "puts", "gets", "getchar", "putchar", "sprintf", "sscanf",
            "malloc", "free", "realloc", "calloc", "exit", "abs", "labs", "atoi", "atof", "atol",
            "rand", "srand", "qsort", "bsearch", "strlen", "strcpy", "strncpy", "strcat", "strncat",
            "strcmp", "strncmp", "strchr", "strstr", "memset", "memcpy", "memcmp", "sqrt", "pow",
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "exp", "log", "log10", "ceil",
            "floor", "fabs", "fmod", "round", "hypot", "isalpha", "isdigit", "isalnum", "isspace",
            "isupper", "islower", "toupper", "tolower", "time", "clock", "difftime", "system",
            "fopen", "fclose", "fread", "fwrite", "fgets", "fputs", "fgetc", "fputc", "feof", "fflush"
        ).contains(name)
    }

    private fun executeStandardLibraryFunction(
        name: String,
        args: List<CValue>,
        scopes: MutableList<MutableMap<String, CValue>>
    ): CValue {
        return when (name) {
            "printf" -> {
                if (args.isEmpty()) return CValue.IntVal(0)
                val format = args[0].toString()
                val extraArgs = args.drop(1)
                val formatted = formatPrintf(format, extraArgs)
                stdout.append(formatted)
                CValue.IntVal(formatted.length.toLong())
            }
            "scanf" -> {
                if (args.isEmpty()) return CValue.IntVal(0)
                val format = args[0].toString()
                val targetPointers = args.drop(1)
                var assignedCount = 0
                val scanner = stdinScanner ?: Scanner(rawStdin)

                // Match format specifiers %d, %i, %f, %lf, %s, %c
                val specifiers = Regex("""%[0-9]*[diflscupxX]""").findAll(format).toList()
                for (i in specifiers.indices) {
                    if (i < targetPointers.size && scanner.hasNext()) {
                        val spec = specifiers[i].value
                        val ptr = targetPointers[i]
                        try {
                            when {
                                spec.endsWith("d") || spec.endsWith("i") || spec.endsWith("u") || spec.endsWith("l") -> {
                                    val num = scanner.nextLong()
                                    writeToPointer(ptr, CValue.IntVal(num), scopes)
                                    assignedCount++
                                }
                                spec.endsWith("f") -> {
                                    val flt = scanner.nextDouble()
                                    writeToPointer(ptr, CValue.FloatVal(flt), scopes)
                                    assignedCount++
                                }
                                spec.endsWith("s") -> {
                                    val str = scanner.next()
                                    writeToPointer(ptr, CValue.StringVal(str), scopes)
                                    assignedCount++
                                }
                                spec.endsWith("c") -> {
                                    val ch = scanner.next()
                                    if (ch.isNotEmpty()) {
                                        writeToPointer(ptr, CValue.IntVal(ch[0].code.toLong()), scopes)
                                        assignedCount++
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
                CValue.IntVal(assignedCount.toLong())
            }
            "puts" -> {
                val str = if (args.isNotEmpty()) args[0].toString() else ""
                stdout.append(str).append("\n")
                CValue.IntVal(1)
            }
            "putchar" -> {
                val code = if (args.isNotEmpty()) args[0].toLong().toInt() else 0
                stdout.append(code.toChar())
                CValue.IntVal(code.toLong())
            }
            "getchar" -> {
                val scanner = stdinScanner ?: Scanner(rawStdin)
                if (scanner.hasNext()) {
                    val token = scanner.next()
                    CValue.IntVal(token[0].code.toLong())
                } else {
                    CValue.IntVal(-1) // EOF
                }
            }
            "malloc" -> {
                val size = if (args.isNotEmpty()) args[0].toLong().toInt() else 0
                val addr = nextHeapAddress
                nextHeapAddress += max(8, size + 8)
                rawMemoryBlocks[addr] = size
                CValue.PointerVal(addr)
            }
            "free" -> {
                if (args.isNotEmpty() && args[0] is CValue.PointerVal) {
                    val addr = (args[0] as CValue.PointerVal).address
                    heapMemory.remove(addr)
                    rawMemoryBlocks.remove(addr)
                }
                CValue.VoidVal
            }
            "calloc" -> {
                val num = if (args.isNotEmpty()) args[0].toLong().toInt() else 1
                val size = if (args.size > 1) args[1].toLong().toInt() else 1
                val totalSize = num * size
                val addr = nextHeapAddress
                nextHeapAddress += max(8, totalSize + 8)
                rawMemoryBlocks[addr] = totalSize
                CValue.PointerVal(addr)
            }
            "realloc" -> {
                val oldAddr = if (args.isNotEmpty() && args[0] is CValue.PointerVal) (args[0] as CValue.PointerVal).address else 0L
                val newSize = if (args.size > 1) args[1].toLong().toInt() else 0
                val newAddr = nextHeapAddress
                nextHeapAddress += max(8, newSize + 8)
                if (heapMemory.containsKey(oldAddr)) {
                    heapMemory[newAddr] = heapMemory[oldAddr]!!
                }
                CValue.PointerVal(newAddr)
            }
            "strlen" -> {
                val str = if (args.isNotEmpty()) args[0].toString() else ""
                CValue.IntVal(str.length.toLong())
            }
            "strcmp" -> {
                val s1 = if (args.isNotEmpty()) args[0].toString() else ""
                val s2 = if (args.size > 1) args[1].toString() else ""
                CValue.IntVal(s1.compareTo(s2).toLong())
            }
            "strcpy", "strncpy" -> {
                if (args.size >= 2) {
                    val dest = args[0]
                    val src = args[1].toString()
                    writeToPointer(dest, CValue.StringVal(src), scopes)
                }
                if (args.isNotEmpty()) args[0] else CValue.IntVal(0)
            }
            "strcat" -> {
                if (args.size >= 2) {
                    val dest = args[0]
                    val destStr = dest.toString()
                    val srcStr = args[1].toString()
                    writeToPointer(dest, CValue.StringVal(destStr + srcStr), scopes)
                }
                if (args.isNotEmpty()) args[0] else CValue.IntVal(0)
            }
            "strstr" -> {
                val h = if (args.isNotEmpty()) args[0].toString() else ""
                val n = if (args.size > 1) args[1].toString() else ""
                val idx = h.indexOf(n)
                if (idx >= 0) {
                    val ptr = args[0]
                    val baseAddr = if (ptr is CValue.PointerVal) ptr.address else 0x1000L
                    CValue.PointerVal(baseAddr + idx)
                } else {
                    CValue.PointerVal(0L) // NULL
                }
            }
            "abs", "labs" -> {
                val x = if (args.isNotEmpty()) args[0].toLong() else 0L
                CValue.IntVal(abs(x))
            }
            "sqrt" -> {
                val x = if (args.isNotEmpty()) args[0].toDouble() else 0.0
                CValue.FloatVal(sqrt(x))
            }
            "pow" -> {
                val base = if (args.isNotEmpty()) args[0].toDouble() else 0.0
                val exp = if (args.size > 1) args[1].toDouble() else 0.0
                CValue.FloatVal(base.pow(exp))
            }
            "sin" -> CValue.FloatVal(sin(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "cos" -> CValue.FloatVal(cos(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "tan" -> CValue.FloatVal(tan(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "asin" -> CValue.FloatVal(asin(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "acos" -> CValue.FloatVal(acos(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "atan" -> CValue.FloatVal(atan(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "atan2" -> {
                val y = if (args.isNotEmpty()) args[0].toDouble() else 0.0
                val x = if (args.size > 1) args[1].toDouble() else 0.0
                CValue.FloatVal(atan2(y, x))
            }
            "exp" -> CValue.FloatVal(exp(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "log" -> CValue.FloatVal(ln(if (args.isNotEmpty()) args[0].toDouble() else 1.0))
            "log10" -> CValue.FloatVal(log10(if (args.isNotEmpty()) args[0].toDouble() else 1.0))
            "ceil" -> CValue.FloatVal(ceil(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "floor" -> CValue.FloatVal(floor(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "fabs" -> CValue.FloatVal(abs(if (args.isNotEmpty()) args[0].toDouble() else 0.0))
            "hypot" -> {
                val a = if (args.isNotEmpty()) args[0].toDouble() else 0.0
                val b = if (args.size > 1) args[1].toDouble() else 0.0
                CValue.FloatVal(hypot(a, b))
            }
            "rand" -> {
                CValue.IntVal((Random().nextInt(32768)).toLong())
            }
            "srand" -> CValue.VoidVal
            "atoi", "atol" -> {
                val str = if (args.isNotEmpty()) args[0].toString().trim() else "0"
                val num = Regex("""^[-+]?\d+""").find(str)?.value?.toLongOrNull() ?: 0L
                CValue.IntVal(num)
            }
            "atof" -> {
                val str = if (args.isNotEmpty()) args[0].toString().trim() else "0.0"
                CValue.FloatVal(str.toDoubleOrNull() ?: 0.0)
            }
            "isalpha" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isLetter()) 1 else 0)
            }
            "isdigit" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isDigit()) 1 else 0)
            }
            "isalnum" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isLetterOrDigit()) 1 else 0)
            }
            "isspace" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isWhitespace()) 1 else 0)
            }
            "isupper" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isUpperCase()) 1 else 0)
            }
            "islower" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(if (c.isLowerCase()) 1 else 0)
            }
            "toupper" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(c.uppercaseChar().code.toLong())
            }
            "tolower" -> {
                val c = (if (args.isNotEmpty()) args[0].toLong() else 0L).toInt().toChar()
                CValue.IntVal(c.lowercaseChar().code.toLong())
            }
            "clock" -> {
                val elapsed = System.currentTimeMillis() - startTimeMs
                CValue.IntVal(elapsed * 1000)
            }
            "time" -> {
                val t = System.currentTimeMillis() / 1000L
                if (args.isNotEmpty() && args[0] is CValue.PointerVal) {
                    writeToPointer(args[0], CValue.IntVal(t), scopes)
                }
                CValue.IntVal(t)
            }
            "exit" -> {
                val code = if (args.isNotEmpty()) args[0].toLong().toInt() else 0
                throw CRuntimeException("exit($code) called", 0)
            }
            "qsort" -> {
                // In-place sorting of array if passed pointer
                if (args.size >= 2) {
                    val arr = args[0]
                    val count = args[1].toLong().toInt()
                    if (arr is CValue.ArrayVal) {
                        val sublist = arr.elements.take(count).sortedBy { it.toDouble() }
                        for (i in sublist.indices) {
                            arr.elements[i] = sublist[i]
                        }
                    }
                }
                CValue.VoidVal
            }
            "fflush" -> CValue.IntVal(0)
            else -> CValue.IntVal(0)
        }
    }

    private fun writeToPointer(
        ptr: CValue,
        value: CValue,
        scopes: MutableList<MutableMap<String, CValue>>
    ) {
        when (ptr) {
            is CValue.PointerVal -> {
                if (ptr.targetVarName != null) {
                    assignVariable(ptr.targetVarName, value, scopes)
                } else {
                    heapMemory[ptr.address] = value
                }
            }
            else -> {}
        }
    }

    private fun formatPrintf(format: String, args: List<CValue>): String {
        val sb = StringBuilder()
        var argIdx = 0
        var i = 0

        while (i < format.length) {
            val c = format[i]
            if (c == '%' && i + 1 < format.length) {
                if (format[i + 1] == '%') {
                    sb.append('%')
                    i += 2
                    continue
                }

                var j = i + 1
                // Read width/precision modifiers: e.g. .2f, 10d, 02X
                val modSb = StringBuilder()
                while (j < format.length && (format[j].isDigit() || format[j] == '.' || format[j] == '-' || format[j] == '+')) {
                    modSb.append(format[j])
                    j++
                }
                if (j < format.length) {
                    val spec = format[j]
                    val arg = if (argIdx < args.size) args[argIdx++] else CValue.IntVal(0)
                    val mod = modSb.toString()

                    when (spec) {
                        'd', 'i', 'u', 'l' -> {
                            val v = arg.toLong()
                            if (mod.isNotEmpty()) {
                                try {
                                    sb.append(String.format("%$mod" + "d", v))
                                } catch (e: Exception) {
                                    sb.append(v)
                                }
                            } else {
                                sb.append(v)
                            }
                        }
                        'f', 'g' -> {
                            val v = arg.toDouble()
                            if (mod.isNotEmpty()) {
                                try {
                                    sb.append(String.format("%$mod" + "f", v))
                                } catch (e: Exception) {
                                    sb.append(v)
                                }
                            } else {
                                sb.append(String.format(Locale.US, "%.6f", v))
                            }
                        }
                        's' -> {
                            sb.append(arg.toString())
                        }
                        'c' -> {
                            val code = arg.toLong().toInt()
                            sb.append(code.toChar())
                        }
                        'x' -> {
                            sb.append(arg.toLong().toString(16))
                        }
                        'X' -> {
                            sb.append(arg.toLong().toString(16).uppercase())
                        }
                        'p' -> {
                            sb.append("0x").append(arg.toLong().toString(16))
                        }
                        else -> {
                            sb.append('%').append(spec)
                        }
                    }
                    i = j + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
