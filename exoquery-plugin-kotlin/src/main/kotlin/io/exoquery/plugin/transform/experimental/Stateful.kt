package io.exoquery.plugin.transform.experimental

import io.exoquery.select.ProgramBuilder
import io.exoquery.select.program


/*
for {
  x <- state.get()
  _ <- state.set(x + 1)
  x1 <- state.get()
} yield (x1)


state.get().flatMap { x ->
  state.set(x + 1).flatMap {
    state.get().map { x1 ->
      x1
    }
  }
}


val x =



 */

sealed interface Stateful<in S, out T> {
  data class Pure<in S, out T>(val value: T): Stateful<S, T>
  data class FlatMap<in S, in T, out R>(val head: Stateful<S, @UnsafeVariance T>, val next: (T) -> Stateful<S, R>): Stateful<S, R>
  data class Set<S>(val value: S): Stateful<S, Unit>
  class Get<S>(): Stateful<S, S>
  data class Log<S>(val msg: String): Stateful<S, Unit>

  fun <R> flatMap(f: (T) -> Stateful<@UnsafeVariance S, R>): Stateful<S, R> = Stateful.FlatMap(this, f)
  fun <R> map(f: (T) -> R): Stateful<S, R> = Stateful.FlatMap(this, { t: T -> Stateful.build<S>().lift(f(t)) })

  fun set(value: @UnsafeVariance S): Stateful<S, Unit> = Set(value)
  fun get(): Stateful<S, @UnsafeVariance S> = Get()

  fun run(initialValue: @UnsafeVariance S) =
    Interpreter(initialValue).run(this)

  class Builder<S> {
    fun <T> lift(t: T) = Pure<S, T>(t)
  }

  companion object {
    fun <S> build() = Builder<S>()
  }
}

data class Interpreter<S>(val initialState: S) {
  var state = initialState

  @Suppress("CANNOT_CHECK_FOR_ERASED")
  fun <T> run(stateful: Stateful<S, T>): T {
    with(stateful) {
      return when {
        // FlatMap(FlatMap(x, f0), f) ->
        // this is Stateful.FlatMap<S, Any, T> && head is Stateful.FlatMap<*, *, *> ->
        //   TODO()
        // FlatMap(x, f) ->
        this is Stateful.FlatMap<S, Any, T> -> {
          val headValue = run(head)
          val nextRun = next(headValue)
          run(nextRun)
        }
        this is Stateful.Get<T> ->
          // In this case S has to be T because Get<X> is Stateful<X, X>
          state as T
        this is Stateful.Log -> {
          println(msg)
          Unit as T // in this case T has to be unit since that is the output of msg
        }
        this is Stateful.Pure ->
          value
        this is Stateful.Set<S> -> {
          state = value
          Unit as T  // in this case T has to be unit since that is the output of Set
        }
        else -> TODO("Illegal state")
      }
    }
  }
}

fun stuff() {
  val state = Stateful.build<Int>().lift(123)
  state.set(2).flatMap {
    state.get().flatMap { x ->
      state.set(x + 1).flatMap {
        state.get().map { x1 ->
          x1
        }
      }
    }
  }
}

//fun stuff2() {
//  val state = Stateful.lift(123)
//  state.getAndFlatMap { x ->
//    state.setAndFlatMap(x + 1) {
//      state.get().map { x1 ->
//        x1
//      }
//    }
//  }
//}

/*
stateful {
  val x = get()
  val y = set(x + 1)
  val x1 = get()
  (x + y)
}



 */

/*
suspend fun funA(): A =
    //val (result, stateA) = funB()
    // funB().flatMap { result -> dostuff(result, getStateA()) }
    perform { mapping ->

    }


  suspend fun funB(): A =
    TODO()

  suspend fun funC(): A = TODO()
//    perform { a ->
//
//    }
 */

class StatefulDsl<A>(): ProgramBuilder<Stateful<A, A>, A>({ t -> Stateful.build<A>().lift(t)}, { e -> throw e}) {
  suspend fun log(str: String): Unit =
    performUnit { f ->
      Stateful.FlatMap(Stateful.Log<A>(str), { f() /*Don't need/want the element that will return from this */ })
    }

  suspend fun get(): A =
    // f: A -> Stateful<A> is the NEXT step i.e. it is suspended
    perform { f ->
      Stateful.FlatMap(Stateful.Get(), f)
    }

  suspend fun set(newValue: A): Unit =
    perform { f ->
      Stateful.FlatMap(Stateful.Set(newValue), f)
    }

  suspend fun bind(stateful: Stateful<A, A>): A =
    perform { f ->
      Stateful.FlatMap(stateful, f)
    }
}

public fun <T> stateful(block: suspend StatefulDsl<T>.() -> T): Stateful<T, T> =
  program<Stateful<T, T>, T, StatefulDsl<T>>(
    machine = StatefulDsl<T>(),
    f = block
  ) as Stateful<T, T>



fun main() {

  val output =
    stateful<String> {
      val aa = set("A")
      println(aa)

      //log("Al")
      val x = get()
      println(x)
      set(x + "B")
      val y = get()

      val s1 = stateful<String> {
        log("Bl")
        set(y + "C")
        val x0 = get()
        log("Cl")
        x0
      }

      bind(s1)

      val c = get() // bind(s1)
      set(c + "D")
      //log("Dl")
      val x1 = get()
      //log("El")
      x1
    }

  println(output.run("-"))
  //println(output.printLog())
}