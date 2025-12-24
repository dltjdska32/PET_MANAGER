package com.petmanager.presentation.ui.auth

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.ui.auth.SignInActivity
import com.petmanager.domain.model.Info
import com.example.test1.R

class BasicInfoActivity : AppCompatActivity() {

    private lateinit var editTextNickname: EditText
    private lateinit var editTextAge: EditText
    private lateinit var radioGroupGender: RadioGroup
    private lateinit var spinnerLocations: Spinner
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_basic_info)

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        editTextNickname = findViewById(R.id.editTextNickname)
        editTextAge = findViewById(R.id.editTextAge)
        radioGroupGender = findViewById(R.id.radioGroupGender)
        spinnerLocations = findViewById(R.id.spinnerLocations)

        // Spinner 설정
        val locations = arrayOf("서울", "부산", "천안", "인천", "광주")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLocations.adapter = adapter

        if (currentUser == null) {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }

        db.collection("user")
            .whereEqualTo("userID", currentUser?.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val user = document.toObject<Info>()
                    editTextNickname.setText(user?.name.toString())
                    editTextAge.setText(user?.age.toString())
                    if(user?.sex.toString() == "남"){
                        radioGroupGender.check(R.id.radioButtonMale)
                    }else{
                        radioGroupGender.check(R.id.radioButtonFemale)
                    }
                    val locationIndex = locations.indexOf(user?.location)
                    if (locationIndex >= 0) {
                        spinnerLocations.setSelection(locationIndex)
                    }
                    Log.d(TAG, "${document.id} => ${document.data}")
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
            }

        val submit_button = findViewById<Button>(R.id.buttonSubmit)
        submit_button.setOnClickListener {
            submitInfo()
        }
    }

    private fun submitInfo() {
        val nickname = editTextNickname.text.toString()
        val ageStr = editTextAge.text.toString()
        val selectedGenderId = radioGroupGender.checkedRadioButtonId
        val location = spinnerLocations.selectedItem.toString()
        val gender: String
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (nickname.isEmpty() || ageStr.isEmpty() || selectedGenderId == -1 || location.isEmpty()) {
            Toast.makeText(this@BasicInfoActivity, "모든 정보를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        gender = if (selectedGenderId == R.id.radioButtonMale) {
            "남"
        } else {
            "여"
        }

        val age: Int = ageStr.toInt()

        FirebaseApp.initializeApp(this)

        val db = Firebase.firestore



        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result.toString()
                val info = Info(
                    currentUser?.uid,
                    nickname,
                    age,
                    gender,
                    location,
                    token
                )

                db.collection("user")
                    .whereEqualTo("userID", currentUser?.uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        if(documents.isEmpty){
                            db.collection("user")
                                .add(info)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(TAG, "DocumentSnapshot written with ID: ${documentReference.id}")
                                    val intent = Intent(this, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error adding document", e)
                                }
                        }
                        else{
                            for (document in documents) {
                                db.collection("user").document(document.id)
                                    .set(info)
                                val intent = Intent(this, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "Error getting documents: ", exception)
                    }






            } else {
                Log.e(TAG, "Fetching FCM token failed", task.exception)
            }
        }





    }
}

