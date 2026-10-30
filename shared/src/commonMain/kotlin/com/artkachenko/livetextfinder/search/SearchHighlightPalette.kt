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
        val slots = if (spec.terms.all { it.paletteSlot == null }) {
            legacyPaletteSlots(
                spec.terms.map { it.text },
                spec.terms.map { it.id },
            )
        } else {
            val explicitSlots = spec.terms.map { term ->
                checkNotNull(term.paletteSlot) { "A search term has no palette slot." }
            }
            require(explicitSlots.all { it in colors.indices }) {
                "A search term has an invalid palette slot."
            }
            require(explicitSlots.toSet().size == explicitSlots.size) {
                "Search term palette slots must be unique."
            }
            explicitSlots
        }
        return spec.terms.indices.sortedBy { index -> slots[index] }
            .associate { index -> spec.terms[index].id to colors[slots[index]] }
    }
}

/** Assigns slots using the original list palette order for legacy and synthetic search specs. */
internal fun legacyPaletteSlots(
    terms: List<String>,
    termIds: List<String> = terms.indices.map(Int::toString),
): List<Int> {
    require(terms.size == termIds.size)
    val order = terms.indices.sortedWith(
        compareBy<Int> { normalizeRecognizedText(terms[it]).lowercase() }
            .thenBy { termIds[it] },
    )
    return List(terms.size) { index -> order.indexOf(index) }
}

/** Assigns lowest free slots to new terms while retaining every case-insensitive survivor. */
internal fun allocatePaletteSlots(
    terms: List<String>,
    previous: SavedSearchList?,
): List<Int> {
    val slots = MutableList<Int?>(terms.size) { null }
    val used = mutableSetOf<Int>()
    if (previous == null) {
        return legacyPaletteSlots(terms)
    } else {
        require(previous.paletteSlots.size == previous.terms.size &&
            previous.paletteSlots.all { it in SearchHighlightPalette.colors.indices } &&
            previous.paletteSlots.toSet().size == previous.paletteSlots.size
        ) {
            "The existing list has invalid palette slots."
        }
        terms.forEachIndexed { index, term ->
            val priorIndex = previous.terms.indexOfFirst { it.equals(term, ignoreCase = true) }
            val priorSlot = previous.paletteSlots.getOrNull(priorIndex)
            if (priorIndex >= 0 && priorSlot != null &&
                priorSlot in SearchHighlightPalette.colors.indices && used.add(priorSlot)
            ) {
                slots[index] = priorSlot
            }
        }
    }
    terms.indices.filter { slots[it] == null }.forEach { index ->
        val slot = SearchHighlightPalette.colors.indices.firstOrNull { it !in used }
            ?: error("No free highlight color slots remain.")
        slots[index] = slot
        used += slot
    }
    return slots.map { checkNotNull(it) }
}
