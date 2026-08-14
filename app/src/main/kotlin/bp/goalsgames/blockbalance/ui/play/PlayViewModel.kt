package bp.goalsgames.blockbalance.ui.play

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.core.RoundMachine
import bp.goalsgames.blockbalance.core.RoundPhase
import bp.goalsgames.blockbalance.core.RoundSnapshot
import bp.goalsgames.blockbalance.core.SeededChance
import bp.goalsgames.blockbalance.core.SettleResult
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.data.ProfileRules
import bp.goalsgames.blockbalance.data.RankGain
import bp.goalsgames.blockbalance.game
import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.stage.Art
import bp.goalsgames.blockbalance.stage.SpriteBank
import bp.goalsgames.blockbalance.stage.StageCommand
import bp.goalsgames.blockbalance.stage.StageEvent
import bp.goalsgames.blockbalance.stage.StageScene
import bp.goalsgames.blockbalance.ui.formatMultiplier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one run: it asks [RoundMachine] for verdicts, plays them back through
 * [StageScene], and commits the results to the stored profile once the drop
 * animation reports what happened.
 */
class PlayViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Long enough that a real drop always beats it, short enough to unstick a run. */
        const val SETTLE_GUARD_MILLIS = 6_000L
        const val BANK_PANEL_DELAY_MILLIS = 850L
        const val TOPPLE_RESET_MILLIS = 750L
        const val TOAST_MILLIS = 3_000L

        /** Lets the winch finish reeling in before the next block appears. */
        const val RESET_HANG_MILLIS = 420L
    }

    private val store = application.game.profileStore
    private val clock = application.game.dayClock
    private val haptics = application.game.haptics

    private val machine = RoundMachine(SeededChance())

    val scene = StageScene()

    private val stageEvents = Channel<StageEvent>(Channel.UNLIMITED)

    private val round = MutableStateFlow(machine.snapshot)
    private val local = MutableStateFlow(LocalState())

    private val profile: StateFlow<PlayerProfile> =
        store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerProfile())

    private var headlineCounter = 0L
    private var lastStyleId: Int? = null
    private var settleGuard: Job? = null
    private var toastJob: Job? = null

    val state: StateFlow<PlayUiState> =
        combine(profile, round, local) { stored, snapshot, view -> assemble(stored, snapshot, view) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayUiState())

    init {
        scene.onEvent = { event -> stageEvents.trySend(event) }
        scene.baseAspect = SpriteBank.aspect(Art.STACK_BASE)

        viewModelScope.launch {
            for (event in stageEvents) consume(event)
        }
        viewModelScope.launch {
            profile.collect { stored ->
                haptics.enabled = stored.hapticsOn
                if (!machine.snapshot.isLive) machine.selectTier(stored.tier)
                pushConfigure(stored)
                round.value = machine.snapshot
            }
        }
    }

    override fun onCleared() {
        scene.onEvent = null
        super.onCleared()
    }

    /** Called when the yard becomes visible; art may have finished loading since init. */
    fun onStageAttached() {
        scene.baseAspect = SpriteBank.aspect(Art.STACK_BASE)
        pushConfigure(profile.value)
        if (!machine.snapshot.isLive) hangIdleBlock()
    }

    fun onPrimaryAction() {
        when (machine.snapshot.phase) {
            RoundPhase.READY -> startRun()
            RoundPhase.SWINGING -> releaseBlock()
            RoundPhase.RELEASING, RoundPhase.BANKED, RoundPhase.TOPPLED -> Unit
        }
    }

    fun onYardTap() {
        if (machine.snapshot.phase == RoundPhase.SWINGING) releaseBlock()
    }

    fun onBank() {
        val result = machine.bank() ?: return
        round.value = machine.snapshot
        haptics.tick()
        scene.submit(StageCommand.Celebrate)

        viewModelScope.launch {
            var gain: RankGain? = null
            edit { current ->
                val change = ProfileRules.onBanked(current, result.payout, result.floors)
                gain = change.rankGain
                change.profile
            }
            delay(BANK_PANEL_DELAY_MILLIS)
            local.update { view ->
                view.copy(
                    bankPanel = BankPanel(result.payout, result.floors, result.combo),
                    rankUp = gain?.takeIf { it.gainedRank } ?: view.rankUp,
                )
            }
        }
    }

    /** Closes the banked panel and clears the yard for the next run. */
    fun onCollect() {
        machine.clear()
        round.value = machine.snapshot
        scene.submit(StageCommand.Wipe)
        local.update { it.copy(bankPanel = null, headline = null) }
        viewModelScope.launch {
            delay(RESET_HANG_MILLIS)
            hangIdleBlock()
        }
    }

    fun onStakeStep(delta: Long) {
        if (machine.snapshot.isLive) return
        val stored = profile.value
        val next = Economy.clampStake(currentStake(stored) + delta, stored.credits)
        local.update { it.copy(stake = next) }
    }

    fun onStakeDouble() {
        if (machine.snapshot.isLive) return
        val stored = profile.value
        val next = Economy.clampStake(currentStake(stored) * 2, stored.credits)
        local.update { it.copy(stake = next) }
    }

    fun onStakeAllIn() {
        if (machine.snapshot.isLive) return
        val stored = profile.value
        local.update { it.copy(stake = Economy.clampStake(stored.credits, stored.credits)) }
    }

    fun onRankUpDismissed() {
        local.update { it.copy(rankUp = null) }
    }

    private fun startRun() {
        val stored = profile.value
        val stake = Economy.clampStake(currentStake(stored), stored.credits)
        if (!Economy.canAfford(stake, stored.credits)) {
            showToast()
            haptics.thud()
            return
        }
        if (!machine.begin(stake)) return

        local.update { it.copy(stake = stake, headline = null, bankPanel = null) }
        viewModelScope.launch { edit { ProfileRules.onStakePlaced(it, stake) } }

        pushConfigure(stored)
        val styleId = nextStyleId()
        scene.submit(StageCommand.Prime(styleId, styleAspect(styleId)))
        round.value = machine.snapshot
        releaseBlock()
    }

    private fun releaseBlock() {
        val verdict = machine.release() ?: return
        round.value = machine.snapshot
        scene.submit(StageCommand.Release(verdict.holds))
        armSettleGuard()
    }

    /** Both outcomes hand back to the rules; only the timing differs. */
    private suspend fun consume(event: StageEvent) {
        when (event) {
            is StageEvent.Landed -> resolvePending()
            StageEvent.Missed -> resolvePending()
        }
    }

    private suspend fun resolvePending() {
        when (val result = machine.settle()) {
            is SettleResult.Placed -> afterPlaced(result)
            is SettleResult.Toppled -> afterToppled(result)
            null -> Unit
        }
    }

    private suspend fun afterPlaced(result: SettleResult.Placed) {
        settleGuard?.cancel()
        round.value = machine.snapshot
        haptics.tick()
        local.update {
            it.copy(headline = Headline(nextHeadlineId(), "x${formatMultiplier(result.step)}", HeadlineTone.GAIN))
        }

        var gain: RankGain? = null
        edit { current ->
            val change = ProfileRules.onBlockPlaced(current, result.floors)
            gain = change.rankGain
            change.profile
        }
        gain?.takeIf { it.gainedRank }?.let { earned -> local.update { it.copy(rankUp = earned) } }

        val stored = profile.value
        pushConfigure(stored)
        val styleId = nextStyleId()
        scene.submit(StageCommand.Hoist(styleId, styleAspect(styleId)))
    }

    private suspend fun afterToppled(result: SettleResult.Toppled) {
        settleGuard?.cancel()
        round.value = machine.snapshot
        haptics.thud()
        local.update { it.copy(headline = Headline(nextHeadlineId(), "x0", HeadlineTone.LOSS)) }
        edit { ProfileRules.onToppled(it, result.floors) }

        delay(TOPPLE_RESET_MILLIS)
        machine.clear()
        round.value = machine.snapshot
        scene.submit(StageCommand.Wipe)
        delay(RESET_HANG_MILLIS)
        hangIdleBlock()
    }

    /** Keeps a block on the hook between runs, the way the yard idles. */
    private fun hangIdleBlock() {
        if (machine.snapshot.isLive) return
        val styleId = nextStyleId()
        scene.submit(StageCommand.Prime(styleId, styleAspect(styleId)))
    }

    private fun armSettleGuard() {
        settleGuard?.cancel()
        settleGuard = viewModelScope.launch {
            delay(SETTLE_GUARD_MILLIS)
            // The drop animation never reported back, most likely because the
            // surface was gone. The verdict already exists, so apply it.
            resolvePending()
        }
    }

    private fun showToast() {
        toastJob?.cancel()
        local.update { it.copy(toastVisible = true) }
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            local.update { it.copy(toastVisible = false) }
        }
    }

    private suspend fun edit(transform: (PlayerProfile) -> PlayerProfile) {
        val day = clock.today()
        val week = clock.week()
        store.mutate { current -> transform(ProfileRules.rollOver(current, day, week)) }
    }

    private fun pushConfigure(stored: PlayerProfile, tier: RiskTier = machine.snapshot.tier) {
        val swing = tier.swingSeconds(machine.snapshot.floors).toFloat()
        scene.submit(StageCommand.Configure(swing, Catalogue.skyMood(stored.activeSky)))
    }

    /** Random building section without an immediate visual repeat. */
    private fun nextStyleId(): Int {
        val choices = Catalogue.blockStyles.filter { it.id != lastStyleId }
        val chosen = (choices.ifEmpty { Catalogue.blockStyles }).random().id
        lastStyleId = chosen
        return chosen
    }

    private fun styleAspect(styleId: Int): Float = SpriteBank.aspect(Catalogue.blockStyle(styleId).asset)

    private fun currentStake(stored: PlayerProfile): Long =
        local.value.stake ?: stored.lastStake

    private fun nextHeadlineId(): Long {
        headlineCounter += 1
        return headlineCounter
    }

    private fun assemble(stored: PlayerProfile, snapshot: RoundSnapshot, view: LocalState): PlayUiState {
        val stake = if (snapshot.isLive) {
            snapshot.stake
        } else {
            Economy.clampStake(view.stake ?: stored.lastStake, stored.credits)
        }
        return PlayUiState(
            credits = stored.credits,
            rank = stored.rank,
            phase = snapshot.phase,
            tier = snapshot.tier,
            stake = stake,
            floors = snapshot.floors,
            combo = snapshot.combo,
            potential = snapshot.potential,
            recentSteps = snapshot.steps.asReversed().take(6),
            headline = view.headline,
            showLowFunds = view.toastVisible,
            bankPanel = view.bankPanel,
            rankUp = view.rankUp,
        )
    }

    private data class LocalState(
        val stake: Long? = null,
        val headline: Headline? = null,
        val toastVisible: Boolean = false,
        val bankPanel: BankPanel? = null,
        val rankUp: RankGain? = null,
    )
}
