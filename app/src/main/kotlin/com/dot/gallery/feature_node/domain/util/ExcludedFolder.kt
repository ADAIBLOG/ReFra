package com.dot.gallery.feature_node.domain.util

import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media

/**
 * Matching helpers for the "Excluded Folders" setting.
 *
 * A pattern is either:
 *  - a literal folder path (e.g. `/storage/emulated/0/Blog/` or `Blog`) that excludes the
 *    folder itself and every descendant, or
 *  - a glob pattern (e.g. `*/Blog/**`, `*/assets/**`) for more flexible matching.
 *
 * Matching is case-insensitive and normalizes both `/` and `\` separators. A literal pattern
 * matches the media path, MediaStore relative path, or the resolved album path.
 */

private fun normalizePath(value: String): String =
    value.trim().replace('\\', '/').trim('/')

private fun hasGlobMeta(pattern: String): Boolean =
    pattern.any { it == '*' || it == '?' || it == '[' || it == '{' }

private fun globToRegex(pattern: String): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < pattern.length) {
        when (val c = pattern[i]) {
            '*' -> {
                if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                    // "**" matches any number of characters, including directory separators.
                    sb.append(".*")
                    i++
                } else {
                    // "*" matches any number of characters except a directory separator.
                    sb.append("[^/]*")
                }
            }
            '?' -> sb.append("[^/]")
            '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                sb.append('\\').append(c)
            else -> sb.append(c)
        }
        i++
    }
    sb.append('$')
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

/**
 * Returns true when [candidate] (a media/album path, relative path, or volume) is inside the
 * folder described by [pattern].
 */
fun matchesExcludedFolder(candidate: String, pattern: String): Boolean {
    val normalizedPattern = normalizePath(pattern)
    if (normalizedPattern.isEmpty()) return false
    val normalizedCandidate = normalizePath(candidate)
    if (normalizedCandidate.isEmpty()) return false

    if (!hasGlobMeta(normalizedPattern)) {
        return normalizedCandidate == normalizedPattern ||
            normalizedCandidate.startsWith("$normalizedPattern/")
    }

    val regex = runCatching { globToRegex(normalizedPattern) }.getOrNull() ?: return false
    return normalizedCandidate.matches(regex)
}

fun Media.isInExcludedFolder(excludedFolders: Set<String>): Boolean =
    excludedFolders.any { pattern ->
        matchesExcludedFolder(path, pattern) ||
            matchesExcludedFolder(relativePath, pattern) ||
            matchesExcludedFolder(volume, pattern)
    }

fun Album.isInExcludedFolder(excludedFolders: Set<String>): Boolean =
    excludedFolders.any { pattern ->
        matchesExcludedFolder(absolutePath, pattern) ||
            matchesExcludedFolder(relativePath, pattern) ||
            matchesExcludedFolder(pathToThumbnail, pattern) ||
            matchesExcludedFolder(volume, pattern)
    }
