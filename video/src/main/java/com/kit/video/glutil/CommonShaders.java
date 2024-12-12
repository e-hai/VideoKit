package com.kit.video.glutil;

import java.nio.FloatBuffer;

/** Collection of common simple shaders and related resources. */
public class CommonShaders {

    public static final String VERTEX_SHADER_NO_TRANSFORM =
            "attribute vec4 position;\n"
                    + "attribute mediump vec4 texture_coordinate;\n"
                    + "varying mediump vec2 sample_coordinate;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  gl_Position = position;\n"
                    + "  sample_coordinate =  texture_coordinate.xy;\n"
                    + "}";


    /**
     * Shader for rendering a simple geometry.
     *
     * <p>Keeps the vertices that are passed in, and applies a transformation to the texture (pass an
     * identity matrix if not needed). The transformation uniform is there to support {@link
     * android.graphics.SurfaceTexture#getTransformMatrix}.
     */
    public static final String VERTEX_SHADER =
            "uniform mat4 texture_transform;\n"
                    + "attribute vec4 position;\n"
                    + "attribute mediump vec4 texture_coordinate;\n"
                    + "varying mediump vec2 sample_coordinate;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  gl_Position = position;\n"
                    + "  sample_coordinate = (texture_transform * texture_coordinate).xy;\n"
                    + "}";
    /** Simple fragment shader that renders a 2D texture. */
    public static final String FRAGMENT_SHADER =
            "varying mediump vec2 sample_coordinate;\n"
                    + "uniform sampler2D video_frame;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(video_frame, sample_coordinate);\n"
                    + "}";
    /**
     * Simple fragment shader that renders a texture bound to the {@link
     * android.opengl.GLES11Ext#GL_TEXTURE_EXTERNAL_OES} target. See {@link
     * android.graphics.SurfaceTexture}.
     */
    public static final String FRAGMENT_SHADER_EXTERNAL =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "varying mediump vec2 sample_coordinate;\n"
                    + "uniform samplerExternalOES video_frame;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(video_frame, sample_coordinate);\n"
                    + "}";
    /**
     * Fragment shader that renders a 2D texture with a black CLAMP_TO_BORDER effect for
     * out-of-bounds co-ordinates.
     */
    public static final String FRAGMENT_SHADER_CLAMP_TO_BORDER =
            "varying mediump vec2 sample_coordinate;\n"
                    + "uniform sampler2D video_frame;\n"
                    + "\n"
                    + "void main() {\n"
                    + " gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);\n"
                    + "  if (all(greaterThan(sample_coordinate, vec2(0.0))) \n"
                    + "      && all(lessThan(sample_coordinate, vec2(1.0)))) {\n"
                    + "    gl_FragColor = texture2D(video_frame, sample_coordinate);\n"
                    + "  }\n"
                    + "}";
    /** Simple fragment shader that renders a texture bound to the {@link
     * android.opengl.GLES11Ext#GL_TEXTURE_EXTERNAL_OES} target with a black CLAMP_TO_BORDER effect
     * for out-of-bounds co-ordinates. See {@link android.graphics.SurfaceTexture}.
     */
    public static final String FRAGMENT_SHADER_EXTERNAL_CLAMP_TO_BORDER =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "varying mediump vec2 sample_coordinate;\n"
                    + "uniform sampler2D video_frame;\n"
                    + "\n"
                    + "void main() {\n"
                    + " gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);\n"
                    + "  if (all(greaterThan(sample_coordinate, vec2(0.0))) \n"
                    + "      && all(lessThan(sample_coordinate, vec2(1.0)))) {\n"
                    + "    gl_FragColor = texture2D(video_frame, sample_coordinate);\n"
                    + "  }\n"
                    + "}";
    /**
     * Vertices for a quad that fills the drawing area.
     *
     * <p>Can be used directly with {@link android.opengl.GLES10#glDrawArrays}.
     */
    public static final FloatBuffer SQUARE_VERTICES =
            ShaderUtil.floatBuffer(
                    -1.0f, -1.0f,  // bottom left
                    1.0f, -1.0f,   // bottom right
                    -1.0f, 1.0f,   // top left
                    1.0f, 1.0f     // top right
            );
    /**
     * Vertices for a quad that fills the drawing area, but rotated 90 degrees.
     */
    public static final FloatBuffer ROTATED_SQUARE_VERTICES =
            ShaderUtil.floatBuffer(
                    -1.0f, 1.0f,   // top left
                    -1.0f, -1.0f,  // bottom left
                    1.0f, 1.0f,    // top right
                    1.0f, -1.0f    // bottom right
            );

    public static final FloatBuffer TEXTURE_VERTICES =
            ShaderUtil.floatBuffer(
                    0.0f, 0.0f, // bottom left
                    1.0f, 0.0f, // bottom right
                    0.0f, 1.0f, // top left
                    1.0f, 1.0f // top right

            );

    public static final FloatBuffer FLIPPED_TEXTURE_VERTICES =
            ShaderUtil.floatBuffer(
                    0.0f, 1.0f, // top left
                    1.0f, 1.0f, // top right
                    0.0f, 0.0f, // bottom left
                    1.0f, 0.0f // bottom right
            );
}
