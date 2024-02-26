package io.exoquery.sql

fun main() {
  val x: ProductAggregationToken = ProductAggregationToken.Star
  when (x) {
    is ProductAggregationToken.Star -> println("Star")
    ProductAggregationToken.VariableDotStar -> println("VariableDotStar")
  }
}