package com.bantai.service

import android.view.accessibility.AccessibilityNodeInfo
import com.bantai.model.ScreenContext

/**
 * Extracts visible text labels and interactive elements from the active window
 * using Android AccessibilityNodeInfo.
 *
 * Constraints:
 * - Traversal limit: up to 30 nodes
 * - Character limit: total text up to 600 characters
 * - Deduplication & filtering: drops blanks, whitespace-only, and duplicates
 * - Recycles AccessibilityNodeInfo instances properly to avoid memory leaks
 */
class ScreenContextReader {

    companion object {
        const val MAX_NODES = 30
        const val MAX_CHARACTERS = 600
    }

    /**
     * Traverses the given root AccessibilityNodeInfo and returns a ScreenContext
     * containing the current package/app name and filtered labels.
     */
    fun extractScreenContext(
        rootNode: AccessibilityNodeInfo?,
        packageName: CharSequence? = null
    ): ScreenContext {
        if (rootNode == null) {
            return ScreenContext(
                appName = packageName?.toString().orEmpty(),
                labels = emptyList()
            )
        }

        val app = packageName?.toString()
            ?: rootNode.packageName?.toString().orEmpty()

        val labels = mutableListOf<String>()
        val seenTexts = mutableSetOf<String>()
        var totalLength = 0
        var visitedNodes = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (visitedNodes >= MAX_NODES || totalLength >= MAX_CHARACTERS) return

            visitedNodes++

            // Extract visible text or content description
            val rawText = node.text?.toString() ?: node.contentDescription?.toString()
            val cleanText = rawText?.trim()

            if (!cleanText.isNullOrBlank()) {
                val normalized = cleanText.replace(Regex("\\s+"), " ")
                if (seenTexts.add(normalized)) {
                    val remainingBudget = MAX_CHARACTERS - totalLength
                    if (remainingBudget > 0) {
                        val toAdd = if (normalized.length <= remainingBudget) {
                            normalized
                        } else {
                            normalized.take(remainingBudget)
                        }
                        labels.add(toAdd)
                        totalLength += toAdd.length
                    }
                }
            }

            // Traverse children
            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (visitedNodes >= MAX_NODES || totalLength >= MAX_CHARACTERS) break
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) {
                    try {
                        traverse(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }

        traverse(rootNode)

        return ScreenContext(
            appName = app,
            labels = labels
        )
    }
}
