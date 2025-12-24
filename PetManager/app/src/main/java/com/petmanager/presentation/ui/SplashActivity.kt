package com.petmanager.presentation.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.petmanager.presentation.ui.auth.SignInActivity
import com.petmanager.presentation.ui.auth.BasicInfoActivity
import com.petmanager.presentation.ui.main.MainActivity
import com.example.test1.R


class SplashActivity : AppCompatActivity() {


    private lateinit var auth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()
        val db = Firebase.firestore

        val currentUser = auth.currentUser



        if (currentUser != null) {
            // The user is already signed in, navigate to MainActivity

            db.collection("user")
                .whereEqualTo("userID", currentUser?.uid) //키값넣는거. where절
                .get()
                .addOnSuccessListener { documents ->
                    if(!documents.isEmpty){
                        Handler(Looper.getMainLooper()).postDelayed({

                            // 일정 시간이 지나면 MainActivity로 이동
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            // 이전 키를 눌렀을 때 스플래스 스크린 화면으로 이동을 방지하기 위해
                            // 이동한 다음 사용안함으로 finish 처리
                            finish()

                        }, 1300) // 시간 1.3초 이후 실행
                    } //if문은 임의로 넣은거
                    else{
                        Handler(Looper.getMainLooper()).postDelayed({

                            // 일정 시간이 지나면 MainActivity로 이동
                            val intent = Intent(this, BasicInfoActivity::class.java)
                            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            // 이전 키를 눌렀을 때 스플래스 스크린 화면으로 이동을 방지하기 위해
                            // 이동한 다음 사용안함으로 finish 처리
                            finish()

                        }, 1300) // 시간 1.3초 이후 실행

                    }
                    for (document in documents) {
                        Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                    }

                }
                .addOnFailureListener { exception ->
                    Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                }
        } else {

            Handler(Looper.getMainLooper()).postDelayed({

                // 일정 시간이 지나면 MainActivity로 이동
                val intent = Intent(this, SignInActivity::class.java)
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                // 이전 키를 눌렀을 때 스플래스 스크린 화면으로 이동을 방지하기 위해
                // 이동한 다음 사용안함으로 finish 처리
                finish()

            }, 1300) // 시간 1.3초 이후 실행
        }
    }


}

