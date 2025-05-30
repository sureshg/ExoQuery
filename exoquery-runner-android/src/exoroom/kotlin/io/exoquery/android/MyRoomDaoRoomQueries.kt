package io.exoquery.android

import androidx.room.Dao
import androidx.room.Query

interface MyRoomDaoRoomQueries {
  @Query("SELECT x.id, x.firstName, x.lastName, x.age FROM PersonRoom x")
  fun joes(): List<PersonRoom>
}
