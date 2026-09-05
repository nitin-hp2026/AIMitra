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

import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

enum class PromptTemplateInputEditorType {
  SINGLE_SELECT
}

enum class WritingTaskType(val label: String) {
  CREATE(label = "Create new"),
  REWRITE(label = "Rewrite"),
}

enum class OutputType(val label: String) {
  ARTICLE(label = "Article"),
  EMAIL(label = "Email"),
  BLOG_POST(label = "Blog post"),
  REPORT(label = "Report"),
  EXECUTIVE_BRIEF(label = "Executive brief"),
  TECHNICAL_DOC(label = "Technical doc"),
}

enum class AudienceType(val label: String) {
  GENERAL(label = "General readers"),
  TECHNICAL(label = "Technical team"),
  EXECUTIVES(label = "Executives"),
}

enum class WritingToneType(val label: String) {
  NEUTRAL(label = "Neutral"),
  FORMAL(label = "Formal"),
  CONVERSATIONAL(label = "Conversational"),
  PERSUASIVE(label = "Persuasive"),
}

enum class LengthType(val label: String) {
  SHORT(label = "Short"),
  MEDIUM(label = "Medium"),
  LONG(label = "Long"),
}

enum class InputEditorLabel(val label: String) {
  TASK(label = "Task"),
  OUTPUT_TYPE(label = "Output"),
  AUDIENCE(label = "Audience"),
  TONE(label = "Tone"),
  LENGTH(label = "Length"),
  STYLE(label = "Style"),
}

/** Text summarization styles (used when no PDF is attached). */
enum class SummarizationType(val label: String) {
  KEY_BULLET_POINT(label = "Key bullet points (3-5)"),
  SHORT_PARAGRAPH(label = "Short paragraph (1-2 sentences)"),
  CONCISE_SUMMARY(label = "Concise summary (~50 words)"),
  HEADLINE_TITLE(label = "Headline / title"),
  ONE_SENTENCE_SUMMARY(label = "One-sentence summary"),
}

/** Extra style that is only enabled once a PDF has been uploaded. */
const val SUMMARIZE_PDF_STYLE = "Summarize PDF"

open class PromptTemplateInputEditor(
  open val label: String,
  open val type: PromptTemplateInputEditorType,
  open val defaultOption: String = "",
)

/** Single select that shows options in bottom sheet. */
class PromptTemplateSingleSelectInputEditor(
  override val label: String,
  val options: List<String> = listOf(),
  override val defaultOption: String = "",
) :
  PromptTemplateInputEditor(
    label = label,
    type = PromptTemplateInputEditorType.SINGLE_SELECT,
    defaultOption = defaultOption,
  )

data class PromptTemplateConfig(val inputEditors: List<PromptTemplateInputEditor> = listOf())

private val GEMINI_GRADIENT_STYLE =
  SpanStyle(
    brush = linearGradient(colors = listOf(Color(0xFF4285f4), Color(0xFF9b72cb), Color(0xFFd96570)))
  )

@Suppress("ImmutableEnum")
enum class PromptTemplateType(
  val label: String,
  val config: PromptTemplateConfig,
  val genFullPrompt: (userInput: String, inputEditorValues: Map<String, Any>) -> AnnotatedString =
    { _, _ ->
      AnnotatedString("")
    },
  val examplePrompts: List<String> = listOf(),
) {
  AI_WRITING(
    label = "AI Writing",
    config =
      PromptTemplateConfig(
        inputEditors =
          listOf(
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.TASK.label,
              options = WritingTaskType.entries.map { it.label },
              defaultOption = WritingTaskType.CREATE.label,
            ),
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.OUTPUT_TYPE.label,
              options = OutputType.entries.map { it.label },
              defaultOption = OutputType.ARTICLE.label,
            ),
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.AUDIENCE.label,
              options = AudienceType.entries.map { it.label },
              defaultOption = AudienceType.GENERAL.label,
            ),
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.TONE.label,
              options = WritingToneType.entries.map { it.label },
              defaultOption = WritingToneType.NEUTRAL.label,
            ),
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.LENGTH.label,
              options = LengthType.entries.map { it.label },
              defaultOption = LengthType.MEDIUM.label,
            ),
          )
      ),
    genFullPrompt = { userInput, inputEditorValues ->
      val task = inputEditorValues[InputEditorLabel.TASK.label] as? String ?: "Create new"
      val outputType = inputEditorValues[InputEditorLabel.OUTPUT_TYPE.label] as? String ?: "Article"
      val audience = inputEditorValues[InputEditorLabel.AUDIENCE.label] as? String ?: "General readers"
      val tone = inputEditorValues[InputEditorLabel.TONE.label] as? String ?: "Neutral"
      val length = inputEditorValues[InputEditorLabel.LENGTH.label] as? String ?: "Medium"
      buildAnnotatedString {
        withStyle(GEMINI_GRADIENT_STYLE) {
          append(
            buildWritingInstruction(
              task = task,
              outputType = outputType,
              audience = audience,
              tone = tone,
              length = length,
            )
          )
        }
        append(userInput)
      }
    },
    examplePrompts =
      listOf(
        "Our Q3 sales grew 18% led by the new mobile app; churn dropped to 4%; we hired 12 engineers and shipped 3 major features.",
        "Notes for a blog post: on-device AI keeps data private, works offline, and reduces cloud costs.",
        "Draft an email: reschedule Thursday's design review to next Monday 10am; agenda unchanged.",
        "Key points for a report: renewable capacity up 22%, storage costs down 30%, grid upgrades still the main bottleneck.",
      ),
  ),
  SUMMARIZE_TEXT(
    label = "AI Summarize",
    config =
      PromptTemplateConfig(
        inputEditors =
          listOf(
            PromptTemplateSingleSelectInputEditor(
              label = InputEditorLabel.STYLE.label,
              // All text styles, plus the PDF style (disabled until a PDF is uploaded).
              options = SummarizationType.entries.map { it.label } + SUMMARIZE_PDF_STYLE,
              defaultOption = SummarizationType.KEY_BULLET_POINT.label,
            )
          )
      ),
    genFullPrompt = { userInput, inputEditorValues ->
      val style =
        inputEditorValues[InputEditorLabel.STYLE.label] as? String
          ?: SummarizationType.KEY_BULLET_POINT.label
      buildAnnotatedString {
        withStyle(GEMINI_GRADIENT_STYLE) {
          append("Please summarize the following in ${style.lowercase()}: ")
        }
        append(userInput)
      }
    },
    examplePrompts =
      listOf(
        "The new Pixel phone features an advanced camera system with improved low-light performance and AI-powered editing tools. The display is brighter and more energy-efficient. It runs on the latest Tensor chip, offering faster processing and enhanced security features. Battery life has also been extended, providing all-day power for most users.",
        "Beginning this Friday, January 24, giant pandas Bao Li and Qing Bao are officially on view to the public at the Smithsonian’s National Zoo and Conservation Biology Institute (NZCBI). The 3-year-old bears arrived in Washington this past October, undergoing a quarantine period before making their debut. Under NZCBI’s new agreement with the CWCA, Qing Bao and Bao Li will remain in the United States for ten years, until April 2034, in exchange for an annual fee of \$1 million.",
      ),
  ),
}

private fun lengthGuideFor(length: String): String =
  when (length) {
    "Short" -> "keep it short"
    "Long" -> "make it thorough and detailed"
    else -> "moderate length"
  }

private fun actionVerbFor(task: String): String =
  when (task) {
    "Rewrite" -> "Rewrite and improve the request below into"
    else -> "Write"
  }

/**
 * Builds a direct, artifact-first writing instruction. Each output type asks the model to produce
 * the finished artifact itself (an actual email, report, etc.) — not an explanation about it —
 * while Task, Audience, Tone and Length are always applied. Kept as a single-pass instruction so
 * output streams instantly.
 */
private fun buildWritingInstruction(
  task: String,
  outputType: String,
  audience: String,
  tone: String,
  length: String,
): String {
  val type = outputType.lowercase()
  val article = if (type.firstOrNull() in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
  val action = actionVerbFor(task)
  val format =
    when (outputType) {
      "Email" ->
        "Structure it as a real email: a Subject line, greeting, a concise body, and a sign-off."
      "Blog post" ->
        "Format in markdown: a title (#), a short intro, a few ## sections, and a brief conclusion."
      "Report" ->
        "Format in markdown with sections: Summary, Details, and Recommendations."
      "Executive brief" ->
        "Keep it tight: one bottom-line sentence, then 3-5 bullet points."
      "Technical doc" ->
        "Format in markdown: Overview, numbered Steps, and Notes. Use code blocks where useful."
      else -> "Format in markdown: a title (#), intro, a few sections, and a short conclusion."
    }
  return "$action $article ${type} for $audience in a ${tone.lowercase()} tone (${lengthGuideFor(length)}). " +
    "$format\n" +
    "Write the finished $type itself that fulfils the request below. Output ONLY the $type — do " +
    "NOT explain, define, or add any commentary before or after it.\n\n" +
    "REQUEST:\n"
}
