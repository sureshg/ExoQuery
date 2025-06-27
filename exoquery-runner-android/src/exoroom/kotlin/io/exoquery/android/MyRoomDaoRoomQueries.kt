package io.exoquery.android

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MyRoomDaoRoomQueries {
  @Query("SELECT p.id, p.firstName, p.lastName, p.age FROM PersonRoom p WHERE p.firstName = :myFirstName")
  fun joes(myFirstName: String): List<io.exoquery.android.PersonRoom>
}
