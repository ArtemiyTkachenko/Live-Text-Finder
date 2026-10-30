package com.artkachenko.livetextfinder.search

/** Resolves one deterministic opaque outline colour for each term in an active list. */
internal object SearchHighlightPalette {
    val colors: List<HighlightColor> = listOf(
        0xFFFF5252, 0xFF00E676, 0xFFFFFF00, 0xFF448AFF,
        0xFFFF9100, 0xFFE040FB, 0xFF18FFFF, 0xFFFF4081,
        0xFFC6FF00, 0xFFFF8A80, 0xFF64FFDA, 0xFFEA80FC,
        0xFFFFD180, 0xFFFFFF8D, 0xFFFF8A65, 0xFFCCFF90,
        0xFFF4FF81, 0xFFFFD740, 0xFF82B1FF, 0xFFB388FF,
        0xFFA7FFEB, 0xFFF8BBD0, 0xFFB2FF59, 0xFFFFAB40,
        0xFF80D8FF, 0xFFD1C4E9, 0xFFB9F6CA, 0xFFFFE57F,
        0xFF84FFFF, 0xFFFF80AB, 0xFFCFD8DC, 0xFFAEEA00,
    ).map(::HighlightColor)

    init {
        check(colors.map { it.argb }.toSet().size == 32)
    }

    fun colorsByTerm(
        spec: ActiveSearchSpec,
        manualColor: HighlightColor,
    ): Map<String, HighlightColor> {
        if (spec.terms.size == 1 && spec.terms.single().id == MANUAL_TERM_ID) {
            return mapOf(MANUAL_TERM_ID to manualColor)
        }
        val ordered = spec.terms.sortedWith(
            compareBy<SearchTerm> { normalizeRecognizedText(it.text).lowercase() }
                .thenBy { it.id },
        )
        return ordered.mapIndexed { index, term ->
            term.id to colors[index % colors.size]
        }.toMap()
    }
}
