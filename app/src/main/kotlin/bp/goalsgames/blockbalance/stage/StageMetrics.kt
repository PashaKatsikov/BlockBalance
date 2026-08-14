package bp.goalsgames.blockbalance.stage

/**
 * World tuning for the yard. Distances are in world units; the renderer decides
 * how many pixels one unit is worth. World Y grows downward, the pavement is at
 * zero and the stack climbs into negative Y.
 */
object StageMetrics {

    /** A block occupies about 29% of a portrait viewport, matching the reference. */
    const val WORLD_WIDTH = 16f

    /** Smallest world height that must stay visible, used to keep wide screens sane. */
    const val MIN_WORLD_HEIGHT = 24f

    const val GROUND_Y = 0f

    /** How far the pavement strip reaches below the ground line. */
    const val STREET_BELOW = 1.9f

    const val BLOCK_WIDTH = 4.7f
    const val BASE_WIDTH = 7.4f

    /** Hoist pivot sits this far above the middle of the viewport. */
    const val PIVOT_ABOVE = 18f
    /** Horizontal travel of the trolley and the tiny visual tilt of its pulley. */
    const val MAX_SWAY_X = 2.8f
    const val MAX_HOOK_TILT_RAD = 0.08f

    /** Rope length from the off-screen pivot to the middle of the pulley. */
    const val HOOK_RADIUS = 3.8f

    /** Pulley plus hook, sized to the lower part of the hook sprite. */
    const val HOOK_WIDTH = 1.25f
    const val HOOK_HEIGHT = 2.15f

    /** Slack between the hook tip and the top edge of the hanging block. */
    const val STRAP_DROP = 0.35f

    /** Share of the rope the winch takes up while it reels the hook back in. */
    const val REEL_TAKEUP = 0.22f
    const val REEL_SECONDS = 0.35f

    const val DROP_START_VY = 6f
    const val GRAVITY = 70f

    /** Sideways slop and lean applied to a block that stays on the stack. */
    const val PLACE_JITTER = 0.7f
    const val TILT_JITTER_RAD = 0.06f
    const val AXIS_PULL = 0.62f
    const val MAX_DRIFT = 1.6f

    const val MISS_DRIFT = 1.4f
    const val MISS_SPIN = 1.8f
    const val MISS_TRIGGER = 0.9f

    const val CAMERA_LEAD = 2.1f
    const val CAMERA_SMOOTH = 4.2f

    /** Bottom slice occupied by the compact action deck. */
    const val DECK_FRACTION = 0.11f

    const val MAX_STEP_SECONDS = 1f / 30f

    const val SHAKE_LAND = 0.30f
    const val SHAKE_MISS = 0.60f
    const val SHAKE_BANK = 0.30f
    const val SHAKE_DECAY = 1.2f
    const val SHAKE_TRAVEL = 0.55f

    const val WOBBLE_START = 0.045f
    const val WOBBLE_DECAY = 2.6f
    const val WOBBLE_SPEED = 12f

    const val DUST_COUNT = 24
    const val SPARK_COUNT = 16
    const val COIN_COUNT = 24

    const val CLOUD_COUNT = 7
}
