package com.example.eeg_v0

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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

    // Gerenciadores Bluetooth
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }

    // Launcher para ativar o Bluetooth, usando a API moderna
    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth ativado.", Toast.LENGTH_SHORT).show()
            // Prossiga para verificar as permissões de escaneamento
            checkPermissionsAndStartScan()
        } else {
            Toast.makeText(this, "Falha ao ativar o Bluetooth.", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para solicitar múltiplas permissões
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allPermissionsGranted = false
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissões concedidas.", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "Permissões de Bluetooth negadas. Não é possível escanear dispositivos.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.button.setOnClickListener {
            if (isBluetoothConnected) {
                // Aqui você vai para a próxima tela se já estiver conectado
                val intent = Intent(this, ViewScreen::class.java)
                startActivity(intent)
            } else {
                // Se não, inicie o processo de verificação de Bluetooth e permissões
                checkAndEnableBluetooth()
            }
        }
    }

    private fun checkAndEnableBluetooth() {
        // Verifique se o dispositivo suporta Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Dispositivo não suporta Bluetooth.", Toast.LENGTH_LONG).show()
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            // Se o Bluetooth não estiver ativado, inicie a atividade para ativar
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            // Se o Bluetooth estiver ativado, prossiga para verificar as permissões
            checkPermissionsAndStartScan()
        }
    }

    private fun checkPermissionsAndStartScan() {
        // Verifique se as permissões de escaneamento estão concedidas
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Para versões anteriores a S, apenas a permissão de localização é necessária
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val permissionsToRequest = requiredPermissions.filter {

            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            // Se todas as permissões estiverem concedidas, inicie o escaneamento
            startBluetoothSearch()
        } else {
            // Se houver permissões não concedidas, solicite-as ao usuário
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }

    val foundDevices: MutableList<String> = mutableListOf()
    val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val deviceName = it.name ?: "Dispositivo Desconhecido"
                        val deviceAddress = it.address
                        val deviceInfo = "$deviceName ($deviceAddress)"

                        if (!foundDevices.contains(deviceInfo)) {
                            foundDevices.add(deviceInfo)
                            Toast.makeText(context, "Dispositivo encontrado: $deviceName", Toast.LENGTH_SHORT).show()
                            // TODO: Adicionar lógica para atualizar a lista de dispositivos na sua UI
                        }
                    }
                }
            }
        }

    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
    @SuppressLint("MissingPermission")
    private fun startBluetoothSearch() {

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)

        registerReceiver(receiver, filter)
        foundDevices.clear()
        bluetoothAdapter?.startDiscovery()

        Toast.makeText(this, "Iniciando busca por dispositivos Bluetooth...", Toast.LENGTH_LONG).show()

    }


}