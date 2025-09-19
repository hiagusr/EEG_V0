package com.example.eeg_v0

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eeg_v0.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBluetoothConnected: Boolean = false
    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth ativado.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Falha ao ativar o Bluetooth.", Toast.LENGTH_SHORT).show()
        }
    }

    // Nova forma de lidar com a solicitação de permissão de localização
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean -> // isGranted é true se a permissão foi concedida
        if (isGranted) {
            Toast.makeText(this, "Permissão de localização concedida.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndEnableBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Dispositivo não suporta Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            startBluetoothDiscovery()
        }
    }

    private fun startBluetoothDiscovery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // TODO: AQUI É ONDE VOCÊ INICIA O ESCANEAMENTO E CONEXÃO

            Toast.makeText(this, "Bluetooth ativo e permissão de localização concedida. Pronto para escanear!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // As duas linhas abaixo devem ser as primeiras, logo após o super.onCreate()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.button.setOnClickListener {
            if (isBluetoothConnected) {
                val intent = Intent(this, ViewScreen::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "tudo pronto só falta conectar", Toast.LENGTH_SHORT).show()
                checkAndEnableBluetooth()
            }
        }
    }
}