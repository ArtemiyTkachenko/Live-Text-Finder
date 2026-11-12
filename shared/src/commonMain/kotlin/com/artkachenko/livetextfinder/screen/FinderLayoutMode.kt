package com.artkachenko.livetextfinder.screen

/** The mutually exclusive arrangements of the main screen's bars, viewport and footer. */
internal enum class FinderLayoutMode {
    /** Live camera with the search and capture dock. */
    Live,

    /** Live camera with the scan-area draft actions in place of the dock. */
    AreaEditing,

    /** Saved-list manager with the inline search bar and capture controls. */
    ListManager,

    /** Frozen or imported image with inspection controls above the dock. */
    StillInspection,
    ;

    /** True when the app bar and bottom dock replace the inline search bar. */
    val usesDockChrome: Boolean get() = this != ListManager

    /** True when the bottom dock itself is shown. */
    val showsDock: Boolean get() = this == Live || this == StillInspection

    /** True when the live camera, without an open list manager, is the displayed source. */
    val isLiveCamera: Boolean get() = this == Live || this == AreaEditing
}

/** Resolves the layout mode; a retained still source outranks the manager and area editor. */
internal fun finderLayoutMode(
    hasStillSource: Boolean,
    managerOpen: Boolean,
    areaEditing: Boolean,
): FinderLayoutMode = when {
    hasStillSource -> FinderLayoutMode.StillInspection
    managerOpen -> FinderLayoutMode.ListManager
    areaEditing -> FinderLayoutMode.AreaEditing
    else -> FinderLayoutMode.Live
}
