package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object SelectClauseQueryReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "table.emb?.field/join(p.name?.first)/XR" to kt(
      "select { val p = from(Table(Person)); val r = join(Table(Robot)) { { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first } == r.ownerFirstName } }"
    ),
    "table.emb?.field/join(p.name?.first)/SQL" to cr(
      "SELECT p.first, p.last, p.age, r.ownerFirstName, r.model FROM Person p INNER JOIN Robot r ON p.first = r.ownerFirstName"
    ),
    "table.emb?.field/join(p.name?.first ?: alternative)/XR" to kt(
      "select { val p = from(Table(Person)); val r = join(Table(Robot)) { { val tmp1_elvis_lhs = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first }; if (tmp1_elvis_lhs == null) defaultName else tmp1_elvis_lhs } == r.ownerFirstName } }"
    ),
    "table.emb?.field/join(p.name?.first ?: alternative)/SQL" to cr(
      "SELECT p.first, p.last, p.age, r.ownerFirstName, r.model FROM Person p INNER JOIN Robot r ON CASE WHEN p.first IS NULL THEN 'defaultName' ELSE p.first END = r.ownerFirstName"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative)/XR" to kt(
      "select { val p = from(Table(Person)); val r = leftJoin(Table(Robot)) { { val tmp1_elvis_lhs = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first }; if (tmp1_elvis_lhs == null) defaultName else tmp1_elvis_lhs } == r.ownerFirstName } }"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative)/SQL" to cr(
      "SELECT p.first, p.last, r.model AS second FROM Person p LEFT JOIN Robot r ON CASE WHEN p.first IS NULL THEN 'defaultName' ELSE p.first END = r.ownerFirstName"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative) -> r ?: alternative/XR" to kt(
      "select { val p = from(Table(Person)); val r = leftJoin(Table(Robot)) { { val tmp1_elvis_lhs = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first }; if (tmp1_elvis_lhs == null) defaultName else tmp1_elvis_lhs } == r.ownerFirstName } }"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative) -> r ?: alternative/SQL" to cr(
      "SELECT p.first, p.last, r.model AS second FROM Person p LEFT JOIN Robot r ON CASE WHEN p.first IS NULL THEN 'defaultName' ELSE p.first END = r.ownerFirstName"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative) -> p ?: alternative/XR" to kt(
      "select { val r = from(Table(Robot)); val p = leftJoin(Table(Person)) { r.ownerFirstName == { val tmp1_elvis_lhs = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first }; if (tmp1_elvis_lhs == null) defaultName else tmp1_elvis_lhs } } }"
    ),
    "table.emb?.field/joinLeft(p.name?.first ?: alternative) -> p ?: alternative/SQL" to cr(
      "SELECT CASE WHEN p.first IS NULL THEN 'foo' ELSE p.first END AS first, r.model AS second FROM Robot r LEFT JOIN Person p ON r.ownerFirstName = CASE WHEN p.first IS NULL THEN 'defaultName' ELSE p.first END"
    ),
    "table.emb?.field/join + aggregation/XR" to kt(
      "select { val p = from(Table(Person)); val r = join(Table(Robot)) { { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first } == r.ownerFirstName }; groupBy(TupleA2(first = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first }, second = r.model)) }"
    ),
    "table.emb?.field/join + aggregation/SQL" to cr(
      "SELECT p.first, r.model AS second FROM Person p INNER JOIN Robot r ON p.first = r.ownerFirstName GROUP BY p.first, r.model"
    ),
    "table.emb?.field/sortBy/XR" to kt(
      "select { val p = from(Table(Person)); sortBy(TupleOrdering)(TupleA2(first = p.age, second = { val tmp0_safe_receiver = p.name; if (tmp0_safe_receiver == null) null else tmp0_safe_receiver.first })) }"
    ),
    "table.emb?.field/sortBy/SQL" to cr(
      "SELECT p.first, p.last, p.age AS second FROM Person p ORDER BY p.age ASC, p.first DESC"
    ),
  )
}
