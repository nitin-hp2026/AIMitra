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

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Top-level host that takes the user straight into the Prompt Lab experience.
 *
 * It automatically selects the on-device model, starts its download when needed, and shows a branded
 * setup splash while the model is being prepared.
 */
@Composable
fun PromptLabHost(
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val activity = LocalActivity.current
  val task = modelManagerViewModel.getTaskById(id = BuiltInTaskId.LLM_PROMPT_LAB)

  val targetModel: Model? =
    remember(uiState.tasks, task?.models?.size) { task?.let { pickPromptLabModel(it) } }

  LaunchedEffect(targetModel?.name) {
    if (targetModel != null) {
      modelManagerViewModel.selectModel(model = targetModel)
    }
  }

  val downloadStatus = targetModel?.let { uiState.modelDownloadStatus[it.name] }

  var autoDownloadAttempted by remember(targetModel?.name) { mutableStateOf(false) }
  LaunchedEffect(targetModel?.name, downloadStatus?.status) {
    val status = downloadStatus?.status
    if (targetModel != null && !autoDownloadAttempted && status == ModelDownloadStatusType.NOT_DOWNLOADED) {
      autoDownloadAttempted = true
      modelManagerViewModel.downloadModel(task = task, model = targetModel)
    }
  }

  val isReady =
    downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED &&
      task != null &&
      targetModel != null

  val setupMessage: String
  val setupSubmessage: String?
  val setupProgress: Float?
  val setupRetry: (() -> Unit)?

  when {
    uiState.loadingModelAllowlistError.isNotEmpty() -> {
      setupMessage = "Couldn't load the model catalog. Please connect to the internet and try again."
      setupSubmessage = null
      setupProgress = null
      setupRetry = { modelManagerViewModel.loadModelAllowlist() }
    }
    uiState.loadingModelAllowlist || task == null || targetModel == null || downloadStatus == null -> {
      setupMessage = "Preparing AI Mitra…"
      setupSubmessage = "Fetching the model catalog…"
      setupProgress = null
      setupRetry = null
    }
    downloadStatus.status == ModelDownloadStatusType.FAILED -> {
      setupMessage = "Setup could not be completed. Please check your internet connection."
      setupSubmessage = null
      setupProgress = null
      setupRetry = {
        autoDownloadAttempted = true
        modelManagerViewModel.downloadModel(task = task, model = targetModel)
      }
    }
    !isReady -> {
      val received = downloadStatus.receivedBytes
      val total = downloadStatus.totalBytes
      setupMessage = "Setting up AI Mitra…"
      setupSubmessage = "Downloading the AI model (this is a large one-time download)."
      setupProgress =
        if (total > 0L) (received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
      setupRetry = null
    }
    else -> {
      setupMessage = ""
      setupSubmessage = null
      setupProgress = null
      setupRetry = null
    }
  }

  AnimatedContent(
    targetState = isReady,
    modifier = modifier,
    transitionSpec = {
      if (targetState) {
        (fadeIn(tween(700, easing = FastOutSlowInEasing)) +
          scaleIn(initialScale = 0.94f, animationSpec = tween(700, easing = FastOutSlowInEasing)))
          .togetherWith(
            fadeOut(tween(500, easing = FastOutSlowInEasing)) +
              scaleOut(targetScale = 1.03f, animationSpec = tween(500, easing = FastOutSlowInEasing))
          )
      } else {
        fadeIn(tween(350)) togetherWith fadeOut(tween(250))
      }
    },
    label = "prompt-lab-host",
  ) { ready ->
    if (ready) {
      LlmSingleTurnScreen(
        modelManagerViewModel = modelManagerViewModel,
        navigateUp = { activity?.finish() },
        modifier = Modifier,
      )
    } else {
      HpSetupSplash(
        message = setupMessage,
        submessage = setupSubmessage,
        progress = setupProgress,
        onRetry = setupRetry,
        modifier = Modifier,
      )
    }
  }
}

/** Picks the model to use for the Prompt Lab, preferring the compact E2B build. */
private fun pickPromptLabModel(task: Task): Model? {
  val models = task.models
  if (models.isEmpty()) return null
  fun matches(vararg needles: String) =
    models.firstOrNull { model -> needles.all { model.name.contains(it, ignoreCase = true) } }
  return matches("4", "E2B")
    ?: matches("E2B")
    ?: matches("4", "E4B")
    ?: matches("E4B")
    ?: models.first()
}
