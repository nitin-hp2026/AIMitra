/*
 * Copyright 2026 Google LLC
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

package com.google.ai.edge.gallery.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Repository for managing custom system prompts per task. */
@Singleton
open class SystemPromptRepository @Inject constructor() {

  private val prompts = MutableStateFlow<Map<String, String>>(emptyMap())

  private fun getKey(taskId: String): String = "system_prompt_$taskId"

  suspend fun updateSystemPrompt(taskId: String, newPrompt: String) {
    prompts.value = prompts.value + (getKey(taskId) to newPrompt)
  }

  fun getCustomSystemPrompt(taskId: String): Flow<String?> {
    return prompts.map { it[getKey(taskId)] }
  }

  suspend fun clearCustomSystemPrompt(taskId: String) {
    prompts.value = prompts.value - getKey(taskId)
  }
}
