package com.deutschstart.app.playlist

import com.deutschstart.app.data.local.VocabularyEntity
import javax.inject.Inject

/**
 * Represents a segment in the playlist audio stream.
 */
sealed class PlaylistSegment {
    data class Audio(val path: String, val type: SegmentType) : PlaylistSegment()
    data class Silence(val durationMs: Int) : PlaylistSegment()
}

enum class SegmentType {
    GERMAN_WORD,      // Der Tisch
    KAIKKI_AUDIO,     // Human pronunciation from Kaikki
    TRANSLATION,      // "the table"
    SENTENCE          // German example sentence
}

/**
 * Configuration for playlist generation.
 */
data class PlaylistConfig(
    val thinkingGapMs: Int = 4000,         // 4 seconds to think/speak
    val cardGapMs: Int = 1500,             // 1.5 seconds between cards
    val mode: PlaylistMode = PlaylistMode.GERMAN_TO_ENGLISH,
    val includeSentences: Boolean = true,
    val includeArticles: Boolean = true,     // Whether to play articles separately
    val includeKaikki: Boolean = true        // Play human audio if available
)

enum class PlaylistMode {
    GERMAN_TO_ENGLISH,   // German → pause → English → sentence
    ENGLISH_TO_GERMAN,   // English → pause → German → sentence
    GERMAN_ONLY          // Just German pronunciation practice
}


/**
 * Builds a sequential playlist from flashcards with configurable patterns.
 */
class PlaylistBuilder @Inject constructor() {
    
    fun buildPlaylist(cards: List<VocabularyEntity>, config: PlaylistConfig): PlaylistData {
        val segments = mutableListOf<PlaylistSegment>()
        
        cards.forEachIndexed { index, card ->
            when (config.mode) {
                PlaylistMode.GERMAN_TO_ENGLISH -> {
                    addGermanToEnglishSequence(segments, card, config)
                }
                PlaylistMode.ENGLISH_TO_GERMAN -> {
                    addEnglishToGermanSequence(segments, card, config)
                }
                PlaylistMode.GERMAN_ONLY -> {
                    addGermanOnlySequence(segments, card, config)
                }
            }
            
            // Add gap between cards (except after last card)
            if (index < cards.size - 1) {
                segments.add(PlaylistSegment.Silence(config.cardGapMs))
            }
        }
        
        return PlaylistData(
            segments = segments,
            cardCount = cards.size,
            totalDurationMs = estimateDuration(segments)
        )
    }
    
    private fun addGermanToEnglishSequence(segments: MutableList<PlaylistSegment>, card: VocabularyEntity, config: PlaylistConfig) {
        // 1. German word
        if (card.audioLearnPath.isNotBlank()) {
            segments.add(PlaylistSegment.Audio(card.audioLearnPath, SegmentType.GERMAN_WORD))
        }

        // 1a. Kaikki Audio (Optional Human Voice)
        if (config.includeKaikki && !card.kaikkiAudioPath.isNullOrBlank()) {
             segments.add(PlaylistSegment.Silence(500))
             segments.add(PlaylistSegment.Audio(card.kaikkiAudioPath, SegmentType.KAIKKI_AUDIO))
        }
        
        // 2. Thinking gap
        segments.add(PlaylistSegment.Silence(config.thinkingGapMs))
        
        // 3. English translation
        if (card.audioEnPath.isNotBlank()) {
            segments.add(PlaylistSegment.Audio(card.audioEnPath, SegmentType.TRANSLATION))
        }
        
        // 4. Example sentence (if enabled)
        if (config.includeSentences) {
            addSentenceAudio(segments, card)
        }
    }
    
    private fun addEnglishToGermanSequence(segments: MutableList<PlaylistSegment>, card: VocabularyEntity, config: PlaylistConfig) {
        // 1. English translation
        if (card.audioEnPath.isNotBlank()) {
            segments.add(PlaylistSegment.Audio(card.audioEnPath, SegmentType.TRANSLATION))
        }
        
        // 2. Thinking gap
        segments.add(PlaylistSegment.Silence(config.thinkingGapMs))
        
        // 3. German word
        if (card.audioLearnPath.isNotBlank()) {
            segments.add(PlaylistSegment.Audio(card.audioLearnPath, SegmentType.GERMAN_WORD))
        }

        if (config.includeKaikki && !card.kaikkiAudioPath.isNullOrBlank()) {
             segments.add(PlaylistSegment.Silence(500))
             segments.add(PlaylistSegment.Audio(card.kaikkiAudioPath, SegmentType.KAIKKI_AUDIO))
        }
        
        // 4. Example sentence (if enabled)
        if (config.includeSentences) {
            addSentenceAudio(segments, card)
        }
    }
    
    private fun addGermanOnlySequence(segments: MutableList<PlaylistSegment>, card: VocabularyEntity, config: PlaylistConfig) {
        // Just German word + sentence (for pronunciation practice)
        if (card.audioLearnPath.isNotBlank()) {
            segments.add(PlaylistSegment.Audio(card.audioLearnPath, SegmentType.GERMAN_WORD))
        }
        
        if (config.includeKaikki && !card.kaikkiAudioPath.isNullOrBlank()) {
             segments.add(PlaylistSegment.Silence(500))
             segments.add(PlaylistSegment.Audio(card.kaikkiAudioPath, SegmentType.KAIKKI_AUDIO))
        }
        
        if (config.includeSentences) {
            segments.add(PlaylistSegment.Silence(1000)) // Short gap before sentence
            addSentenceAudio(segments, card)
        }
    }
    
    private fun addSentenceAudio(segments: MutableList<PlaylistSegment>, card: VocabularyEntity) {
        // Parse sentences and add first one (if available)
        try {
            val sentences = com.google.gson.Gson().fromJson<List<Map<String, String>>>(
                card.exampleSentencesJson,
                object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            )
            
            sentences.firstOrNull()?.get("audio_path")?.let { audioPath ->
                if (audioPath.isNotBlank()) {
                    segments.add(PlaylistSegment.Silence(800)) // Brief pause before sentence
                    segments.add(PlaylistSegment.Audio(audioPath, SegmentType.SENTENCE))
                }
            }
        } catch (e: Exception) {
            // Silently skip if sentence parsing fails
        }
    }
    
    private fun estimateDuration(segments: List<PlaylistSegment>): Long {
        // Rough estimate: Audio files average 2s, actual duration will be calculated by player
        return segments.sumOf { segment ->
            when (segment) {
                is PlaylistSegment.Audio -> 2000L // Rough estimate
                is PlaylistSegment.Silence -> segment.durationMs.toLong()
            }
        }
    }
}

data class PlaylistData(
    val segments: List<PlaylistSegment>,
    val cardCount: Int,
    val totalDurationMs: Long
)
