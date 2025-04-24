# ExoQuery
Language-Integrated Querying for Kotlin Mutiplatform

## Introduction

### *Question: Why does querying a database need to be any harder than traversing an array?*

Let's say something like:
```kotlin
data class Person(val name: String, val age: Int)

Table<Person>().map { p -> p.name }
```
Naturally we're pretty sure it should look something like:
```sql
SELECT name FROM Person
```

### *...but wait, don't databases have complicated things like joins, case-statements, and subqueries?*

Let's take some data:
```kotlin
data class Person(val name: String, val age: Int, val companyId: Int)
data class Address(val city: String, val personId: Int)
data class Company(val name: String, val id: Int)
val people: SqlQuery<Person> = capture { Table<Person>() }
val addresses: SqlQuery<Address> = capture { Table<Address>() }
val companies: SqlQuery<Company> = capture { Table<Company>() }
```

Here is a query with some Joins:
```kotlin
capture.select {
  val p = from(people)
  val a = join(addresses) { a -> a.personId == p.id }
  Data(p.name, a.city)
}
//> SELECT p.name, a.city FROM Person p JOIN Address a ON a.personId = p.id
```

Let's add some case-statements:
```kotlin
capture.select {
  val p = from(people)
  val a = join(addresses) { a -> a.personId == p.id }
  Data(p.name, a.city, if (p.age > 18) 'adult' else 'minor')
}
//> SELECT p.name, a.city, CASE WHEN p.age > 18 THEN 'adult' ELSE 'minor' END FROM Person p JOIN Address a ON a.personId = p.id
```

Now let's try a subquery:
```kotlin
capture.select {
  val p = from(
    select {
      val c = from(companies)
      val p = join(people) { p -> p.companyId == c.id }
      p
    }
  )
  val a = join(addresses) { a -> a.personId == p.id }
  Data(p.name, a.city)
}
//> SELECT p.name, a.city FROM (
//   SELECT p.name, p.age, p.companyId FROM Person p JOIN companies c ON c.id = p.companyId
//  ) p JOIN Address a ON a.personId = p.id
```

The `select` and `catpure.select` functions return a `SqlQuery<T>` object, just like `Table<T>` does.
ExoQuery is well-typed, functionally composeable, and deeply respects functional-programming
principles to the core.

### *...but wait, how can you use `==`, or regular `if` or regular case classes in a DSL?*

By using the `capture` function to deliniate relevant code snippets and a compiler-plugin to
transform them, I can synthesize a SQL query the second your code is compiled in most cases.

You can even see it in the build output in a file:

TODO Video

### So I can just use normal Kotlin to write Queries?

That's right! You can use regular Kotlin constructs that you know and love in order to write SQL code including:

TODO double-check these

- Elvis operators
  ```kotlin
  people.map { p ->
    p.name ?: "default"
  }
  //> SELECT CASE WHEN p.name IS NULL THEN 'default' ELSE p.name END FROM Person p
  ```
- Question marks and nullary .let statements
  ```kotlin
  people.map { p ->
      p.name?.let { free("mySuperSpecialUDF($it)").asPure<String>() } ?: "default"
  }
  //> SELECT CASE WHEN p.name IS NULL THEN 'default' ELSE mySuperSpecialUDF(p.name) END FROM Person p
  ```
- If and When
  ```kotlin
  people.map { p ->
    when {
      p.age >= 18 -> "adult"
      p.age < 18 && p.age > 10 -> "minor"
      else -> "child"
    } 
  }
  //> SELECT CASE WHEN p.age >= 18 THEN 'adult' WHEN p.age < 18 AND p.age > 10 THEN 'minor' ELSE 'child' END FROM Person p
  ```
- Simple arithmetic, simple functions on datatypes
  ```kotlin
  @CapturedFunction
  fun peRatioWeighted(stock: Stock, weight: Double): Double = catpure.expression {
    (stock.price / stock.earnings) * weight
  }
  capture {
    Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap/totalWeight) } 
  }
  //> SELECT (s.price / s.earnings) * s.marketCap / totalWeight FROM Stock s
  ```
- Pairs and Tuples
  ```kotlin
  val query: SqlQuery<Pair<String, String>> = capture {
    Table<Person>().map { p -> p.name to p.age }  
  }
  ```
  You can use pairs and tuples with the whole row too! See below for examples.

### What is this `capture` thing?

The `capture` function is how ExoQuery knows whot code to capture inside of the Kotlin
compiler plugin in order to be transformed into SQL. This is how ExoQuery is able to
use regular Kotlin constructs like `if`, `when`, and `let` in the DSL. There are a few
different kinds of things that you can capture:

- A regular table-expression
  ```kotlin
  val people: SqlQuery<Person> = capture { Table<Person>() }
  //> SELECT x.name, x.age FROM Person x
  val joes: SqlQuery<Person> = capture { Table<Person>().where { p -> p.name == "Joe" } }
  //> SELECT p.name, p.age FROM Person p WHERE p.name = 'Joe'
  // (Notice how ExoQuery knows that 'p' should be the variable in this case?)
  ```
- A table-select function. This is a special syntax for doing joins, groupBy, and other complex expressions.
  ```kotlin
  val people: SqlQuery<Pair<Person, Address>> = capture.select {
    val p = from(people)
    val a = join(addresses) { a -> a.personId == p.id }
    p to a
  }
  //> SELECT p.id, p.name, p.age, a.personId, a.street, a.zip  FROM Person p JOIN Address a ON a.personId = p.id 
  ```
  (This is actually just a shortening of the `capture { select { ... } }` expression.)

- An arbitrary code snippet
  ```kotlin
  val nameIsJoe: SqlExpression<(Person) -> Boolean> = capture.expression {
    { p: Person -> p.name == "Joe" }
  }
  ```
  You can them use them with normal queries like this:
  ```kotlin
  // The .use function changes it from SqlExpression<(Person) -> Boolean> to just (Person) -> Boolean
  // you can only use it inside of a `capture` block. 
  capture { Table<Person>().filter { p -> nameIsJoe.use(p) } }
  ```

### How do I use normal runtime data inside my SQL captures?

For most data types use `param(...)`. For example:
```Kotlin
val runtimeName = "Joe"
capture { Table<Person>().filter { p -> p.name == param(runtimeName) } }
```
This will work for primitives, and KMP and Java date-types (i.e. from `java.time.*`)
ExoQuery uses kotlinx.serialization behind the scenes. In some cases you might
want to use `paramCtx` to create a contextual parameter (Kotlin docs: [contextual-serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#contextual-serialization)) or `paramCustom`
to sepecify a custom serializer (Kotlin docs:  [custom-serializers](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#custom-serializers)).
See the [Parameters](#parameters) section for more details.

# Getting Started

Add the following to your `build.gradle.kts`. First add the plugin and then one of the below
dependency blocks.

```kotlin
plugins {
  id("io.exoquery.exoquery-plugin") version "2.1.0-0.2.0.PL"
  kotlin("plugin.serialization") version "2.1.0" // exoquery relies on this
}

// For Java:
dependencies {
  implementation("io.exoquery:exoquery-jdbc:0.2.0.PL-0.2.0")
  implementation("org.postgresql:postgresql:42.7.0") // Remember to include the right JDBC Driver
}

// For: IOS, OSX, Native Linux, and Mingw using Kotlin Multiplatform
dependencies {
  implementation("io.exoquery:exoquery-native:0.2.0.PL-0.2.0")
  implementation("app.cash.sqldelight:native-driver:2.0.2")
}

// For Android:
dependencies {
  implementation("io.exoquery:exoquery-android:0.2.0.PL-0.2.0")
  implementation("androidx.sqlite:sqlite-framework:2.4.0")
}
```

You can get started writing queries like this:
```kotlin
// Create a JVM DataSource the way you normally would
val ds: DataSource = ...
val controller = JdbcControllers.Postgres(ds) 

// Create a query:
val query = capture {
  Table<Person>().filter { p -> p.name == "Joe" }
}

// Build it for the right dialect and execute:
val output = query.buildFor.Postgres().runOn(controller)
```

Have a look at code samples for starter projects here:
- Basic Java project: 
- Basic Linux Native project:
- Android and OSX project: 

# ExoQuery Features

## Composing Queries

### Map

```Kotlin
val q = capture {
  Table<Person>().map { p -> p.name }
}
q.buildFor.Postgres().runOn(myDatabase)
```

### Filter

```Kotlin
val q = capture {
  Table<Person>().filter { p -> p.name == "Joe" }
}
q.buildFor.Postgres().runOn(myDatabase)
```



### Joins

## Column and Table Naming

If need your table or columns to be named differently that than the data-class name or it's fields
you can use the kotlinx.serialization `SerialName("...")` annotation:
```kotlin
@SerialName("corp_customer")
data class CorpCustomer {
  val name: String
  @SerialName("num_orders")
  val numOrders: Int
  @SerialName("created_at")
  val createdAt: Int
}
val q = capture { Table<CorpCustomer>() }
q.buildFor.Postgres().runOn(myDatabase)
//> 
```
If you do not want to use this annotation for somemthing else (e.g. JSON field names) you can also
use `@ExoEntity` and `@ExoField` to do the same thing.

```kotlin
@ExoEntity("corp_customer")
data class CorpCustomer {
  val name: String
  @ExoField("num_orders")
  val numOrders: Int
  @ExoField("created_at")
  val createdAt: Int
}
val q = capture { Table<CorpCustomer>() }
q.buildFor.Postgres().runOn(myDatabase)
//> 
```

## Transactions

## Nested Datatypes

## Captured Functions


## Parameters
