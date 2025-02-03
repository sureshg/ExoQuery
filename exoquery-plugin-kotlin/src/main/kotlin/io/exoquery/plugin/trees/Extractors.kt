@file:Suppress("NAME_SHADOWING", "NAME_SHADOWING")

package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.parseError
import io.exoquery.plugin.*
import io.exoquery.plugin.transform.ReceiverCaller
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.superTypes

fun <T> List0() = Is(listOf<T>())

val IrCall.simpleValueArgsCount get() = this.valueArgumentsCount  //- this.contextReceiversCount
val IrCall.simpleValueArgs get() = this.valueArguments
  //if (this.contextReceiversCount > 0)
  //  this.valueArguments.drop(this.contextReceiversCount)
  //else
  //  this.valueArguments

val IrFunction.simpleValueParamsCount get() = this.valueParameters.size - this.contextReceiverParametersCount
val IrFunction.simpleValueParams get() =
  if (this.contextReceiverParametersCount > 0)
    this.valueParameters.drop(this.contextReceiverParametersCount)
  else
    this.valueParameters

object List1 {
  operator fun <AP: Pattern<A>, A> get(elem1: AP) =
    customPattern1("List1", elem1) { it: List<A> -> if (it.size == 1) Components1(it.first()) else null }
}

object List2 {
  operator fun <AP: Pattern<T>, BP: Pattern<T>, T> get(elem1: AP, elem2: BP) =
    customPattern2("List2", elem1, elem2) { it: List<T> ->
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
      customPattern1("Ir.Expression", components) { it: IrExpression ->
        Components1(it)
      }
  }

  object StringConcatenation {
    // Can't do just get(components: Pattern<List<IrExpression>) need to do:
    // <AP: Pattern<List<IrExpression>>> get(components: AP) or it doesn't work because
    // it needs to have a concrete pattern instance
    context(CompileLogger) operator fun <AP: Pattern<List<IrExpression>>> get(components: AP) =
      customPattern1("StringConcatenation", components) { it: IrExpression ->
        if (it is IrStringConcatenation) {
          Components1(it.arguments)
        } else {
          null
        }
      }
  }

  object Expr {
    class ClassOf<R>(val className: String): Pattern0<IrExpression>(Typed<IrExpression>()) {
      override fun matches(r: ProductClass<IrExpression>): Boolean =
        Typed<IrExpression>().typecheck(r.productClassValueUntyped) &&
          r.productClassValue.type.let { tpe ->
            className == tpe.classFqName.toString() || tpe.superTypes().any { it.classFqName.toString() == className }
          }

      companion object {
        inline operator fun <reified T> invoke() =
          ClassOf<T>(T::class.qualifiedNameForce)
      }
    }
  }

  object Type {

//    object Nullable {
//      context(CompileLogger) operator fun <AP: Pattern<IrType>> get(realType: AP) =
//        customPattern1("Type.Nullable", realType) { it: IrType ->
//
//          if (cls != null && simpleTypeArgs.size == 1 && it.isClass<List<*>>()) {
//            Components1(simpleTypeArgs.first())
//          }
//          else null
//        }
//    }

    object NullableOf {
      context(CompileLogger) operator fun <AP: Pattern<IrType>> get(realType: AP) =
        customPattern1("Type.NullableOf", realType) { it: IrType ->
          if (it.isNullable()) {
            Components1(it.makeNotNull())
          }
          else null
        }
    }

    object KotlinList {
      context(CompileLogger) operator fun <AP: Pattern<IrType>> get(realType: AP) =
        customPattern1("Type.KotlinList", realType) { it: IrType ->
          val cls = it.classOrNull
          val simpleTypeArgs = it.simpleTypeArgs
          if (cls != null && simpleTypeArgs.size == 1 && it.isClass<List<*>>()) {
            Components1(simpleTypeArgs.first())
          }
          else null
        }
    }

    object DataClass {
      context(CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<List<Pair<String, IrType>>>> get(name: AP, fields: BP) =
        customPattern2("Type.DataClass", name, fields) { it: IrType ->
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

    class ClassOfType<R>(val className: String): Pattern0<IrType>(Typed<IrType>()) {
      override fun matches(r: ProductClass<IrType>): Boolean =
        Typed<IrType>().typecheck(r.productClassValueUntyped) &&
          r.productClassValue.let { tpe ->
            className == tpe.classFqName.toString() || tpe.superTypes().any { it.classFqName.toString() == className }
          }

      companion object {
        inline operator fun <reified T> invoke() =
          ClassOfType<T>(T::class.qualifiedNameForce)
      }
    }

    object Generic {
      context(CompileLogger) operator fun get(type: Pattern0<IrType>) =
        customPattern1("Ir.Call.Generic", type) { it: IrType ->
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
        customPattern1("Ir.Call.Value", type) { it: IrType ->
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
      customPattern2("Ir.Block", statements, ret) { it: IrBlock ->
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
    context (CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<B>, B: IrExpression> get(name: AP, rhs: BP) =
      customPattern2("Ir.Variable", name, rhs) { it: IrVariable ->
        it.initializer?.let { init -> Components2(it.name.asString(), init) }
      }
  }

  object Field {
    context (CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<IrExpression>> get(name: AP, rhs: BP) =
      customPattern2("Ir.Field", name, rhs) { it: IrField ->
        it.initializer?.let { initExprBody -> Components2(it.name.asString(), initExprBody.expression) }
      }
  }

  object GetValue {
    context (CompileLogger) operator fun get(value: Pattern0<IrSymbol>) =
      customPattern1("Ir.GetValue", value) { it: IrGetValue ->
        Components1(it.symbol)
      }
  }

  object GetField {
    context (CompileLogger) operator fun get(value: Pattern0<IrSymbol>) =
      customPattern1("Ir.GetField", value) { it: IrGetField ->
        Components1(it.symbol)
      }
  }

  object GetEnumValue {
    operator fun get(value: Pattern0<IrSymbol>) =
      customPattern1("Ir.GetEnumValue", value) { it: IrGetEnumValue ->
        Components1(it.symbol)
      }
  }


  object ConstString {
    operator fun get(value: Pattern0<String>) =
      customPattern1("Ir.Const.String", value) { it: IrConst ->
        if (it.kind == IrConstKind.String)
          Components1((Const.Value.fromIrConst(it) as Ir.Const.Value.String).value)
        else
          null
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

      companion object {
        fun fromIrConst(it: IrConst): Value =
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
      customPattern1("Ir.Const", value) { it: IrConst ->
        Components1(Value.fromIrConst(it))
      }
  }

  object When {
    context (CompileLogger) operator fun <AP: Pattern<L>, L: List<A>, A: IrBranch> get(value: AP) =
      customPattern1("Ir.When", value) { it: IrWhen ->
        Components1(it.branches)
      }
  }

  object Branch {
    // would to have a genrics A and B here but that seems to slow down kotlin pattern match to a crawl
    operator fun <AP: Pattern<IrExpression>,  BP: Pattern<IrExpression>> get(condition: AP, result: BP) =
      customPattern2("Ir.Branch", condition, result) { it: IrBranch ->
        Components2(it.condition, it.result)
      }
  }



  object Call {

    context (CompileLogger) operator fun <AP : Pattern<IrCall>> get(x: AP): Pattern1<AP, IrCall, IrCall> =
      customPattern1("Ir.Call", x) { it: IrCall ->
        if (it.simpleValueArgs.all { it != null }) {
          Components1(it)
        } else {
          null
        }
      }

    // TODO get rid of this in favor of FunctionMem
    object FunctionRec {
      context (CompileLogger) operator fun <AP : Pattern<List<IrExpression>>> get(x: AP): Pattern1<AP, List<IrExpression>, IrCall> =
        customPattern1("Ir.Call.FunctionRec", x) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.all { it != null }) {
            Components1(it.simpleValueArgs.requireNoNulls())
          } else {
            null
          }
        }
    }

    // TODO get rid of this in favor of FunctionMem1
    object FunctionRec1 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression, BP : Pattern<B>, B:IrExpression> get(x: AP, y: BP): Pattern2<AP, BP, A, B, IrCall> =
        customPattern2("Ir.Call.FunctionRec1", x, y) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
            Components2(reciever, it.simpleValueArgs.first())
          } else {
            null
          }
        }
    }

    // TODO get rid of this in favor of FunctionMem1
    object FunctionRec0 {
      // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
      context (CompileLogger) operator fun <AP : Pattern<A>, A:IrExpression> get(x: AP): Pattern1<AP, A, IrCall> =
        customPattern1("Ir.Call.FunctionRec0", x) { it: IrCall ->
          val reciever = it.dispatchReceiver ?: it.extensionReceiver
          if (reciever != null && it.simpleValueArgs.size == 0 && it.simpleValueArgs.all { it != null }) {
            Components1(reciever)
          } else {
            null
          }
        }
    }

    // Would like to have a list on the generic L here but that seems to slow down kotlin pattern match to a crawl
    object FunctionMemN {
      object Caller {
        // Interesting here how we can have just AP/BP and not need the additional parameters A and B
        context (CompileLogger) operator fun <AP : Pattern<ReceiverCaller>, BP : Pattern<List<IrExpression>>> get(x: AP, y: BP): Pattern2<AP, BP, ReceiverCaller, List<IrExpression>, IrCall> =
          customPattern2("Ir.Call.FunctionMem", x, y) { it: IrCall ->
            val reciever = it.caller()
            if (reciever != null && it.simpleValueArgs.all { it != null }) {
              Components2(reciever, it.simpleValueArgs.requireNoNulls())
            } else {
              null
            }
          }
      }

      context (CompileLogger) operator fun <AP : Pattern<IrExpression>, MP : Pattern<String>, BP : Pattern<List<IrExpression>>> get(x: AP, m: MP, y: BP): Pattern2<AP, BP, IrExpression, List<IrExpression>, IrCall> =
        customPattern2("Ir.Call.FunctionMemN", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.all { it != null } && m.matchesAny(it.symbol.safeName)) {
            Components2(reciever, it.simpleValueArgs)
          } else {
            null
          }
        }
    }

    object FunctionMem2 {
      context (CompileLogger) operator fun <AP : Pattern<IrExpression>, MP : Pattern<String>, BP : Pattern<Pair<IrExpression, IrExpression>>> get(x: AP, m: MP, y: BP): Pattern2<AP, BP, IrExpression, Pair<IrExpression, IrExpression>, IrCall> =
        customPattern2("Ir.Call.FunctionMemN", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.size == 2 && it.simpleValueArgs.all { it != null } && m.matchesAny(it.symbol.safeName)) {
            Components2(reciever, it.simpleValueArgs[0]!! to it.simpleValueArgs[1]!!)
          } else {
            null
          }
        }
    }

    object FunctionMemAllowNulls {
      // Interesting here how we can have just AP/BP and not need the additional parameters A and B
      context (CompileLogger) operator fun <AP: Pattern<ReceiverCaller>, BP : Pattern<List<IrExpression>>> get(x: AP, y: BP): Pattern2<AP, BP, ReceiverCaller, List<IrExpression?>, IrCall> =
        customPattern2("Ir.Call.FunctionMemAllowNulls", x, y) { it: IrCall ->
          val reciever = it.caller()
          if (reciever != null) {
            Components2(reciever, it.simpleValueArgs)
          } else {
            null
          }
        }
    }

    // Member Function1
    object FunctionMem1 {

      context (CompileLogger) operator fun <AP : Pattern<IrExpression>, MP : Pattern<String>, BP : Pattern<IrExpression>> get(x: AP, m: MP, y: BP): Pattern2<AP, BP, IrExpression, IrExpression, IrCall> =
        customPattern2("Ir.Call.FunctionMem1", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null } && m.matchesAny(it.symbol.safeName)) {
            Components2(reciever, it.simpleValueArgs.first())
          } else {
            null
          }
        }

      object WithCaller {
        /**
         * This is an interesting pattern because we can match on a middle-component inside of the logic of the customPattern without actually needing to
         * pass it deep into the Then2 sets of functions. We can match the pattern in the business logic below and then return the components.
         * Since it is unlikely that we will want some kind of structure based decompositon of the name of the function (e.g. we won't want some
         * kind of Pattern2 that decomposes the string into multiple pieces). Therefore we can treat the middle-parameter as a simple Pattern argument
         * that has no effect on other side of the `case` expressions.
         */
        context (CompileLogger) operator fun <AP : Pattern<ReceiverCaller>, MP : Pattern<String>, BP : Pattern<IrExpression>> get(x: AP, m: MP, y: BP): Pattern2<AP, BP, ReceiverCaller, IrExpression, IrCall> =
          customPattern2("Ir.Call.FunctionMem1.WithCaller", x, y) { it: IrCall ->
            val reciever = it.caller()
            if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null } && m.matchesAny(it.symbol.safeName)) {
              Components2(reciever, it.simpleValueArgs.first())
            } else {
              null
            }
          }

        /**
         * This is a similar pattern to the one above but there is one more insight. Namely that so long as you don't care about structurally decomposing the middle-parameter
         * (i.e. since it's a string that  we assume we don't care about breaking down into component parts ...since it's just being treated as a function-name match)
         * then we can not only match on the the middle paramter in the customPattern logic, but we can also return the string itself on the other side of the `case`  logic
         * using the Pattern2M family of functions. This gives us both the ability to match on the middle parameter and return it as a component, so long as
         * we don't care about passing it to further Pattern___ functions that would deconstruct them (because they would not turn into nested Component(T) instances
         * on the other side of the `case` function.
         */
        object Named {
          // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
          context (CompileLogger) operator fun <AP : Pattern<ReceiverCaller>, MP : Pattern<String>, BP : Pattern<IrExpression>> get(x: AP, m: MP, y: BP): Pattern2M<AP, String, BP, ReceiverCaller, IrExpression, IrCall> =
            customPattern2M("Ir.Call.FunctionMem1.WithCaller.Named", x, y) { it: IrCall ->
              val reciever = it.caller()
              if (reciever != null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null } && m.matchesAny(it.symbol.safeName)) {
                Components2M(reciever, it.symbol.safeName, it.simpleValueArgs.first())
              } else {
                null
              }
            }
        }
      }
    }

    object FunctionMem0 {
      object WithCaller {
        // context (CompileLogger) operator fun <AP: Pattern<A>, BP: Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP) =
        context(CompileLogger) operator fun <AP : Pattern<A>, A : ReceiverCaller> get(x: AP): Pattern1<AP, A, IrCall> =
          customPattern1("Ir.Call.FunctionMem0.WithCaller", x) { it: IrCall ->
            val reciever = it.caller()
            if (reciever != null && it.simpleValueArgs.size == 0) {
              Components1(reciever)
            } else {
              null
            }
          }
      }

      context(CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<String>> get(x: AP, y: BP): Pattern2<AP, BP, IrExpression, String, IrCall> =
        customPattern2("Ir.Call.FunctionMem0", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever != null && it.simpleValueArgs.size == 0) {
            Components2(reciever, it.symbol.safeName)
          } else {
            null
          }
        }
    }

    object FunctionUntethered0 {
      context (CompileLogger) operator fun <AP: Pattern<String>> get(x: AP) =
        customPattern1("Ir.Call.FunctionUntethered0", x) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever == null && it.simpleValueArgs.size == 0) {
            Components1(it.symbol.fullName)
          } else {
            null
          }
        }
    }

    object FunctionMemVararg {
      context (CompileLogger) operator fun <AP : Pattern<IrExpression>, MP : Pattern<String>, TP: Pattern<IrType>, BP : Pattern<List<IrExpression>>> get(x: AP, m: MP, yTpe: TP, y: BP): Pattern2<AP, BP, IrExpression, List<IrExpression>, IrCall> =
        customPattern2("Ir.Call.FunctionMemVararg", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever != null
            && it.simpleValueArgs.size == 1
            && it.simpleValueArgs.first() != null
            && (it.simpleValueArgs.first() as? IrVararg)?.let { varg -> varg.elements.all { it is IrExpression } && yTpe.matchesAny(varg.varargElementType) } ?: false
            && m.matchesAny(it.symbol.safeName))
          {
            val varargElem = it.simpleValueArgs.first() as IrVararg
            Components2(reciever, varargElem.elements.map { it as IrExpression }.toList())
          } else {
            null
          }
        }
    }

    // not a function on an object or class i.e. top-level
    object FunctionUntethered1 {
      object Arg {
        /*context (CompileLogger) operator fun <AP: Pattern<IrExpression>> get(x: AP) =
          customPattern1(x) { it: IrCall ->
            val reciever = it.extensionReceiver ?: it.dispatchReceiver
            if (reciever == null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
              Components1(it.simpleValueArgs.first())
            } else {
              null
            }
          }*/

        context (CompileLogger) operator fun <AP : Pattern<E>, E: IrExpression> get(x: AP): Pattern1<AP, E, IrCall> =
          customPattern1("Ir.Call.FunctionUntethered1.Arg", x) { it: IrCall ->
            val reciever = it.extensionReceiver ?: it.dispatchReceiver
            if (reciever == null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
              Components1(it.simpleValueArgs.first())
            } else {
              null
            }
          }
      }

      context (CompileLogger) operator fun <AP : Pattern<String>, BP: Pattern<IrExpression>> get(x: AP, y: BP): Pattern2<AP, BP, String, IrExpression, IrCall> =
        customPattern2("Ir.Call.FunctionUntethered1", x, y) { it: IrCall ->
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          if (reciever == null && it.simpleValueArgs.size == 1 && it.simpleValueArgs.all { it != null }) {
            Components2(it.symbol.fullName, it.simpleValueArgs.first() ?: error("Expected non-null value"))
          } else {
            null
          }
        }
    }

    object FunctionUntethered2 {
      context (CompileLogger) operator fun <AP : Pattern<A>, BP : Pattern<B>, A: IrExpression, B: IrExpression> get(x: AP, y: BP): Pattern2<AP, BP, A, B, IrCall> =
        customPattern2("Ir.Call.FunctionUntethered2", x, y) { it: IrCall ->
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
        customPattern2("Ir.Call.Property", host, name) { it: IrCall ->
          // if there exists both a dispatch reciever and an extension reciever it's an extension
          // of some class defined inside of some other class, in that case we only care about the extension reciever
          val reciever = it.extensionReceiver ?: it.dispatchReceiver
          val isProperty =
            when(it.origin) {
              IrStatementOrigin.GET_PROPERTY -> true
              IrStatementOrigin.GET_LOCAL_PROPERTY -> true
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
      customPattern1("BlockBody", statements) { it: IrBlockBody ->
        Components1(it.statements.toList())
      }

    // Single element returning block body
    object ReturnOnly {
      operator fun <AP: Pattern<IrExpression>> get(statements: AP) =
        customPattern1("BlockBody.ReturnOnly", statements) { it: IrBlockBody ->
          on(it).match(
            case(BlockBody[List1[Is<IrReturn>()]]).then { (irReturn) -> Components1(irReturn.value) }
          )
        }
    }

    object StatementsWithReturn {
      operator fun <AP: Pattern<List<IrStatement>>, BP: Pattern<IrExpression>> get(statements: AP, ret: BP) =
        customPattern2("BlockBody.StatementsWithReturn", statements, ret) { it: IrBlockBody ->
          if (it.statements.size > 0 && it.statements.last() is IrReturn) {
            Components2(it.statements.dropLast(1), (it.statements.last() as IrReturn).value)
          } else {
            null
          }
        }
    }

    object Return {
      operator fun <AP: Pattern<A>, A: IrExpression> get(statements: AP) =
        customPattern1("BlockBody.Return", statements) { it: IrBlockBody ->
          on(it).match(
            case(BlockBody[Is()]).then { statements ->
              statements.find { it is IrReturn }?.let { Components1((it as IrReturn).value) }
            }
          )
        }
    }

    object Statements {
      operator fun <AP: Pattern<A>, A: List<IrStatement>> get(statements: AP) =
        customPattern1("BlockBody.Statements", statements) { it: IrBlockBody ->
          on(it).match(
            case(BlockBody[Is()]).then { statements -> Components1(statements) }
          )
        }
    }
  }

  object Return {
    operator fun <AP: Pattern<A>, A: IrExpression> get(statements: AP) =
      customPattern1("Return", statements) { it: IrReturn ->
        Components1(it.value)
      }
  }

  object ReturnBlockInto {
    operator fun <AP: Pattern<A>, A: IrExpression> get(statements: AP) =
      customPattern1("ReturnBlockInto", statements) { it: IrBlockBody ->
        it.match(
          case(Ir.BlockBody[List1[Ir.Return[Is()]]])
            .then { (irReturn) ->
              Components1(irReturn.value)
            }
        )
      }
  }

  /** I.e. a Lambda! */
  object FunctionExpression {
    operator fun <AP: Pattern<A>, A: IrSimpleFunction> get(body: AP)  /*: Pattern1<AP, A, IrFunctionExpression>*/ =
      // Note, each one of these in here needs to be it:IrExression, not it:IrFunctionExpression or they will never match arbitrary IrExpression instances
      customPattern1("FunctionExpression", body) { it: IrFunctionExpression ->
        Components1(it.function)
      }

    object withReturnOnlyBlock {
      operator fun <AP: Pattern<A>, A: IrExpression> get(body: AP) =
        customPattern1("FunctionExpression.withReturnOnlyBlock", body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withReturnOnlyExpression[Is()]]).then { (expr) ->
              Components1(expr)
            }
          )
        }
    }

    object withBlock {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<IrBlockBody>> get(params: AP, body: BP) =
        customPattern2("FunctionExpression.withBlock", params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withBlock[Is(), Is()]])
              .then { (params, body) -> Components2(params, body) }
          )
        }
    }

    object withBlockStatements {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<List<IrStatement>>> get(params: AP, body: BP) =
        customPattern2("FunctionExpression.withBlockStatements", params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withBlockStatements[Is(), Is()]])
              .then { (params, statements) -> Components2(params, statements) }
          )
        }
    }

    object withBlockStatementsAndReturn {
      data class Output(val statements: List<IrStatement>, val ret: IrExpression)

      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<Output>> get(params: AP, body: BP) =
        customPattern2("FunctionExpression.withBlockStatementsAndReturn", params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression[SimpleFunction.withBlockStatements[Is(), Is()]])
              .thenIf { (params, statements) -> statements.lastOrNull() is IrReturn }
              .then { (params, statements) -> Components2(params, Output(statements.dropLast(1), (statements.last() as IrReturn).value)) }
          )
        }
    }
  }

  object FunctionExpression1 {
    operator fun <AP: Pattern<A>, A: IrSimpleFunction> get(body: AP)  /*: Pattern1<AP, A, IrFunctionExpression>*/ =
      // Note, each one of these in here needs to be it:IrExression, not it:IrFunctionExpression or they will never match arbitrary IrExpression instances
      customPattern1("FunctionExpression", body) { it: IrFunctionExpression ->
        Components1(it.function)
      }

    object withBlock {
      operator fun <AP: Pattern<IrValueParameter>, BP: Pattern<IrBlockBody>> get(params: AP, body: BP) =
        customPattern2("FunctionExpression.withBlock", params, body) { it: IrFunctionExpression ->
          on(it).match(
            case(FunctionExpression1[SimpleFunction.withBlock[Is(), Is()]])
              .thenIf { (params, _) -> params.size == 1 }
              .then { (params, body) -> Components2(params.first(), body) }
          )
        }
    }
  }

  object SimpleFunction {
    operator fun <AP: Pattern<A>, BP: Pattern<B>, A: List<IrValueParameter>, B: IrBlockBody> get(args: AP, body: BP): Pattern2<AP, BP, A, B, IrSimpleFunction> =
      customPattern2("Ir.SimpleFunction", args, body) { it: IrSimpleFunction ->
        it.body?.let { bodyVal ->
          when (val body = it.body) {
            // Ignore context-parameters here
            is IrBlockBody -> Components2(it.simpleValueParams, body)
            else -> parseError("The function ${it.name} body was not a blockBody")
          }

        }
      }

    object withReturnOnlyExpression {
      operator fun <AP: Pattern<A>, A: IrExpression> get(body: AP) =
        customPattern1("Ir.SimpleFunction.withReturnOnlyExpression", body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), BlockBody.ReturnOnly[Is()]]).then { _, (b) ->
              Components1(b)
            }
          )
        }
    }

    object withReturnExpression {
      operator fun <AP: Pattern<A>, A: IrExpression> get(body: AP) =
        customPattern1("Ir.SimpleFunction.withReturnExpression", body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), BlockBody.Return[Is()]]).then { _, (b) ->
              Components1(b)
            }
          )
        }
    }

    object withBlock {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<IrBlockBody>> get(params: AP, body: BP) =
        customPattern2("Ir.SimpleFunction.withBlock", params, body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), Is()]).then { params, blockBody ->
              Components2(params, blockBody)
            }
          )
        }
    }

    object withBlockStatements {
      operator fun <AP: Pattern<List<IrValueParameter>>, BP: Pattern<List<IrStatement>>> get(params: AP, body: BP) =
        customPattern2("Ir.SimpleFunction.withBlockStatements", params, body) { it: IrSimpleFunction ->
          on(it).match(
            case(SimpleFunction[Is(), BlockBody.Statements[Is()]]).then { params, (b) ->
              Components2(params, b)
            }
          )
        }
    }
  }

}
