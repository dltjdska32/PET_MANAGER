package com.petmanager.presentation.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petmanager.presentation.theme.Dimens
import com.petmanager.presentation.theme.Error
import com.petmanager.presentation.theme.GradientEnd
import com.petmanager.presentation.theme.GradientStart
import com.petmanager.presentation.theme.KakaoText
import com.petmanager.presentation.theme.KakaoYellow
import com.petmanager.presentation.theme.Success
import com.petmanager.presentation.viewmodel.AuthViewModel

private val AuthGradient = Brush.linearGradient(listOf(GradientStart, GradientEnd))

@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(loginState) {
        if (loginState is AuthViewModel.LoginState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AuthGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.SpacingXl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "우쭈쭈",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier
                    .padding(bottom = Dimens.SpacingXxl)
                    .semantics { contentDescription = "우쭈쭈 앱 로고" },
            )

            GlassmorphicCard {
                Text(
                    text = "환영합니다!",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = Dimens.SpacingLg),
                )

                AuthTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "아이디",
                )

                Spacer(modifier = Modifier.height(Dimens.SpacingMd))

                AuthTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "비밀번호",
                    isPassword = true,
                )

                Spacer(modifier = Modifier.height(Dimens.SpacingLg))

                PrimaryActionButton(
                    text = "로그인",
                    onClick = { viewModel.login(username, password) },
                    isLoading = loginState is AuthViewModel.LoginState.Loading,
                )

                Spacer(modifier = Modifier.height(Dimens.SpacingMd))

                PrimaryActionButton(
                    text = "카카오로 시작하기",
                    onClick = { /* ViewModel 카카오 로그인 호출 */ },
                    containerColor = KakaoYellow,
                    contentColor = KakaoText,
                )

                TextButton(
                    onClick = onNavigateToSignUp,
                    modifier = Modifier.height(Dimens.MinTouchTarget),
                ) {
                    Text(
                        text = "처음이신가요? 회원가입",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            AnimatedVisibility(
                visible = loginState is AuthViewModel.LoginState.Error,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                val haptic = LocalHapticFeedback.current
                LaunchedEffect(Unit) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                Text(
                    text = (loginState as? AuthViewModel.LoginState.Error)?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Error,
                    modifier = Modifier.padding(top = Dimens.SpacingMd),
                )
            }
        }
    }
}

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
) {
    var currentStep by remember { mutableStateOf(1) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    val selectedRegions = remember { mutableStateListOf<Long>() }

    val usernameCheckState by viewModel.usernameCheckState.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AuthGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.SpacingXl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "우쭈쭈 가입하기",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.padding(vertical = Dimens.SpacingXxl),
            )

            StepIndicator(
                totalSteps = 3,
                currentStep = currentStep,
                modifier = Modifier.padding(bottom = Dimens.SpacingLg),
            )

            GlassmorphicCard {
                when (currentStep) {
                    1 -> SignUpStepAccount(
                        username = username,
                        onUsernameChange = {
                            username = it
                            viewModel.resetUsernameCheckState()
                        },
                        password = password,
                        onPasswordChange = { password = it },
                        email = email,
                        onEmailChange = { email = it },
                        usernameCheckState = usernameCheckState,
                        onCheckDuplicate = { viewModel.checkUsernameDuplicate(username) },
                        onNext = { currentStep = 2 },
                    )

                    2 -> SignUpStepNickname(
                        nickname = nickname,
                        onNicknameChange = { nickname = it },
                        onNext = { currentStep = 3 },
                    )

                    3 -> SignUpStepRegion(
                        loginState = loginState,
                        onSubmit = {
                            viewModel.signUp(nickname, username, password, email, selectedRegions)
                        },
                    )
                }
            }

            TextButton(
                onClick = { if (currentStep > 1) currentStep-- else onNavigateBack() },
                modifier = Modifier
                    .padding(top = Dimens.SpacingMd)
                    .height(Dimens.MinTouchTarget),
            ) {
                Text(
                    text = if (currentStep > 1) "이전 단계로" else "취소",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

// ─── Sign-Up Step Composables ───

@Composable
private fun SignUpStepAccount(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    usernameCheckState: AuthViewModel.LoginState,
    onCheckDuplicate: () -> Unit,
    onNext: () -> Unit,
) {
    AuthTextField(value = username, onValueChange = onUsernameChange, label = "아이디")

    PrimaryActionButton(
        text = "중복 확인",
        onClick = onCheckDuplicate,
        enabled = username.isNotBlank(),
        containerColor = Color.White.copy(alpha = 0.2f),
        contentColor = Color.White,
        modifier = Modifier.padding(top = Dimens.SpacingSm),
    )

    AnimatedVisibility(visible = usernameCheckState is AuthViewModel.LoginState.Error) {
        Text(
            text = (usernameCheckState as? AuthViewModel.LoginState.Error)?.message.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Error,
            modifier = Modifier.padding(top = Dimens.SpacingXs),
        )
    }
    AnimatedVisibility(visible = usernameCheckState is AuthViewModel.LoginState.Success) {
        Text(
            text = "사용 가능한 아이디입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Success,
            modifier = Modifier.padding(top = Dimens.SpacingXs),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.SpacingMd))
    AuthTextField(value = password, onValueChange = onPasswordChange, label = "비밀번호", isPassword = true)
    Spacer(modifier = Modifier.height(Dimens.SpacingMd))
    AuthTextField(value = email, onValueChange = onEmailChange, label = "이메일")

    Spacer(modifier = Modifier.height(Dimens.SpacingXl))
    PrimaryActionButton(
        text = "다음으로",
        onClick = onNext,
        enabled = usernameCheckState is AuthViewModel.LoginState.Success
                && password.isNotBlank()
                && email.isNotBlank(),
    )
}

@Composable
private fun SignUpStepNickname(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        text = "어떻게 불러드릴까요?",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(bottom = Dimens.SpacingLg),
    )

    AuthTextField(value = nickname, onValueChange = onNicknameChange, label = "닉네임")

    Spacer(modifier = Modifier.height(Dimens.SpacingXl))
    PrimaryActionButton(
        text = "다음으로",
        onClick = onNext,
        enabled = nickname.isNotBlank(),
    )
}

@Composable
private fun SignUpStepRegion(
    loginState: AuthViewModel.LoginState,
    onSubmit: () -> Unit,
) {
    Text(
        text = "활동 지역을 선택해주세요 (최대 3개)",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        modifier = Modifier.padding(bottom = Dimens.SpacingLg),
    )

    Text(
        text = "지역 선택 UI (구현 예정)",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(0.6f),
    )

    Spacer(modifier = Modifier.height(Dimens.SpacingXl))
    PrimaryActionButton(
        text = "우쭈쭈 시작하기",
        onClick = onSubmit,
        isLoading = loginState is AuthViewModel.LoginState.Loading,
    )
}

// ─── Shared UI Components ───

@Composable
private fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        repeat(totalSteps) { index ->
            val isActive = currentStep == index + 1
            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else Dimens.SpacingSm)
                    .clip(CircleShape)
                    .background(if (isActive) Color.White else Color.White.copy(alpha = 0.3f))
                    .semantics {
                        contentDescription = "단계 ${index + 1}${if (isActive) " (현재)" else ""}"
                    },
            )
        }
    }
}
