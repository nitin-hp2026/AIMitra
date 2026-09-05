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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.MessageBubbleShape
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.bodyLargeNarrow
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val promptTemplateTypes: List<PromptTemplateType> = PromptTemplateType.entries
private val TAB_TITLES = PromptTemplateType.entries.map { it.label }
private val ICON_BUTTON_SIZE = 42.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplatesPanel(
  task: Task,
  model: Model,
  viewModel: LlmSingleTurnViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSend: () -> Unit,
  onStopButtonClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedPromptTemplateType = uiState.selectedPromptTemplateType
  val inProgress = uiState.inProgress
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  var curTextInputContent by remember { mutableStateOf("") }
  val inputEditorValues: SnapshotStateMap<String, Any> = remember { mutableStateMapOf() }
  val fullPrompt by remember {
    derivedStateOf {
      uiState.selectedPromptTemplateType.genFullPrompt(curTextInputContent, inputEditorValues)
    }
  }
  val context = LocalContext.current
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[model.name]

  // PDF upload state (used by the "Summarize text" template).
  var pdfResult by remember { mutableStateOf<PdfExtractionResult?>(null) }
  var pdfLoading by remember { mutableStateOf(false) }
  var pdfMessage by remember { mutableStateOf<String?>(null) }
  var showAddMenu by remember { mutableStateOf(false) }

  val isSummarizeTemplate = selectedPromptTemplateType == PromptTemplateType.SUMMARIZE_TEXT
  val isPdfSummarize =
    isSummarizeTemplate &&
      pdfResult != null &&
      inputEditorValues[InputEditorLabel.STYLE.label] == SUMMARIZE_PDF_STYLE

  val clearPdf = {
    pdfResult = null
    pdfMessage = null
    inputEditorValues[InputEditorLabel.STYLE.label] = SummarizationType.KEY_BULLET_POINT.label
  }

  val pdfPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) {
        pdfLoading = true
        pdfMessage = null
        scope.launch {
          val result = extractPdfText(context = context, uri = uri)
          pdfLoading = false
          if (result == null) {
            pdfResult = null
            pdfMessage = "Couldn't read text from this PDF. Try a text-based (non-scanned) file."
          } else {
            pdfResult = result
            pdfMessage =
              if (result.totalPages > MAX_PDF_PAGES) {
                "This PDF has ${result.totalPages} pages; only the first $MAX_PDF_PAGES were added."
              } else {
                null
              }
            // Automatically switch the style to "Summarize PDF".
            inputEditorValues[InputEditorLabel.STYLE.label] = SUMMARIZE_PDF_STYLE
          }
        }
      }
    }

  val canSend = if (isPdfSummarize) true else curTextInputContent.isNotEmpty()

  // Builds the text sent to the model. Evaluated lazily on send (never during composition) so the
  // template's input-editor values are guaranteed to be populated first.
  val buildSendText: () -> String = {
    val pdf = pdfResult
    if (isPdfSummarize && pdf != null) {
      buildPdfSummaryPrompt(pdf)
    } else {
      fullPrompt.text
    }
  }

  // Update input editor values when prompt template changes.
  LaunchedEffect(selectedPromptTemplateType) {
    for (config in selectedPromptTemplateType.config.inputEditors) {
      inputEditorValues[config.label] = config.defaultOption
    }
    expandedStates.clear()
  }

  var showExamplePromptBottomSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val bubbleBorderRadius = dimensionResource(R.dimen.chat_bubble_corner_radius)

  Column(modifier = modifier) {
    // Scrollable tab row for all prompt templates.
    PrimaryScrollableTabRow(selectedTabIndex = selectedTabIndex) {
      TAB_TITLES.forEachIndexed { index, title ->
        Tab(
          selected = selectedTabIndex == index,
          enabled = !inProgress,
          onClick = {
            // Clear input when tab changes.
            curTextInputContent = ""
            // Clear any uploaded PDF.
            pdfResult = null
            pdfMessage = null

            selectedTabIndex = index
            viewModel.selectPromptTemplate(
              model = model,
              promptTemplateType = promptTemplateTypes[index],
            )
          },
          text = {
            Text(
              text = title,
              modifier = Modifier.alpha(if (inProgress) 0.5f else 1f),
              color =
                if (selectedTabIndex == index) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
        )
      }
    }

    // Content.
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
      // Input editor row (horizontally scrollable to fit multiple selectors).
      if (selectedPromptTemplateType.config.inputEditors.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
          for (inputEditor in selectedPromptTemplateType.config.inputEditors) {
            when (inputEditor.type) {
              PromptTemplateInputEditorType.SINGLE_SELECT -> {
                val isStyleEditor =
                  isSummarizeTemplate && inputEditor.label == InputEditorLabel.STYLE.label
                SingleSelectButton(
                  config = inputEditor as PromptTemplateSingleSelectInputEditor,
                  onSelected = { option -> inputEditorValues[inputEditor.label] = option },
                  // "Summarize PDF" is only selectable once a PDF has been uploaded.
                  disabledOptions =
                    if (isStyleEditor && pdfResult == null) setOf(SUMMARIZE_PDF_STYLE)
                    else emptySet(),
                  selectedValue =
                    if (isStyleEditor) inputEditorValues[inputEditor.label] as? String else null,
                )
              }
            }
          }
        }
      }

      // Uploaded PDF status (Summarize text template only).
      if (isSummarizeTemplate && (pdfResult != null || pdfLoading || pdfMessage != null)) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
          if (pdfLoading) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Text("Reading PDF…", style = MaterialTheme.typography.labelMedium)
            }
          }

          pdfResult?.let { pdf ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier =
                Modifier.clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondaryContainer)
                  .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
              Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
              Text(
                "${pdf.fileName} · ${pdf.pagesRead} page(s)",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              IconButton(onClick = { clearPdf() }, modifier = Modifier.size(24.dp)) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = "Remove PDF",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          pdfMessage?.let { message ->
            Text(
              message,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
          }
        }
      }

      // Text input box.
      Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.weight(1f)) {
        Column(
          modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).clickable(
              interactionSource = interactionSource,
              indication = null, // Disable the ripple effect
            ) {
              // Request focus on the TextField when the Column is clicked
              focusRequester.requestFocus()
            }
        ) {
          if (isSummarizeTemplate && pdfResult != null) {
            // Content input is collapsed while a PDF is attached: the PDF is the source.
            Text(
              "The uploaded PDF will be summarized. Remove the PDF to type your own text.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(16.dp)
                  .padding(bottom = 40.dp)
                  .clip(MessageBubbleShape(radius = bubbleBorderRadius))
                  .background(MaterialTheme.customColors.agentBubbleBgColor)
                  .padding(16.dp),
            )
          } else {
            val cdContentInput = stringResource(R.string.cd_content_input_field)
            TextField(
              value = curTextInputContent,
              onValueChange = { curTextInputContent = it },
              colors =
                TextFieldDefaults.colors(
                  unfocusedContainerColor = Color.Transparent,
                  focusedContainerColor = Color.Transparent,
                  focusedIndicatorColor = Color.Transparent,
                  unfocusedIndicatorColor = Color.Transparent,
                  disabledIndicatorColor = Color.Transparent,
                  disabledContainerColor = Color.Transparent,
                ),
              textStyle = bodyLargeNarrow,
              placeholder = { Text("Enter content") },
              modifier =
                Modifier.padding(bottom = 40.dp).focusRequester(focusRequester).semantics {
                  contentDescription = cdContentInput
                },
            )
          }
        }

        // Text action row.
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp),
        ) {
          Spacer(modifier = Modifier.weight(1f))

          // Add button: for the Summarize template it opens a menu to upload a PDF or insert an
          // example; for other templates it inserts an example prompt.
          Box {
            OutlinedIconButton(
              enabled = !inProgress,
              onClick = {
                if (isSummarizeTemplate) {
                  showAddMenu = true
                } else {
                  showExamplePromptBottomSheet = true
                }
              },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.customColors.agentBubbleBgColor,
                  disabledContainerColor =
                    MaterialTheme.customColors.agentBubbleBgColor.copy(alpha = 0.4f),
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                ),
              border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surface),
              modifier = Modifier.size(ICON_BUTTON_SIZE),
            ) {
              Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_add_example_prompt_icon),
                modifier = Modifier.size(20.dp),
              )
            }

            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
              DropdownMenuItem(
                text = { Text("Upload PDF (max $MAX_PDF_PAGES pages)") },
                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                onClick = {
                  showAddMenu = false
                  pdfPickerLauncher.launch("application/pdf")
                },
              )
              DropdownMenuItem(
                text = { Text("Insert example") },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                onClick = {
                  showAddMenu = false
                  showExamplePromptBottomSheet = true
                },
              )
            }
          }

          val modelInitializing =
            modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
          if (inProgress && !modelInitializing && !uiState.preparing) {
            IconButton(
              onClick = { onStopButtonClicked(model) },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
              modifier = Modifier.size(ICON_BUTTON_SIZE),
            ) {
              Icon(
                Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.cd_stop_icon),
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          } else {
            // Send button
            OutlinedIconButton(
              enabled = !inProgress && canSend,
              onClick = {
                focusManager.clearFocus()
                val templateType = selectedPromptTemplateType
                val pdf = pdfResult
                val passes: List<PipelinePass> =
                  when {
                    isPdfSummarize && pdf != null -> {
                      // Instant single pass for short PDFs; hierarchical map-reduce only for long
                      // ones (> 5 pages).
                      val chunks = if (pdf.pagesRead > 5) chunkPdfText(pdf.text) else listOf(pdf.text)
                      if (chunks.size <= 1) {
                        listOf(PipelinePass("") { buildPdfSummaryPrompt(pdf) })
                      } else {
                        buildList {
                          chunks.forEachIndexed { i, chunk ->
                            add(
                              PipelinePass("Summarizing section ${i + 1} of ${chunks.size}…") {
                                buildChunkSummaryPrompt(chunk, i + 1, chunks.size)
                              }
                            )
                          }
                          add(
                            PipelinePass("Combining summaries…") { prior ->
                              buildPdfReducePrompt(pdf, prior)
                            }
                          )
                        }
                      }
                    }
                    // Everything else (AI Writing, text summarize, code) is a single streaming pass.
                    else -> {
                      val text = buildSendText()
                      listOf(PipelinePass("") { text })
                    }
                  }
                val pdfPostProcess: ((String) -> String)? =
                  if (isPdfSummarize && pdf != null) {
                    { summary -> MathTextProcessor.postProcessPdfSummary(pdf.text, summary) }
                  } else {
                    null
                  }
                viewModel.runPipeline(
                  task = task,
                  model = model,
                  templateType = templateType,
                  passes = passes,
                  postProcess = pdfPostProcess,
                )
                onSend()
              },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  disabledContainerColor =
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                ),
              border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surface),
              modifier = Modifier.size(ICON_BUTTON_SIZE),
            ) {
              Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(R.string.cd_send_prompt_icon),
                modifier = Modifier.size(20.dp).offset(x = 2.dp),
              )
            }
          }
        }
      }
    }
  }

  if (showExamplePromptBottomSheet) {
    ModalBottomSheet(
      onDismissRequest = { showExamplePromptBottomSheet = false },
      sheetState = sheetState,
      modifier = Modifier.wrapContentHeight(),
    ) {
      Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // Title
        Text(
          "Select an example",
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          style = MaterialTheme.typography.titleLarge,
        )

        // Examples
        for (prompt in selectedPromptTemplateType.examplePrompts) {
          var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }
          val hasOverflow =
            remember(textLayoutResultState) { textLayoutResultState?.hasVisualOverflow ?: false }
          val isExpanded = expandedStates[prompt] ?: false

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  curTextInputContent = prompt
                  scope.launch {
                    // Give it sometime to show the click effect.
                    delay(200)
                    showExamplePromptBottomSheet = false
                  }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Outlined.Description, contentDescription = null)
              Text(
                prompt,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                onTextLayout = { textLayoutResultState = it },
              )
            }

            if (hasOverflow && !isExpanded) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
              ) {
                Box(
                  modifier =
                    Modifier.padding(end = 16.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                      .clickable { expandedStates[prompt] = true }
                      .padding(vertical = 1.dp, horizontal = 6.dp)
                ) {
                  Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.cd_expand_icon),
                    modifier = Modifier.size(12.dp),
                  )
                }
              }
            } else if (isExpanded) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
              ) {
                Box(
                  modifier =
                    Modifier.padding(end = 16.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                      .clickable { expandedStates[prompt] = false }
                      .padding(vertical = 1.dp, horizontal = 6.dp)
                ) {
                  Icon(
                    Icons.Outlined.ExpandLess,
                    contentDescription = stringResource(R.string.cd_collapse_icon),
                    modifier = Modifier.size(12.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/** Word budget scaled by document length (page count). */
private fun wordBudgetForPages(pages: Int): String =
  when {
    pages <= 2 -> "about 80-120 words"
    pages <= 4 -> "about 150-220 words"
    pages == 5 -> "about 250-320 words"
    pages <= 8 -> "about 350-480 words"
    else -> "about 500-650 words"
  }

/** Tells the model to classify the document and shape the summary to it. */
private const val DOC_TYPE_GUIDANCE =
  "Classify the document into exactly ONE type from this list: Medical, Machine Learning / AI, " +
    "Science, Finance, Legal, Educational, Technical, Business, News, Resume, Engineering. " +
    "If none clearly fit, use \"Miscellaneous\". Then tailor the summary and its section headings " +
    "to that type:\n" +
    "- Medical: conditions, findings, treatments/dosages, and cautions.\n" +
    "- Machine Learning / AI: models, datasets, metrics, methods, results, and limitations.\n" +
    "- Science: hypothesis, methods, data, results, and conclusions.\n" +
    "- Finance/Business: key figures, metrics, growth/trends, risks, and recommendations.\n" +
    "- Legal: parties, obligations, dates, key clauses, and implications.\n" +
    "- Educational: core concepts, definitions, methods, findings, and takeaways.\n" +
    "- Technical/Engineering: components, procedures, specifications, results, and caveats.\n" +
    "- News: the who, what, when, where, why and the main points.\n" +
    "- Resume: skills, experience, education, and notable achievements.\n"

/** First output line: visible document-type header the user sees at the top of every summary. */
private const val DOC_TYPE_HEADER_INSTRUCTION =
  "The VERY FIRST line of your output must be exactly: \"## Summary — <the chosen type>\" " +
    "(use \"Miscellaneous\" if unsure). Example: \"## Summary — Finance\" or " +
    "\"## Summary — Machine Learning / AI\". Then continue with the sections below.\n"

/** Ensures math and structured content render cleanly in the markdown viewer (no raw LaTeX). */
private const val MATH_FORMAT_GUIDANCE =
  "Formatting rules: render any math, equations or formulas in clean, readable form using Unicode " +
    "symbols (× ÷ √ ≈ ≤ ≥ π ² ³ ₁ ₂ →) and put multi-line or complex formulas inside fenced code " +
    "blocks. Do NOT output raw LaTeX commands (e.g. \\frac, \\sum, \\alpha, \$...\$) — they will not " +
    "render. Keep numbers, units and symbols accurate. Use markdown tables for tabular data.\n"

/**
 * Builds a single-pass, page-count-aware, document-type-aware summarization prompt for a PDF (used
 * for short PDFs so output streams instantly). The source text is capped to fit the context window.
 */
private fun buildPdfSummaryPrompt(pdf: PdfExtractionResult): String {
  val pages = pdf.pagesRead
  val wordBudget = wordBudgetForPages(pages)

  val maxChars = 40000
  val truncated = pdf.text.length > maxChars
  val sourceText = if (truncated) pdf.text.substring(0, maxChars) else pdf.text

  return buildString {
    append("You are an expert document summarizer. The document is $pages page(s) long.\n")
    append(DOC_TYPE_GUIDANCE)
    append(MATH_FORMAT_GUIDANCE)
    append("\nWrite a summary of $wordBudget scaled to this length.\n")
    append(DOC_TYPE_HEADER_INSTRUCTION)
    append("Immediately after that line, write the summary paragraph (no second \"## Summary\" heading).\n")
    append("Then add:\n")
    append("## Key points  (bullets, count scaled to the document length)\n")
    append("Then add 1-2 extra sections most relevant to the document type (e.g. \"Figures & metrics\", ")
    append("\"Key terms\", \"Parties & dates\", \"Steps\", \"Results\").\n")
    append("Output ONLY the summary — no preamble or meta commentary.\n")
    if (truncated) append("Note: the document was long and was truncated for length.\n")
    append("\nDOCUMENT:\n")
    append(sourceText)
  }
}

private const val MAX_CHUNK_CHARS = 5000
private const val MAX_CHUNKS = 6

/** Splits PDF text into paragraph-aligned chunks (bounded to [MAX_CHUNKS]) for map-reduce. */
private fun chunkPdfText(text: String): List<String> {
  val trimmed = text.trim()
  if (trimmed.length <= MAX_CHUNK_CHARS) return listOf(trimmed)

  val paragraphs = trimmed.split(Regex("\\n\\s*\\n"))
  val chunks = mutableListOf<String>()
  val current = StringBuilder()
  for (p in paragraphs) {
    if (current.isNotEmpty() && current.length + p.length > MAX_CHUNK_CHARS) {
      chunks.add(current.toString().trim())
      current.clear()
    }
    if (p.length > MAX_CHUNK_CHARS) {
      var i = 0
      while (i < p.length) {
        val end = minOf(i + MAX_CHUNK_CHARS, p.length)
        chunks.add(p.substring(i, end))
        i = end
      }
    } else {
      if (current.isNotEmpty()) current.append("\n\n")
      current.append(p)
    }
  }
  if (current.isNotEmpty()) chunks.add(current.toString().trim())

  // Bound the number of passes: fold any overflow into the last kept chunk.
  if (chunks.size > MAX_CHUNKS) {
    val kept = chunks.take(MAX_CHUNKS - 1).toMutableList()
    val rest = chunks.drop(MAX_CHUNKS - 1).joinToString("\n\n").take(MAX_CHUNK_CHARS * 2)
    kept.add(rest)
    return kept
  }
  return chunks
}

/** Map step: summarize one section into concise bullets. */
private fun buildChunkSummaryPrompt(chunk: String, index: Int, total: Int): String =
  "You are summarizing part $index of $total of a longer document.\n" +
    MATH_FORMAT_GUIDANCE +
    "Extract the key facts, figures, names and terms as 4-7 concise bullet points. " +
    "Do not add commentary or a preamble.\n\n" +
    "SECTION $index:\n$chunk"

/** Reduce step: synthesize the section summaries into one final structured summary. */
private fun buildPdfReducePrompt(pdf: PdfExtractionResult, partialSummaries: List<String>): String {
  val joined = partialSummaries.mapIndexed { i, s -> "Section ${i + 1}:\n$s" }.joinToString("\n\n")
  val wordBudget = wordBudgetForPages(pdf.pagesRead)
  return "You are producing the FINAL summary of a ${pdf.pagesRead}-page document from the section " +
    "summaries below.\n" +
    DOC_TYPE_GUIDANCE +
    MATH_FORMAT_GUIDANCE +
    "\nWrite a cohesive summary of $wordBudget. Merge overlapping points and remove redundancy.\n" +
    DOC_TYPE_HEADER_INSTRUCTION +
    "Immediately after that line, write the summary paragraph (no second \"## Summary\" heading).\n" +
    "Then add:\n" +
    "## Key points\n" +
    "Then add 1-2 extra sections most relevant to the document type (e.g. \"Figures & metrics\", " +
    "\"Key terms\", \"Parties & dates\", \"Results\").\n" +
    "Output ONLY the summary — no preamble.\n\n" +
    "SECTION SUMMARIES:\n" +
    joined
}
