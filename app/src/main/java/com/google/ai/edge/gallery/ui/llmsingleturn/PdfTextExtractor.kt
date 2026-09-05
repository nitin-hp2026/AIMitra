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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Maximum number of PDF pages that will be read for summarization. */
const val MAX_PDF_PAGES = 10

private const val TAG = "AGPdfTextExtractor"

/** Result of extracting text from an uploaded PDF. */
data class PdfExtractionResult(
  val text: String,
  val fileName: String,
  /** Number of pages actually read (capped at [MAX_PDF_PAGES]). */
  val pagesRead: Int,
  /** Total number of pages in the document. */
  val totalPages: Int,
)

/**
 * Extracts text from the first [MAX_PDF_PAGES] pages of the PDF pointed to by [uri].
 *
 * Returns `null` when the document contains no extractable text (e.g. a scanned/image-only PDF) or
 * when it could not be read.
 */
suspend fun extractPdfText(context: Context, uri: Uri): PdfExtractionResult? =
  withContext(Dispatchers.IO) {
    try {
      PDFBoxResourceLoader.init(context.applicationContext)
      val fileName = queryDisplayName(context, uri) ?: "document.pdf"

      context.contentResolver.openInputStream(uri).use { input ->
        if (input == null) {
          Log.e(TAG, "Could not open input stream for uri: $uri")
          return@withContext null
        }
        PDDocument.load(input).use { document ->
          val totalPages = document.numberOfPages
          val pagesRead = min(MAX_PDF_PAGES, totalPages)
          val stripper =
            PDFTextStripper().apply {
              startPage = 1
              endPage = pagesRead
            }
          val text = stripper.getText(document).trim()
          if (text.isEmpty()) {
            return@withContext null
          }
          PdfExtractionResult(
            text = text,
            fileName = fileName,
            pagesRead = pagesRead,
            totalPages = totalPages,
          )
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to extract text from PDF", e)
      null
    }
  }

private fun queryDisplayName(context: Context, uri: Uri): String? {
  return try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
  } catch (e: Exception) {
    null
  }
}
