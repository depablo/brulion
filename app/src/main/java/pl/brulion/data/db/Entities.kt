package pl.brulion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Librus portal account (Konto Librus); one account can see several students. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portalAccessToken: String? = null,
    val portalRefreshToken: String? = null,
    val portalTokenExpiresAt: Long = 0,
)

/** One Synergia account, i.e. one student seen by a parent or by the student themselves. */
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index(value = ["synergiaLogin"], unique = true)],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val synergiaLogin: String,
    val studentName: String,
    val isParent: Boolean,
    val apiAccessToken: String? = null,
    val apiTokenExpiresAt: Long = 0,
    /** Epoch seconds of the last sync that fetched anything; 0 = never synced. */
    @ColumnInfo(defaultValue = "0") val lastSyncAt: Long = 0,
    /**
     * Epoch seconds of "mark everything as read": things first seen before it lose their "new"
     * marker in Brulion. Librus isn't told, so the messages stay unread there.
     */
    @ColumnInfo(defaultValue = "0") val markedReadAt: Long = 0,
)
