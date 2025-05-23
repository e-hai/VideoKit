package com.kit.video.glutil;



import static com.kit.video.glutil.CommonShaders.FLIPPED_TEXTURE_VERTICES;
import static com.kit.video.glutil.CommonShaders.TEXTURE_VERTICES;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.util.HashMap;
import java.util.Map;

/**
 * 简单快捷的渲染一般的纹理
 * **/
public class TextureRenderer {


    private static final String TAG = "TextureRenderer";
    private static final int ATTRIB_POSITION = 1;
    private static final int ATTRIB_TEXTURE_COORDINATE = 2;
    private int program = 0;
    private int frameUniform;
    private int textureTransformUniform;
    private final float[] textureTransformMatrix = new float[16];
    private boolean flipY;

    /**
     * Call this to setup the shader program before rendering.
     */
    public void setup() {
        Map<String, Integer> attributeLocations = new HashMap<>();
        attributeLocations.put("position", ATTRIB_POSITION);
        attributeLocations.put("texture_coordinate", ATTRIB_TEXTURE_COORDINATE);
        program =
                ShaderUtil.createProgram(
                        CommonShaders.VERTEX_SHADER, CommonShaders.FRAGMENT_SHADER, attributeLocations);
        frameUniform = GLES20.glGetUniformLocation(program, "video_frame");
        textureTransformUniform = GLES20.glGetUniformLocation(program, "texture_transform");
        ShaderUtil.checkGlError("glGetUniformLocation");
        Matrix.setIdentityM(textureTransformMatrix, 0 /* offset */);
    }

    /**
     * Flips rendering output vertically, useful for conversion between coordinate systems with
     * top-left v.s. bottom-left origins. Effective in subsequent { #render(int)} calls.
     */
    public void setFlipY(boolean flip) {
        flipY = flip;
    }

    /**
     * Renders a texture to the framebuffer.
     *
     * <p>Before calling this, {@link #setup} must have been called.
     *
     * 增加flush参数，控制是否需要立即执行GLES20.glFlush()，该方法执行后，已调用的GL方法都会被执行，
     * 如果在该方法后面再去调用GL方法的话，会出现预想不到的渲染情况
     */
    public void render(int textureName, boolean flush) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        ShaderUtil.checkGlError("glActiveTexture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName);
        ShaderUtil.checkGlError("glBindTexture");
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        ShaderUtil.checkGlError("glTexParameteri");

        GLES20.glUseProgram(program);
        ShaderUtil.checkGlError("glUseProgram");
        GLES20.glUniform1i(frameUniform, 0);
        ShaderUtil.checkGlError("glUniform1i");
        GLES20.glUniformMatrix4fv(textureTransformUniform, 1, false, textureTransformMatrix, 0);
        ShaderUtil.checkGlError("glUniformMatrix4fv");
        GLES20.glEnableVertexAttribArray(ATTRIB_POSITION);
        GLES20.glVertexAttribPointer(
                ATTRIB_POSITION, 2, GLES20.GL_FLOAT, false, 0, CommonShaders.SQUARE_VERTICES);

        GLES20.glEnableVertexAttribArray(ATTRIB_TEXTURE_COORDINATE);
        GLES20.glVertexAttribPointer(
                ATTRIB_TEXTURE_COORDINATE,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                flipY ? FLIPPED_TEXTURE_VERTICES : TEXTURE_VERTICES);
        ShaderUtil.checkGlError("program setup");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        ShaderUtil.checkGlError("glDrawArrays");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGlError("glBindTexture");

        if (flush) {
            GLES20.glFlush();
        }
    }

    /**
     * Call this to delete the shader program.
     *
     * <p>This is only necessary if one wants to release the program while keeping the context around.
     */
    public void release() {
        GLES20.glDeleteProgram(program);
    }
}