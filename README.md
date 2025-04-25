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
      val c = from(companies /*SqlQuery<Company>*/)
      val p = join(people /*SqlQuery<Person>*/) { p -> p.companyId == c.id }
      p
    }
  )
  val a = join(addresses /*SqlQuery<Address>*/) { a -> a.personId == p.id }
  Data(p.name, a.city)
}
//> SELECT p.name, a.city FROM (
//   SELECT p.name, p.age, p.companyId FROM Person p JOIN companies c ON c.id = p.companyId
//  ) p JOIN Address a ON a.personId = p.id
```
Notice how the types compose completely fluidly? The output of a subquery is the same datatype as a table.

### *...but wait, how can you use `==`, or regular `if` or regular case classes in a DSL?*

By using the `capture` function to deliniate relevant code snippets and a compiler-plugin to
transform them, I can synthesize a SQL query the second your code is compiled in most cases.

You can even see it in the build output in a file. Have a look at the `build/generated/exoquery` directory.

TODO add picture

### So I can just use normal Kotlin to write Queries?

That's right! You can use regular Kotlin constructs that you know and love in order to write SQL code including:

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
  //>  SELECT CASE WHEN p.age >= 18 THEN 'adult' WHEN p.age < 18 AND p.age > 10 THEN 'minor' ELSE 'child' END AS value FROM Person p
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
     // or Triple(p.name, p.age, p.companyId), or MyDataClass(p.name, p.age, p.companyId)
  }
  //> SELECT p.name, p.age FROM Person p
  ```
  You can use pairs and tuples with the whole row too! In fact, you can output any simple data-class.
  See below for examples.

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
val q = capture { Table<Person>().filter { p -> p.name == param(runtimeName) } }
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ?
```
The ExoQuery infrastructure will know to inject the value of `runtimeName` into the `?`
placeholder in the prepared-statement of the database driver.

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
- Basic Java project
- Basic Linux Native project: TBD
- Android and OSX project: TBD

# ExoQuery Features

## Composing Queries

ExoQuery compose allow you to perform functions on SqlQuery instances. These functions are not available outside of a
compose block becuase the compose block delimiates the boundary of the query. SqlQuery instances created by the
`Table<MyRow>` function representing simple table elements. Following functional-programming principles, 
any transformation on any SqlQuery instance work, and work the same way.

### Map

This is also known as an SQL projection. It allows you to select a subset of the columns in a table.
```Kotlin
val q = capture {
  Table<Person>().map { p -> p.name }
}
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name FROM Person p
```

You can also use the `.map` funtion to perform simple aggreations on tables. For example:
```kotlin
val q = capture {
  Table<Person>().map { p -> Triple(min(p.age), max(p.age), avg(p.age)) }
}
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT min(p.age), max(p.age), avg(p.age) FROM Person p
```

### Filter

This is also known as a SQL where clause. It allows you to filter the rows in a table.

```Kotlin
val q = capture {
  Table<Person>().filter { p -> p.name == "Joe" }
}
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Joe'
```

If you are using a `capture.select` block, you can also use the `where` function to filter the rows:
```kotlin
val q = capture.select {
  val p = from(Table<Person>())
  where { p.name == "Joe" }
}
```

### Correlated Subqueries

You can use a combine filter and map functions to create correlated subqueries. 
For example, let's say we want to find all the people over the average age:
```kotlin
val q = capture {
  Table<Person>().filter { p -> p.age > Table<Person>().map { it.age }.avg() }
}
//> SELECT p.id, p.name, p.age FROM Person p WHERE p.age > (SELECT avg(p1.age) FROM Person p1)
```

Recall that you could table the `.map` function with table aggregations. Let's say you want to get
not average but also use that together with another aggreagtor (e.g. the average minus the minimum).
Normally you could use an expression `avg(p.age) + min(p.age)` with a the `.map` function.
```kotlin
val customExpr: SqlQuery<Double> = capture.select { Table<Person>().map { p -> avg(p.age) + min(p.age) } }
```
If you want to use a statement like this inside of a correlated subquery, we can use the `.value()`
function inside of a capture block to convert a `SqlQuery<T>` into just `T`.
```kotlin
val q = capture {
  Table<Person>().filter { p -> p.age > Table<Person>().map { p -> avg(p.age) + min(p.age) }.value() }
}
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name, p.age FROM Person p WHERE p.age > (SELECT avg(p1.age) + min(p1.age) FROM Person p1)
```

### SortedBy

The kotlin collections `sortedBy` and `sortedByDescending` functions are also available on SqlQuery instances.
```kotlin
val q = capture {
  Table<Person>().sortedBy { p -> p.name }
}
//> SELECT p.id, p.name, p.age FROM Person p ORDER BY p.name

val q = capture {
  Table<Person>().sortedByDescending { p -> p.name }
}
//> SELECT p.id, p.name, p.age FROM Person p ORDER BY p.name DESC
```

When you want to do advanced sorting (e.g. different sorting for different columns) use a `select` block 
and the sortBy function inside.
```kotlin
val q = capture.select {
  val p = from(Table<Person>())
  sortBy(p.name to Asc, p.age to Desc)
}
//> SELECT p.id, p.name, p.age FROM Person p ORDER BY p.name ASC, p.age DESC
```

### Joins

Use the `capture.select` to do joins as many joins as you need.
```kotlin
val q: SqlQuery<Pair<Person, Address>> = 
  capture.select {
    val p = from(Table<Person>())
    val a = join(Table<Address>()) { a -> a.ownerId == p.id }
    p to a
  }
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON a.personId = p.id
```

Let's add a left-join:
```kotlin
val q: SqlQuery<Pair<Person, Address?>> = 
  capture.select {
    val p = from(Table<Person>())
    val a = join(Table<Address>()) { a -> a.ownerId == p.id }
    val f = joinLeft(Table<Furnitire>()) { f -> f.locatedAt == a.id }
    Triple(p, a, f)
  }
//> SELECT p.id, p.name, p.age, a.ownerId, a.street, a.zip, f.name, f.locatedAt FROM Person p 
//  LEFT JOIN Address a ON a.personId = p.id 
//  LEFT JOIN Furnitire f ON f.locatedAt = a.id
```
Notice that the `Address` table is now nullable. This is because the left-join can return null values.

What can go inside of the `capture.select` function is very carefully controlled by exoquery.
It needs to be one of the following:
- A `from` statement
- A `join` or `leftJoin` statement
- A `where` clause (see the [filter](#filter) section above for more details).
- A `sortBy` clause (see the [sortBy](#sortedby) section above for more details).
- A `groupBy` clause (see the [groupBy](#groupby) section below for more details).

You can use all of these features all together. For example:
```kotlin
val q: SqlQuery<Pair<Person, Address>> = 
  capture.select {
    val p = from(Table<Person>())
    val a = join(Table<Address>()) { a -> a.ownerId == p.id }
    where { p.name == "Joe" }
    sortBy(p.name to Asc, p.age to Desc)
    groupBy(p.name)
    p to a
  }

q.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.id, p.name, p.age, a.ownerId, a.street, a.city FROM Person p 
//  INNER JOIN Address a ON p.id = a.ownerId 
//  WHERE p.age > 18 GROUP BY p.age, a.street ORDER BY p.age ASC, a.street DESC
```

### GroupBy

Use a `capture.select` function to do groupBy. You can use the `groupBy` function to group by multiple columns.
```kotlin
val q: SqlQuery<Pair<Person, Address>> = 
  capture.select {
    val p = from(Table<Person>())
    val a = join(Table<Address>()) { a -> a.ownerId == p.id }
    groupBy(p.name, a.street)
    MyData(p.name, a.street, avg(p.age)) // Average age of all people named X on the same street
  }
//> SELECT p.name, a.street, avg(p.age) FROM Person p
```

## SQL Actions
ExoQuery has a simple DSL for performing SQL actions.

### Insert

```kotlin
val q =
  capture {
    insert<Person> { set(name to "Joe", age to 44, companyId to 123) }
  }
q.buildFor.Postgres().runOn(myDatabase)
//> INSERT INTO Person (name, age, companyId) VALUES ('Joe', 44, 123)
```
Typically you will use `param` to insert data from runtime values:
```kotlin
val nameVal = "Joe"
val ageVal = 44
val companyIdVal = 123
val q =
  capture {
    insert<Person> { set(name to param(nameVal), age to param(ageVal), companyId to param(companyIdVal)) }
  }
//> INSERT INTO Person (name, age, companyId) VALUES (?, ?, ?)
```

When this gets to cumbersome (and it will!) see how to insert a whole row.

#### Insert a Whole Row

You can insert and entire `person` object using `setParams`.
```kotlin
val person = Person("Joe", 44, 123)
val q =
  capture {
    insert<Person> { setParams(person) }
  }
//> INSERT INTO Person (name, age, companyId) VALUES (?, ?, ?)
```

#### Insert with Exclusions

Wait a second, don't database-model objects like `Person` typically have one or more primary keys key columns
that need to be excluded during the insert because the database generates them?

Here is how to do that:
```kotlin
val person = Person(id = 0, "Joe", 44, 123)

val q =
  capture {
    insert<Person> { setParams(person).excluding(id) } // you can add multiple exclusions here e.g. exlcuding(id, id1, id2, ...)
  }
//> INSERT INTO Person (name, age, companyId) VALUES (?, ?, ?)
```

#### Insert with Returning ID

What do we do if we need to know the row id of the row we just inserted?
The best way to do that is to use the `returning` function to add a `RETURNING` clause to the insert statement.
```kotlin
data class Person(val id: Int, val name: String, val age: Int, val companyId: Int)

val person = Person(id = 0, "Joe", 44, 123)
val q =
  capture {
    insert<Person> { setParams(person).excluding(id) }.returning { p -> p.id }
  }

q.buildFor.Postgres().runOn(myDatabase) // Also works with SQLite
//> INSERT INTO Person (name, age, companyId) VALUES (?, ?, ?) RETURNING id
q.buildFor.SqlServer().runOn(myDatabase)
//> INSERT INTO Person (name, age, companyId) OUTPUT INSERTED.id VALUES (?, ?, ?)
```
This will work for Postgres, SQLite, and SqlServer. For other databases use
`.returningKeys { id }` which will instruct the database-driver to return the
inserted row keys on a more low level. This function is more limited than what
`returning` can do and it is prone to various database-driver quirks
so be sure to test it on your database appropriately.

The `returning` function is more flexible that `returningKeys` because it allows you to
return not only any column in the inserted row but also collect these columns into
a composite object of your choice. For example:
```kotlin
data class MyOutputData(val id: Int, val name: String)

val person = Person(id = 0, "Joe", 44, 123)

val q =
  capture {
    insert<Person> { setParams(person).excluding(id) }
      .returning { p -> MyOutputData(p.id, p.name + "-suffix") }
  }

val output: List<MyOutputData> = q.buildFor.Postgres().runOn(myDatabase)
//> INSERT INTO Person (name, age, companyId) VALUES (?, ?, ?) RETURNING id, name || '-suffix' AS name
```

### Update

The update statement is similar to the insert statement. You can use the `set` function to set the values of the 
columns you want to update and typically you will use `param` to set SQL placeholders for runtime values. 
Use a `.where` clause to filter your update query.
```kotlin
val joeName = "Joe"
val joeAge = 44
val joeId = 123

val q =
  capture {
    update<Person> { set(name to param(joeName), age to param(joeAge))
      .where { id == param(joeId) } }
  }
q.buildFor.Postgres().runOn(myDatabase)
// > UPDATE Person SET name = ?, age = ?, companyId = ? WHERE id = 1
```

Similar to INSERT, you can use `setParams` to set columns from the entire `person` object.
Combine this with `excluding` to exclude the primary key column from the update statement and
use the `where` clause to filter your update query.
```kotlin
val person = Person(id = 1, "Joe", 44, 123)
val q =
  capture {
    update<Person> { setParams(person).excluding(id).where { id == param(joeId) } }
  }
q.buildFor.Postgres().runOn(myDatabase)
//> UPDATE Person SET name = ?, age = ?, companyId = ? WHERE id = 1
```

You can also use a `returning` clause to return the updated row if your database supports it.
```kotlin
val person = Person(id = 1, "Joe", 44, 123)

val q =
  capture {
    update<Person> { 
      setParams(person).excluding(id).where { id == param(joeId) } }
        .returning { p -> p.id }
  }

q.buildFor.Postgres().runOn(myDatabase) // Also works with SQLite
//> UPDATE Person SET name = ?, age = ?, companyId = ? WHERE id = 1 RETURNING id
q.buildFor.SqlServer().runOn(myDatabase)
//> UPDATE Person SET name = ?, age = ?, companyId = ? OUTPUT INSERTED.id WHERE id = 1
```


### Delete

### Batch Actions

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
//> SELECT x.name, x.num_orders, x.created_at FROM corp_customer x
```

### Captured Functions

Captured functions allow you to use kotlin functions inside of of blocks. Writing a captured function is as simple as adding
the `@CapturedFunction` annotation to a function that returns a `SqlQuery<T>` or `SqlExpression<T>` instance.
Recall that in the introduction we saw a captured function that calculated the P/E ratio of a stock:
```kotlin
  @CapturedFunction
  fun peRatioWeighted(stock: Stock, weight: Double): Double = catpure.expression {
    (stock.price / stock.earnings) * weight
  }
```
Once this function is defined you can use it inside of a `capture` block like this:
```kotlin
capture {
  Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap/totalWeight) } 
}
```

Note that captured functions can call other captured functions, for example:
```kotlin
@CapturedFunction
fun peRationSimple(stock: Stock): Double = catpure.expression {
  stock.price / stock.earnings
}
@CapturedFunction
fun peRatioWeighted(stock: Stock, weight: Double): Double = catpure.expression {
  peRationSimple(stock) * weight
}
capture {
  Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap/totalWeight) } 
}
```

Also note that captured functions can make use of the context-reciver position. For example, let's make the
`marketCap` field into a function:
```kotlin
@CapturedFunction
fun Stock.marketCap() = capture.expression {
    price * sharesOutstanding
  }
val q = capture {
  val totalWeight = Table<Stock>().map { it.marketCap().use }.sum() // A local variable used in the query!
  Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap().use/totalWeight) }
}
println(q.buildFor.Postgres().value)
// SELECT (stock.price / stock.earnings) * ((this.price * this.sharesOutstanding) / (SELECT sum(this.price * this.sharesOutstanding) FROM Stock it)) AS value FROM Stock stock
```

### Polymorphic Query Abstraction

Continuing from the section on [captured-functions](#captured-functions) above, captured functions can use generics and polymorphism in order to create highly abstractable query components.
For example:
```kotlin
interface Locateable { val locationId: Int }
data class Person(val name: String, val age: Int, locationId: Int)
data class Robot(val model: String, val createdOn: LocalDate, val locationId: Int)
data class Address(val id: Int, val street: String, val zip: String)

// Now let's create a captured function that can be used with any Locateable object:
@CapturedFunction
fun <L: Locateable> joinLocation(locateable: SqlQuery<L>): SqlQuery<Pair<L, Address>> = 
  capture.select {
    val l = from(locateable)
    val a = join(addresses) { a -> a.id == l.locationId }
    l to a
  }
```
Now I can use this function with the Person table
```kotlin
val people: SqlQuery<Pair<Person, Address>> = capture {
  joinLocation(Table<Person>().filter { p -> p.name == "Joe" })
}
people.buildFor.Postgres().runOn(myDatabase)
//> SELECT p.name, p.age, p.locationId, a.id, a.street, a.zip FROM Person p JOIN Address a ON a.id = p.locationId WHERE p.name = 'Joe'
```
As well as the Robot table:
```kotlin
val robots: SqlQuery<Pair<Robot, Address>> = capture {
  joinLocation(Table<Robot>().filter { r -> r.model == "R2D2" })
}
//> SELECT r.model, r.createdOn, r.locationId, a.id, a.street, a.zip FROM Robot r JOIN Address a ON a.id = r.locationId WHERE r.model = 'R2D2'
```

You can then continue to compose the output of this function to get more and more powerful queries!

### Local Variables
Captured functions can also be used to define local variables inside of a `capture` block. In the introduction we saw
a query that looked like this:
```kotlin
capture {
  Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap/totalWeight) } 
}
```
Note how I intentionally left the `totalWeight` variable undefined. Let's try to define it as a local varaible:
```kotlin
val q =
  capture {
    val totalWeight = Table<Stock>().map { it.marketCap().use }.sum() 
    Table<Stock>().map { stock -> peRatioWeighted(stock, stock.marketCap/totalWeight) } 
  }
q.buildFor.Postgres().runOn(myDatabase)
//> SELECT (stock.price / stock.earnings) * ((this.price * this.sharesOutstanding) / (SELECT sum(this.price * this.sharesOutstanding) FROM Stock it)) AS value FROM Stock stock
```

## Transactions

ExoQuery supports transactions! Once a query or action is built e.g. once you do `.buildFor.Postgres()` you will get
one of three possbile things:
1. A `SqlCompiledQuery` object. This is a query that can be executed on the database.
2. A `SqlCompiledAction` object. This is an action that can be executed on the database.
3. A `SqlCompiledBatchAction` object This is a SQL batch action, typically it is not supported for transactions.

Once you have imported a ExoQuery runner project (e.g. exoquery-jdbc) and created a DatabaseController
(e.g. `JdbcControllers.Postgres`), you can run the query or action:
```kotlin
val ds: DataSource = ...
val controller = JdbcControllers.Postgres(ds)

val getJoes = capture {
  Table<Person>().filter { p -> p.name == "Joe" }
}.buildFor.Postgres()

fun setJoesToJims(ids: List<String>) = capture {
  update<Person> { set(name to "Jim").where { p -> p.id in params(ids) } }
}.buildFor.Postgres()

controller.transaction {
  val allJoes = getJoes.runOn(controller)
  // Execute some runtime logic to filter the people we want to update i.e. `shouldActuallyBeUpdated`
  val someJoes = allJoes.filter { p -> shouldActuallyBeUpdated(p) }
  setJoesToJims(someJoes).runOn(controller)
}
```


## Nested Datatypes





## Parameters

### param

### params

## Dynamic Queries
