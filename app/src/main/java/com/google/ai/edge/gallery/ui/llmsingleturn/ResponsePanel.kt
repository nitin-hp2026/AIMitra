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

import android.content.ClipData
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.BufferedFadingMarkdownText
import com.google.ai.edge.gallery.ui.common.ScrollToBottomButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsePanel(
  task: Task,
  model: Model,
  viewModel: LlmSingleTurnViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val inProgress = uiState.inProgress
  val initializing = uiState.preparing
  val selectedPromptTemplateType = uiState.selectedPromptTemplateType
  val responseScrollState = rememberScrollState()
  var selectedOptionIndex by remember { mutableIntStateOf(0) }
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  LaunchedEffect(selectedPromptTemplateType) { selectedOptionIndex = 0 }

  val response =
    uiState.responsesByModel[model.name]?.get(selectedPromptTemplateType.label) ?: ""
  val isProcessing = initializing || inProgress
  val statusText =
    when {
      uiState.statusMessage.isNotEmpty() -> uiState.statusMessage
      initializing -> "Processing…"
      inProgress -> "Generating…"
      else -> null
    }

  Box(modifier = modifier.fillMaxSize()) {
    if (initializing && response.isEmpty()) {
      Column(modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp)) {
        HpResponseProcessingIndicator(
          isProcessing = true,
          statusMessage = statusText,
        )
      }
    } else if (response.isEmpty()) {
      if (isProcessing) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp)) {
          HpResponseProcessingIndicator(
            isProcessing = true,
            statusMessage = statusText,
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "Response will appear here",
            modifier = Modifier.alpha(0.5f),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    } else {
      var isAtBottom by remember { mutableStateOf(true) }
      LaunchedEffect(responseScrollState) {
        snapshotFlow {
            !responseScrollState.canScrollForward
          }
          .collectLatest { rawAtBottom ->
            if (!rawAtBottom) {
              delay(500)
            }
            isAtBottom = rawAtBottom
          }
      }

      Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
        if (selectedOptionIndex == 0) {
          Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(responseScrollState)) {
              if (isProcessing) {
                HpResponseProcessingIndicator(
                  isProcessing = true,
                  statusMessage = statusText,
                  modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
              }
              BufferedFadingMarkdownText(
                text = response,
                inProgress = uiState.inProgress,
                modifier =
                  Modifier.padding(top = 8.dp, bottom = 40.dp).semantics {
                    if (!inProgress) {
                      liveRegion = LiveRegionMode.Polite
                    }
                  },
              )
            }
            IconButton(
              onClick = {
                scope.launch {
                  val clipData = ClipData.newPlainText("response", response)
                  val clipEntry = ClipEntry(clipData = clipData)
                  clipboard.setClipEntry(clipEntry = clipEntry)
                }
              },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                  contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_to_clipboard_icon),
                modifier = Modifier.size(20.dp),
              )
            }

            Column(
              modifier =
                Modifier.align(alignment = Alignment.BottomCenter)
                  .fillMaxWidth()
                  .padding(bottom = 0.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              ScrollToBottomButton(
                isAtBottom = isAtBottom,
                onClick = {
                  scope.launch {
                    responseScrollState.animateScrollTo(
                      responseScrollState.maxValue,
                      animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    )
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}
