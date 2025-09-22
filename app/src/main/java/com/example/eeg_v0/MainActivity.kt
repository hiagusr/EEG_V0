@file:Suppress("DEPRECATION")

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
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eeg_v0.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBluetoothConnected: Boolean = false // Você usará isso para lógica de conexão real

    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }

    // Lista para armazenar os dispositivos Bluetooth encontrados (objetos BluetoothDevice)
    private val discoveredDevicesList: MutableList<BluetoothDevice> = mutableListOf()
    // ArrayAdapter para a ListView
    private lateinit var devicesArrayAdapter: ArrayAdapter<String>
    // Lista de strings (Nome + Endereço MAC) para popular o ArrayAdapter
    private val deviceListStrings: MutableList<String> = mutableListOf()

    companion object {
        private const val TAG = "MainActivity"
    }

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth ativado.", Toast.LENGTH_SHORT).show()
            checkPermissionsAndStartScan()
        } else {
            Toast.makeText(this, "Falha ao ativar o Bluetooth.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val bluetoothScanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false else true
            val bluetoothConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false else true

            if (fineLocationGranted && bluetoothScanGranted && bluetoothConnectGranted) {
                Toast.makeText(this, "Permissões concedidas.", Toast.LENGTH_SHORT).show()
                startBluetoothSearch() // Inicia a busca após conceder permissões
            } else {
                var message = "Permissão(ões) negada(s):"
                if (!fineLocationGranted) message += "\n- Localização Precisa"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!bluetoothScanGranted) message += "\n- Escaneamento Bluetooth"
                    if (!bluetoothConnectGranted) message += "\n- Conexão Bluetooth"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

        // Inicializa o ArrayAdapter e a ListView
        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceListStrings)
        binding.bleDeviceListView.adapter = devicesArrayAdapter

        binding.bleDeviceListView.setOnItemClickListener { _, _, position, _ ->
            // Cancela a descoberta, pois é intensiva e não é necessária ao tentar conectar
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                 bluetoothAdapter?.cancelDiscovery()
            }

            val selectedDevice = discoveredDevicesList[position]
            // Aqui você obteria o MAC address se necessário: selectedDevice.address
            // E o nome: selectedDevice.name
            Toast.makeText(this, "Selecionado: ${selectedDevice.name ?: "Dispositivo Desconhecido"} - ${selectedDevice.address}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Dispositivo selecionado: ${selectedDevice.name}, MAC: ${selectedDevice.address}")

            // TODO: Iniciar a lógica de conexão com selectedDevice
            // Por exemplo: connectToDevice(selectedDevice)
            // Atualize isBluetoothConnected = true após conexão bem sucedida
        }

        binding.button.setOnClickListener {
            if (isBluetoothConnected) {
                val intent = Intent(this, ViewScreen::class.java)
                startActivity(intent)
            } else {
                devicesArrayAdapter.clear() // Limpa a lista antes de um novo scan
                discoveredDevicesList.clear()
                checkAndEnableBluetooth()
            }
        }

        // Registra o BroadcastReceiver para eventos de descoberta de Bluetooth
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryReceiver, filter)
    }

    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Dispositivo não suporta Bluetooth.", Toast.LENGTH_LONG).show()
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    enableBtLauncher.launch(enableBtIntent)
                } else {
                    // A solicitação de permissão lidará com o início da verificação ou ativação do BT
                    checkPermissionsAndStartScan()
                }
            } else {
                enableBtLauncher.launch(enableBtIntent)
            }
        } else {
            checkPermissionsAndStartScan()
        }
    }

    private fun checkPermissionsAndStartScan() {
        val requiredPermissions = mutableListOf<String>()
        // ACCESS_FINE_LOCATION é necessária para descoberta de dispositivos, mesmo para Bluetooth Clássico
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            startBluetoothSearch() // Todas as permissões já concedidas
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }

    @SuppressLint("MissingPermission") // As permissões são verificadas em checkPermissionsAndStartScan
    private fun startBluetoothSearch() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter!!.cancelDiscovery() // Cancela descoberta anterior, se houver
        }

        // Limpa listas antes de nova busca
        deviceListStrings.clear()
        discoveredDevicesList.clear()
        devicesArrayAdapter.notifyDataSetChanged()

        // Inicia a descoberta. O resultado será tratado pelo BroadcastReceiver.
        val discoveryStarted = bluetoothAdapter?.startDiscovery()

        if (discoveryStarted == true) {
            Toast.makeText(this, "Iniciando busca por dispositivos Bluetooth...", Toast.LENGTH_LONG).show()
            Log.d(TAG, "startDiscovery iniciada com sucesso.")
        } else {
            Toast.makeText(this, "Falha ao iniciar a busca por dispositivos.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Falha ao iniciar startDiscovery. Verifique permissões e estado do Bluetooth.")
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // As permissões são verificadas antes de chamar startDiscovery
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.action
            when (action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Busca por dispositivos iniciada.")
                    Toast.makeText(context, "Buscando...", Toast.LENGTH_SHORT).show()
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
onNullDevice -> // Renomeado para evitar conflito com o nome da classe
                        val deviceName = onNullDevice.name
                        val deviceAddress = onNullDevice.address
                        // Adiciona apenas se tiver nome e não estiver na lista (para evitar duplicatas)
                        if (deviceName != null && !discoveredDevicesList.any { it.address == deviceAddress }) {
                            discoveredDevicesList.add(onNullDevice)
                            deviceListStrings.add("$deviceName\n$deviceAddress")
                            devicesArrayAdapter.notifyDataSetChanged()
                            Log.i(TAG, "Dispositivo encontrado: $deviceName ($deviceAddress)")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Busca por dispositivos finalizada.")
                    Toast.makeText(context, "Busca finalizada.", Toast.LENGTH_SHORT).show()
                    if (discoveredDevicesList.isEmpty()) {
                        Toast.makeText(context, "Nenhum dispositivo encontrado.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancela a descoberta e remove o registro do receiver para evitar memory leaks
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothAdapter?.cancelDiscovery()
        }
        unregisterReceiver(discoveryReceiver)
        Log.d(TAG, "onDestroy: Descoberta cancelada e receiver desregistrado.")
    }
}
