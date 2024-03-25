@file:Suppress("NAME_SHADOWING", "NAME_SHADOWING")

package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.isDataClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.safeName
import io.exoquery.parseError
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.isTypeParameter

fun <T> List0() = Is(listOf<T>())

val IrCall.simpleValueArgsCount get() = this.valueArgumentsCount - this.contextReceiversCount
val IrCall.simpleValueArgs get() =
  if (this.contextReceiversCount > 0)
    this.valueArguments.drop(this.contextReceiversCount)
  else
    this.valueArguments

val IrFunction.simpleValueParamsCount get() = this.valueParameters.size - this.contextReceiverParametersCount
val IrFunction.simpleValueParams get() =
  if (this.contextReceiverParametersCount > 0)
    this.valueParameters.drop(this.contextReceiverParametersCount)
  else
    this.valueParameters

object List1 {
  operator fun <AP: Pattern<A>, A> get(elem1: AP) =
    customPattern1(elem1) { it: List<A> -> if (it.size == 1) Components1(it.first()) else null }
}

object List2 {
  operator fun <AP: Pattern<T>, BP: Pattern<T>, T> get(elem1: AP, elem2: BP) =
    customPattern2(elem1, elem2) { it: List<T> ->
      if (it.size == 2) {
        val first: T = it.get(0)
        val second: T = it.get(1)
        Components2(first, second)
      } else
        null
    }
}

val IrType.simpleTypeArgs: List<IrType> get() =
  when (this) {
    is IrSimpleType ->
      this.arguments.mapNotNull { it.typeOrNull }
    else ->
      listOf()
  }

object Ir {
  object Expression {
    context(CompileLogger) operator fun <AP: Pattern<IrExpression>> get(components: AP) =
      customPattern1(components) { it: IrExpression ->
        Components1(it)
      }
  }

  object StringConcatenation {
    // Can't do just get(components: Pattern<List<IrExpression>) need to do:
    // <AP: Pattern<List<IrExpression>>> get(components: AP) or it doesn't work because
    // it needs to have a concrete pattern instance
    context(CompileLogger) operator fun <AP: Pattern<List<IrExpression>>> get(components: AP) =
      customPattern1(components) { it: IrExpression ->
        if (it is IrStringConcatenation) {
          Components1(it.arguments)
        } else {
          null
        }
      }
  }

  object Type {

    object SqlVariable {
      private val sqlVariableTypeName = io.exoquery.SqlVariable::class.qualifiedName!!

      context(CompileLogger) operator fun <AP: Pattern<IrType>> get(realType: AP) =
        customPattern1(realType) { it: IrType ->
          val cls = it.classOrNull
          val simpleTypeArgs = it.simpleTypeArgs
          if (cls != null && simpleTypeArgs.size == 1 && it.classFqName?.asString() == sqlVariableTypeName) {
            Components1(simpleTypeArgs.first())
          }
          else null
        }
    }

    object Query {
      private val queryTypeName = io.exoquery.Query::class.qualifiedName!!

      context(CompileLogger) operator fun <AP: Pattern<IrType>> get(realType: AP) =
        customPattern1(realType) { it: IrType ->
          val cls = it.classOrNull
          val simpleTypeArgs = it.simpleTypeArgs
          if (cls != null && simpleTypeArgs.size == 1 && it.classFqName?.asString() == queryTypeName) {
            Components1(simpleTypeArgs.first())
          }
          else null
        }
    }

    object DataClass {
      context(CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<List<Pair<String, IrType>>>> get(name: AP, fields: BP) =
        customPattern2(name, fields) { it: IrType ->
          val cls = it.classOrNull
          if (cls != null && cls.isDataClass()) {
            val name = cls.safeName
            val props = cls.dataClassProperties()
            // Note that this was not matching without props.toList() because it was a Sequence object instead of a list
            // this is improtant to note since if the types to not line up the match won't happen although the IDE
            // or build will not complain about the mismatched types (at least during compilation of this file)
            val output = Components2(name, props.toList())
            output
          }
          else null
        }
    }

    object Generic {
      context(CompileLogger) operator fun get(type: Pattern0<IrType>) =
        customPattern1(type) { it: IrType ->
          if (it.isTypeParameter())
            Components1(it)
          else
            null
        }
    }

    object Value {
      private fun isValue(it: IrType) =
        it.isString() ||
          it.isLong() ||
          it.isShort() ||
          it.isInt() ||
          it.isByte() ||
          it.isBoolean() ||
          it.isFloat() ||
          it.isDouble() ||
          it.isNullableNothing()

      context(CompileLogger) operator fun get(type: Pattern0<IrType>) =
        customPattern1(type) { it: IrType ->
          if (isValue(it)) {
            // If mistake is made in the Components-returning section, the match will not be successful
            // but the compiler will not tell us we are returing something incorrect
            Components1(it)
          }
          else null
        }
    }

  }

  object Block {
    operator fun <AP: Pattern<List<IrStatement>>, BP: Pattern<IrExpression>> get(statements: AP, ret: BP) =
      customPattern2(statements, ret) { it: IrBlock ->
        val stmt = it.statements.takeLast(1).firstOrNull()
        val lastStatement =
          when {
            (stmt != null && stmt is IrExpression) -> stmt
            else -> error("Unexepected last-statement block was not an expression: ${it.dumpKotlinLike()}")
          }

        val others = it.statements.dropLast(1)
        Components2(others, lastStatement)
      }
  }

  object Variable {
    context (CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<IrExpression>> get(name: AP, rhs: BP) =
      customPattern2(name, rhs) { it: IrVariable ->
        if (it.initializer != null) {
          Components2(it.name.asString(), it.initializer)
        } else {
          val msg = "Could not find a right-hand-side for the variable:${it.dumpKotlinLike()}"
          error(msg)
          parseError(msg)
        }
      }
  }

  object GetValue {
    context (CompileLogger) operator fun get(value: Pattern0<IrSymbol>) =
      customPattern1(value) { it: IrGetValue ->
        Components1(it.symbol)
      }
  }



  object Const {
    /**
     * Decomat breaks internally if there is a null-value in any inner part of the pattern,
     * therefore make sure to capute this into a Value.Null instead.
     * (note that this approach assumes we don't have a situation where we need to distinguish
     * between a null value whose type is Null and a null value that has some other type e.g. Int?)
     */
    sealed interface Value {
      companion object {
        data class Boolean(val value: kotlin.Boolean) : Value
        data class Char(val value: kotlin.Char) : Value
        data class Byte(val value: kotlin.Byte) : Value
        data class Short(val value: kotlin.Short) : Value
        data class Int(val value: kotlin.Int) : Value
        data class Long(val value: kotlin.Long) : Value
        data class String(val value: kotlin.String) : Value
        data class Float(val value: kotlin.Float) : Value
        data class Double(val value: kotlin.Double) : Value
        object Null: Value {
          override fun toString(): kotlin.String = "Null"
        }

        fun fromIrConst(it: IrConst<*>): Value =
          when (it.kind) {
            IrConstKind.Boolean -> Boolean(it.value as kotlin.Boolean)
            IrConstKind.Char -> Char(it.value as kotlin.Char)
            IrConstKind.Byte -> Byte(it.value as kotlin.Byte)
            IrConstKind.Short -> Short(it.value as kotlin.Short)
            IrConstKind.Int -> Int(it.value as kotlin.Int)
            IrConstKind.Long -> Long(it.value as kotlin.Long)
            IrConstKind.String -> String(it.value as kotlin.String)
            IrConstKind.Float -> Float(it.value as kotlin.Float)
            IrConstKind.Double -> Double(it.value as kotlin.Double)
            IrConstKind.Null -> Null
          }
      }
    }

    operator fun get(value: Pattern0<Value>) =
      customPattern1(value) { it: IrConst<*> ->
        Components1(Value.fromIrConst(it))
      }
  }

  object When {
    context (CompileLogger) operator fun <AP: Pattern<L>, L: List<A>, A: IrBranch> get(value: AP) =
      customPattern1(value) { it: IrWhen ->
        Components1(it.branches)
      }
  }

  object Branch {
    // would to have a genrics A and B here but that seems to slow down kotlin pattern match to a crawl
    operator fun <AP: Pattern<IrExpression>,  BP: Pattern<IrExpression>> get(condition: AP, result: BP) =
      customPattern2(condition, result) { it: IrBranch -> Components2(it.condition, it.result) }
  }



  object Call {

    object FunctionRec {
      context (CompileLogger) operator fun <AP : Pattern<List<IrExpression>>> get(x: AP): Pattern1<AP, List<IrExpression>, IrCall> =
        customPattern1(x) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.all { it != null }) {
            Components1(it.simpleValueArgs.requireNoNulls())
          } else {
            null
          }
        }
    }

    object FunctionRec1 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression, BP : Pattern<B>, B:IrExpression> get(x: AP, y: BP): Pattern2<AP, BP, A, B, IrCall> =
        customPattern2(x, y) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
            Components2(reciever, it.simpleValueArgs.first())
          } else {
            null
          }
        }
    }

    object FunctionRec0 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression> get(x: AP): Pattern1<AP, A, IrCall> =
        customPattern1(x) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.size == 0 && it.simpleValueArgs.all { it != null }) {
            Components1(reciever)
          } else {
            null
          }
        }
    }

    // Would like to have a list on the generic L here but that seems to slow down kotlin pattern match to a crawl
    object FunctionMem {
      // Interesting here how we can have just AP/BP and not need the additional parameters A and B
      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP : Pattern<List<IrExpression>>> get(x: AP, y: BP): Pattern2<AP, BP, IrExpression, List<IrExpression>, IrCall> =
        customPattern2(x, y) { it: IrCall ->
          val reciever = it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.all { it != null }) {
            Components2(reciever, it.simpleValueArgs.requireNoNulls())
          } else {
            null
          }
        }
    }

    // Member Function1
    object FunctionMem1 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression, BP : Pattern<B>, B:IrExpression> get(x: AP, y: BP): Pattern2<AP, BP, A, B, IrCall> =
        customPattern2(x, y) { it: IrCall ->
          val reciever = it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
            Components2(reciever, it.simpleValueArgs.first())
          } else {
            null
          }
        }
    }

    object FunctionMem0 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression> get(x: AP): Pattern1<AP, A, IrCall> =
        customPattern1(x) { it: IrCall ->
          val reciever = it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.size == 0) {
            Components1(reciever)
          } else {
            null
          }
        }
    }

    // not a function on an object or class i.e. top-level
    object FunctionUntethered1 {
      context (CompileLogger) operator fun <AP : Pattern<E>, E: IrExpression> get(x: AP): Pattern1<AP, E, IrCall> =
        customPattern1(x) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever == null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
            Components1(it.simpleValueArgs.first())
          } else {
            null
          }
        }
    }

    object FunctionUntethered2 {
      context (CompileLogger) operator fun <AP : Pattern<A>, BP : Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP): Pattern2<AP, BP, A, B, IrCall> =
        customPattern2(x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever == null && it.simpleValueArgs.size == 2 && it.simpleValueArgs.all { it != null }) {
            Components2(it.simpleValueArgs.first(), it.simpleValueArgs.get(1))
          } else {
            null
          }
        }
    }

    object Property {
      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<String>> get(host: AP, name: BP) =
        customPattern2(host, name) { it: IrCall ->
          // if there exists both a dispatch reciever and an extension reciever it's an extension
          // of some class defined inside of some other class, in that case we only care about the extension reciever
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          val isProperty =
            when(it.origin) {
              is IrStatementOrigin.GET_PROPERTY -> true
              is IrStatementOrigin.GET_LOCAL_PROPERTY -> true
              else -> false
            }

          // if there is a reciever and a single value property then this is a property call and we return it, otherwise it is not
          if (isProperty && reciever != null && it.simpleValueArgs.all { it != null }) {
            Components2(reciever, it.symbol.safeName)
          } else {
            null
          }
        }
    }
  }




  object BlockBody {
    // Need to use type-parameter like this or matching (e.g. in SimpleBlockBody) won't type correctly
    operator fun <AP: Pattern<A>, A: List<S>, S: IrStatement> get(statements: AP) =
      customPattern1(statements) { it: IrBlockBody -> Components1(it.statements.toList()) }

    // Single element returning block body
    object ReturnOnly {
      operator fun <AP: Pattern<A>, A: IrExpression> get(statements: AP) =
        customPattern1(statements) { it: IrBlockBody ->
          on(it).match(
            case(BlockBody[List1[Is<IrReturn>()]]).then { (irReturn) -> Components1(irReturn) }
          )
        }
    }

    object Statements {
      operator fun <AP: Pattern<A>, A: List<IrStatement>> get(statements: AP) =
        customPattern1(statements) { it: IrBlockBody ->
          on(it).match(
            case(BlockBody[Is()]).then { statements -> Components1(statements) }
          )
        }
    }
  }

  object Return {
    operator fun <AP: Pattern<A>, A: IrExpression> get(statements: AP) =
      customPattern1(statements) { it: IrReturn -> Components1(it.value) }
  }

  /** I.e. a Lambda! */
  object FunctionExpression {
    operator fun <AP: Pattern<A>, A: IrSimpleFunction> get(body: AP)  /*: Pattern1<AP, A, IrFunctionExpression>*/ =
      // Note, each one of these in here needs to be it:IrExression, not it:IrFunctionExpression or they will never match arbitrary IrExpression instances
      customPattern1(body) { it: IrFunctionExpression ->
        Components1(it.function)
      }

    object withReturnOnlyBlock {
      operator fun <AP: Pattern<A>, A: IrExpression> get(body: AP) =
        customPattern1(body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withReturnOnlyExpression[Is()]]).then { (expr) ->
                on(expr).match(
                  // output the return-body
                  case(Return[Is()]).then { returnExpr -> Components1(returnExpr) }
                )
              }
          )
        }
    }

    object withBlock {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<IrBlockBody>> get(params: AP, body: BP) =
        customPattern2(params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withBlock[Is(), Is()]])
              .then { (params, body) -> Components2(params, body) }
          )
        }
    }

    object withBlockStatements {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<List<IrStatement>>> get(params: AP, body: BP) =
        customPattern2(params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withBlockStatements[Is(), Is()]])
              .then { (params, statements) -> Components2(params, statements) }
          )
        }
    }
  }

  object SimpleFunction {
    operator fun <AP: Pattern<A>, BP: Pattern<B>, A: List<IrValueParameter>, B: IrBlockBody> get(args: AP, body: BP): Pattern2<AP, BP, A, B, IrSimpleFunction> =
      customPattern2(args, body) { it: IrSimpleFunction ->
        it.body?.let { bodyVal ->
          when (val body = it.body) {
            // Ignore context-parameters here
            is IrBlockBody -> Components2(it.simpleValueParams, body)
            else -> parseError("The function ${it.name} body was not a blockBody")
          }

        }
      }

    object withReturnOnlyExpression    {
      operator fun <AP: Pattern<A>, A: IrExpression> get(body: AP) =
        customPattern1(body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[List0(), BlockBody.ReturnOnly[Is()]]).then { _, (b) ->
              Components1(b)
            }
          )
        }
    }

    object withBlock {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<IrBlockBody>> get(params: AP, body: BP) =
        customPattern2(params, body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), Is()]).then { params, blockBody ->
              Components2(params, blockBody)
            }
          )
        }
    }

    object withBlockStatements {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<List<IrStatement>>> get(params: AP, body: BP) =
        customPattern2(params, body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), BlockBody.Statements[Is()]]).then { params, (b) ->
              Components2(params, b)
            }
          )
        }
    }
  }

}