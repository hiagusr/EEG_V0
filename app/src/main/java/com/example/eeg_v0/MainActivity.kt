package com.example.eeg_v0

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager // Import adicionado
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eeg_v0.databinding.ActivityMainBinding
import java.nio.charset.Charset
import java.util.UUID

@SuppressLint("MissingPermission") // As permissões são verificadas antes do uso
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBluetoothConnected: Boolean = false
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // Escaneia por 10 segundos

    private val bluetoothManager: BluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private val discoveredDevicesList: MutableList<BluetoothDevice> = mutableListOf()
    private lateinit var devicesArrayAdapter: ArrayAdapter<String>
    private val deviceListStrings: MutableList<String> = mutableListOf()

    companion object {
        private const val TAG = "MainActivity_BLE"
        // !!! IMPORTANTE: Substitua estes UUIDs pelos UUIDs corretos do SEU dispositivo EEG !!!
        private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b") // Exemplo, troque!
        private val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8") // Exemplo, troque!
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Ação para o Broadcast
        const val ACTION_EEG_DATA_RECEIVED = "com.example.eeg_v0.ACTION_EEG_DATA_RECEIVED"
        const val EXTRA_EEG_DATA = "com.example.eeg_v0.EXTRA_EEG_DATA"
    }

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth ativado.", Toast.LENGTH_SHORT).show()
            checkPermissionsAndStartBleScan()
        } else {
            Toast.makeText(this, "Falha ao ativar o Bluetooth.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissões concedidas.", Toast.LENGTH_SHORT).show()
                startBleScan()
            } else {
                Toast.makeText(this, "Permissões BLE negadas. Não é possível escanear.", Toast.LENGTH_LONG).show()
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

        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceListStrings)
        binding.bleDeviceListView.adapter = devicesArrayAdapter

        binding.bleDeviceListView.setOnItemClickListener { _, _, position, _ ->
            if (isScanning) {
                stopBleScan()
            }
            val selectedDevice = discoveredDevicesList[position]
            connectToDevice(selectedDevice)
        }

        binding.button.setOnClickListener {
            if (isBluetoothConnected) {
                val intent = Intent(this, ViewScreen::class.java)
                startActivity(intent)
            } else {
                discoveredDevicesList.clear()
                deviceListStrings.clear()
                devicesArrayAdapter.notifyDataSetChanged()
                checkAndEnableBluetooth()
            }
        }
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
                    checkPermissionsAndStartBleScan()
                }
            } else {
                enableBtLauncher.launch(enableBtIntent)
            }
        } else {
            checkPermissionsAndStartBleScan()
        }
    }

    private fun checkPermissionsAndStartBleScan() {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissionsToRequest.isEmpty()) {
            startBleScan()
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun startBleScan() {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Tentando escanear sem permissões.")
            checkPermissionsAndStartBleScan()
            return
        }
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner não está disponível.")
            Toast.makeText(this, "Scanner BLE não disponível.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isScanning) {
            handler.postDelayed({
                if (isScanning) {
                    isScanning = false
                    if (bluetoothLeScanner != null) bluetoothLeScanner!!.stopScan(leScanCallback) else throw NullPointerException("Expression 'bluetoothLeScanner' must not be null")
                    Log.d(TAG, "Scan BLE parado automaticamente.")
                    Toast.makeText(this, "Escaneamento BLE finalizado.", Toast.LENGTH_SHORT).show()
                }
            }, SCAN_PERIOD)
            isScanning = true
            discoveredDevicesList.clear()
            deviceListStrings.clear()
            devicesArrayAdapter.notifyDataSetChanged()
            bluetoothLeScanner!!.startScan(leScanCallback)
            Log.d(TAG, "Scan BLE iniciado.")
            Toast.makeText(this, "Escaneando dispositivos BLE...", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Scan BLE já em progresso.")
        }
    }

    private fun stopBleScan() {
        if (isScanning) {
            isScanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
            Log.d(TAG, "Scan BLE parado manually.")
        }
        handler.removeCallbacksAndMessages(null)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toast.makeText(this, "Permissão BLUETOOTH_CONNECT necessária.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Conectando a ${device.name ?: device.address}...", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Conectando ao dispositivo: ${device.address}")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceName = device.name
            val deviceAddress = device.address
            if (deviceName != null && !discoveredDevicesList.any { it.address == deviceAddress }) {
                discoveredDevicesList.add(device)
                deviceListStrings.add("$deviceName\n$deviceAddress")
                runOnUiThread {
                    devicesArrayAdapter.notifyDataSetChanged()
                    Log.i(TAG, "Dispositivo BLE encontrado: $deviceName ($deviceAddress)")
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan BLE falhou com código: $errorCode")
            isScanning = false
            Toast.makeText(this@MainActivity, "Falha no escaneamento BLE: $errorCode", Toast.LENGTH_LONG).show()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: deviceAddress
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i(TAG, "Conectado ao GATT de $deviceName ($deviceAddress)")
                    bluetoothGatt = gatt
                    isBluetoothConnected = true
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Conectado a $deviceName", Toast.LENGTH_SHORT).show()
                        binding.button.text = "Abrir Visualização"
                        Log.i(TAG, "Tentando descobrir serviços...")
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.i(TAG, "Desconectado do GATT de $deviceName ($deviceAddress)")
                    isBluetoothConnected = false
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Desconectado de $deviceName", Toast.LENGTH_SHORT).show()
                        binding.button.text = "Conectar"
                    }
                }
            } else {
                Log.w(TAG, "Erro GATT: $status ao conectar/desconectar de $deviceName ($deviceAddress)")
                isBluetoothConnected = false
                bluetoothGatt?.close()
                bluetoothGatt = null
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Falha na conexão com $deviceName. Status: $status", Toast.LENGTH_LONG).show()
                    binding.button.text = "Conectar"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Serviços descobertos para ${gatt.device.address}")
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Serviço especificado (SERVICE_UUID) não encontrado.")
                    runOnUiThread { Toast.makeText(this@MainActivity, "Serviço EEG não encontrado.", Toast.LENGTH_LONG).show() }
                    return
                }
                val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    Log.e(TAG, "Característica especificada (CHARACTERISTIC_UUID) não encontrada.")
                    runOnUiThread { Toast.makeText(this@MainActivity, "Característica EEG não encontrada.", Toast.LENGTH_LONG).show() }
                    return
                }
                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (gatt.writeDescriptor(descriptor)) {
                            Log.i(TAG, "Notificações habilitadas para ${characteristic.uuid}")
                            runOnUiThread { Toast.makeText(this@MainActivity, "Pronto para receber dados EEG.", Toast.LENGTH_SHORT).show() }
                        } else {
                            Log.e(TAG, "Falha ao escrever no descritor CCCD para habilitar notificações.")
                        }
                    } else {
                        Log.e(TAG, "Descritor CCCD não encontrado para ${characteristic.uuid}")
                    }
                } else {
                    Log.e(TAG, "Falha ao definir notificação para ${characteristic.uuid}")
                }
            } else {
                Log.w(TAG, "Falha ao descobrir serviços: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val data = characteristic.value
                // O código do ESP32 envia uma String, então convertemos o ByteArray para String.
                // É importante usar o Charset correto, UTF-8 é comum.
                val dataString = String(data, Charset.forName("UTF-8"))
                Log.i(TAG, "Dados EEG recebidos [${characteristic.uuid}]: $dataString")

                // Envia os dados para a ViewScreen via LocalBroadcastManager
                val intent = Intent(ACTION_EEG_DATA_RECEIVED)
                intent.putExtra(EXTRA_EEG_DATA, dataString)
                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)

                // Toast para depuração rápida (pode ser removido)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Dado EEG: $dataString", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
             if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == CHARACTERISTIC_UUID) {
                    val data = characteristic.value
                    val dataString = String(data, Charset.forName("UTF-8"))
                    Log.i(TAG, "Leitura da Característica [${characteristic.uuid}] bem sucedida: $dataString")
                    // TODO: Faça algo com os dados lidos, se necessário
                }
            } else {
                Log.w(TAG, "Falha ao ler característica ${characteristic.uuid}, status: $status")
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return fineLocationGranted && scanGranted && connectGranted
        } else {
            return fineLocationGranted
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        Log.d(TAG, "onDestroy: Scan parado, GATT fechado.")
    }
}
