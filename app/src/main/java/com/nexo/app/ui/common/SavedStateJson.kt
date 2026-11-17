package com.nexo.app.ui.common

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("YearMonth", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YearMonth) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): YearMonth = YearMonth.parse(decoder.decodeString())
}

// Also encodes the dates and months screens keep as their place.
val SavedStateJson: Json = Json {
    serializersModule = SerializersModule {
        contextual(LocalDate::class, LocalDateSerializer)
        contextual(YearMonth::class, YearMonthSerializer)
    }
}

@PublishedApi
internal const val SAVED_JSON = "json"

// Lets a half-filled form survive process death; snapshots that no longer decode are ignored.
inline fun <reified T> SavedStateHandle.getJson(key: String): T? =
    get<String>(key)?.let { runCatching { SavedStateJson.decodeFromString<T>(it) }.getOrNull() }

inline fun <reified T> SavedStateHandle.setJson(key: String, value: T) {
    set(key, SavedStateJson.encodeToString(value))
}

// Restores where the user was after process death. Written only when the system saves state;
// a value that no longer decodes is dropped.
inline fun <reified T> SavedStateHandle.savedStateFlow(
    key: String,
    initial: T,
): MutableStateFlow<T> {
    lateinit var flow: MutableStateFlow<T>
    val restored = restoreAndSave(key) { flow.value }
    flow = MutableStateFlow(restored ?: initial)
    return flow
}

// For a place spread over a screen's state: [snapshot] is taken when the system saves state;
// returns the one saved before process death, if any.
inline fun <reified T> SavedStateHandle.restoreAndSave(
    key: String,
    crossinline snapshot: () -> T,
): T? {
    val restored = get<Bundle>(key)?.getString(SAVED_JSON)?.let {
        runCatching { SavedStateJson.decodeFromString<T>(it) }.getOrNull()
    }
    setSavedStateProvider(key) { bundleOf(SAVED_JSON to SavedStateJson.encodeToString(snapshot())) }
    return restored
}
