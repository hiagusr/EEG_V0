package com.example.eeg_v0

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.eeg_v0.databinding.ActivityViewBinding // Presumindo que você queira usar ViewBinding aqui também

class ViewScreen : AppCompatActivity() {

    private lateinit var binding: ActivityViewBinding
    private lateinit var bleDataTextView: TextView

    private val eegDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_EEG_DATA_RECEIVED) {
                val data = intent.getStringExtra(MainActivity.EXTRA_EEG_DATA)
                bleDataTextView.text = data ?: "Nenhum dado recebido"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() // Removido se você estiver usando setOnApplyWindowInsetsListener para padding
        binding = ActivityViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleDataTextView = binding.bleDataTextview // Assumindo que o ID no XML é ble_data_textview

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(MainActivity.ACTION_EEG_DATA_RECEIVED)
        LocalBroadcastManager.getInstance(this).registerReceiver(eegDataReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(eegDataReceiver)
    }
}
