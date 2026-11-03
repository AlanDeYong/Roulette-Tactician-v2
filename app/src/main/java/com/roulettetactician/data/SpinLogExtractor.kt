package com.roulettetactician.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.roulettetactician.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts the chronological winning-number history from a photo of a roulette
 * marquee or ETG screen using Gemini Vision with a strict enum-constrained JSON schema.
 *
 * See the domain research: marquees list newest-at-top with red/black offset columns;
 * ETGs use a "last results" ribbon. Hot/cold stats, percentages, timers, credits,
 * limits and the betting grid are explicitly excluded by the prompt.
 */
@Singleton
class SpinLogExtractor @Inject constructor() {

    data class RawExtraction(
        val logFound: Boolean,
        val layout: String,
        val newestPosition: String,
        /** Model tokens, newest first. Each is "0".."36", "00" or "?". */
        val spinsNewestFirst: List<String>
    )

    class ExtractionException(message: String) : Exception(message)

    suspend fun extract(bitmap: Bitmap, apiKey: String): RawExtraction = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            throw ExtractionException("Gemini API key is not set. Tap the Key icon to enter your API key.")
        }

        val body = buildRequestBody(encodeJpegBase64(bitmap))
        var lastException: ExtractionException? = null
        for (attempt in 1..2) {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", trimmedKey)
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code in 200..299) {
                    return@withContext parseInteractionResponse(text)
                }

                val formattedMsg = when (code) {
                    429 -> "Quota exceeded (Rate Limit 429). Your Google AI Studio project hit its free request/token cap. Wait a moment before retrying, or check your quota at aistudio.google.com/app/plan."
                    400, 401, 403 -> "API Key rejected (HTTP $code). Verify that your key is active and has access in Google AI Studio."
                    else -> "Gemini request failed (HTTP $code): ${text.take(250)}"
                }

                lastException = ExtractionException(formattedMsg)

                if (code == 429 && attempt == 1) {
                    kotlinx.coroutines.delay(2500)
                    continue
                }
                break
            } finally {
                conn.disconnect()
            }
        }
        throw lastException ?: ExtractionException("Extraction request failed.")
    }

    /** Downscale to keep upload small; digits stay sharp at 1280px long edge while saving token quota. */
    private fun encodeJpegBase64(src: Bitmap): String {
        val longEdge = maxOf(src.width, src.height)
        val scaled = if (longEdge > MAX_EDGE_PX) {
            val f = MAX_EDGE_PX.toFloat() / longEdge
            Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== src) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val MODEL = "gemini-3.8-flash"
        private const val MAX_EDGE_PX = 1280

        private val VALID_TOKENS: List<String> = (0..36).map { it.toString() } + listOf("00", "?")

        internal val SYSTEM_PROMPT = """
            You read casino roulette displays. Your ONLY job is to transcribe the WINNING-NUMBER HISTORY (the spin log).

            WHERE THE SPIN LOG IS:
            - Live-table marquee/billboard: one tall vertical column of results. Red numbers are offset to one side,
              black numbers to the other side, 0/00 centered. The NEWEST result is at the TOP (often larger or highlighted).
              Read strictly top-to-bottom, merging the offset sides into ONE sequence by vertical position.
            - Electronic table game (ETG) terminal: a "Last Results"/"History" ribbon of colour-coded tiles near the
              betting area. The newest is usually at the left or top and is often highlighted or larger.
            - Statistics pop-up grid / bead plate: read in the grid's own chronological direction.

            IGNORE COMPLETELY (these are NOT the spin log):
            - Hot / Cold / Frequent / Overdue number panels and any number shown with a count, percentage, flame or ice icon.
            - Percentage or statistic bars (Red/Black, Odd/Even, High/Low, Dozens, Columns, sectors).
            - The betting grid / felt layout, racetrack and wheel graphics (they show every number in fixed order).
            - Timers, countdowns, clocks, credits, balance, bet/win amounts, table limits (Min/Max), game or round IDs.

            RULES:
            - Each result is exactly one value: "0".."36", or "00" on double-zero wheels.
            - Use the tile colour as a cross-check (red/black/green) when digits are faint.
            - If a cell in the sequence is unreadable because of glare, reflection or blur, output "?" for that position.
              Never skip a position and never guess.
            - Output the sequence NEWEST FIRST.
            - If no spin log is visible, set log_found=false and return an empty list.
        """.trimIndent()

        internal fun buildRequestBody(imageBase64: String): String {
            val schema = JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("log_found", JsonObject().apply { addProperty("type", "boolean") })
                    add("layout", JsonObject().apply {
                        addProperty("type", "string")
                        add("enum", jsonArrayOf("vertical_marquee", "horizontal_ribbon", "grid", "other"))
                    })
                    add("newest_position", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Where the newest result is on screen, e.g. top, left, right.")
                    })
                    add("spins_newest_first", JsonObject().apply {
                        addProperty("type", "array")
                        add("items", JsonObject().apply {
                            addProperty("type", "string")
                            add("enum", jsonArrayOf(*VALID_TOKENS.toTypedArray()))
                        })
                    })
                })
                add("required", jsonArrayOf("log_found", "layout", "newest_position", "spins_newest_first"))
            }

            val root = JsonObject().apply {
                addProperty("model", MODEL)
                addProperty("system_instruction", SYSTEM_PROMPT)
                addProperty("store", false) // Do not retain casino photos server-side
                add("input", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Transcribe the spin log in this photo.")
                    })
                    add(JsonObject().apply {
                        addProperty("type", "image")
                        addProperty("mime_type", "image/jpeg")
                        addProperty("data", imageBase64)
                    })
                })
                add("generation_config", JsonObject().apply { addProperty("thinking_level", "medium") })
                add("response_format", JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("mime_type", "application/json")
                    add("schema", schema)
                })
            }
            return Gson().toJson(root)
        }

        /** Extracts the final model_output text from an Interactions API response and decodes the schema JSON. */
        internal fun parseInteractionResponse(responseJson: String): RawExtraction {
            val root = JsonParser.parseString(responseJson).asJsonObject
            val text = extractOutputText(root)
                ?: throw ExtractionException("Gemini returned no text output.")
            val obj = try {
                JsonParser.parseString(text).asJsonObject
            } catch (e: Exception) {
                throw ExtractionException("Could not parse model JSON: ${text.take(200)}")
            }
            return RawExtraction(
                logFound = obj.get("log_found")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                layout = obj.get("layout")?.takeIf { it.isJsonPrimitive }?.asString ?: "other",
                newestPosition = obj.get("newest_position")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                spinsNewestFirst = obj.getAsJsonArray("spins_newest_first")
                    ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString }
                    ?: emptyList()
            )
        }

        private fun extractOutputText(root: JsonObject): String? {
            // Current schema: steps[] -> last "model_output" -> content[] -> text
            root.getAsJsonArray("steps")?.let { steps ->
                val output = steps.lastOrNull {
                    it.isJsonObject && it.asJsonObject.get("type")?.asString == "model_output"
                }?.asJsonObject
                val joined = output?.getAsJsonArray("content")
                    ?.filter { it.isJsonObject && it.asJsonObject.get("type")?.asString == "text" }
                    ?.joinToString("") { it.asJsonObject.get("text").asString }
                if (!joined.isNullOrBlank()) return joined
            }
            // Fallback: legacy outputs[] -> last text
            root.getAsJsonArray("outputs")?.let { outputs ->
                val last = outputs.lastOrNull {
                    it.isJsonObject && it.asJsonObject.get("text") != null
                }?.asJsonObject?.get("text")?.asString
                if (!last.isNullOrBlank()) return last
            }
            return null
        }

        private fun jsonArrayOf(vararg values: String) = JsonArray().apply { values.forEach { add(it) } }

        /** Decodes an image Bitmap from a content URI, preserving orientation and software allocation. */
        fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
