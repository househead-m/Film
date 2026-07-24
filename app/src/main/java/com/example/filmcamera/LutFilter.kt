package com.example.filmcamera

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageTwoInputFilter

/**
 * .cube LUT를 변환한 "가로 스트립" 텍스처(lut_jejucine_w.png 같은 것)를 읽어서
 * 카메라 화면에 실시간으로 색보정을 입히는 필터.
 *
 * 텍스처 구조: 너비 = lutSize*lutSize, 높이 = lutSize
 * 파란색(B) 값에 따라 가로로 나열된 타일 중 하나를 고르고,
 * 그 타일 안에서 x축=R, y축=G 위치를 찾아 색을 치환하는 방식.
 */
class LutFilter(private var lutSize: Float) : GPUImageTwoInputFilter(FRAGMENT_SHADER) {

    private var lutSizeLocation: Int = 0

    override fun onInit() {
        super.onInit()
        lutSizeLocation = GLES20.glGetUniformLocation(program, "lutSize")
    }

    override fun onInitialized() {
        super.onInitialized()
        setLutSize(lutSize)
    }

    fun setLutSize(size: Float) {
        lutSize = size
        setFloat(lutSizeLocation, size)
    }

    companion object {
        private const val FRAGMENT_SHADER = """
            varying highp vec2 textureCoordinate;
            varying highp vec2 textureCoordinate2;

            uniform sampler2D inputImageTexture;
            uniform sampler2D inputImageTexture2;
            uniform highp float lutSize;

            void main() {
                highp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);

                highp float blueColor = textureColor.b * (lutSize - 1.0);
                highp float blue0 = floor(blueColor);
                highp float blue1 = min(blue0 + 1.0, lutSize - 1.0);
                highp float fracPart = blueColor - blue0;

                highp vec2 texPos0;
                texPos0.x = (blue0 * lutSize + textureColor.r * (lutSize - 1.0) + 0.5) / (lutSize * lutSize);
                texPos0.y = (textureColor.g * (lutSize - 1.0) + 0.5) / lutSize;

                highp vec2 texPos1;
                texPos1.x = (blue1 * lutSize + textureColor.r * (lutSize - 1.0) + 0.5) / (lutSize * lutSize);
                texPos1.y = texPos0.y;

                lowp vec4 newColor0 = texture2D(inputImageTexture2, texPos0);
                lowp vec4 newColor1 = texture2D(inputImageTexture2, texPos1);
                lowp vec4 newColor = mix(newColor0, newColor1, fracPart);

                gl_FragColor = vec4(newColor.rgb, textureColor.a);
            }
        """
    }
}
