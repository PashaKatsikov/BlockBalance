package bp.goalsgames.blockbalance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.data.ProfileRules
import bp.goalsgames.blockbalance.game
import bp.goalsgames.blockbalance.meta.BonusCalendar
import bp.goalsgames.blockbalance.meta.BonusState
import bp.goalsgames.blockbalance.meta.LadderBoard
import bp.goalsgames.blockbalance.meta.LadderMetric
import bp.goalsgames.blockbalance.meta.LadderRow
import bp.goalsgames.blockbalance.meta.QuestCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs everything outside a run: the menu, store, quests, ranks and settings.
 * The live run has its own view model so the two never fight over state.
 */
class ShellViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.game.profileStore
    private val clock = application.game.dayClock
    private val haptics = application.game.haptics

    private val _rewardFlash = MutableStateFlow<Long?>(null)
    val rewardFlash: StateFlow<Long?> = _rewardFlash.asStateFlow()

    val profile: StateFlow<PlayerProfile> = store.profile
        .onEach { haptics.enabled = it.hapticsOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerProfile())

    init {
        refreshCalendar()
    }

    /** Rolls day and week scoped state forward; safe to call on every resume. */
    fun refreshCalendar() {
        viewModelScope.launch {
            val day = clock.today()
            val week = clock.week()
            store.mutate { ProfileRules.rollOver(it, day, week) }
        }
    }

    fun bonusState(profile: PlayerProfile): BonusState =
        BonusCalendar.evaluate(profile.bonusLastDay, profile.bonusStreak, clock.today())

    fun questCards(profile: PlayerProfile): List<QuestCard> = ProfileRules.questCards(profile)

    fun ladderRows(profile: PlayerProfile, metric: LadderMetric): List<LadderRow> =
        LadderBoard.table(
            week = clock.week(),
            metric = metric,
            playerScore = ProfileRules.weeklyScore(profile, metric),
        )

    fun ladderDaysLeft(): Int = LadderBoard.daysLeft(clock.today())

    fun claimBonus() {
        viewModelScope.launch {
            val day = clock.today()
            var reward = 0L
            store.mutate { current ->
                val change = ProfileRules.claimBonus(ProfileRules.rollOver(current, day, clock.week()), day)
                reward = change.questRewards
                change.profile
            }
            if (reward > 0L) {
                haptics.tick()
                _rewardFlash.value = reward
            }
        }
    }

    fun claimQuest(code: String) {
        viewModelScope.launch {
            var reward = 0L
            store.mutate { current ->
                val change = ProfileRules.claimQuest(current, code)
                reward = change.questRewards
                change.profile
            }
            if (reward > 0L) {
                haptics.tick()
                _rewardFlash.value = reward
            }
        }
    }

    fun dismissRewardFlash() {
        _rewardFlash.value = null
    }

    fun selectStyle(styleId: Int) {
        viewModelScope.launch { store.mutate { ProfileRules.buyStyle(it, styleId) } }
    }

    fun selectSky(skyId: Int) {
        viewModelScope.launch { store.mutate { ProfileRules.buySky(it, skyId) } }
    }

    /** The yard reads the tier from the profile, so a run never changes mid-flight. */
    fun setTier(tier: RiskTier) {
        viewModelScope.launch { store.mutate { it.copy(tier = tier) } }
    }

    fun setShuffle(enabled: Boolean) {
        viewModelScope.launch { store.mutate { it.copy(shuffleStyles = enabled) } }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { store.mutate { it.copy(hapticsOn = enabled) } }
    }

    fun resetProgress() {
        viewModelScope.launch {
            store.reset()
            refreshCalendar()
        }
    }
}
