package com.walidaldib.cinematic

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalVideoExporter {
    private const val WIDTH = 720
    private const val HEIGHT = 1280
    private const val FPS = 24

    fun export(context: Context, source: Uri, seconds: Int, movement: String): Uri {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("تعذر قراءة الصورة")
        val outputFile = File(context.cacheDir, "wd_${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext = EGL14.EGL_NO_CONTEXT
        var eglSurface = EGL14.EGL_NO_SURFACE
        var texture = 0
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "تعذر تهيئة EGL" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) { "تعذر تشغيل EGL" }
            val configAttrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "لا يوجد إعداد EGL مناسب" }
            val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "تعذر إنشاء سياق الرسم" }
            val surfaceAttrs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface, surfaceAttrs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "تعذر إنشاء سطح الفيديو" }
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "تعذر ربط سطح الفيديو" }

            val program = createProgram()
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            val scaleLoc = GLES20.glGetUniformLocation(program, "uScale")
            val shiftLoc = GLES20.glGetUniformLocation(program, "uShift")
            texture = createTexture(bitmap)
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val outAspect = WIDTH.toFloat() / HEIGHT
            val u0: Float; val u1: Float; val v0: Float; val v1: Float
            if (aspect > outAspect) {
                val visible = outAspect / aspect
                u0 = (1f - visible) / 2f; u1 = 1f - u0; v0 = 0f; v1 = 1f
            } else {
                val visible = aspect / outAspect
                v0 = (1f - visible) / 2f; v1 = 1f - v0; u0 = 0f; u1 = 1f
            }
            val vertices = floatBuffer(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
            val tex = floatBuffer(floatArrayOf(u0,v1, u1,v1, u0,v0, u1,v0))
            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glEnableVertexAttribArray(texCoord)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, tex)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            GLES20.glViewport(0, 0, WIDTH, HEIGHT)

            val info = MediaCodec.BufferInfo()
            var track = -1
            var muxerStarted = false
            fun drain(end: Boolean) {
                var finished = false
                while (!finished) {
                    val index = codec!!.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!end) return
                        }
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "تغير تنسيق الفيديو مرتين" }
                            track = muxer!!.addTrack(codec!!.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            val buffer = codec!!.getOutputBuffer(index)
                            if (buffer != null && info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buffer, info)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) finished = true
                            codec!!.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }

            val frames = seconds * FPS
            for (frame in 0 until frames) {
                val progress = frame.toFloat() / (frames - 1).coerceAtLeast(1)
                val zoom = when (movement) {
                    "ابتعاد سينمائي" -> 1.12f - 0.12f * progress
                    "تحريك أفقي" -> 1.12f
                    else -> 1f + 0.12f * progress
                }
                val shift = if (movement == "تحريك أفقي") (progress - 0.5f) * -0.12f else 0f
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glUniform1f(scaleLoc, zoom)
                GLES20.glUniform1f(shiftLoc, shift)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, frame * 1_000_000_000L / FPS)
                check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "تعذر تسجيل إطار الفيديو" }
                drain(false)
            }
            codec!!.signalEndOfInputStream()
            drain(true)
            check(muxerStarted) { "لم يتم إنشاء مسار الفيديو" }
        } finally {
            if (texture != 0 && eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                texture = 0
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            runCatching { muxer?.stop() }; runCatching { muxer?.release() }
            bitmap.recycle()
        }
        val name = "WaleedAldib_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WaleedAldib")
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر حفظ الفيديو في المعرض")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(outputFile).use { it.copyTo(out) } }
                    ?: error("تعذر كتابة ملف الفيديو")
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
            outputFile.delete()
            uri
        } else {
            val folder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val saved = File(folder, name)
            outputFile.copyTo(saved, overwrite = true)
            outputFile.delete()
            Uri.fromFile(saved)
        }
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

    private fun createTexture(bitmap: android.graphics.Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    private fun createProgram(): Int {
        val vertex = "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 vTex; uniform float uScale; uniform float uShift; void main(){ vec2 p=aPosition*uScale; p.x+=uShift; gl_Position=vec4(p,0.0,1.0); vTex=aTexCoord; }"
        val fragment = "precision mediump float; varying vec2 vTex; uniform sampler2D uTexture; void main(){ gl_FragColor=texture2D(uTexture,vTex); }"
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
        return program
    }
}
