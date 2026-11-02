/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.justifications

import kotlinx.coroutines.CancellationException
import pl.brulion.data.AccountRepository
import pl.brulion.data.Diagnostics
import pl.brulion.librus.LibrusJustifications

class JustificationsRepository(
    private val accounts: AccountRepository,
    private val justifications: LibrusJustifications,
    private val diagnostics: Diagnostics,
) {
    /** Sends one justification for [profileId]; returns the id Librus assigned, if any. */
    suspend fun send(profileId: Long, request: LibrusJustifications.Request): String? = try {
        accounts.withApi(profileId) { justifications.send(it, request) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        diagnostics.log("justifications $profileId failed", e)
        throw e
    }
}
