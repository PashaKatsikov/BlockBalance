package bp.goalsgames.blockbalance.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.meta.QuestProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "block_balance_profile")

/**
 * Single writer for the persisted profile. Callers hand in a transform and the
 * whole record is rewritten inside one DataStore edit, so concurrent reward and
 * quest updates cannot interleave.
 */
class ProfileStore(context: Context) {

    private val store = context.applicationContext.profileDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val profile: Flow<PlayerProfile> = store.data.map(::decode)

    suspend fun mutate(transform: (PlayerProfile) -> PlayerProfile): PlayerProfile {
        var result = PlayerProfile()
        store.edit { prefs ->
            result = transform(decode(prefs))
            encode(prefs, result)
        }
        return result
    }

    private fun decode(prefs: Preferences): PlayerProfile {
        val fallback = PlayerProfile()
        return PlayerProfile(
            credits = prefs[Keys.credits] ?: fallback.credits,
            totalXp = prefs[Keys.totalXp] ?: fallback.totalXp,
            tier = RiskTier.fromKey(prefs[Keys.tier]),
            lastStake = prefs[Keys.lastStake] ?: fallback.lastStake,
            hapticsOn = prefs[Keys.haptics] ?: fallback.hapticsOn,
            shuffleStyles = prefs[Keys.shuffle] ?: fallback.shuffleStyles,
            ownedStyles = decodeIds(prefs[Keys.ownedStyles], fallback.ownedStyles),
            activeStyle = prefs[Keys.activeStyle] ?: fallback.activeStyle,
            ownedSkies = decodeIds(prefs[Keys.ownedSkies], fallback.ownedSkies),
            activeSky = prefs[Keys.activeSky] ?: fallback.activeSky,
            bonusLastDay = prefs[Keys.bonusLastDay],
            bonusStreak = prefs[Keys.bonusStreak] ?: fallback.bonusStreak,
            questDay = prefs[Keys.questDay] ?: fallback.questDay,
            quests = decodeQuests(prefs[Keys.quests]),
            blocksToday = prefs[Keys.blocksToday] ?: 0,
            banksToday = prefs[Keys.banksToday] ?: 0,
            creditsToday = prefs[Keys.creditsToday] ?: 0L,
            bankStreak = prefs[Keys.bankStreak] ?: 0,
            bestFloorToday = prefs[Keys.bestFloorToday] ?: 0,
            bestStreakToday = prefs[Keys.bestStreakToday] ?: 0,
            ladderWeek = prefs[Keys.ladderWeek] ?: fallback.ladderWeek,
            weekBestHeight = prefs[Keys.weekHeight] ?: 0L,
            weekBestCredits = prefs[Keys.weekCredits] ?: 0L,
            weekBestStreak = prefs[Keys.weekStreak] ?: 0L,
            bestPayout = prefs[Keys.bestPayout] ?: 0L,
            bestHeight = prefs[Keys.bestHeight] ?: 0,
            bestStreak = prefs[Keys.bestStreak] ?: 0,
            runsPlayed = prefs[Keys.runsPlayed] ?: 0,
        )
    }

    private fun encode(prefs: MutablePreferences, profile: PlayerProfile) {
        prefs[Keys.credits] = profile.credits
        prefs[Keys.totalXp] = profile.totalXp
        prefs[Keys.tier] = profile.tier.name
        prefs[Keys.lastStake] = profile.lastStake
        prefs[Keys.haptics] = profile.hapticsOn
        prefs[Keys.shuffle] = profile.shuffleStyles
        prefs[Keys.ownedStyles] = encodeIds(profile.ownedStyles)
        prefs[Keys.activeStyle] = profile.activeStyle
        prefs[Keys.ownedSkies] = encodeIds(profile.ownedSkies)
        prefs[Keys.activeSky] = profile.activeSky
        val lastBonus = profile.bonusLastDay
        if (lastBonus == null) prefs.remove(Keys.bonusLastDay) else prefs[Keys.bonusLastDay] = lastBonus
        prefs[Keys.bonusStreak] = profile.bonusStreak
        prefs[Keys.questDay] = profile.questDay
        prefs[Keys.quests] = json.encodeToString(profile.quests)
        prefs[Keys.blocksToday] = profile.blocksToday
        prefs[Keys.banksToday] = profile.banksToday
        prefs[Keys.creditsToday] = profile.creditsToday
        prefs[Keys.bankStreak] = profile.bankStreak
        prefs[Keys.bestFloorToday] = profile.bestFloorToday
        prefs[Keys.bestStreakToday] = profile.bestStreakToday
        prefs[Keys.ladderWeek] = profile.ladderWeek
        prefs[Keys.weekHeight] = profile.weekBestHeight
        prefs[Keys.weekCredits] = profile.weekBestCredits
        prefs[Keys.weekStreak] = profile.weekBestStreak
        prefs[Keys.bestPayout] = profile.bestPayout
        prefs[Keys.bestHeight] = profile.bestHeight
        prefs[Keys.bestStreak] = profile.bestStreak
        prefs[Keys.runsPlayed] = profile.runsPlayed
    }

    private fun decodeIds(raw: String?, fallback: Set<Int>): Set<Int> {
        if (raw.isNullOrBlank()) return fallback
        val parsed = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        return parsed.ifEmpty { fallback }
    }

    private fun encodeIds(ids: Set<Int>): String = ids.sorted().joinToString(",")

    private fun decodeQuests(raw: String?): List<QuestProgress> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<QuestProgress>>(raw)
        } catch (error: SerializationException) {
            emptyList()
        } catch (error: IllegalArgumentException) {
            emptyList()
        }
    }

    private object Keys {
        val credits = longPreferencesKey("credits")
        val totalXp = longPreferencesKey("total_xp")
        val tier = stringPreferencesKey("risk_tier")
        val lastStake = longPreferencesKey("last_stake")
        val haptics = booleanPreferencesKey("haptics")
        val shuffle = booleanPreferencesKey("shuffle_styles")
        val ownedStyles = stringPreferencesKey("owned_styles")
        val activeStyle = intPreferencesKey("active_style")
        val ownedSkies = stringPreferencesKey("owned_skies")
        val activeSky = intPreferencesKey("active_sky")
        val bonusLastDay = longPreferencesKey("bonus_last_day")
        val bonusStreak = intPreferencesKey("bonus_streak")
        val questDay = longPreferencesKey("quest_day")
        val quests = stringPreferencesKey("quest_progress")
        val blocksToday = intPreferencesKey("blocks_today")
        val banksToday = intPreferencesKey("banks_today")
        val creditsToday = longPreferencesKey("credits_today")
        val bankStreak = intPreferencesKey("bank_streak")
        val bestFloorToday = intPreferencesKey("best_floor_today")
        val bestStreakToday = intPreferencesKey("best_streak_today")
        val ladderWeek = longPreferencesKey("ladder_week")
        val weekHeight = longPreferencesKey("week_best_height")
        val weekCredits = longPreferencesKey("week_best_credits")
        val weekStreak = longPreferencesKey("week_best_streak")
        val bestPayout = longPreferencesKey("best_payout")
        val bestHeight = intPreferencesKey("best_height")
        val bestStreak = intPreferencesKey("best_streak")
        val runsPlayed = intPreferencesKey("runs_played")
    }

    /** Wipes the record back to a first-run profile. */
    suspend fun reset() {
        mutate { PlayerProfile() }
    }
    // UNIQUE:AST_DECOYS:BEGIN
    private fun primeQpf(intensity: Float): Float =
        0.071f + (intensity - 0.562f) * 0.796f

    private fun humMyhjy(payload: ByteArray): Int {
        var acc = 7650031
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc % 724
    }

    private fun trickleCbtus(sample: Long): Long =
        (sample xor 0xEB4C3EA5L) shr 2
    // UNIQUE:AST_DECOYS:END
}
