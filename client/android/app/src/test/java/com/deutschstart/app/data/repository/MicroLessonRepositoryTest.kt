package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class MicroLessonRepositoryTest {

    @Mock lateinit var lessonDao: MicroLessonDao
    @Mock lateinit var vocabDao: VocabularyDao
    @Mock lateinit var grammarDao: GrammarDao
    @Mock lateinit var gamificationRepo: GamificationRepository

    private lateinit var repository: MicroLessonRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = MicroLessonRepository(lessonDao, vocabDao, grammarDao, gamificationRepo)
    }

    @Test
    fun `getOrCreateTodaysLesson returns existing lesson if present`() = runTest {
        val existing = MicroLessonEntity(
            stage = 1,
            vocabCompleted = 2,
            grammarTopicId = "topic1",
            playlistWordIds = "[]",
            createdAt = System.currentTimeMillis()
        )
        `when`(lessonDao.getToday()).thenReturn(existing)

        val result = repository.getOrCreateTodaysLesson()

        assertEquals(existing, result)
        verify(lessonDao, never()).insertOrUpdate(any())
    }

    @Test
    fun `startNewLesson creates new lesson with due and new items`() = runTest {
        // Mock data
        val dueItems = listOf(VocabularyEntity(id = "1", word = "due1", translationEn = "t1"))
        val newItems = listOf(
            VocabularyEntity(id = "2", word = "new1", translationEn = "t2"),
            VocabularyEntity(id = "3", word = "new2", translationEn = "t3"),
            VocabularyEntity(id = "4", word = "new3", translationEn = "t4"),
            VocabularyEntity(id = "5", word = "new4", translationEn = "t5")
        )
        
        `when`(vocabDao.getDueItems(anyLong(), anyInt())).thenReturn(dueItems)
        `when`(vocabDao.getNewItems(4)).thenReturn(newItems)
        `when`(grammarDao.getRandomTopic()).thenReturn(GrammarTopicEntity("g1", "Topic 1", "desc", 1, "[]"))
        `when`(vocabDao.getKnownWords(10)).thenReturn(emptyList())

        val result = repository.startNewLesson()

        assertNotNull(result)
        assertEquals(0, result?.stage)
        assertEquals("g1", result?.grammarTopicId)
        verify(lessonDao).insertOrUpdate(any())
    }

    @Test
    fun `startNewLesson returns null if no vocab available`() = runTest {
        `when`(vocabDao.getDueItems(anyLong(), anyInt())).thenReturn(emptyList())
        `when`(vocabDao.getNewItems(anyInt())).thenReturn(emptyList())

        val result = repository.startNewLesson()

        assertNull(result)
        verify(lessonDao, never()).insertOrUpdate(any())
    }
}
