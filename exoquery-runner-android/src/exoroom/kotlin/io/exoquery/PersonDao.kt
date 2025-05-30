package io.exoquery

import androidx.room.Dao
import androidx.room.Query
import io.exoquery.android.PersonRoom

// com.android.tools.r8.internal.vc: Space characters in SimpleName 'get AS' are not allowed prior to DEX version 040
// const val queryA = "SELECT * FROM books WHERE id = :id"

@Dao
interface PersonDao {
  @Query("SELECT * FROM books WHERE id = :id")
  fun findById(id: Int): PersonRoom?

  @Query("SELECT * FROM books WHERE firstName = :firstName")
  fun findByName(firstName: String): PersonRoom?
}
