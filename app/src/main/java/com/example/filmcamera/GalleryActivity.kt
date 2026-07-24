package com.example.filmcamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        findViewById<Button>(R.id.btnCloseGallery).setOnClickListener { finish() }

        loadPhotos()
    }

    private fun loadPhotos() {
        val grid = findViewById<GridLayout>(R.id.galleryGrid)
        grid.removeAllViews()

        val uris = mutableListOf<Uri>()

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Pictures/FilmCamera%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                uris.add(
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                )
            }
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cellSize = screenWidth / 3

        for (uri in uris) {
            val imageView = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize - 8
                    height = cellSize - 8
                    setMargins(4, 4, 4, 4)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            }
            Glide.with(this).load(uri).centerCrop().into(imageView)
            grid.addView(imageView)
        }
    }
}
