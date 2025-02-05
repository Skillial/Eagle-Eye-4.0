package com.wangGang.eagleEye.ui.activities
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.wangGang.eagleEye.R

class PhotoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)

        val imageView: ImageView = findViewById(R.id.photoView)
        val backButton: ImageButton = findViewById(R.id.backButton)

        // Retrieve the image URI from intent
        val imageUri = intent.getStringExtra("imageUri")
        if (imageUri != null) {
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(Uri.parse(imageUri)))
            imageView.setImageBitmap(bitmap)
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}