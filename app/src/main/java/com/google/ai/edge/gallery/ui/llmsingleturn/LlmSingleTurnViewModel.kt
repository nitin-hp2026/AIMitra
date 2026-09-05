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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AGLlmSingleTurnVM"

data class LlmSingleTurnUiState(
  /** Indicates whether the runtime is currently processing a message. */
  val inProgress: Boolean = false,

  /**
   * Indicates whether the model is preparing (before outputting any result and after initializing).
   */
  val preparing: Boolean = false,

  /** Progress message shown during multi-pass generation (e.g. "Summarizing section 2 of 5…"). */
  val statusMessage: String = "",

  // model -> <template label -> response>
  val responsesByModel: Map<String, Map<String, String>>,

  /** Selected prompt template type. */
  val selectedPromptTemplateType: PromptTemplateType = PromptTemplateType.entries[0],
)

/** One inference step in a multi-pass pipeline. [buildInput] receives the outputs of prior passes. */
data class PipelinePass(
  val status: String,
  val buildInput: (priorOutputs: List<String>) -> String,
)

@HiltViewModel
class LlmSingleTurnViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(createUiState())
  val uiState = _uiState.asStateFlow()

  @Volatile private var cancelRequested = false

  /** Convenience single-pass generation (used by the simple templates). */
  fun generateResponse(task: Task, model: Model, input: String) {
    runPipeline(
      task = task,
      model = model,
      templateType = uiState.value.selectedPromptTemplateType,
      passes = listOf(PipelinePass(status = "") { input }),
    )
  }

  /**
   * Runs a sequence of inference passes. Only the final pass streams its output to the UI;
   * intermediate passes drive the [statusMessage] progress indicator and feed the next pass.
   */
  fun runPipeline(
    task: Task,
    model: Model,
    templateType: PromptTemplateType,
    passes: List<PipelinePass>,
    postProcess: ((String) -> String)? = null,
  ) {
    if (passes.isEmpty()) return
    cancelRequested = false
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)
      setStatus("")
      updateResponse(model = model, promptTemplateType = templateType, response = "")

      try {
        val priorOutputs = mutableListOf<String>()
        passes.forEachIndexed { index, pass ->
          if (cancelRequested) return@launch
          setStatus(pass.status)
          val isFinal = index == passes.lastIndex
          val input = pass.buildInput(priorOutputs)
          val output =
            runOnePass(
              model = model,
              input = input,
              templateType = templateType,
              isFinal = isFinal,
              postProcess = if (isFinal) postProcess else null,
            )
          priorOutputs.add(output)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Pipeline failed", e)
      } finally {
        setStatus("")
        setPreparing(false)
        setInProgress(false)
      }
    }
  }

  /** Runs a single inference pass to completion and returns its full text output. */
  private suspend fun runOnePass(
    model: Model,
    input: String,
    templateType: PromptTemplateType,
    isFinal: Boolean,
    postProcess: ((String) -> String)? = null,
  ): String {
    delay(300)
    var response = ""
    if (isFinal) {
      setPreparing(false)
      if (postProcess != null) {
        response = postProcess(response)
      }
      updateResponse(model = model, promptTemplateType = templateType, response = response)
    }
    return response
  }

  fun selectPromptTemplate(model: Model, promptTemplateType: PromptTemplateType) {
    Log.d(TAG, "selecting prompt template: ${promptTemplateType.label}")

    // Clear response.
    updateResponse(model = model, promptTemplateType = promptTemplateType, response = "")

    this._uiState.update {
      this.uiState.value.copy(selectedPromptTemplateType = promptTemplateType)
    }
  }

  fun setInProgress(inProgress: Boolean) {
    _uiState.update { _uiState.value.copy(inProgress = inProgress) }
  }

  fun setPreparing(preparing: Boolean) {
    _uiState.update { _uiState.value.copy(preparing = preparing) }
  }

  fun setStatus(status: String) {
    _uiState.update { _uiState.value.copy(statusMessage = status) }
  }

  fun updateResponse(model: Model, promptTemplateType: PromptTemplateType, response: String) {
    _uiState.update { currentState ->
      val currentResponses = currentState.responsesByModel
      val modelResponses = currentResponses[model.name]?.toMutableMap() ?: mutableMapOf()
      modelResponses[promptTemplateType.label] = response
      val newResponses = currentResponses.toMutableMap()
      newResponses[model.name] = modelResponses
      currentState.copy(responsesByModel = newResponses)
    }
  }

  fun stopResponse(model: Model) {
    Log.d(TAG, "Stopping response for model ${model.name}...")
    cancelRequested = true
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(false)
      setPreparing(false)
      setStatus("")
    }
  }

  private fun createUiState(): LlmSingleTurnUiState {
    val responsesByModel: MutableMap<String, Map<String, String>> = mutableMapOf()
    return LlmSingleTurnUiState(responsesByModel = responsesByModel)
  }
}
