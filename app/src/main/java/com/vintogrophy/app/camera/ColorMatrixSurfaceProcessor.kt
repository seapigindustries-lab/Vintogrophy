package com.vintogrophy.app.camera

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.ProcessingException
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.processing.util.GLUtils
import com.vintogrophy.app.filter.ColorMatrixUniform
import com.vintogrophy.app.filter.PhotoFilter
import java.nio.FloatBuffer
import java.util.concurrent.Executor

private const val TAG = "VintogrophyGL"

/**
 * GPU preview filter: CameraX writes frames into an OES texture, a fragment
 * shader applies the active [PhotoFilter] color matrix, output is drawn to the
 * surface CameraX requests (PreviewView downstream handles scaling/rotation).
 *
 * The color matrix is a per-frame uniform, so filters switch instantly without
 * restarting the camera pipeline.
 *
 * All EGL/GL state is confined to a single dedicated GL thread.
 */
class ColorMatrixSurfaceProcessor : SurfaceProcessor, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = aPosition;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            uniform mat4 uTexMatrix;
            uniform mat4 uColor;
            uniform vec4 uOffset;
            varying vec2 vTexCoord;
            void main() {
                vec2 texCoord = (uTexMatrix * vec4(vTexCoord, 0.0, 1.0)).st;
                vec4 color = texture2D(sTexture, texCoord);
                gl_FragColor = uColor * color + uOffset;
            }
        """

        private val POSITIONS = floatArrayOf(
            -1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f,
        )

        private val TEX_COORDS = floatArrayOf(
            0f, 0f,
            0f, 1f,
            1f, 0f,
            1f, 1f,
        )
    }

    private val glThread = HandlerThread("VintogrophyGL").also { it.start() }
    private val glHandler = Handler(glThread.looper)
    internal val glExecutor: Executor = Executor { glHandler.post(it) }

    @Volatile
    private var uniform = ColorMatrixUniform(PhotoFilter.NONE.values)

    @Volatile
    private var released = false

    private var gl: GlState? = null

    init {
        glExecutor.execute { initGl() }
    }

    fun setColorMatrix(values: FloatArray) {
        uniform = ColorMatrixUniform(values)
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        val state = glState()

        state.currentInput?.let { (surface, texture) ->
            texture.setOnFrameAvailableListener(null)
        }

        val texture = SurfaceTexture(state.textureId)
        texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        val surface = Surface(texture)
        state.currentInput = surface to texture

        request.provideSurface(surface, glExecutor) {
            if (state.currentInput?.first === surface) {
                state.currentInput = null
            }
            texture.setOnFrameAvailableListener(null)
            surface.release()
            texture.release()
        }
        texture.setOnFrameAvailableListener(this, glHandler)
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (released) {
            output.close()
            return
        }
        val state = glState()

        var outputSurface: Surface? = null
        outputSurface = output.getSurface(glExecutor) {
            val key = outputSurface ?: return@getSurface
            state.makeInputCurrent()
            state.eglDestroySurface(state.outputs[key]?.eglSurface)
            state.outputs.remove(key)
        }

        val eglSurface = state.eglCreateWindowSurface(outputSurface)
        state.outputs[outputSurface] = OutputEntry(eglSurface, output)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (released) return
        val state = gl ?: return
        state.makeInputCurrent()
        surfaceTexture.updateTexImage()

        val textureMatrix = FloatArray(16)
        surfaceTexture.getTransformMatrix(textureMatrix)

        for (entry in state.outputs.values) {
            val matrix = FloatArray(16)
            entry.output.updateTransformMatrix(matrix, textureMatrix)
            if (!EGL14.eglMakeCurrent(state.display, entry.eglSurface, entry.eglSurface, state.context)) {
                Log.w(TAG, "eglMakeCurrent failed for output: ${EGL14.eglGetError()}")
                continue
            }
            val size = IntArray(2)
            EGL14.eglQuerySurface(state.display, entry.eglSurface, EGL14.EGL_WIDTH, size, 0)
            EGL14.eglQuerySurface(state.display, entry.eglSurface, EGL14.EGL_HEIGHT, size, 1)
            GLES20.glViewport(0, 0, size[0], size[1])
            state.draw(matrix, uniform)
            EGL14.eglSwapBuffers(state.display, entry.eglSurface)
        }
        state.makeInputCurrent()
    }

    fun release() {
        if (released) return
        released = true
        glExecutor.execute {
            gl?.shutdown()
            gl = null
            glThread.quitSafely()
        }
    }

    private fun initGl() {
        try {
            gl = GlState()
        } catch (e: Exception) {
            Log.e(TAG, "GL initialization failed", e)
            gl = null
        }
    }

    private fun glState(): GlState =
        gl ?: throw ProcessingException().apply {
            initCause(IllegalStateException("GL pipeline not initialized"))
        }

    private class OutputEntry(val eglSurface: EGLSurface, val output: SurfaceOutput)

    private inner class GlState {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
            check(it != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        }

        init {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        }

        private val config: EGLConfig = run {
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            check(
                EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0
            ) { "eglChooseConfig failed" }
            configs[0]!!
        }

        val context: EGLContext = run {
            val attribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attribs, 0).also {
                check(it != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
            }
        }

        private val inputSurface: EGLSurface = GLUtils.createPBufferSurface(display, config, 1, 1)

        val textureId: Int = run {
            check(EGL14.eglMakeCurrent(display, inputSurface, inputSurface, context)) {
                "eglMakeCurrent failed: ${EGL14.eglGetError()}"
            }
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            ids[0]
        }

        private val program: Int = run {
            val vs = GLUtils.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fs = GLUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
            program
        }
        private val positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        private val texCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        private val textureLoc = GLES20.glGetUniformLocation(program, "sTexture")
        private val texMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        private val colorLoc = GLES20.glGetUniformLocation(program, "uColor")
        private val offsetLoc = GLES20.glGetUniformLocation(program, "uOffset")
        private val positionBuffer: FloatBuffer = GLUtils.createFloatBuffer(POSITIONS)
        private val texCoordBuffer: FloatBuffer = GLUtils.createFloatBuffer(TEX_COORDS)

        val outputs = mutableMapOf<Surface, OutputEntry>()
        var currentInput: Pair<Surface, SurfaceTexture>? = null

        fun makeInputCurrent() {
            EGL14.eglMakeCurrent(display, inputSurface, inputSurface, context)
        }

        fun eglCreateWindowSurface(surface: Surface): EGLSurface {
            val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, null, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) {
                "eglCreateWindowSurface failed: ${EGL14.eglGetError()}"
            }
            return eglSurface
        }

        fun eglDestroySurface(eglSurface: EGLSurface?) {
            eglSurface?.let { EGL14.eglDestroySurface(display, it) }
        }

        fun draw(textureMatrix: FloatArray, uniform: ColorMatrixUniform) {
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureLoc, 0)
            GLES20.glUniformMatrix4fv(texMatrixLoc, 1, false, textureMatrix, 0)
            GLES20.glUniformMatrix4fv(colorLoc, 1, false, uniform.color, 0)
            GLES20.glUniform4fv(offsetLoc, 1, uniform.offset, 0)

            positionBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionLoc)
            GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)

            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(texCoordLoc)
            GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun shutdown() {
            currentInput?.let { (surface, texture) ->
                texture.setOnFrameAvailableListener(null)
                surface.release()
                texture.release()
            }
            currentInput = null
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            outputs.values.forEach { EGL14.eglDestroySurface(display, it.eglSurface) }
            outputs.clear()
            EGL14.eglDestroySurface(display, inputSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}

/** CameraX effect wrapping [ColorMatrixSurfaceProcessor] for the preview pipeline. */
@SuppressLint("RestrictedApiAndroidX")
class ColorMatrixEffect(
    processor: ColorMatrixSurfaceProcessor,
) : CameraEffect(
    CameraEffect.PREVIEW,
    CameraEffect.TRANSFORMATION_CAMERA_AND_SURFACE_ROTATION,
    processor.glExecutor,
    processor,
    { Log.w(TAG, "Color matrix effect error", it) },
)

