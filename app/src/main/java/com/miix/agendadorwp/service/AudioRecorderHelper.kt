package com.miix.agendadorwp.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    var currentAudioFile: File? = null
        private set
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        return try {
            val audioDir = File(context.cacheDir, "audio_notes")
            if (!audioDir.exists()) audioDir.mkdirs()

            val file = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            currentAudioFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return currentAudioFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            currentAudioFile
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            isRecording = false
            null
        }
    }

    fun cancelRecording() {
        try {
            if (isRecording) {
                recorder?.stop()
                recorder?.release()
            }
            recorder = null
            isRecording = false
            currentAudioFile?.delete()
            currentAudioFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
