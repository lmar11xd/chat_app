package com.lmar.chatapp.chat

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.lmar.chatapp.Constantes
import com.lmar.chatapp.R
import com.lmar.chatapp.adaptador.AdaptadorChat
import com.lmar.chatapp.databinding.ActivityChatBinding
import com.lmar.chatapp.entidad.Chat

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    private var uid = ""
    private var miUid = ""

    private var chatRuta = ""
    private var imagenUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        progressDialog =ProgressDialog(this)
        progressDialog.setTitle("Espere por favor")
        progressDialog.setCanceledOnTouchOutside(false)

        uid = intent.getStringExtra("uid").toString()
        miUid = firebaseAuth.uid!!

        chatRuta = Constantes.rutaChat(uid, miUid)

        binding.fabAdjuntar.setOnClickListener {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                imagenGaleria()
            } else {
                solicitarPermisoAlmacenamiento.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        binding.fabEnviar.setOnClickListener {
            validarMensaje()
        }

        binding.ibRegresar.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        cargarInfo()
        cargarMensajes()
    }

    private fun cargarMensajes() {
        val mensajes = ArrayList<Chat>()
        val ref = FirebaseDatabase.getInstance().getReference("Chats")
        ref.child(chatRuta)
            .addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    mensajes.clear()
                    for (ds: DataSnapshot in snapshot.children) {
                        try {
                            val chat = ds.getValue(Chat::class.java)
                            mensajes.add(chat!!)
                        } catch (e: Exception) {

                        }
                    }

                    val adaptadorChat = AdaptadorChat(this@ChatActivity, mensajes)
                    binding.rvChats.adapter = adaptadorChat

                    binding.rvChats.setHasFixedSize(true)
                    var linearLayoutManager = LinearLayoutManager(this@ChatActivity)
                    linearLayoutManager.stackFromEnd = true
                    binding.rvChats.layoutManager = linearLayoutManager
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })
    }

    private fun validarMensaje() {
        val mensaje = binding.etMensajeChat.text.toString().trim()
        val tiempo = Constantes.obtenerTiempo()

        if(mensaje.isEmpty()) {
            Toast.makeText(this, "Ingrese un mensaje", Toast.LENGTH_SHORT).show()
        } else {
            enviarMensaje(Constantes.MENSAJE_TIPO_TEXTO, mensaje, tiempo)
        }
    }

    private fun cargarInfo() {
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios")
        ref.child(uid)
            .addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nombres = "${snapshot.child("nombres").value}"
                    val imagen = "${snapshot.child("imagen").value}"

                    binding.tvNombreUsuario.text = nombres
                    try {
                        Glide.with(this@ChatActivity)
                            .load(imagen)
                            .placeholder(R.drawable.perfil_usuario)
                            .into(binding.ivToolbar)
                    } catch (e: Exception) {

                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })
    }

    private fun imagenGaleria() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        resultadoGaleriaARL.launch(intent)
    }

    private val resultadoGaleriaARL =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado->
            if(resultado.resultCode == Activity.RESULT_OK) {
                val data = resultado.data
                imagenUri = data!!.data
                subirImagenStorage()
            } else {
                Toast.makeText(this, "Cancelado", Toast.LENGTH_SHORT).show()
            }
        }

    private val solicitarPermisoAlmacenamiento =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { esConcedido->
            if(esConcedido) {
                imagenGaleria()
            } else {
                Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
        }

    private fun subirImagenStorage() {
        progressDialog.setMessage("Subiendo imagen")
        progressDialog.show()

        val tiempo = Constantes.obtenerTiempo()
        val nombresRutaImg = "imagenes/chat/$tiempo"
        val storageRef = FirebaseStorage.getInstance().getReference(nombresRutaImg)
        storageRef.putFile(imagenUri!!)
            .addOnSuccessListener { taskSnapshot ->
                val uriTask = taskSnapshot.storage.downloadUrl
                while (!uriTask.isSuccessful);
                val urlImagen = uriTask.result.toString()
                if(uriTask.isSuccessful) {
                    enviarMensaje(Constantes.MENSAJE_TIPO_IMAGEN, urlImagen, tiempo)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "No se pudo enviar la imagen debido a ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enviarMensaje(tipoMensaje: String, mensaje: String, tiempo: Long) {
        progressDialog.setMessage("Enviando mensaje")
        progressDialog.show()
        val refChat = FirebaseDatabase.getInstance().getReference("Chats")
        val keyId = "${refChat.push().key}"
        val hashMap = HashMap<String, Any>()

        hashMap["idMensaje"] = "${keyId}"
        hashMap["tipoMensaje"] = "${tipoMensaje}"
        hashMap["mensaje"] = "${mensaje}"
        hashMap["emisorUid"] = "${miUid}"
        hashMap["receptorUid"] = "${uid}"
        hashMap["tiempo"] = tiempo

        refChat.child(chatRuta)
            .child(keyId)
            .setValue(hashMap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                binding.etMensajeChat.setText("")
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "No se pudo enviar el mensaje debido a ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}