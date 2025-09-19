package com.example.eeg_v0

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity // Necessário para onActivityResult
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eeg_v0.databinding.ActivityMainBinding

private const val REQUEST_ENABLE_BT = 1
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var Bluetooth_Connected: Boolean = false // Usado pelo botão em onCreate

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100

    @Deprecated("Este método é obsoleto e foi substituído pelas Activity Result APIs")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth ativado.", Toast.LENGTH_SHORT).show()
                // novamente para verificar as permissões de localização e prosseguir.
                // starBluetoothSearch() // Cuidado para não criar loops.
            } else {
                Toast.makeText(this, "Falha ao ativar o Bluetooth.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun starBluetoothSearch() { //inicia a busca por dispositivos bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        // Verifica se o dispositivo suporta Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Dispositivo não suporta Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }

        // Verifica se o Bluetooth está habilitado.
        // A permissão BLUETOOTH_CONNECT é necessária para ACTION_REQUEST_ENABLE no Android 12+.
        // A anotação @RequiresPermission na função cobre isso.
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return // Aguarda o resultado em onActivityResult
        }

        // Se o Bluetooth já está ativo, verifica a permissão de localização
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
            return // Aguarda o resultado em onRequestPermissionsResult
        } else {
            // Bluetooth está ATIVO e permissão de Localização CONCEDIDA
            Toast.makeText(this, "Bluetooth ativo e permissão de localização concedida.", Toast.LENGTH_SHORT).show()
            // TODO: Inicie a busca por dispositivos Bluetooth aqui ou outras ações necessárias.
            // Exemplo: this.Bluetooth_Connected = true; (após conectar a um dispositivo)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão de localização concedida.", Toast.LENGTH_SHORT).show()
                // Permissão de localização concedida. BLUETOOTH_CONNECT é assumida pela anotação.
                // Chame starBluetoothSearch() novamente. Ele verificará o estado do BT antes de prosseguir.
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                starBluetoothSearch()
            } else {
                Toast.makeText(this, "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Para iniciar o processo de verificação do Bluetooth, você pode chamar starBluetoothSearch()
        // aqui, mas certifique-se de que a permissão BLUETOOTH_CONNECT (Android 12+) seja tratada.
        // Ex: um botão "Configurar Bluetooth" que chama uma função para pedir BLUETOOTH_CONNECT
        // e então chama starBluetoothSearch().

        binding.button.setOnClickListener { //escuta o botão de conectar
            if (Bluetooth_Connected) { //se estiver conectado
                // Certifique-se de que a Activity ViewScreen exista no seu projeto
                val intent = Intent(this, ViewScreen::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Dispositivo não conectado. Verifique o Bluetooth.", Toast.LENGTH_SHORT).show()
                // Considere iniciar o fluxo de configuração do Bluetooth aqui se desejar,
                // por exemplo, chamando uma função que peça a permissão BLUETOOTH_CONNECT
                // e depois chame starBluetoothSearch().
            }
        }
    }
}
