package com.bt.bttune.recognition

interface MusicRecognitionEngine {
    val providerName: String
    suspend fun recognize(audio: AudioSource): RecognitionResult
}
