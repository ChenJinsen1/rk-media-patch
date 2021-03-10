package com.rockchip.vr.videoplayer.model;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.rockchip.vr.videoplayer.utils.SystemProperties;
import com.rockchip.vr.videoplayer.utils.Constant;
import com.rockchip.vr.videoplayer.utils.ShaderHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CurveCanvas {
    private int curve_dot;
    private boolean firstFlag = true;

    private final String TAG = "CurveCanvas";

    int[] gpuBuffer = new int[4];

    private int vBufferHandle_curve_canvas;
    private int tBufferHandle_curve_canvas;
    private int tBufferHandle_curve_canvas_1;
    private int tBufferHandle_curve_canvas_2;

    FloatBuffer curve_canvas_vBuffer;
    FloatBuffer curve_canvas_tBuffer;
    FloatBuffer curve_canvas_tBuffer_l;
    FloatBuffer curve_canvas_tBuffer_r;

    private int flipY = 0;

    private int[] program = new int[3];
    private int texture;
    private float xRatio = 1f;
    private int shaderType;
    private int drawMode;
    private float[] mvpMatrix = new float[16];
    private int positionHandle;
    private int mvpHandle;
    private int textureHandle;
    private int textureCoordinateHandle;
    private int xRatioHandle;
    private int transformMatHandle;
    private int flipYHandle;

    private final String VERTEX_SHADER =
            "precision highp float;\n"
                    + "uniform mat4 uMVP;\n"
                    + "uniform mat4 uTransMat;\n"
                    + "uniform int uFlipY;\n"
                    + "attribute vec4 aPosition;\n"
                    + "attribute vec2 aTexCoordinate;\n"
                    + "varying vec2 v_TexCoordinate;\n"
                    + "void main () {\n"
                    + "	vec4 tmp = vec4(aTexCoordinate.xy, 1.0, 1.0);\n"
                    + "	tmp = uTransMat * tmp;\n"
                    + "	v_TexCoordinate = vec2(tmp.x, tmp.y);\n"
                    + "	if(uFlipY>0){\n"
                    + "		v_TexCoordinate.y = 1.0 - v_TexCoordinate.y;\n"
                    + "	}\n"
                    + "	gl_Position = uMVP * aPosition;\n"
                    + "	gl_Position = gl_Position.xyzz;\n"
                    + "}\n";

    private final String FRAGMENT_SHADER =
            "uniform sampler2D texture;\n"
                    + "precision highp float;\n"
                    + "varying vec2 v_TexCoordinate;\n"
                    + "void main () {\n"
                    + "	gl_FragColor = texture2D(texture, v_TexCoordinate);\n"
                    + "}\n";

    private final String FRAGMENT_SHADER_OES =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "uniform samplerExternalOES texture;\n"
                    + "precision highp float;\n"
                    + "varying vec2 v_TexCoordinate;\n"
                    + "void main () {\n"
                    + "	gl_FragColor = texture2D(texture, v_TexCoordinate);\n"
                    + "}\n";

    public static final int SHADER_MAP_WORLD = 0;
    public static final int SHADER_OES_WORLD = 1;
    private float[] transformMat = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    public void initShader() {
        final int vertex = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        final int fragment = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        final int fragment_oes = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_OES);
        this.program[0] = ShaderHelper.createAndLinkProgram(vertex, fragment,
                new String[]{"texture", "aPosition", "uMVP", "aTexCoordinate"});
        this.program[1] = ShaderHelper.createAndLinkProgram(vertex, fragment_oes,
                new String[]{"texture", "aPosition", "uMVP", "aTexCoordinate"});
    }

    public void init() {
        if (firstFlag) {
            GLES20.glGenBuffers(gpuBuffer.length, gpuBuffer, 0);
            initMesh();
            bindBuffer();
            initShader();
            firstFlag = false;
        }
    }

    private void initMesh() {
        // canvas mesh
        int w = 1; // mesh width
        int h = 1; // mesh height
        int stride = w + 1;
        int num = (w + 1) * (h + 1);
        curve_dot = w * h * 6;
        float[] ver = new float[num * 4];
        float[] tex = new float[num * 4];
        float[] tex_l = new float[num * 4];
        float[] tex_r = new float[num * 4];
        float[] final_ver = new float[curve_dot * 4];
        float[] final_tex = new float[curve_dot * 4];
        float[] final_tex_l = new float[curve_dot * 4];
        float[] final_tex_r = new float[curve_dot * 4];

		float z_scale = 1.0f;
		String p = SystemProperties.get("sys.vr.cinema_scale","1");
		z_scale = Float.parseFloat(p);
		
        // init ver & tex
        for (int i = 0; i <= w; i++)
            for (int j = 0; j <= h; j++) {
                float x = (float) i / (float) w * 2.0f + (-1.0f);
                float y = (float) j / (float) h * 2.0f + (-1.0f);
                double length = Math.sqrt((double) (x * x + y * y + 1.0f));
//				double scale = 1.0f / length;
                double scale = 1f;
                float tx = ((float) i) / ((float) w);
                float ty = ((float) j) / ((float) h);

                ver[(stride * i + j) * 4 + 0] = (float) (x * scale);
                ver[(stride * i + j) * 4 + 1] = (float) (y * scale);
                ver[(stride * i + j) * 4 + 2] = (float) (-1.0f * scale * z_scale);
                ver[(stride * i + j) * 4 + 3] = 1.0f;

                tex[(stride * i + j) * 2 + 0] = tx;
                tex[(stride * i + j) * 2 + 1] = 1.0f - ty;
                tex[(stride * i + j) * 2 + 2] = 0;
                tex[(stride * i + j) * 2 + 3] = 1.0f;

                tex_l[(stride * i + j) * 2 + 0] = (tx / 2.0f);
                tex_l[(stride * i + j) * 2 + 1] = 1.0f - ty;
                tex_l[(stride * i + j) * 2 + 2] = 0;
                tex_l[(stride * i + j) * 2 + 3] = 1.0f;

                tex_r[(stride * i + j) * 2 + 0] = 0.5f + (tx / 2.0f);
                tex_r[(stride * i + j) * 2 + 1] = 1.0f - ty;
                tex_r[(stride * i + j) * 2 + 2] = 0;
                tex_r[(stride * i + j) * 2 + 3] = 1.0f;

            }

        int index_ver = 0;
        int index_tex = 0;
        int index_tex_l = 0;
        int index_tex_r = 0;

        // init final ver & tex
        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++) {
                // triangle 1
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 3];

                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 3];

                final_ver[(index_ver++)] = ver[(stride * i + j) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * i + j) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * i + j) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * i + j) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * i + j) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * i + j) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * i + j) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * i + j) * 2 + 3];

                // triangle 2
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + (j + 1)) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + (j + 1)) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + (j + 1)) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + (j + 1)) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + (j + 1)) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + (j + 1)) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + (j + 1)) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + (j + 1)) * 2 + 3];

                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * i + (j + 1)) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * i + (j + 1)) * 2 + 3];

                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 0];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 1];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 2];
                final_ver[(index_ver++)] = ver[(stride * (i + 1) + j) * 4 + 3];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 0];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 1];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 2];
                final_tex[(index_tex++)] = tex[(stride * (i + 1) + j) * 2 + 3];

            }

        // init left & right final tex
        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++) {
                // left
                // triangle 1
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 3];

                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 3];

                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + j) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + j) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + j) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + j) * 2 + 3];

                // triangle 2
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + (j + 1)) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + (j + 1)) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + (j + 1)) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + (j + 1)) * 2 + 3];

                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * i + (j + 1)) * 2 + 3];

                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 0];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 1];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 2];
                final_tex_l[(index_tex_l++)] = tex_l[(stride * (i + 1) + j) * 2 + 3];

                // right
                // triangle 1
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 3];

                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 3];

                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + j) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + j) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + j) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + j) * 2 + 3];

                // triangle 2
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + (j + 1)) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + (j + 1)) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + (j + 1)) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + (j + 1)) * 2 + 3];

                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * i + (j + 1)) * 2 + 3];

                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 0];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 1];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 2];
                final_tex_r[(index_tex_r++)] = tex_r[(stride * (i + 1) + j) * 2 + 3];
            }

        ByteBuffer vBb1 = ByteBuffer.allocateDirect(final_ver.length * 2 * Constant.BYTE_PER_FLOAT);
        vBb1.order(ByteOrder.nativeOrder());
        curve_canvas_vBuffer = vBb1.asFloatBuffer();

        ByteBuffer tBb1 = ByteBuffer.allocateDirect(final_tex.length * 2 * Constant.BYTE_PER_FLOAT);
        tBb1.order(ByteOrder.nativeOrder());
        curve_canvas_tBuffer = tBb1.asFloatBuffer();

        ByteBuffer tBb2 = ByteBuffer.allocateDirect(final_tex.length * 2 * Constant.BYTE_PER_FLOAT);
        tBb2.order(ByteOrder.nativeOrder());
        curve_canvas_tBuffer_l = tBb2.asFloatBuffer();

        ByteBuffer tBb3 = ByteBuffer.allocateDirect(final_tex.length * 2 * Constant.BYTE_PER_FLOAT);
        tBb3.order(ByteOrder.nativeOrder());
        curve_canvas_tBuffer_r = tBb3.asFloatBuffer();

        curve_canvas_vBuffer.position(0);
        curve_canvas_tBuffer.position(0);
        curve_canvas_tBuffer_l.position(0);
        curve_canvas_tBuffer_r.position(0);

        curve_canvas_vBuffer.put(final_ver, 0, final_ver.length);
        curve_canvas_tBuffer.put(final_tex, 0, final_tex.length);
        curve_canvas_tBuffer_l.put(final_tex_l, 0, final_tex_l.length);
        curve_canvas_tBuffer_r.put(final_tex_r, 0, final_tex_r.length);

        curve_canvas_vBuffer.position(0);
        curve_canvas_tBuffer.position(0);
        curve_canvas_tBuffer_l.position(0);
        curve_canvas_tBuffer_r.position(0);

    }

    private void bindBuffer() {
        vBufferHandle_curve_canvas = gpuBuffer[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vBufferHandle_curve_canvas);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, curve_canvas_vBuffer.capacity() * Constant.BYTE_PER_SHORT, curve_canvas_vBuffer, GLES20.GL_STATIC_DRAW);

        tBufferHandle_curve_canvas = gpuBuffer[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, curve_canvas_tBuffer.capacity() * Constant.BYTE_PER_SHORT, curve_canvas_tBuffer, GLES20.GL_STATIC_DRAW);

        tBufferHandle_curve_canvas_1 = gpuBuffer[2];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas_1);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, curve_canvas_tBuffer_l.capacity() * Constant.BYTE_PER_SHORT, curve_canvas_tBuffer_l, GLES20.GL_STATIC_DRAW);

        tBufferHandle_curve_canvas_2 = gpuBuffer[3];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas_2);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, curve_canvas_tBuffer_r.capacity() * Constant.BYTE_PER_SHORT, curve_canvas_tBuffer_r, GLES20.GL_STATIC_DRAW);

        checkGlError("bindBuffer");
    }

    public void bindTexture(int texture) {
        this.texture = texture;
    }

//	public void setXRatio(float value){
//		this.xRatio = value;
//	}

    public void setFlipY(boolean bool) {
        this.flipY = bool ? 1 : 0;
    }

    public void setTransformMatrix(float[] mat) {
        this.transformMat = mat;
    }

    public void setShaderType(int type) {
        this.shaderType = type;
    }

    public void setDrawMode(int mode) {
        this.drawMode = mode;
    }

    public void loadIdentityMat() {
        Matrix.setIdentityM(this.mvpMatrix, 0);
    }

    public void pushMat(float[] matrix) {
        Matrix.multiplyMM(this.mvpMatrix, 0, matrix, 0, this.mvpMatrix, 0);
    }

    public float[] popMat() {
        return this.mvpMatrix;
    }

    public void draw(int eye) {
        GLES20.glUseProgram(this.program[this.shaderType]);
        this.positionHandle = GLES20.glGetAttribLocation(this.program[this.shaderType], "aPosition");
        this.mvpHandle = GLES20.glGetUniformLocation(this.program[this.shaderType], "uMVP");
        this.textureHandle = GLES20.glGetUniformLocation(this.program[this.shaderType], "texture");
        this.textureCoordinateHandle = GLES20.glGetAttribLocation(this.program[this.shaderType], "aTexCoordinate");
//		this.xRatioHandle = GLES20.glGetUniformLocation(this.program[this.shaderType], "uRatio");
//		GLES20.glUniform1f(this.xRatioHandle, this.xRatio);
        this.transformMatHandle = GLES20.glGetUniformLocation(this.program[this.shaderType], "uTransMat");
        GLES20.glUniformMatrix4fv(this.transformMatHandle, 1, false, this.transformMat, 0);

        this.flipYHandle = GLES20.glGetUniformLocation(this.program[this.shaderType], "uFlipY");
        GLES20.glUniform1i(this.flipYHandle, flipY);
        GLES20.glEnableVertexAttribArray(this.positionHandle);
        GLES20.glEnableVertexAttribArray(this.textureCoordinateHandle);
        if (0 == this.shaderType) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.texture);
            GLES20.glUniform1i(this.textureHandle, 0);
        }
        if (1 == this.shaderType) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this.texture);
            GLES20.glUniform1i(textureHandle, 0);
        }
        if (1 == this.drawMode) {
            if (1 == eye) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vBufferHandle_curve_canvas);
                GLES20.glVertexAttribPointer(this.positionHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas_1);
                GLES20.glVertexAttribPointer(textureCoordinateHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
            } else if (2 == eye) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vBufferHandle_curve_canvas);
                GLES20.glVertexAttribPointer(this.positionHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas_2);
                GLES20.glVertexAttribPointer(textureCoordinateHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
            }
        } else {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vBufferHandle_curve_canvas);
            GLES20.glVertexAttribPointer(this.positionHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tBufferHandle_curve_canvas);
            GLES20.glVertexAttribPointer(this.textureCoordinateHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
        }
        GLES20.glUniformMatrix4fv(this.mvpHandle, 1, false, this.mvpMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, curve_dot);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisableVertexAttribArray(this.positionHandle);
        GLES20.glDisableVertexAttribArray(this.textureCoordinateHandle);

        checkGlError("draw_pano");
    }

    public void checkGlError(String op) {
        int ret = 0;
        if ((ret = GLES20.glGetError()) != 0)
            Log.d(TAG, "checkGlError op " + op + " error " + ret);
    }

}
