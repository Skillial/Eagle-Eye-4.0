package com.wangGang.eagleEye.ui.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.wangGang.eagleEye.databinding.ActivityBeforeAndAfterPreviewBinding
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.ui.adapters.ImagePagerAdapter
import me.relex.circleindicator.CircleIndicator3

class BeforeAndAfterPreviewActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var indicator: CircleIndicator3
    private lateinit var backButton: ImageButton
    private lateinit var binding: ActivityBeforeAndAfterPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeforeAndAfterPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val beforeUri = FileImageReader.getInstance()?.getBeforeUriDefaultResultsFolder()
        val afterUri = FileImageReader.getInstance()?.getAfterUriDefaultResultsFolder()

        assignViews()

        // Setup adapter with the 2 images
        val imagesList = listOfNotNull(beforeUri, afterUri)
        val adapter = ImagePagerAdapter(imagesList)
        viewPager.adapter = adapter

        // Attach circle indicator to the ViewPager2
        indicator.setViewPager(viewPager)

        // Handle back button click
        backButton.setOnClickListener {
            finish()  // Close this Activity
        }
    }

    private fun assignViews() {
        // Initialize Views
        viewPager = binding.viewPager
        indicator = binding.indicator
        backButton = binding.backButton
    }
}
