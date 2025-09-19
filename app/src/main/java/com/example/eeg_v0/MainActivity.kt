package com.example.eeg_v0

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eeg_v0.databinding.ActivityMainBinding
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var Bluetooth_Connected: Boolean = false

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)

    fun starBluetoothSearch() { //inicia a busca por dispositivos bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter : BluetoothAdapter? = bluetoothManager?.adapter

        if (!bluetoothAdapter.isEnabled) { //se não estiver ligado)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(this, 1)//abre a tela de ativação do bluetooth
                return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), BLUETOOTH_PERMISSION_REQUEST_CODE)
            return
        }
        else {
            Toast.makeText(this, "Permissão concedida.", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão concedida.", Toast.LENGTH_SHORT).show()
                starBluetoothSearch()
            }
            else {
                Toast.makeText(this, "Permissão negada.", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) { //cria a tela
        super.onCreate(savedInstanceState) //inicia a tela
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater) //inicia o binding
        setContentView(binding.root) //root é a tela principal, associada ao binding

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets -> //e
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.button.setOnClickListener { //escuta o botão de conectar

            if (Bluetooth_Connected) { //se estiver conectado
                val intent = Intent(this, ViewScreen::class.java) // abre a tela de visualização
                startActivity(intent)
            } else {
                Toast.makeText(this, "Não foi possivel conectar ao dispositivo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
