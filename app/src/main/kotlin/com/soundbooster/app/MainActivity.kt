package com.soundbooster.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.soundbooster.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBoostEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        loadState()
        setupListeners()
        updateUI()
    }

    private fun setupListeners() {
        binding.powerButton.setOnClickListener {
            isBoostEnabled = !isBoostEnabled
            if (isBoostEnabled) {
                startBoostService(binding.seekBarBoost.progress)
            } else {
                stopBoostService()
            }
            saveState()
            updateUI()
        }

        binding.seekBarBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateBoostDisplay(progress)
                if (isBoostEnabled) {
                    sendBroadcast(
                        Intent(AudioBoostService.ACTION_UPDATE_LEVEL)
                            .setPackage(packageName)
                            .putExtra(AudioBoostService.EXTRA_LEVEL, progress)
                    )
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                saveState()
            }
        })
    }

    private fun startBoostService(level: Int) {
        val intent = Intent(this, AudioBoostService::class.java)
            .putExtra(AudioBoostService.EXTRA_LEVEL, level)
        startForegroundService(intent)
    }

    private fun stopBoostService() {
        stopService(Intent(this, AudioBoostService::class.java))
    }

    private fun updateBoostDisplay(progress: Int) {
        val percent = progress * 10
        binding.tvBoostPercent.text = "+${percent}%"
        binding.circularProgress.progress = progress
    }

    private fun updateUI() {
        updateBoostDisplay(binding.seekBarBoost.progress)
        if (isBoostEnabled) {
            binding.powerButton.text = "ВЫКЛЮЧИТЬ"
            binding.powerButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent)
            )
            binding.tvBoostLabel.text = "АКТИВЕН"
            binding.tvBoostLabel.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            binding.powerButton.text = "ВКЛЮЧИТЬ"
            binding.powerButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.button_off)
            )
            binding.tvBoostLabel.text = "ВЫКЛ"
            binding.tvBoostLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun saveState() {
        getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enabled", isBoostEnabled)
            putInt("level", binding.seekBarBoost.progress)
            apply()
        }
    }

    private fun loadState() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        isBoostEnabled = prefs.getBoolean("enabled", false)
        val savedLevel = prefs.getInt("level", 50)
        binding.seekBarBoost.progress = savedLevel
        if (isBoostEnabled) {
            startBoostService(savedLevel)
        }
    }
}
