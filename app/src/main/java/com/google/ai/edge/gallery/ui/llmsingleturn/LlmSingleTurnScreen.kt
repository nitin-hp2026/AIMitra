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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.os.bundleOf
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.theme.customColors

private val DEFAULT_TASK = Task(
  id = BuiltInTaskId.LLM_PROMPT_LAB,
  label = "AI Mitra",
  category = com.google.ai.edge.gallery.data.Category.LLM,
  description = "Single turn use cases",
  models = mutableListOf(),
)

private val DEFAULT_MODEL = Model(
  name = "Gemma-4-E2B-it",
  version = "1.0",
)

@Composable
fun LlmSingleTurnScreen(
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmSingleTurnViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()

  BackHandler {
    if (!uiState.inProgress) {
      navigateUp()
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      HpPromptLabAppBar(onClose = { navigateUp() })
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.padding(
          top = innerPadding.calculateTopPadding(),
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize(),
      ) {
        VerticalSplitView(
          modifier = Modifier.fillMaxSize(),
          topView = {
            PromptTemplatesPanel(
              task = DEFAULT_TASK,
              model = DEFAULT_MODEL,
              viewModel = viewModel,
              onSend = {
                firebaseAnalytics?.logEvent(
                  GalleryEvent.GENERATE_ACTION.id,
                  bundleOf("capability_name" to DEFAULT_TASK.id, "model_id" to DEFAULT_MODEL.name),
                )
              },
              onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
              modifier = Modifier.fillMaxSize(),
            )
          },
          bottomView = {
            Box(
              contentAlignment = Alignment.BottomCenter,
              modifier =
                Modifier.fillMaxSize().background(MaterialTheme.customColors.agentBubbleBgColor),
            ) {
              ResponsePanel(
                task = DEFAULT_TASK,
                model = DEFAULT_MODEL,
                viewModel = viewModel,
                modifier =
                  Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
              )
            }
          },
        )
      }
    }
  }
}
