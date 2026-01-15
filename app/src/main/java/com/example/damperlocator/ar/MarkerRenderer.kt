package com.example.damperlocator.ar

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.damperlocator.floorplan.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders simple colored markers in AR space
 */
class MarkerRenderer {

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private lateinit var circleVertices: FloatBuffer
    private var circleVertexCount: Int = 0

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                gl_PointSize = 20.0;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                // Simple circle rendering via point sprites
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                gl_FragColor = uColor;
            }
        """

        // Colors for different marker types
        const val COLOR_START = 0xFF00FF00.toInt()  // Green
        const val COLOR_CORNER = 0xFFFF9800.toInt()  // Orange
        const val COLOR_DAMPER = 0xFF9C27B0.toInt()  // Purple
        const val COLOR_VENT = 0xFF607D8B.toInt()    // Blue Grey
        const val COLOR_DOOR = 0xFF795548.toInt()    // Brown
        const val COLOR_BEACON = 0xFF00BCD4.toInt()  // Cyan
        const val COLOR_OTHER = 0xFF9E9E9E.toInt()   // Grey
    }

    fun createOnGlThread() {
        // Create shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // Create a simple circle (just a single point for now - rendered as point sprite)
        val coords = floatArrayOf(0f, 0f, 0f)
        circleVertexCount = 1

        circleVertices = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(coords)
        circleVertices.position(0)
    }

    /**
     * Draw a marker at the given 3D position
     */
    fun drawMarker(
        position: Vector3,
        color: Int,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        size: Float = 0.1f  // Size in meters
    ) {
        GLES20.glUseProgram(program)

        // Create model matrix (translate to position)
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, position.x, position.y, position.z)
        Matrix.scaleM(modelMatrix, 0, size, size, size)

        // Calculate MVP matrix
        val mvMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Set color (ARGB to RGBA)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = ((color shr 24) and 0xFF) / 255f
        GLES20.glUniform4f(colorHandle, r, g, b, a)

        // Enable vertex array
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, circleVertices)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, circleVertexCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}
