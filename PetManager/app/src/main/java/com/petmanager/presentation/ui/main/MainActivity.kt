package com.petmanager.presentation.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarView
import com.example.test1.databinding.ActivityMainBinding
import com.example.test1.databinding.HomeFragBinding
import com.example.test1.databinding.MyInfoFragBinding
import com.example.test1.databinding.WriteFragBinding

import android.widget.Button
import android.widget.TextView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.petmanager.presentation.ui.auth.SignInActivity
import com.petmanager.presentation.ui.auth.BasicInfoActivity
import com.petmanager.presentation.ui.home.HomeFragment
import com.petmanager.presentation.ui.write.WriteFragment
import com.petmanager.presentation.ui.chat.ChatFragment
import com.petmanager.presentation.ui.favorite.FavoriteFragment
import com.petmanager.presentation.ui.myinfo.MyInfoFragment
import com.example.test1.R



class MainActivity : AppCompatActivity() {

    private lateinit var homeFragment: HomeFragment
    private lateinit var writeFragment: WriteFragment
    private lateinit var chatFragment: ChatFragment
    private lateinit var myInfoFragment: MyInfoFragment
    private lateinit var favoriteFragment: FavoriteFragment


    private lateinit var binding : ActivityMainBinding

    val manager = supportFragmentManager

    private lateinit var auth: FirebaseAuth
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 앱초기 실행시 실행하는 조건문
        if(savedInstanceState == null) {
            // 처음에 프래그먼트 콘테이너에 들어갈때는 add를 통해서 컨테이너에 프래그먼트를 추가해준다
            // 따라서 add함수를 통해서 처음 프래그먼트를 결정
            homeFragment = HomeFragment.newInstance()
            manager.beginTransaction().add(R.id.fragment_container, homeFragment).commit()
            binding.bottomNavi.selectedItemId = R.id.bottom_nav_home
        }

        binding.bottomNavi.setOnItemSelectedListener(onBottomNaviItemSelectedListener)


        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser




        if (currentUser == null) {
            // The user is already signed in, navigate to MainActivity
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish() // finish the current activity to prevent the user from coming back to the SignInActivity using the back button
        }

        mAuth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)


    }


    private val onBottomNaviItemSelectedListener = NavigationBarView.OnItemSelectedListener{

        when(it.itemId) {
            R.id.bottom_nav_home -> {
                homeFragment = HomeFragment.newInstance()
                manager.beginTransaction().replace(R.id.fragment_container, homeFragment).commit()
            }

            R.id.bottom_nav_write -> {
                writeFragment = WriteFragment.newInstance()
                manager.beginTransaction().replace(R.id.fragment_container, writeFragment).commit()
            }

            R.id.bottom_nav_chat -> {
                chatFragment = ChatFragment.newInstance()
                manager.beginTransaction().replace(R.id.fragment_container, chatFragment).commit()
            }

            R.id.bottom_nav_favorite -> {
                favoriteFragment = FavoriteFragment.newInstance()
                manager.beginTransaction().replace(R.id.fragment_container, favoriteFragment).commit()
            }

            R.id.bottom_nav_my_info -> {
                myInfoFragment = MyInfoFragment.newInstance()
                manager.beginTransaction().replace(R.id.fragment_container, myInfoFragment).commit()
            }



        }
        true
    }



    private fun showInit() {
        val transaction = manager.beginTransaction().add(R.id.fragment_container, homeFragment)
        transaction.commit()
    }

     fun signOutAndStartSignInActivity() {
        mAuth.signOut()

        mGoogleSignInClient.signOut().addOnCompleteListener(this) {
            // Optional: Update UI or show a message to the user
            val intent = Intent(this@MainActivity, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    fun editBasicInfo() {
        val intent = Intent(this@MainActivity, BasicInfoActivity::class.java)
        startActivity(intent)
    }


}

