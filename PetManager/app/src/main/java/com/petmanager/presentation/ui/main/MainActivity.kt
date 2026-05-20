package com.petmanager.presentation.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.navigation.NavigationBarView
import com.petmanager.databinding.ActivityMainBinding
import com.petmanager.presentation.ui.auth.RegionSelectionActivity
import com.petmanager.presentation.ui.auth.SignInActivity
import com.petmanager.presentation.ui.home.HomeFragment
import com.petmanager.presentation.ui.write.WriteFragment
import com.petmanager.presentation.ui.chat.ChatFragment
import com.petmanager.presentation.ui.favorite.FavoriteFragment
import com.petmanager.presentation.ui.myinfo.MyInfoFragment
import com.petmanager.presentation.viewmodel.AuthViewModel
import com.petmanager.R
import com.petmanager.data.remote.api.AuthEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var homeFragment: HomeFragment
    private lateinit var writeFragment: WriteFragment
    private lateinit var chatFragment: ChatFragment
    private lateinit var myInfoFragment: MyInfoFragment
    private lateinit var favoriteFragment: FavoriteFragment

    private lateinit var binding: ActivityMainBinding
    private val authViewModel: AuthViewModel by viewModels()
    private val manager = supportFragmentManager
    private var regionGateConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그인 상태 확인
        authViewModel.checkLoginStatus()
        setupObservers()

        // 세션 만료 이벤트 구독
        observeAuthEvents()

        // 앱 초기 실행 시 실행하는 조건문
        if (savedInstanceState == null) {
            // 처음에 프래그먼트 컨테이너에 들어갈 때는 add를 통해서 컨테이너에 프래그먼트를 추가
            homeFragment = HomeFragment.newInstance()
            manager.beginTransaction().add(R.id.fragment_container, homeFragment).commit()
            binding.bottomNavi.selectedItemId = R.id.bottom_nav_home
        }

        binding.bottomNavi.setOnItemSelectedListener(onBottomNaviItemSelectedListener)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    when (state) {
                        is AuthViewModel.LoginState.NeedRegionSetting -> {
                            if (regionGateConsumed) return@collect
                            regionGateConsumed = true
                            val intent = Intent(this@MainActivity, RegionSelectionActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }
                        is AuthViewModel.LoginState.Error -> {
                            navigateToSignIn()
                        }
                        else -> {
                        }
                    }
                }
            }
        }
    }

    /**
     * 전역 AuthEventBus 에서 세션 만료 이벤트를 구독
     */
    private fun observeAuthEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AuthEventBus.events.collect { event ->
                    when (event) {
                        is AuthEventBus.Event.SessionExpired -> {
                            authViewModel.performLogout()
                            Toast.makeText(
                                this@MainActivity,
                                "세션이 만료되었습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                            navigateToSignIn()
                        }
                    }
                }
            }
        }
    }

    private val onBottomNaviItemSelectedListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
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

    /** 글 등록 성공 등 — 하단 탭을 홈으로 맞추고 홈 화면 표시 */
    fun navigateToHomeTab() {
        if (binding.bottomNavi.selectedItemId != R.id.bottom_nav_home) {
            binding.bottomNavi.selectedItemId = R.id.bottom_nav_home
        } else {
            homeFragment = HomeFragment.newInstance()
            manager.beginTransaction().replace(R.id.fragment_container, homeFragment).commit()
        }
    }

    /**
     * 로그아웃 및 로그인 화면으로 이동
     */
    fun signOutAndStartSignInActivity() {
        lifecycleScope.launch {
            authViewModel.performLogout()
            navigateToSignIn()
        }
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }
}
