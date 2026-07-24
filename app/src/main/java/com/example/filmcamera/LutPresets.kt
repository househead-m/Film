package com.example.filmcamera

data class LutPreset(
    val name: String,
    val drawableResId: Int,
    val lutSize: Float
)

object LutPresets {
    // 필터를 추가하고 싶으면 이 리스트에 한 줄만 추가하면 됨
    // (텍스처 PNG는 res/drawable-nodpi 에 넣고, cube 크기를 lutSize에 정확히 적어야 함)
    fun getAll(): List<LutPreset> = listOf(
        LutPreset("제주시네 W", R.drawable.lut_jejucine_w, 16f),
        LutPreset("제주시네 S", R.drawable.lut_jejucine_s, 16f),
    )
}
