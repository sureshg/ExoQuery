package io.exoquery.util


fun <T> MutableList<T>.withAll(iterator: Iterator<T>): MutableList<T> {
  while (iterator.hasNext()) {
    add(iterator.next())
  }
  return this
}

fun <T> List<T>.interleaveWith(other: List<T>): List<T> {
  tailrec fun rec(l1: Iterator<T>, l2: Iterator<T>, acc: MutableList<T>): List<T> =
    when {
      l1.hasNext() && !l2.hasNext() -> acc.withAll(l1)
      !l1.hasNext() && l2.hasNext() -> acc.withAll(l2)
      else -> rec(l1, l2, acc.withMore(l1.next()).withMore(l2.next()))
    }
  return rec(this.iterator(), other.iterator(), mutableListOf())
}

// Scala
//object Interleave {
//
//  def apply[T](l1: List[T], l2: List[T]): List[T] =
//    interleave(l1, l2, ListBuffer.empty)
//
//  @tailrec
//  private[this] def interleave[T](l1: List[T], l2: List[T], acc: ListBuffer[T]): List[T] =
//    (l1, l2) match {
//      case (Nil, l2)            => (acc ++ l2).toList
//      case (l1, Nil)            => (acc ++ l1).toList
//      case (h1 :: t1, h2 :: t2) => interleave(t1, t2, { acc += h1; acc += h2 })
//    }
//}
