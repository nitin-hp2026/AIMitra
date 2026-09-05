/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmsingleturn

/**
 * Fast, on-device post-processor for math-heavy PDF summaries.
 *
 * Runs only when the source PDF (or model output) looks formula-rich — no network, no extra model
 * call. Converts common LaTeX fragments the small on-device model still emits into readable Unicode
 * and fenced code blocks.
 */
object MathTextProcessor {

  private val LATEX_COMMAND =
    Regex("""\\(?:frac|sum|int|sqrt|alpha|beta|gamma|delta|theta|lambda|mu|sigma|pi|times|div|cdot|leq|geq|neq|approx|infty|partial|nabla|mathbf|mathrm|text)\b""")

  private val INLINE_MATH = Regex("""\$[^$\n]+\$""")

  private val EQUATION_LIKE =
    Regex("""(?i)(?:equation|formula|theorem|∑|∫|√|≈|≤|≥|π|\^[0-9{]|[a-z]\s*=\s*[^,\n]{3,})""")

  /** True when the PDF source text likely contains formulas worth post-processing. */
  fun pdfTextContainsMath(text: String): Boolean {
    if (text.isBlank()) return false
    val sample = text.take(12000)
    if (LATEX_COMMAND.containsMatchIn(sample)) return true
    if (INLINE_MATH.containsMatchIn(sample)) return true
    val equationHits = EQUATION_LIKE.findAll(sample).count()
    return equationHits >= 3
  }

  /** True when streamed model output still contains fixable LaTeX/math garbage. */
  fun outputNeedsMathFix(text: String): Boolean {
    if (text.isBlank()) return false
    return LATEX_COMMAND.containsMatchIn(text) ||
      INLINE_MATH.containsMatchIn(text) ||
      text.contains("\\(") ||
      text.contains("\\)")
  }

  /** Post-process a finished PDF summary. No-op when no math fix is needed. */
  fun postProcessPdfSummary(sourcePdfText: String, summary: String): String {
    var result = normalizeSummaryHeader(summary)
    if (pdfTextContainsMath(sourcePdfText) || outputNeedsMathFix(result)) {
      result = fixMathFormatting(result)
    }
    return result
  }

  /** Ensures the first line is "## Summary — <Type>" (fallback: Miscellaneous). */
  fun normalizeSummaryHeader(summary: String): String {
    val trimmed = summary.trimStart()
    if (trimmed.startsWith("## Summary —")) return summary

    val lines = summary.lines()
    if (lines.isEmpty()) return summary

    val first = lines.first().trim()
    val typeFromOld =
      Regex("""^\*\*Document type:\*\*\s*(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(first)
        ?.groupValues
        ?.get(1)
        ?.trim()
    val typeFromPlain =
      Regex("""^Document type:\s*(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(first)
        ?.groupValues
        ?.get(1)
        ?.trim()

    val type = typeFromOld ?: typeFromPlain
    if (type != null) {
      val rest = lines.drop(1).joinToString("\n").trimStart()
      return if (rest.isEmpty()) "## Summary — $type" else "## Summary — $type\n$rest"
    }

    return summary
  }

  /** Converts common LaTeX / math fragments to readable markdown-friendly text. */
  fun fixMathFormatting(text: String): String {
    var result = text

    // Strip display/inline math delimiters but keep inner content for conversion.
    result = result.replace(Regex("""\$\$([^$]+)\$\$""")) { convertMathFragment(it.groupValues[1]) }
    result = result.replace(Regex("""\$([^$\n]+)\$""")) { convertMathFragment(it.groupValues[1]) }
    result = result.replace(Regex("""\\\(([^)]+)\\\)""")) { convertMathFragment(it.groupValues[1]) }
    result = result.replace(Regex("""\\\[([^\]]+)\\\]""")) { convertMathFragment(it.groupValues[1]) }

    // \frac{a}{b} → (a)/(b)
    var prev = ""
    while (prev != result) {
      prev = result
      result =
        result.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
    }

    result = result.replace(Regex("""\\sqrt\{([^{}]+)\}""")) { "√(${it.groupValues[1]})" }
    result = result.replace("""\\sqrt""", "√")

    // Common symbols and greek letters.
    val replacements =
      mapOf(
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\neq" to "≠",
        "\\approx" to "≈",
        "\\infty" to "∞",
        "\\sum" to "Σ",
        "\\int" to "∫",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\pi" to "π",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\sigma" to "σ",
        "^2" to "²",
        "^3" to "³",
        "^4" to "⁴",
        "_0" to "₀",
        "_1" to "₁",
        "_2" to "₂",
        "_n" to "ₙ",
      )
    for ((from, to) in replacements) {
      result = result.replace(from, to)
    }

    // Wrap lines that still look like raw formulas in code blocks for readability.
    result =
      result
        .lines()
        .joinToString("\n") { line ->
          val trimmed = line.trim()
          if (
            trimmed.isNotEmpty() &&
              !trimmed.startsWith("#") &&
              !trimmed.startsWith("-") &&
              !trimmed.startsWith("*") &&
              (trimmed.contains("=") && trimmed.count { it == '=' } >= 1) &&
              (trimmed.contains("/") || trimmed.contains("^") || trimmed.contains("√") || trimmed.length > 40)
          ) {
            if (trimmed.startsWith("```")) line else "```\n$trimmed\n```"
          } else {
            line
          }
        }

    return result.trim()
  }

  private fun convertMathFragment(fragment: String): String = fixMathFormatting(fragment.trim())
}
