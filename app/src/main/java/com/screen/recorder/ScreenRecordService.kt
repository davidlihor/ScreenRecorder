package com.screen.recorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ScreenRecordService : Service() {
    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaRecorder: MediaRecorder
    private var virtualDisplay: VirtualDisplay? = null
    
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra("data", Intent::class.java) ?: return START_NOT_STICKY

        if (resultCode != Activity.RESULT_OK) return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)!!

        startRecording()
        return START_NOT_STICKY
    }


    private fun startRecording() {
        val metrics = resources.displayMetrics
        val videoUri = createVideoUri()
        val fileDescriptor = contentResolver.openFileDescriptor(videoUri, "w")!!.fileDescriptor

        mediaRecorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fileDescriptor)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5_000_000)
            prepare()
        }

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                try {
                    mediaRecorder.stop()
                } catch (_: Exception) {}
                mediaRecorder.release()
                virtualDisplay?.release()
                stopSelf()
            }
        }, null)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecorder",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface, null, null
        )


        mediaRecorder.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder.stop()
        mediaRecorder.release()
        virtualDisplay?.release()
        mediaProjection.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "screen_record_channel"
        val channel = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording screen")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun createVideoUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "screen_record_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val resolver = applicationContext.contentResolver
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }
}