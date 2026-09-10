package com.nightcap.camera.engine

enum class StackingMode(val displayName: String, val description: String) {
    PHOTO(
        displayName = "Photo",
        description = "Standard manual photography with ISO Boost and fine tuning"
    ),
    VIDEO(
        displayName = "Video",
        description = "High quality video recording with audio and exposure controls"
    ),
    STARS(
        displayName = "Stars",
        description = "Deep noise reduction and average stacking to reveal the Milky Way and faint stars"
    ),
    STAR_TRAILS(
        displayName = "Star Trails",
        description = "Maximum luminance blending for circular star trails around the celestial pole"
    ),
    LIGHT_TRAILS(
        displayName = "Light Trails",
        description = "Endless exposure for traffic light trails, fireworks, and light painting"
    ),
    METEORS(
        displayName = "Meteors",
        description = "Automatic detection and capture of shooting stars and ISS transit trails"
    )
}
