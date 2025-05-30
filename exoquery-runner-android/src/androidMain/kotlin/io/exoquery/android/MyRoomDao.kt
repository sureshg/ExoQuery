@file:ExoRoomInterface
package io.exoquery.android

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.exoquery.annotation.ExoRoomInterface
import io.exoquery.capture

@Entity(tableName = "Person")
data class PersonRoom(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val firstName: String,
  val lastName: String,
  val age: Int
)

interface MyRoomDao {
  fun joes() {
    capture.select {
      val p = from(Table<PersonRoom>())
      p
    }.buildFor.Room("joes")
  }
}
