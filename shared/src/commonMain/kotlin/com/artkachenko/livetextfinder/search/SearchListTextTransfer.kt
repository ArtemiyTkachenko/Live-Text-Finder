package com.artkachenko.livetextfinder.search

internal const val MAX_SEARCH_LIST_TRANSFER_BYTES = 65_536

/** Parses one literal search term or phrase per line from a UTF-8 text file. */
internal fun parseSearchListText(bytes: ByteArray): Result<List<String>> {
    if (bytes.size > MAX_SEARCH_LIST_TRANSFER_BYTES) {
        return transferFailure(
            "Text files can contain at most 65,536 bytes.",
        )
    }

    val decoded = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: Exception) {
        return transferFailure("The file is not valid UTF-8 text.")
    }
    val text = decoded.removePrefix("\uFEFF")
    if (text.contains('\uFEFF')) {
        return transferFailure("A text file can contain only one initial byte order mark.")
    }
    if (text.any { char ->
            (char.code < 0x20 && char != '\t' && char != '\n' && char != '\r') ||
                char.code == 0x7f
        }
    ) {
        return transferFailure("The file contains unsupported control characters.")
    }
    for (index in text.indices) {
        if (text[index] == '\r' && text.getOrNull(index + 1) != '\n') {
            return transferFailure("Use LF or CRLF line endings in the text file.")
        }
    }

    val linesText = text.replace("\r\n", "\n").removeSuffix("\n")
    val lines = linesText.split('\n')
    if (lines.any(String::isEmpty)) {
        return transferFailure("Remove blank lines. Each line must contain a term or phrase.")
    }
    return validateSearchListTerms(lines.joinToString("\n"))
}

/** Serializes canonical saved search terms as UTF-8 with one LF after the final term. */
internal fun serializeSearchListText(terms: List<String>): ByteArray {
    require(terms.isNotEmpty()) { "A search list must contain at least one term." }
    val result = validateSearchListTerms(terms.joinToString("\n"))
    val canonicalTerms = result.getOrElse { error ->
        throw IllegalArgumentException(error.message ?: "Search list terms are invalid.")
    }
    require(canonicalTerms == terms) { "Search list terms must already be canonical." }
    val bytes = try {
        (terms.joinToString("\n") + "\n").encodeToByteArray(
            throwOnInvalidSequence = true,
        )
    } catch (_: Exception) {
        throw IllegalArgumentException("Search list terms must be valid UTF-8 text.")
    }
    val importedTerms = parseSearchListText(bytes).getOrElse { error ->
        throw IllegalArgumentException(error.message ?: "Search list terms cannot be exported.")
    }
    require(importedTerms == terms) { "Search list terms cannot change during text transfer." }
    return bytes
}

/** Suggests a list name from an imported filename or the caller's localized fallback. */
internal fun searchListImportName(displayName: String?, fallback: String): String {
    val withoutExtension = displayName.orEmpty().removeFinalTextExtension()
    return sanitizeSearchListFileName(withoutExtension).ifEmpty {
        sanitizeSearchListFileName(fallback)
    }
}

/** Creates a safe text filename for exporting a saved search list. */
internal fun searchListExportFileName(name: String): String {
    val safeName = sanitizeSearchListFileName(name.removeFinalTextExtension())
        .ifEmpty { "search-list" }
    return "$safeName.txt"
}

private fun String.removeFinalTextExtension(): String =
    if (endsWith(".txt", ignoreCase = true)) dropLast(4) else this

private fun sanitizeSearchListFileName(value: String): String {
    val safe = normalizeRecognizedText(
        value.filterNot { char ->
            char == '/' || char == '\\' || char.code < 0x20 || char.code == 0x7f
        },
    )
    val boundedLength = safe.take(MAX_NAME_LENGTH).let { prefix ->
        if (prefix.lastOrNull()?.isHighSurrogate() == true) prefix.dropLast(1) else prefix
    }
    return boundedLength
}

private fun transferFailure(message: String): Result<List<String>> =
    Result.failure(IllegalArgumentException(message))
