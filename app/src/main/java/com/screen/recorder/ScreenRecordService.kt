package com.screen.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioCaptureThread: Thread? = null

    private var mediaMuxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null

    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var isMuxerStarted: Boolean = false
    private val isRecording = AtomicBoolean(false)

    private val notificationChannelId = "ScreenRecorderChannel"
    private val notificationId = 101
    private var audioMode: AudioMode = AudioMode.MEDIA_MIC

    companion object {
        const val ACTION_START = "com.screen.recorder.START"
        const val ACTION_STOP = "com.screen.recorder.STOP"
        const val EXTRA_AUDIO_MODE = "audio_mode"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createNotificationChannel()
                startForeground(notificationId, createNotification())

                val resultCode = intent.getIntExtra("resultCode", 0)
                val data = intent.getParcelableExtra("data", Intent::class.java)
                audioMode = AudioMode.fromValue(intent.getIntExtra(EXTRA_AUDIO_MODE, AudioMode.MEDIA_MIC.modeValue))

                if (resultCode != 0 && data != null) {
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpm.getMediaProjection(resultCode, data)

                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            stopRecording()
                        }
                    }, null)

                    startScreenAndAudioCapture()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_STICKY
    }


    private fun startScreenAndAudioCapture() {
        val metrics = resources.displayMetrics
        val outputUri = createFinalVideoUri()

        try {
            val pfd = contentResolver.openFileDescriptor(outputUri, "w")
            mediaMuxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, metrics.widthPixels, metrics.heightPixels).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder!!.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder!!.createInputSurface()
            videoEncoder!!.start()

            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder!!.start()

            isRecording.set(true)
            startAudioCapture()
            startVirtualDisplay(inputSurface)
            startMixingThreads()
        } catch (_: Exception) {
            stopRecording()
        }
    }


    private fun startVirtualDisplay(surface: android.view.Surface) {
        val metrics = resources.displayMetrics
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
    }

    private fun startAudioCapture() {
        if (audioMode == AudioMode.NONE) return

        val sampleRate = 44100
        val channelCount = 2
        val bytesPerSample = 2
        val frameSize = 1024 * channelCount * bytesPerSample
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val bufferSize = maxOf(minBuf, frameSize * 4)

        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val mediaRecord = if (audioMode == AudioMode.MEDIA || audioMode == AudioMode.MEDIA_MIC) {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()
        } else null

        val micRecord = if (audioMode == AudioMode.MEDIA_MIC) {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .build()
        } else null

        mediaRecord?.startRecording()
        micRecord?.startRecording()

        audioCaptureThread = Thread {
            val mediaBuffer = ShortArray(bufferSize / 2)
            val micBuffer = ShortArray(bufferSize / 2)
            val mixedBuffer = ShortArray(bufferSize / 2)
            var totalBytesSent = 0L

            while (isRecording.get()) {
                val readMedia = mediaRecord?.read(mediaBuffer, 0, mediaBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                val readMic = micRecord?.read(micBuffer, 0, micBuffer.size, AudioRecord.READ_BLOCKING) ?: 0

                val samples = when (audioMode) {
                    AudioMode.MEDIA -> readMedia
                    AudioMode.MEDIA_MIC -> minOf(readMedia, readMic).coerceAtLeast(0)
                    else -> 0
                }

                if(samples > 0){
                    if (audioMode == AudioMode.MEDIA_MIC) {
                        for (i in 0 until samples) {
                            val mixed = mediaBuffer[i] + micBuffer[i]
                            mixedBuffer[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }

                    val sourceBuffer = when (audioMode) {
                        AudioMode.MEDIA -> mediaBuffer
                        AudioMode.MEDIA_MIC -> mixedBuffer
                        else -> null
                    }

                    sourceBuffer?.let {
                        var consumed = 0
                        val bytesToSend = samples * bytesPerSample
                        while (consumed < bytesToSend) {
                            val inIndex = audioEncoder!!.dequeueInputBuffer(10_000)
                            if (inIndex >= 0) {
                                val inputBuffer = audioEncoder!!.getInputBuffer(inIndex)!!
                                inputBuffer.clear()
                                val toWrite = minOf(bytesToSend - consumed, inputBuffer.remaining())
                                val tempBytes = ByteArray(toWrite)
                                ByteBuffer.wrap(tempBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer().put(it, consumed / 2, toWrite / 2)
                                inputBuffer.put(tempBytes)
                                totalBytesSent += toWrite
                                val totalSamples = totalBytesSent / bytesPerSample / channelCount
                                val ptsUs = (1_000_000L * totalSamples) / sampleRate
                                audioEncoder!!.queueInputBuffer(inIndex, 0, toWrite, ptsUs, 0)
                                consumed += toWrite
                            } else break
                        }
                    }


                }
            }

            val inIndex = audioEncoder!!.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                audioEncoder!!.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            mediaRecord?.stop()
            mediaRecord?.release()
            micRecord?.stop()
            micRecord?.release()
        }
        audioCaptureThread?.start()
    }


    private var videoStartPts = -1L
    private var audioStartPts = -1L

    private fun startMixingThreads() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = videoEncoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
                when (outIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrackIndex = mediaMuxer!!.addTrack(videoEncoder!!.outputFormat)
                        if (audioTrackIndex != -1 && !isMuxerStarted) {
                            mediaMuxer!!.start()
                            isMuxerStarted = true
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!isRecording.get()) break
                    }
                    else -> {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            videoEncoder!!.releaseOutputBuffer(outIndex, false)
                            continue
                        }

                        if (videoStartPts == -1L) {
                            videoStartPts = bufferInfo.presentationTimeUs
                        }
                        bufferInfo.presentationTimeUs -= videoStartPts

                        if (isMuxerStarted && bufferInfo.size > 0) {
                            val outputBuffer = videoEncoder!!.getOutputBuffer(outIndex)!!
                            mediaMuxer!!.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        videoEncoder!!.releaseOutputBuffer(outIndex, false)
                        if (eos) {
                            break
                        }
                    }
                }
            }
        }.start()

        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = audioEncoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
                when (outIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        audioTrackIndex = mediaMuxer!!.addTrack(audioEncoder!!.outputFormat)

                        if (videoTrackIndex != -1 && !isMuxerStarted) {
                            mediaMuxer!!.start()
                            isMuxerStarted = true
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!isRecording.get()) break
                    }
                    else -> {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            audioEncoder!!.releaseOutputBuffer(outIndex, false)
                            continue
                        }

                        if (audioStartPts == -1L) {
                            audioStartPts = bufferInfo.presentationTimeUs
                        }
                        bufferInfo.presentationTimeUs -= audioStartPts

                        if (isMuxerStarted && bufferInfo.size > 0) {
                            val outputBuffer = audioEncoder!!.getOutputBuffer(outIndex)!!
                            mediaMuxer!!.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        audioEncoder!!.releaseOutputBuffer(outIndex, false)
                        if (eos) {
                            break
                        }
                    }
                }
            }
        }.start()
    }



    private fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)

        try { audioCaptureThread?.join(3000) } catch (_: Exception) {}
        try {
            videoEncoder?.signalEndOfInputStream()
        } catch (_: Exception) {}

        try { Thread.sleep(200) } catch (_: Exception) {}

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
        } catch (_: Exception) {
        } finally {
            mediaMuxer?.release()
        }

        try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
        try { videoEncoder?.stop(); videoEncoder?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release(); mediaProjection?.stop() } catch (_: Exception) {}

        mediaProjection = null
        virtualDisplay = null

        audioCaptureThread = null
        videoEncoder = null
        audioEncoder = null
        mediaMuxer = null
        videoTrackIndex = -1
        audioTrackIndex = -1
        isMuxerStarted = false

        stopSelf()
    }


    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            notificationChannelId,
            "Screen Recorder Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Screen Recorder")
            .setContentText("Recording screen...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createFinalVideoUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "screen_record_final_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}