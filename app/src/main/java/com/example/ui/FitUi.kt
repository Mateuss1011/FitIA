package com.example.ui

import com.example.data.db.UserProfile
import com.example.data.repository.getSpecificExerciseInstructions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.util.Log
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.animation.core.*
import com.example.data.db.Meal
import com.example.data.db.WorkoutExercise
import com.example.data.db.HistoryEntry
import com.example.data.db.ConsumoDiario
import com.example.data.db.RegistroAgua
import com.example.data.db.RegistroDiario
import com.example.data.db.PesoHistoryEntry
import com.example.data.db.Suplemento
import com.example.data.repository.AppThemeMode
import androidx.compose.foundation.lazy.items
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitApp(viewModel: FitViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val showOnboarding = viewModel.showOnboarding

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    if (viewModel.isCheckingProfile) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                if (viewModel.profileLoadError != null) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = viewModel.profileLoadError ?: "Falha ao carregar perfil.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.retryLoadProfileOrReturnToAuth()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retornar para o Início / Cadastro")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "FitAI Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Carregando perfil...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            }
        }
    } else if (currentUser == null) {
        LoginRegisterScreen(viewModel = viewModel)
    } else if ((!viewModel.hasCompletedOnboarding || showOnboarding) && viewModel.generationState !is GenerationUiState.Generating) {
        OnboardingScreen(viewModel = viewModel)
    } else {
        // Request notification permission once user reaches Dashboard
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (!hasPermission) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "FitAI Bolt Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "FitAI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = {
                                viewModel.openDailyMessageModal()
                            },
                            modifier = Modifier.testTag("notification_bell_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notificações",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                        if (viewModel.hasUnreadNotification && userProfile != null && userProfile?.notificationMessage?.isNotEmpty() == true) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!showOnboarding) {
                FitBottomBar(
                    currentTab = viewModel.currentTab,
                    onTabSelected = { viewModel.currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.currentTab) {
                FitTab.TRAINING -> TrainingTabScreen(viewModel = viewModel)
                FitTab.NUTRITION -> NutritionTabScreen(viewModel = viewModel)
                FitTab.EVOLUTION -> EvolutionTabScreen(viewModel = viewModel)
                FitTab.PROFILE -> ProfileTabScreen(viewModel = viewModel)
            }

            // Global active countdown timer card overlay (Rule 3)
            if (viewModel.isTimerActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    RestTimerCard(
                        secondsRemaining = viewModel.timerSecondsRemaining,
                        message = viewModel.timerMessage,
                        isSoundEnabled = viewModel.isTimerSoundEnabled,
                        onToggleSound = { viewModel.toggleTimerSound() },
                        onCancel = { viewModel.cancelTimer() }
                    )
                }
            }

            // Resting Timer finish visual alert popup (Rule 3)
            if (viewModel.showTimerAlert) {
                val exercises by viewModel.allExercises.collectAsStateWithLifecycle()
                val selectedDay = viewModel.selectedWorkoutDay
                val filtered = remember(exercises, selectedDay) {
                    exercises.filter { it.workoutDay == selectedDay }.sortedBy { it.orderIndex }
                }

                val timerExercise = viewModel.timerExercise
                val completedSeries = viewModel.timerCompletedSeries
                val totalSeries = timerExercise?.series ?: 1

                val isLastSetOfExercise = completedSeries >= totalSeries

                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val currentIndex = if (timerExercise != null) filtered.indexOfFirst { it.id == timerExercise.id } else -1
                val nextExerciseIndex = if (currentIndex != -1 && currentIndex + 1 < filtered.size) {
                    currentIndex + 1
                } else {
                    filtered.indexOfFirst { !it.isCompletedOn(todayStr) && (timerExercise == null || it.id != timerExercise.id) }
                }

                val hasRemainingExercises = nextExerciseIndex != -1 && !filtered.all { it.isCompletedOn(todayStr) }

                val (btnText, primaryAction) = when {
                    !isLastSetOfExercise -> {
                        "Próxima Série" to {
                            viewModel.dismissTimerAlert()
                        }
                    }
                    hasRemainingExercises -> {
                        "Próximo Exercício" to {
                            viewModel.dismissTimerAlert()
                            viewModel.currentTab = FitTab.TRAINING
                            viewModel.scrollToExerciseIndexEvent = nextExerciseIndex
                        }
                    }
                    else -> {
                        "Finalizar Treino" to {
                            viewModel.dismissTimerAlert()
                            viewModel.currentTab = FitTab.TRAINING
                            viewModel.triggerShowWorkoutCompletedEvent = true
                        }
                    }
                }

                TimerAlertPopup(
                    primaryButtonText = btnText,
                    onPrimaryAction = primaryAction,
                    onDismiss = { viewModel.dismissTimerAlert() }
                )
            }

            // Daily Motivational Message Dialog (Bell Icon)
            if (viewModel.showDailyMessageModal) {
                DailyMessageDialog(
                    userProfile = userProfile,
                    onDismiss = { viewModel.dismissDailyMessageModal() }
                )
            }

            // Interactive Guided App Tour (First Visit / Onboarding Completion)
            if (viewModel.showAppTour) {
                AppTourDialog(
                    onDismiss = { viewModel.skipTour() },
                    onFinish = {
                        viewModel.completeTour()
                        viewModel.currentTab = FitTab.TRAINING
                    }
                )
            }
        }
    }
}
}

// --- Bottom Navigation Bar ---

@Composable
fun FitBottomBar(
    currentTab: FitTab,
    onTabSelected: (FitTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NavigationBarItem(
            selected = currentTab == FitTab.TRAINING,
            onClick = { onTabSelected(FitTab.TRAINING) },
            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Treino") },
            label = { Text("Treino", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = navColors,
            modifier = Modifier.testTag("nav_tab_training")
        )
        NavigationBarItem(
            selected = currentTab == FitTab.NUTRITION,
            onClick = { onTabSelected(FitTab.NUTRITION) },
            icon = { Icon(Icons.Default.Restaurant, contentDescription = "Nutrição") },
            label = { Text("Nutrição", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = navColors,
            modifier = Modifier.testTag("nav_tab_nutrition")
        )
        NavigationBarItem(
            selected = currentTab == FitTab.EVOLUTION,
            onClick = { onTabSelected(FitTab.EVOLUTION) },
            icon = { Icon(Icons.Default.Insights, contentDescription = "Evolução") },
            label = { Text("Evolução", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = navColors,
            modifier = Modifier.testTag("nav_tab_evolution")
        )
        NavigationBarItem(
            selected = currentTab == FitTab.PROFILE,
            onClick = { onTabSelected(FitTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
            label = { Text("Perfil", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = navColors,
            modifier = Modifier.testTag("nav_tab_profile")
        )
    }
}

// --- Welcome Screen ---

@Composable
fun WelcomeScreen(viewModel: FitViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .align(Alignment.Center)
        ) {
            // Header Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "FitAI Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title "FitAI 2026"
            Text(
                text = "FitAI 2026",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle/Message: "Bem-vindo ao FitAI. Como deseja entrar hoje?"
            Text(
                text = "Bem-vindo ao FitAI. Como deseja entrar hoje?",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Buttons container
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botão 1: Meu Primeiro Acesso
                Button(
                    onClick = { /* Deprecated in favor of Firebase Auth */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("welcome_first_access_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Text(
                            text = "Meu Primeiro Acesso",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Botão 2: Já Sou Cadastrado
                OutlinedButton(
                    onClick = { /* Deprecated in favor of Firebase Auth */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("welcome_already_registered_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                        Text(
                            text = "Já Sou Cadastrado",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Footer disclaimer warning
        Text(
            text = "Aviso: O FitAI é uma ferramenta tecnológica de auxílio e incentivo físico. Não substitui, em hipótese alguma, o diagnóstico, avaliação ou acompanhamento de profissionais da saúde, médicos ou nutricionistas.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

// --- Login / Registration Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRegisterScreen(viewModel: FitViewModel) {
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showEmailAlreadyExistsModal by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var forgotPasswordError by remember { mutableStateOf<String?>(null) }
    var showPasswordResetSuccessAlert by remember { mutableStateOf(false) }
    var isSendingResetEmail by remember { mutableStateOf(false) }
    var forgotPasswordMode by remember { mutableStateOf(0) } // 0 = Link por e-mail, 1 = Redefinir nova senha
    var newResetPassword by remember { mutableStateOf("") }
    var confirmResetPassword by remember { mutableStateOf("") }
    var newResetPasswordVisible by remember { mutableStateOf(false) }
    var confirmResetPasswordVisible by remember { mutableStateOf(false) }

    // Real-time password validation rules for registration
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasNumber = password.any { it.isDigit() }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val isPasswordValid = hasMinLength && hasUppercase && hasNumber && passwordsMatch

    // Real-time password validation rules for password redefinition
    val hasResetMinLength = newResetPassword.length >= 8
    val hasResetUppercase = newResetPassword.any { it.isUpperCase() }
    val hasResetNumber = newResetPassword.any { it.isDigit() }
    val resetPasswordsMatch = newResetPassword.isNotEmpty() && newResetPassword == confirmResetPassword
    val isResetPasswordValid = hasResetMinLength && hasResetUppercase && hasResetNumber && resetPasswordsMatch

    LaunchedEffect(isLoginMode) {
        if (!isLoginMode) {
            viewModel.resetForm()
            viewModel.clearTemporaryCaches()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "FitAI Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = if (isLoginMode) "Acesse sua Conta" else "Crie sua Conta FitAI",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isLoginMode) "Monitore seus treinos e nutrição com IA de ponta." else "Comece hoje a sua transformação com o assistente de performance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error Card
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Erro",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Input Fields
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isLoginMode) {
                        // Name Input
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome Completo") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("name_input")
                        )
                    }

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Senha") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("password_toggle_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    if (!isLoginMode) {
                        // Confirm Password Input
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                errorMessage = null
                            },
                            label = { Text("Confirmar Senha") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                    modifier = Modifier.testTag("confirm_password_toggle_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (confirmPasswordVisible) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_password_input")
                        )

                        // Real-Time Password Validation Indicators
                        PasswordValidationRulesList(
                            hasMinLength = hasMinLength,
                            hasUppercase = hasUppercase,
                            hasNumber = hasNumber,
                            passwordsMatch = passwordsMatch
                        )
                    }

                    if (isLoginMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(
                                onClick = {
                                    forgotPasswordEmail = email.trim()
                                    forgotPasswordError = null
                                    showForgotPasswordDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Text(
                                    text = "Esqueceu a senha?",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isActionEnabled = if (isLoginMode) {
                email.isNotBlank() && password.isNotBlank()
            } else {
                email.isNotBlank() && name.isNotBlank() && isPasswordValid
            }

            // Action Button
            Button(
                onClick = {
                    try {
                        errorMessage = null
                        if (email.isBlank() || password.isBlank()) {
                            val msg = "Por favor, preencha todos os campos."
                            errorMessage = msg
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!isLoginMode && !isPasswordValid) {
                            val msg = "A senha deve atender a todos os requisitos de segurança."
                            errorMessage = msg
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isLoginMode) {
                            viewModel.loginUser(
                                email = email.trim(),
                                password = password,
                                onSuccess = { /* Handle SUCCESS safely, handled reactively */ },
                                onError = { err ->
                                    try {
                                        errorMessage = err
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                    } catch (t: Throwable) {
                                        android.util.Log.e("FitUi", "Error in login onError callback", t)
                                    }
                                }
                            )
                        } else {
                            if (name.isBlank()) {
                                val msg = "Por favor, informe seu nome."
                                errorMessage = msg
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.resetForm()
                            viewModel.clearTemporaryCaches()
                            viewModel.registerUser(
                                email = email.trim(),
                                password = password,
                                name = name.trim(),
                                onSuccess = { /* Handle SUCCESS safely, handled reactively */ },
                                onError = { err ->
                                    try {
                                        val isCollision = err.contains("email-already-in-use", ignoreCase = true) ||
                                                err.contains("already in use", ignoreCase = true) ||
                                                err.contains("já possui um cadastro", ignoreCase = true) ||
                                                err.contains("conta já existe", ignoreCase = true) ||
                                                err.contains("EMAIL_EXISTS", ignoreCase = true) ||
                                                err.contains("409", ignoreCase = true)
                                        if (isCollision) {
                                            showEmailAlreadyExistsModal = true
                                        } else {
                                            errorMessage = err
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (t: Throwable) {
                                        android.util.Log.e("FitUi", "Error in register onError callback", t)
                                    }
                                },
                                onEmailAlreadyExists = {
                                    showEmailAlreadyExistsModal = true
                                }
                            )
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("FitUi", "Error executing auth button onClick", t)
                    }
                },
                enabled = isActionEnabled && !viewModel.isAuthLoading && viewModel.authUiState !is FitViewModel.AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(if (isLoginMode) "login_button" else "register_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (viewModel.isAuthLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isLoginMode) "Entrar" else "Criar Conta",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Mode Button
            TextButton(
                onClick = {
                    isLoginMode = !isLoginMode
                    errorMessage = null
                    name = ""
                    email = ""
                    password = ""
                    confirmPassword = ""
                    viewModel.resetForm()
                    viewModel.clearTemporaryCaches()
                },
                modifier = Modifier.testTag("toggle_auth_mode_button")
            ) {
                Text(
                    text = if (isLoginMode) "Não tem uma conta? Cadastre-se" else "Já possui uma conta? Entre aqui",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showEmailAlreadyExistsModal) {
        AlertDialog(
            onDismissRequest = { showEmailAlreadyExistsModal = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Conta Existente",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Essa conta já existe! Você já possui um cadastro com este e-mail.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmailAlreadyExistsModal = false
                        isLoginMode = true
                        errorMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("go_to_login_button")
                ) {
                    Text("Ir para o Login")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showEmailAlreadyExistsModal = false
                    },
                    modifier = Modifier.testTag("try_another_email_button")
                ) {
                    Text("Tentar outro e-mail")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("email_already_exists_dialog")
        )
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSendingResetEmail) {
                    showForgotPasswordDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Recuperar Senha",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabRow(
                        selectedTabIndex = forgotPasswordMode,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = forgotPasswordMode == 0,
                            onClick = { forgotPasswordMode = 0 },
                            text = { Text("Link por e-mail", style = MaterialTheme.typography.bodySmall) }
                        )
                        Tab(
                            selected = forgotPasswordMode == 1,
                            onClick = { forgotPasswordMode = 1 },
                            text = { Text("Redefinir Senha", style = MaterialTheme.typography.bodySmall) }
                        )
                    }

                    if (forgotPasswordMode == 0) {
                        Text(
                            text = "Informe seu e-mail para receber o link de redefinição de senha:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = {
                                forgotPasswordEmail = it
                                forgotPasswordError = null
                            },
                            label = { Text("E-mail") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_email_input")
                        )
                    } else {
                        Text(
                            text = "Defina sua nova senha de acesso:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = {
                                forgotPasswordEmail = it
                                forgotPasswordError = null
                            },
                            label = { Text("E-mail") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_password_email_input")
                        )

                        OutlinedTextField(
                            value = newResetPassword,
                            onValueChange = {
                                newResetPassword = it
                                forgotPasswordError = null
                            },
                            label = { Text("Nova Senha") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { newResetPasswordVisible = !newResetPasswordVisible },
                                    modifier = Modifier.testTag("new_password_toggle_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (newResetPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (newResetPasswordVisible) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            visualTransformation = if (newResetPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_password_input")
                        )

                        OutlinedTextField(
                            value = confirmResetPassword,
                            onValueChange = {
                                confirmResetPassword = it
                                forgotPasswordError = null
                            },
                            label = { Text("Confirmar Senha") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmResetPasswordVisible = !confirmResetPasswordVisible },
                                    modifier = Modifier.testTag("confirm_new_password_toggle_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (confirmResetPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (confirmResetPasswordVisible) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            visualTransformation = if (confirmResetPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_new_password_input")
                        )

                        PasswordValidationRulesList(
                            hasMinLength = hasResetMinLength,
                            hasUppercase = hasResetUppercase,
                            hasNumber = hasResetNumber,
                            passwordsMatch = resetPasswordsMatch
                        )
                    }

                    if (forgotPasswordError != null) {
                        Text(
                            text = forgotPasswordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                val isConfirmEnabled = if (forgotPasswordMode == 0) {
                    forgotPasswordEmail.isNotBlank() && !isSendingResetEmail
                } else {
                    forgotPasswordEmail.isNotBlank() && isResetPasswordValid && !isSendingResetEmail
                }

                Button(
                    onClick = {
                        if (forgotPasswordEmail.isBlank()) {
                            forgotPasswordError = "Por favor, informe seu e-mail."
                            return@Button
                        }
                        if (forgotPasswordMode == 1 && !isResetPasswordValid) {
                            forgotPasswordError = "A senha deve cumprir todos os requisitos."
                            return@Button
                        }
                        isSendingResetEmail = true
                        forgotPasswordError = null
                        viewModel.sendPasswordResetEmail(
                            email = forgotPasswordEmail.trim(),
                            onSuccess = {
                                isSendingResetEmail = false
                                showForgotPasswordDialog = false
                                showPasswordResetSuccessAlert = true
                            },
                            onError = { err ->
                                isSendingResetEmail = false
                                forgotPasswordError = err
                            }
                        )
                    },
                    enabled = isConfirmEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag(if (forgotPasswordMode == 0) "send_reset_link_button" else "save_new_password_button")
                ) {
                    if (isSendingResetEmail) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (forgotPasswordMode == 0) "Enviar link de redefinição" else "Salvar Nova Senha")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showForgotPasswordDialog = false
                    },
                    enabled = !isSendingResetEmail,
                    modifier = Modifier.testTag("cancel_forgot_password_button")
                ) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("forgot_password_dialog")
        )
    }

    if (showPasswordResetSuccessAlert) {
        AlertDialog(
            onDismissRequest = {
                showPasswordResetSuccessAlert = false
                isLoginMode = true
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Operação Concluída!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (forgotPasswordMode == 0) "Instruções enviadas! Verifique sua caixa de entrada e spam" else "Sua nova senha foi configurada com sucesso. Faça login para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasswordResetSuccessAlert = false
                        isLoginMode = true
                        if (forgotPasswordEmail.isNotBlank()) {
                            email = forgotPasswordEmail.trim()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("ok_password_reset_success_button")
                ) {
                    Text("OK, Voltar ao Login")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("password_reset_success_dialog")
        )
    }
}

@Composable
fun PasswordValidationRulesList(
    hasMinLength: Boolean,
    hasUppercase: Boolean,
    hasNumber: Boolean,
    passwordsMatch: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Requisitos de segurança da senha:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PasswordRuleItem(
            label = "Mínimo de 8 caracteres",
            isFulfilled = hasMinLength,
            testTag = "rule_min_length"
        )
        PasswordRuleItem(
            label = "Pelo menos 1 letra maiúscula",
            isFulfilled = hasUppercase,
            testTag = "rule_uppercase"
        )
        PasswordRuleItem(
            label = "Pelo menos 1 número",
            isFulfilled = hasNumber,
            testTag = "rule_number"
        )
        PasswordRuleItem(
            label = "As senhas dos campos 'Senha' e 'Confirmar Senha' devem ser idênticas",
            isFulfilled = passwordsMatch,
            testTag = "rule_match"
        )
    }
}

@Composable
fun PasswordRuleItem(
    label: String,
    isFulfilled: Boolean,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(testTag)
    ) {
        Icon(
            imageVector = if (isFulfilled) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = if (isFulfilled) "Cumprido" else "Pendente",
            tint = if (isFulfilled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFulfilled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = if (isFulfilled) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// --- Onboarding / Edit Profile Screen ---

@Composable
fun OnboardingScreen(viewModel: FitViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val generationState = viewModel.generationState
    val step = viewModel.onboardingStep

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (generationState is GenerationUiState.Generating) {
            // High Tech Performance Loader
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(100.dp)
                        .animateContentSize()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "FitAI PRO 2026",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "A IA está processando sua biometria...\nSincronizando treinos ABC de alta performance e plano nutricional inteligente.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header row with title & optional Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (userProfile == null) "Bem-vindo ao FitAI 2026" else "Editar Perfil FitAI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    
                    if (userProfile != null) {
                        IconButton(
                            onClick = { viewModel.closeOnboarding() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Step Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isCompletedOrCurrent = i <= step
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isCompletedOrCurrent) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error banner if any
                if (generationState is GenerationUiState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Ocorreu um erro ao gerar o plano:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = generationState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Active step content
                Box(modifier = Modifier.weight(1f)) {
                    when (step) {
                        1 -> OnboardingStep1(viewModel, onNext = { viewModel.onboardingStep = 2 })
                        2 -> OnboardingStep2(viewModel, onPrev = { viewModel.onboardingStep = 1 }, onNext = { viewModel.onboardingStep = 3 })
                        3 -> OnboardingManualStep(viewModel, onPrev = { viewModel.onboardingStep = 2 }, onNext = { viewModel.onboardingStep = 4 })
                        4 -> OnboardingStep3(viewModel, onPrev = { viewModel.onboardingStep = 3 }, onNext = { viewModel.onboardingStep = 5 })
                        5 -> OnboardingStep4(viewModel, onPrev = { viewModel.onboardingStep = 4 })
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStep1(viewModel: FitViewModel, onNext: () -> Unit) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Vamos começar.",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Insira seus dados biométricos iniciais para personalizarmos seu programa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitInput(
                    label = "NOME COMPLETO",
                    value = viewModel.formName ?: "",
                    onValueChange = { 
                        viewModel.formName = it 
                        errorMessage = null
                    },
                    placeholder = "Ex: Alex Silva",
                    testTag = "input_name"
                )
                
                FitInput(
                    label = "IDADE",
                    value = viewModel.formAge ?: "",
                    onValueChange = { 
                        viewModel.formAge = it 
                        errorMessage = null
                    },
                    placeholder = "Ex: 28",
                    keyboardType = KeyboardType.Number,
                    testTag = "input_age"
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FitInput(
                            label = "PESO (KG)",
                            value = viewModel.formWeight ?: "",
                            onValueChange = { 
                                viewModel.formWeight = it 
                                errorMessage = null
                            },
                            placeholder = "Ex: 82.0",
                            keyboardType = KeyboardType.Decimal,
                            testTag = "input_weight"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FitInput(
                            label = "ALTURA (CM)",
                            value = viewModel.formHeight ?: "",
                            onValueChange = { 
                                viewModel.formHeight = it 
                                errorMessage = null
                            },
                            placeholder = "Ex: 180",
                            keyboardType = KeyboardType.Number,
                            testTag = "input_height"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seletor obrigatório de sexo: "Masculino" ou "Feminino" (Requirement 1)
                Text(
                    text = "GÊNERO (OBRIGATÓRIO)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Masculino", "Feminino").forEach { gender ->
                        val selected = viewModel.formGender == gender
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.formGender = gender }
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = gender,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val name = (viewModel.formName ?: "").trim()
                    val idadeInt = (viewModel.formAge ?: "").toIntOrNull() ?: 0
                    val pesoDouble = (viewModel.formWeight ?: "").toDoubleOrNull() ?: 0.0
                    val alturaDouble = (viewModel.formHeight ?: "").toDoubleOrNull() ?: 0.0

                    if (name.isEmpty()) {
                        errorMessage = "Por favor, preencha o seu nome."
                    } else if (idadeInt <= 0 || idadeInt > 120) {
                        errorMessage = "Por favor, insira uma idade válida (ex: 25)."
                    } else if (pesoDouble <= 20.0 || pesoDouble > 400.0) {
                        errorMessage = "Por favor, insira um peso válido em kg (ex: 75.5)."
                    } else if (alturaDouble <= 50.0 || alturaDouble > 260.0) {
                        errorMessage = "Por favor, insira uma altura válida em cm (ex: 175)."
                    } else {
                        errorMessage = null
                        onNext()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_next_step1"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Próximo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun OnboardingStep2(viewModel: FitViewModel, onPrev: () -> Unit, onNext: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Sua experiência.",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Fale sobre sua vivência na academia e informe se possui alguma restrição física.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "TEMPO DE ACADEMIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                expanded = !expanded
                            }
                            .padding(horizontal = 12.dp)
                            .testTag("onboarding_fitness_dropdown"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.formFitnessLevel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Selecionar nível",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        listOf("Iniciante", "Intermediário", "Avançado").forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = level,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (viewModel.formFitnessLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.formFitnessLevel = level
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                FitInput(
                    label = "RESTRIÇÕES DE SAÚDE OU LESÕES",
                    value = viewModel.formRestrictions,
                    onValueChange = { viewModel.formRestrictions = it },
                    placeholder = "Ex: Nenhuma ou dor no joelho",
                    testTag = "input_restrictions"
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_prev_step2"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Anterior", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next_step2"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Próximo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OnboardingManualStep(viewModel: FitViewModel, onPrev: () -> Unit, onNext: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Manual do FitAI",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Dicas científicas fundamentais para maximizar os seus resultados e treinar de forma eficiente e segura.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tempo de Descanso Ideal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Exercícios Multiarticulares (Agachamento, supino, leg press):\n  Exigem de 1:30 a 3 minutos para a recuperação completa do sistema nervoso e do ATP.\n• Exercícios Isolados (Bíceps, tríceps, elevação lateral):\n  Exigem de 45s a 1:30min para otimizar o estresse metabólico muscular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Quando Progredir a Carga",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gatilhos práticos para aumentar o peso com segurança:\n1. Cumprir com perfeição a meta de repetições estipulada em todas as séries.\n2. Manter uma excelente execução técnica e cadência de movimento.\n3. Sentir que ao terminar a série ainda sobrariam de 2 a 4 repetições de reserva.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Importância do Alongamento",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Alongar os músculos-alvo é crucial para ganhar amplitude de movimento, melhorar a ativação e prevenir lesões. Isso se torna especialmente necessário nos dias de treino de perna, onde a amplitude de movimento correta do agachamento e leg press previne lesões graves e melhora a hipertrofia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_prev_manual"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Anterior", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next_manual"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Entendi", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OnboardingStep3(viewModel: FitViewModel, onPrev: () -> Unit, onNext: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Disponibilidade.",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Selecione quais dias da semana você tem disponíveis para treinar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "DIAS DE TREINO DISPONÍVEIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TrainingDaysSelector(
                    selectedDaysString = viewModel.formTrainingDays,
                    onSelectionChanged = { viewModel.formTrainingDays = it }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_prev_step3"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Anterior", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next_step3"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Próximo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class ObjectiveOption(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun OnboardingStep4(viewModel: FitViewModel, onPrev: () -> Unit) {
    var isDisclaimerAccepted by remember { mutableStateOf(false) }
    val objectivesList = listOf(
        ObjectiveOption("Hipertrofia", "Ganho de massa muscular e força", Icons.Default.FitnessCenter),
        ObjectiveOption("Emagrecimento", "Queima de gordura e perda de peso", Icons.AutoMirrored.Filled.TrendingDown),
        ObjectiveOption("Definição", "Definição muscular e queima calórica", Icons.Default.Bolt),
        ObjectiveOption("Saúde", "Qualidade de vida, saúde e bem-estar", Icons.Default.Favorite)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "O Objetivo FINAL.",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Selecione o seu objetivo final para gerarmos sua rotina personalizada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                objectivesList.forEach { option ->
                    val isSelected = viewModel.formObjective == option.title
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.formObjective = option.title }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("objective_card_${option.title.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selecionado",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Professional Responsibility Disclaimer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Aviso Legal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Aviso Importante",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "O FitAI é uma ferramenta de suporte para organizar sua rotina de treinos e dieta. Suas sugestões não substituem o acompanhamento presencial e individualizado de profissionais de Educação Física, Nutricionistas ou Médicos.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Confirmation Checkbox
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDisclaimerAccepted = !isDisclaimerAccepted }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isDisclaimerAccepted,
                    onCheckedChange = { isDisclaimerAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("disclaimer_checkbox")
                )
                Text(
                    text = "Estou ciente e aceito os termos do aviso legal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_prev_step4"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Anterior", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.generatePlanFromAi() },
                    enabled = isDisclaimerAccepted,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .testTag("submit_onboarding_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Gerar Treino",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FitInput(
    label: String,
    value: String?,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String = ""
) {
    val safeValue = value ?: ""
    var textFieldValue by remember { 
        mutableStateOf(
            TextFieldValue(
                text = safeValue, 
                selection = TextRange(safeValue.length.coerceAtLeast(0))
            )
        ) 
    }
    var lastEmittedValue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(safeValue) {
        if (lastEmittedValue == null || safeValue != lastEmittedValue || textFieldValue.text != safeValue) {
            val safeLen = safeValue.length.coerceAtLeast(0)
            textFieldValue = TextFieldValue(text = safeValue, selection = TextRange(safeLen))
            lastEmittedValue = safeValue
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val rawText = newValue.text ?: ""
                val filtered = try {
                    if (keyboardType == KeyboardType.Decimal) {
                        rawText.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                    } else if (keyboardType == KeyboardType.Number) {
                        rawText.filter { it.isDigit() }
                    } else {
                        rawText
                    }
                } catch (e: Exception) {
                    rawText
                }

                if (keyboardType == KeyboardType.Decimal && filtered.count { it == '.' } > 1) {
                    return@OutlinedTextField
                }

                val safeStart = newValue.selection.start.coerceIn(0, filtered.length)
                val safeEnd = newValue.selection.end.coerceIn(0, filtered.length)
                val clampedSelection = TextRange(minOf(safeStart, safeEnd), maxOf(safeStart, safeEnd))
                val newSelection = if (filtered.length != rawText.length) {
                    TextRange(filtered.length)
                } else {
                    clampedSelection
                }

                textFieldValue = TextFieldValue(text = filtered, selection = newSelection)
                lastEmittedValue = filtered
                onValueChange(filtered)
            },
            placeholder = { Text(placeholder, color = Color(0xFF666666)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isFocused) {
                        val len = textFieldValue.text.length.coerceAtLeast(0)
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, len)
                        )
                    }
                    isFocused = focusState.isFocused
                }
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrect = false
            ),
            singleLine = true
        )
    }
}

@Composable
fun CargaInputField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Carga (kg)",
    testTag: String? = null
) {
    fun formatCargaValue(v: Double): String {
        if (v <= 0.0 || v.isNaN() || v.isInfinite()) return ""
        val rounded = Math.round(v * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(java.util.Locale.US, "%.1f", rounded)
    }

    val formattedInitial = formatCargaValue(value)
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = formattedInitial)) }
    var lastEmittedValue by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(value) {
        if (lastEmittedValue == null || Math.abs(value - (lastEmittedValue ?: 0.0)) > 0.05) {
            val formatted = formatCargaValue(value)
            textFieldValue = TextFieldValue(text = formatted)
            lastEmittedValue = value
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    val updatedModifier = modifier
        .onFocusChanged { focusState ->
            if (focusState.isFocused && !isFocused) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(0, textFieldValue.text.length)
                )
            }
            isFocused = focusState.isFocused
        }
        .then(if (!testTag.isNullOrEmpty()) Modifier.testTag(testTag) else Modifier)

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val filtered = newValue.text.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
            if (filtered.count { it == '.' } <= 1) {
                val newSelection = if (filtered.length != newValue.text.length) {
                    TextRange(filtered.length)
                } else {
                    newValue.selection
                }
                textFieldValue = newValue.copy(text = filtered, selection = newSelection)
                val rawVal = filtered.toDoubleOrNull()
                val dVal = if (rawVal == null || rawVal.isNaN() || rawVal.isInfinite() || rawVal <= 0.0) 0.0 else Math.round(rawVal * 10.0) / 10.0
                lastEmittedValue = dVal
                onValueChange(dVal)
            }
        },
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = updatedModifier
    )
}

@Composable
fun RepsInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Reps",
    testTag: String? = null
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    var lastEmittedValue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        if (lastEmittedValue == null || value != lastEmittedValue) {
            textFieldValue = TextFieldValue(text = value)
            lastEmittedValue = value
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    val updatedModifier = modifier
        .onFocusChanged { focusState ->
            if (focusState.isFocused && !isFocused) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(0, textFieldValue.text.length)
                )
            }
            isFocused = focusState.isFocused
        }
        .then(if (!testTag.isNullOrEmpty()) Modifier.testTag(testTag) else Modifier)

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            lastEmittedValue = newValue.text
            onValueChange(newValue.text)
        },
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = updatedModifier
    )
}

@Composable
fun TrainingDaysSelector(
    selectedDaysString: String,
    onSelectionChanged: (String) -> Unit
) {
    val allDays = listOf("S", "T", "Q", "Q", "S", "S", "D")
    // Keys used: Mon, Tue, Wed, Thu, Fri, Sat, Sun
    val activeList = remember(selectedDaysString) {
        val list = mutableListOf(false, false, false, false, false, false, false)
        val items = selectedDaysString.split(",")
        
        // Also map standard "M,T,W,T,F" to indices
        if (selectedDaysString.contains("M")) list[0] = true
        
        val occurrencesT = selectedDaysString.count { it == 'T' }
        if (occurrencesT >= 2) {
            list[1] = true
            list[3] = true
        } else if (occurrencesT == 1) {
            val idxT = selectedDaysString.indexOf('T')
            val idxW = selectedDaysString.indexOf('W')
            if (idxW != -1) {
                if (idxT < idxW) list[1] = true else list[3] = true
            } else {
                list[1] = true
            }
        }
        if (selectedDaysString.contains("W")) list[2] = true
        if (selectedDaysString.contains("F")) list[4] = true
        
        val occurrencesS = selectedDaysString.count { it == 'S' }
        if (occurrencesS >= 2) {
            list[5] = true
            list[6] = true
        } else if (occurrencesS == 1) {
            list[5] = true
        }
        list
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val dayNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        allDays.forEachIndexed { index, day ->
            val isActive = activeList.getOrElse(index) { false }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .clickable {
                            val newList = activeList.toMutableList()
                            newList[index] = !newList[index]
                            
                            // Map back to string of letters M,T,W,T,F,S,S
                            val daysString = mutableListOf<String>()
                            if (newList[0]) daysString.add("M")
                            if (newList[1]) daysString.add("T")
                            if (newList[2]) daysString.add("W")
                            if (newList[3]) daysString.add("T")
                            if (newList[4]) daysString.add("F")
                            if (newList[5]) daysString.add("S")
                            if (newList[6]) daysString.add("S")
                            onSelectionChanged(daysString.joinToString(","))
                        }
                        .testTag("day_chip_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = dayNames[index],
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// --- Training Tab Screen ---

@Composable
fun WorkoutSkeletonLoader() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Pulse header
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Pulse day chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        // Pulse cards
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrainingTabScreen(viewModel: FitViewModel) {
    if (viewModel.generationState is GenerationUiState.Generating) {
        WorkoutSkeletonLoader()
        return
    }

    val exercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val availableDays by viewModel.availableWorkoutDays.collectAsStateWithLifecycle()
    val selectedDay = viewModel.selectedWorkoutDay

    val filteredExercises = remember(exercises, selectedDay) {
        exercises.filter { it.workoutDay == selectedDay }.sortedBy { it.orderIndex }
    }
    var showReorderModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Workout Top Header and "Adicionar Nova Ficha"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Treino Diário",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(
                onClick = { viewModel.showCustomWorkoutModal = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Adicionar Nova Ficha", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Workout Tab Filter Chips (A, B, C, D, E, etc.)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(availableDays) { day ->
                val isSelected = day == selectedDay
                val isStandardLetter = day.length == 1 && day[0] in 'A'..'Z'
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { viewModel.selectedWorkoutDay = day }
                        .padding(horizontal = 16.dp)
                        .testTag("workout_tab_$day"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isStandardLetter) "Treino $day" else day,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
                        )
                        // If it's a non-standard custom day name, allow deletion
                        if (!isStandardLetter) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Deletar",
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.deleteCustomWorkoutDay(day) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active day headline
        val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
        val isFeminino = userProfile?.gender?.equals("Feminino", ignoreCase = true) == true
        val customFichaNames by viewModel.customFichaNames.collectAsStateWithLifecycle()
        val headlineText = remember(selectedDay, isFeminino, customFichaNames) {
            viewModel.getWorkoutFichaName(selectedDay, isFeminino)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reordenar Exercícios Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .clickable { showReorderModal = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("reorder_exercises_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Reordenar Exercícios",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reordenar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Editar Ficha Button (Requirement 6)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .clickable {
                            viewModel.editingWorkoutDayState = selectedDay
                            viewModel.showEditWorkoutModal = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar Ficha",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Editar Ficha",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredExercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhum exercício nesta ficha.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            var activeExerciseForExecutionGuide by remember { mutableStateOf<WorkoutExercise?>(null) }
            val todayDateStr = remember {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            }
            val allExercisesCompleted = remember(filteredExercises, todayDateStr) {
                filteredExercises.isNotEmpty() && filteredExercises.all { it.isCompletedOn(todayDateStr) }
            }
            var showWorkoutCompletedDialog by remember { mutableStateOf(false) }
            var autoShownForDay by remember(selectedDay) { mutableStateOf(false) }

            LaunchedEffect(allExercisesCompleted) {
                if (allExercisesCompleted && !autoShownForDay) {
                    showWorkoutCompletedDialog = true
                    autoShownForDay = true
                }
            }

            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(viewModel.scrollToExerciseIndexEvent) {
                viewModel.scrollToExerciseIndexEvent?.let { targetIndex ->
                    val scrollTarget = if (allExercisesCompleted) targetIndex + 1 else targetIndex
                    listState.animateScrollToItem(scrollTarget)
                    viewModel.scrollToExerciseIndexEvent = null
                }
            }

            LaunchedEffect(viewModel.triggerShowWorkoutCompletedEvent) {
                if (viewModel.triggerShowWorkoutCompletedEvent) {
                    showWorkoutCompletedDialog = true
                    viewModel.triggerShowWorkoutCompletedEvent = false
                }
            }

            if (showWorkoutCompletedDialog) {
                WorkoutCompletedDialog(
                    day = selectedDay,
                    headlineText = headlineText,
                    motivationalMessage = userProfile?.notificationMessage ?: "",
                    onDismiss = { showWorkoutCompletedDialog = false }
                )
            }

            activeExerciseForExecutionGuide?.let { guideExercise ->
                ExecutionGuideDialog(
                    exercise = guideExercise,
                    onDismiss = { activeExerciseForExecutionGuide = null }
                )
            }

            if (showReorderModal) {
                ReorderExercisesDialog(
                    viewModel = viewModel,
                    day = selectedDay,
                    onDismiss = { showReorderModal = false }
                )
            }

            if (viewModel.showEditWorkoutModal) {
                viewModel.editingWorkoutDayState?.let { editingDay ->
                    EditWorkoutFichaDialog(
                        viewModel = viewModel,
                        day = editingDay,
                        onDismiss = {
                            viewModel.showEditWorkoutModal = false
                            viewModel.editingWorkoutDayState = null
                        }
                    )
                }
            }

            val history by viewModel.historyEntries.collectAsStateWithLifecycle()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (allExercisesCompleted) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showWorkoutCompletedDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "🎉", fontSize = 22.sp)
                                    Column {
                                        Text(
                                            text = "Treino de hoje 100% concluído!",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Toque para ver a mensagem motivacional do FitAI",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(filteredExercises) { index, exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        exerciseIndex = index + 1,
                        history = history,
                        onCheckedChange = { isCompleted -> viewModel.setExerciseCompleted(exercise, isCompleted) },
                        onWeightChange = { weight -> viewModel.updateExerciseWeight(exercise, weight) },
                        onComoExecutarClicked = { activeExerciseForExecutionGuide = exercise },
                        restTimerSeconds = viewModel.customRestTimerSeconds,
                        onRestTimerSecondsChange = { viewModel.customRestTimerSeconds = it },
                        onRestTimerStart = { completedCount -> viewModel.startRestTimer(exercise, completedCount) }
                    )
                }
            }
        }
    }

    // Modal: Adicionar Nova Ficha (Rule 3)
    if (viewModel.showCustomWorkoutModal) {
        CustomWorkoutDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.showCustomWorkoutModal = false }
        )
    }
}

fun formatExerciseSeriesAndReps(series: Int, reps: String): String {
    val cleanedReps = reps.replace("+", "").replace("%", "").replace("↗", "").replace("1 2", "").trim()
    val normalizedReps = if (cleanedReps.contains("a", ignoreCase = true) || cleanedReps.contains("-")) {
        cleanedReps.replace("-", " a ")
    } else {
        cleanedReps
    }
    return "$series SÉRIES • $normalizedReps REPS"
}

fun cleanExerciseBadge(badge: String?): String? {
    if (badge.isNullOrBlank()) return null
    val cleaned = badge.replace("+", "").replace("%", "").replace("↗", "").replace("1 2", "").replace("12", "").trim()
    if (cleaned.isBlank() || cleaned.all { it.isDigit() }) return null
    return cleaned.uppercase()
}

fun Double?.formatWeight(): String {
    if (this == null || this.isNaN() || this.isInfinite() || this == 0.0) return "0"
    return try {
        val rounded = (Math.round(this * 100.0) / 100.0)
        if (rounded % 1.0 == 0.0) {
            String.format(java.util.Locale.US, "%.0f", rounded)
        } else if ((rounded * 10.0) % 1.0 == 0.0) {
            String.format(java.util.Locale.US, "%.1f", rounded)
        } else {
            String.format(java.util.Locale.US, "%.2f", rounded)
        }
    } catch (e: Exception) {
        "0"
    }
}

@Composable
fun ExerciseCard(
    exercise: WorkoutExercise,
    onCheckedChange: (Boolean) -> Unit,
    onWeightChange: (Double) -> Unit,
    onComoExecutarClicked: () -> Unit,
    restTimerSeconds: Int,
    onRestTimerSecondsChange: (Int) -> Unit,
    onRestTimerStart: (Int) -> Unit = {},
    exerciseIndex: Int? = null,
    history: List<HistoryEntry> = emptyList()
) {
    val displayBadge = cleanExerciseBadge(exercise.badge)

    // Last historical entry for auto-fill of weight and reps (Requirement 1)
    val lastHistory = remember(history, exercise.name) {
        history.firstOrNull { it.exerciseName.equals(exercise.name, ignoreCase = true) }
    }

    // Auto-fill execution: pre-fill load from previous workout if current weight is unset (0.0)
    LaunchedEffect(exercise.id, lastHistory) {
        if (exercise.currentWeight == 0.0 && lastHistory != null && lastHistory.weight > 0.0) {
            onWeightChange(lastHistory.weight)
        }
    }

    // Personal Record (PR) evaluation logic (Requirement 3)
    val historicalWeights = remember(history, exercise.name) {
        history.filter { it.exerciseName.equals(exercise.name, ignoreCase = true) }.map { it.weight }
    }
    val maxPastWeight = remember(historicalWeights, exercise.currentWeight) {
        val strictlyLesser = historicalWeights.filter { it < exercise.currentWeight }
        if (strictlyLesser.isNotEmpty()) {
            strictlyLesser.maxOrNull() ?: 0.0
        } else if (historicalWeights.size > 1) {
            historicalWeights.sortedDescending().getOrNull(1) ?: 0.0
        } else {
            0.0
        }
    }
    val isPersonalRecord = remember(exercise.currentWeight, maxPastWeight) {
        exercise.currentWeight > 0.0 && maxPastWeight > 0.0 && exercise.currentWeight > maxPastWeight
    }

    val todayDateStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val isCompletedToday = remember(exercise.completed, exercise.completedDate, todayDateStr) {
        exercise.isCompletedOn(todayDateStr)
    }

    // Track completed series locally for set-by-set checkmarks
    var completedSeriesCount by remember(exercise.id, isCompletedToday) {
        mutableStateOf(if (isCompletedToday) exercise.series else 0)
    }

    val isExerciseCompleted = remember(completedSeriesCount, exercise.series, isCompletedToday) {
        (completedSeriesCount >= exercise.series && exercise.series > 0) || isCompletedToday
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExerciseCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isExerciseCompleted) 2.dp else if (isPersonalRecord) 1.5.dp else 1.dp,
            if (isExerciseCompleted) androidx.compose.ui.graphics.Color(0xFF4CAF50)
            else if (isPersonalRecord) androidx.compose.ui.graphics.Color(0xFF00E676)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Anatomical Muscle Thumbnail with Red Target Highlight
                    AnatomicalMuscleThumbnail(
                        exerciseName = exercise.name,
                        isPersonalRecord = isPersonalRecord,
                        onClick = onComoExecutarClicked,
                        modifier = Modifier.size(54.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Title container taking full width of column
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (exerciseIndex != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "#$exerciseIndex",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Dedicated flexible row for secondary tags & actions
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Tag 1: Series & Reps
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = formatExerciseSeriesAndReps(exercise.series, exercise.reps),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            // Tag 2: "Como executar" button link
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { onComoExecutarClicked() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Como executar",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Tag 3: Execution mode badge (e.g., DROPSET, BI-SET) if present
                            if (displayBadge != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = displayBadge,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Glossary explanation caption for beginners
                        val glossaryExplanation = when (displayBadge) {
                            "DROPSET" -> "Dropset: treine até a falha, diminua a carga e continue sem descansar."
                            "BI-SET" -> "Bi-set: faça dois exercícios integrados de forma consecutiva."
                            "FALHA" -> "Até a falha: realize as repetições até o esgotamento total."
                            "EXPLOSIVO" -> "Explosivo: execute o movimento com foco em força dinâmica rápida."
                            "ISOMETRIA" -> "Isometria: contraia o músculo de forma estática sem movimento."
                            "PERSONALIZADO" -> "Ficha customizada criada pelo usuário."
                            else -> null
                        }
                        if (glossaryExplanation != null) {
                            Text(
                                text = glossaryExplanation,
                                fontSize = 10.sp,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Top-right status badges column (CONCLUÍDO ✓ & PR)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    // Badge "CONCLUÍDO ✓" em destaque verde
                    if (isExerciseCompleted) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "CONCLUÍDO ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    // Celebration PR Badge
                    if (isPersonalRecord) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF00E676).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF00E676),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Novo Recorde!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = androidx.compose.ui.graphics.Color(0xFF00E676)
                                )
                            }
                        }
                    }
                }
            }

            // Auto-fill status banner (Requirement 1)
            if (lastHistory != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Pré-preenchimento Automático",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Treino Anterior: ${lastHistory.weight.formatWeight()} kg • ${if (lastHistory.reps.isNotBlank()) lastHistory.reps else exercise.reps} reps (Pré-preenchido)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico de Carga",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (exercise.currentWeight > 0.0) "Histórico Recente: ${exercise.currentWeight.formatWeight()} kg" else "Histórico: Primeiro registro do exercício",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // PR Celebration Banner (Requirement 3)
            if (isPersonalRecord) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "🏆 RECORD DE CARGA! Ultrapassou a marca anterior de ${maxPastWeight.formatWeight()} kg!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFF00E676)
                    )
                }
            }

            // Set-by-set interactive checkmarks (Requirement 5)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Séries:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (s in 1..exercise.series) {
                        val isSetDone = s <= completedSeriesCount
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSetDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    val newCount = if (isSetDone && s == completedSeriesCount) s - 1 else s
                                    val countChanged = newCount != completedSeriesCount
                                    completedSeriesCount = newCount
                                    val isFullyDone = newCount >= exercise.series
                                    onCheckedChange(isFullyDone)
                                    if (countChanged && newCount > 0) {
                                        onRestTimerStart(newCount)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSetDone) Icons.Default.Check else Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = if (isSetDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Série $s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSetDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Load edit, Rest edit and Complete Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Carga atual (kg)
                Column(modifier = Modifier.weight(1.5f)) {
                    Text(
                        text = "Carga atual (kg)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CargaInputField(
                        value = exercise.currentWeight,
                        onValueChange = { dVal -> onWeightChange(dVal) },
                        placeholder = "0.0",
                        testTag = "carga_input_${exercise.id}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    )
                }

                // Edit Tempo de descanso (s)
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "Descanso (s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RepsInputField(
                        value = if (restTimerSeconds == 0) "" else restTimerSeconds.toString(),
                        onValueChange = { input ->
                            val sec = input.toIntOrNull() ?: 0
                            onRestTimerSecondsChange(sec)
                        },
                        placeholder = "0",
                        testTag = "rest_time_input_${exercise.id}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    )
                }

                // Checkbox Concluding Exercise (Sequential set completion 1 by 1)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    val isFullyDone = completedSeriesCount >= exercise.series
                    val hasSomeDone = completedSeriesCount > 0

                    IconButton(
                        onClick = {
                            val newCount = if (completedSeriesCount >= exercise.series) 0 else completedSeriesCount + 1
                            completedSeriesCount = newCount
                            val fullyDone = newCount >= exercise.series
                            onCheckedChange(fullyDone)
                            if (newCount > 0 && newCount <= exercise.series) {
                                onRestTimerStart(newCount)
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isFullyDone -> MaterialTheme.colorScheme.primary
                                    hasSomeDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .border(
                                1.dp,
                                if (hasSomeDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Concluir série sequencial",
                            tint = if (isFullyDone) MaterialTheme.colorScheme.onPrimary
                                   else if (hasSomeDone) MaterialTheme.colorScheme.primary
                                   else Color(0xFF666666)
                        )
                    }
                }
            }
        }
    }
}

// --- Rest Timer Floating Card Overlay (Rule 3) ---

@Composable
fun RestTimerCard(
    secondsRemaining: Int,
    message: String,
    isSoundEnabled: Boolean = true,
    onToggleSound: () -> Unit = {},
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .widthIn(min = 210.dp, max = 250.dp)
            .testTag("rest_timer_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${secondsRemaining}s",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DESCANSO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Speaker Sound Toggle Button
            IconButton(
                onClick = onToggleSound,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("rest_timer_sound_toggle")
            ) {
                Icon(
                    imageVector = if (isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isSoundEnabled) "Desativar alerta sonoro" else "Ativar alerta sonoro",
                    tint = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Close / Cancel Timer Button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("rest_timer_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar timer",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// --- Visual Alert Popup Dialog ---

@Composable
fun TimerAlertPopup(
    primaryButtonText: String = "Próxima Série",
    onPrimaryAction: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DESCANSO CONCLUÍDO!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPrimaryAction() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(primaryButtonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Voltar ao Treino", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// --- Daily Motivational Message Dialog (Bell Icon) ---

@Composable
fun DailyMessageDialog(
    userProfile: UserProfile?,
    onDismiss: () -> Unit
) {
    val message = if (!userProfile?.notificationMessage.isNullOrBlank()) {
        userProfile?.notificationMessage ?: ""
    } else {
        "A constância é o segredo dos grandes resultados. Mantenha o foco nos seus treinos, siga seu plano alimentar e lembre-se de se hidratar hoje! 💪"
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("daily_message_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notificação / Dica do Dia",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MENSAGEM DO DIA",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Dica e Motivação FitAI 💡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Start
                    )
                }

                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// --- Full Workout Completed Dialog ---

@Composable
fun WorkoutCompletedDialog(
    day: String,
    headlineText: String,
    motivationalMessage: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFF4CAF50)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "TREINO CONCLUÍDO! 🎉",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$headlineText (Ficha $day)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "FitAI Motivacional",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val displayMsg = if (motivationalMessage.isNotBlank()) motivationalMessage else "Parabéns por finalizar o treino de hoje! A consistência é a chave para o seu progresso!"

                        Text(
                            text = "\"$displayMsg\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                lineHeight = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Excelente! Fechar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// --- Dialog: Custom Workout "Adicionar Nova Ficha" (Rule 3) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutDialog(
    viewModel: FitViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // --- TIMER DE DESCANSO NO TOPO (Requirement 6) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Descanso padrão",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${viewModel.customRestTimerSeconds}s",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Button(
                        onClick = {
                            // Cycle resting timer: 30 -> 45 -> 60 -> 90 -> 120 -> 30
                            viewModel.customRestTimerSeconds = when (viewModel.customRestTimerSeconds) {
                                30 -> 45
                                45 -> 60
                                60 -> 90
                                90 -> 120
                                120 -> 30
                                else -> 60
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Alterar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Criar Nova Ficha de Treino",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Ficha Name Input
                FitInput(
                    label = "NOME DA FICHA DE TREINO",
                    value = viewModel.customWorkoutName,
                    onValueChange = { viewModel.customWorkoutName = it },
                    placeholder = "Ex: Treino D ou Ombros",
                    testTag = "input_custom_workout_name"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content of series and details
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "CONFIGURAR PRIMEIRO EXERCÍCIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = viewModel.customExerciseNameState,
                            onValueChange = { viewModel.customExerciseNameState = it },
                            placeholder = { Text("Nome do exercício (ex: Supino Inclinado)") },
                            textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        Text(
                            text = "Lista de Séries",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    itemsIndexed(viewModel.customExerciseSeries.value) { sIndex, seriesData ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Série ${sIndex + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(48.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CargaInputField(
                                value = seriesData.first,
                                onValueChange = { d ->
                                    viewModel.updateSeriesCarga(sIndex, d)
                                },
                                placeholder = "Carga (kg)",
                                modifier = Modifier.weight(1f)
                            )

                            RepsInputField(
                                value = seriesData.second,
                                onValueChange = { reps ->
                                    viewModel.updateSeriesReps(sIndex, reps)
                                },
                                placeholder = "Reps",
                                modifier = Modifier.weight(1f)
                            )

                            if (viewModel.customExerciseSeries.value.size > 1) {
                                IconButton(onClick = { viewModel.removeSeriesFromCustomExercise(sIndex) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Deletar Série",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        TextButton(onClick = { viewModel.addSeriesToCustomExercise() }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar série", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.saveCustomWorkoutFichaWithSeries()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Concluir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditWorkoutFichaDialog(
    viewModel: FitViewModel,
    day: String,
    onDismiss: () -> Unit
) {
    val exercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val dayExercises = remember(exercises, day) {
        exercises.filter { it.workoutDay.equals(day, ignoreCase = true) }.sortedBy { it.orderIndex }
    }
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isFeminino = userProfile?.gender?.equals("Feminino", ignoreCase = true) == true
    val customFichaNames by viewModel.customFichaNames.collectAsStateWithLifecycle()
    val currentFichaName = remember(day, isFeminino, customFichaNames) {
        viewModel.getWorkoutFichaName(day, isFeminino)
    }
    var fichaNameInput by remember(day, currentFichaName) { mutableStateOf(currentFichaName) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var isNameFieldFocused by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Editar Treino $day",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Altere o nome da ficha e gerencie os exercícios do treino.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Ficha Name Input Field with Real-time Validation
                OutlinedTextField(
                    value = fichaNameInput,
                    onValueChange = { newValue ->
                        if (newValue.length <= 40) {
                            fichaNameInput = newValue
                            nameError = if (newValue.trim().isEmpty()) {
                                "O nome da ficha não pode ficar vazio"
                            } else {
                                null
                            }
                        }
                    },
                    label = { Text("Nome da Ficha / Grupo Muscular", fontSize = 12.sp) },
                    placeholder = { Text("Ex: Tríceps e Bíceps, Braços, etc.", fontSize = 12.sp) },
                    isError = nameError != null,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (nameError != null) {
                                Text(
                                    text = nameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Text(
                                text = "${fichaNameInput.trim().length}/40",
                                color = if (nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_ficha_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable container
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "EXERCÍCIOS ATUAIS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (dayExercises.isEmpty()) {
                        item {
                            Text(
                                text = "Nenhum exercício nesta ficha.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        itemsIndexed(dayExercises) { index, exercise ->
                            val isEditing = viewModel.editingExerciseId == exercise.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isEditing)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reordenar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    AnatomicalMuscleThumbnail(
                                        exerciseName = exercise.name,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.startEditingExercise(exercise) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = exercise.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                                color = if (isEditing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isEditing) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "EDITANDO",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${exercise.series} séries • ${exercise.reps} reps",
                                            fontSize = 12.sp,
                                            color = if (isEditing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.moveExerciseUp(exercise) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Mover para cima",
                                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveExerciseDown(exercise) },
                                        enabled = index < dayExercises.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Mover para baixo",
                                            tint = if (index < dayExercises.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.startEditingExercise(exercise) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteExercise(exercise) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remover",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.editingExerciseId != null) "EDITANDO EXERCÍCIO" else "ADICIONAR NOVO EXERCÍCIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (viewModel.editingExerciseId != null) {
                                TextButton(onClick = { viewModel.cancelEditingExercise() }) {
                                    Text("Cancelar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = viewModel.customExerciseNameState,
                                onValueChange = { viewModel.customExerciseNameState = it },
                                label = { Text("Nome do exercício") },
                                placeholder = { Text("Ex: Supino Reto") },
                                textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (isNameFieldFocused && !focusState.isFocused) {
                                            if (viewModel.customExerciseNameState.trim().isNotEmpty()) {
                                                viewModel.generateInstructionsForExercise(viewModel.customExerciseNameState)
                                            }
                                        }
                                        isNameFieldFocused = focusState.isFocused
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        if (viewModel.customExerciseNameState.trim().isNotEmpty()) {
                                            viewModel.generateInstructionsForExercise(viewModel.customExerciseNameState)
                                        }
                                    }
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = viewModel.customExerciseInstructionsState,
                                onValueChange = { viewModel.customExerciseInstructionsState = it },
                                label = { Text("Como Executar") },
                                placeholder = { Text("Descreva o passo a passo ou deixe a IA gerar automaticamente...") },
                                textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                maxLines = 4,
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    if (viewModel.isGeneratingInstructionsState) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (viewModel.customExerciseNameState.trim().isNotEmpty()) {
                                                    viewModel.generateInstructionsForExercise(viewModel.customExerciseNameState)
                                                }
                                            },
                                            enabled = viewModel.customExerciseNameState.trim().isNotEmpty()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Gerar Instruções com IA",
                                                tint = if (viewModel.customExerciseNameState.trim().isNotEmpty())
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var seriesInputText by remember(viewModel.customExerciseSeries.value.size) {
                                    mutableStateOf(viewModel.customExerciseSeries.value.size.toString())
                                }
                                var repsInputText by remember(viewModel.customExerciseSeries.value.firstOrNull()?.second) {
                                    mutableStateOf(viewModel.customExerciseSeries.value.firstOrNull()?.second ?: "10")
                                }

                                OutlinedTextField(
                                    value = seriesInputText,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() }
                                        seriesInputText = clean
                                        val num = clean.toIntOrNull()
                                        if (num != null && num in 1..10) {
                                            viewModel.updateSeriesCount(num)
                                        }
                                    },
                                    label = { Text("Nº de Séries", fontSize = 12.sp) },
                                    placeholder = { Text("Ex: 3", fontSize = 12.sp) },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = repsInputText,
                                    onValueChange = { input ->
                                        repsInputText = input
                                        viewModel.updateAllReps(input)
                                    },
                                    label = { Text("Repetições", fontSize = 12.sp) },
                                    placeholder = { Text("Ex: 10", fontSize = 12.sp) },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Text(
                                text = "Configuração de Séries & Cargas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // List of Series builders
                            viewModel.customExerciseSeries.value.forEachIndexed { sIndex, seriesData ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Série ${sIndex + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(48.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    CargaInputField(
                                        value = seriesData.first,
                                        onValueChange = { d ->
                                            viewModel.updateSeriesCarga(sIndex, d)
                                        },
                                        placeholder = "Carga (kg)",
                                        modifier = Modifier.weight(1f)
                                    )

                                    RepsInputField(
                                        value = seriesData.second,
                                        onValueChange = { reps ->
                                            viewModel.updateSeriesReps(sIndex, reps)
                                        },
                                        placeholder = "Reps",
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (viewModel.customExerciseSeries.value.size > 1) {
                                        IconButton(onClick = { viewModel.removeSeriesFromCustomExercise(sIndex) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Deletar Série",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.addSeriesToCustomExercise() }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Adicionar Série", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.saveCustomExerciseToDay(day)
                                    },
                                    enabled = viewModel.customExerciseNameState.trim().isNotEmpty(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Salvar Exercício", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_edit_ficha_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val trimmed = fichaNameInput.trim()
                            if (trimmed.isEmpty()) {
                                nameError = "O nome da ficha não pode ficar vazio"
                            } else if (trimmed.length > 40) {
                                nameError = "Máximo de 40 caracteres permitido"
                            } else {
                                viewModel.saveWorkoutFichaName(day, trimmed)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_edit_ficha_button"),
                        enabled = fichaNameInput.trim().isNotEmpty() && fichaNameInput.trim().length <= 40,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Salvar Alterações", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReorderExercisesDialog(
    viewModel: FitViewModel,
    day: String,
    onDismiss: () -> Unit
) {
    val exercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val dayExercises = remember(exercises, day) {
        exercises.filter { it.workoutDay.equals(day, ignoreCase = true) }.sortedBy { it.orderIndex }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Reordenar Treino $day",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Altere a ordem dos exercícios da sua ficha de treino usando as setas para subir ou descer.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (dayExercises.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum exercício nesta ficha.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(dayExercises) { index, exercise ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Alça de arrastar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        AnatomicalMuscleThumbnail(
                                            exerciseName = exercise.name,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = exercise.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${exercise.series} séries • ${exercise.reps} reps",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.moveExerciseUp(exercise) },
                                            enabled = index > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Subir Posição",
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveExerciseDown(exercise) },
                                            enabled = index < dayExercises.size - 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Descer Posição",
                                                tint = if (index < dayExercises.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Salvar Nova Ordem", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Anatomical Muscle Map & Execution Guide Dialog are provided by ExerciseAnatomyComponents.kt ---

// --- Nutrition Tab Screen ---

// --- Nutrition Local Database & UI Helpers ---

data class LocalFoodItem(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val source: String = "TACO",
    val tip: String? = null
)

val localFoodDatabase = listOf(
    LocalFoodItem("Whey Protein Concentrado (30g)", 120, 24.0, 3.0, 1.5),
    LocalFoodItem("Whey Protein Isolado (30g)", 110, 27.0, 1.0, 0.0),
    LocalFoodItem("Creatina (5g)", 0, 0.0, 0.0, 0.0),
    LocalFoodItem("Peito de Frango Grelhado (100g)", 165, 31.0, 0.0, 3.6),
    LocalFoodItem("Patinho Moído Cozido (100g)", 219, 35.9, 0.0, 7.3),
    LocalFoodItem("Alcatra Grelhada (100g)", 185, 28.0, 0.0, 7.5),
    LocalFoodItem("Ovo Cozido Inteiro (1 unid)", 78, 6.3, 0.6, 5.3),
    LocalFoodItem("Ovo Frito Inteiro (1 unid)", 120, 6.3, 0.6, 10.0),
    LocalFoodItem("Clara de Ovo (1 unid/30g)", 17, 3.6, 0.2, 0.0),
    LocalFoodItem("Arroz Branco Cozido (100g)", 130, 2.7, 28.0, 0.2),
    LocalFoodItem("Arroz Integral Cozido (100g)", 111, 2.6, 23.0, 0.9),
    LocalFoodItem("Batata Doce Cozida (100g)", 86, 1.6, 20.0, 0.15),
    LocalFoodItem("Aveia em Flocos (30g)", 117, 4.3, 17.0, 2.2),
    LocalFoodItem("Banana Prata Média (1 unid)", 89, 1.1, 23.0, 0.3),
    LocalFoodItem("Pasta de Amendoim (1 colher/15g)", 90, 4.0, 3.0, 8.0),
    LocalFoodItem("Abacate (100g)", 160, 2.0, 8.5, 14.6),
    LocalFoodItem("Iogurte Desnatado (170g)", 70, 6.0, 10.0, 0.0),
    LocalFoodItem("Filé de Tilápia Grelhado (100g)", 128, 26.0, 0.0, 2.7),
    LocalFoodItem("Macarrão Semolina Cozido (100g)", 131, 5.0, 25.0, 1.1),
    LocalFoodItem("Azeite de Oliva (1 colher/13ml)", 119, 0.0, 0.0, 13.5),
    LocalFoodItem("Pão de Forma Integral (2 fatias/50g)", 120, 6.0, 22.0, 1.5),
    LocalFoodItem("Leite Integral (200ml)", 120, 6.0, 10.0, 6.0),
    LocalFoodItem("Leite Desnatado (200ml)", 70, 6.0, 10.0, 0.0)
)

@Composable
fun EditTodayWaterDialog(
    initialAmount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(initialAmount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Ajustar Consumo de Água (Hoje)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                Text("Digite o valor exato consumido hoje em mililitros (ml):", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input.filter { it.isDigit() }
                    },
                    label = { Text("Quantidade (ml)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_today_water_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = textValue.toIntOrNull() ?: initialAmount
                    onSave(amount)
                    onDismiss()
                }
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun WaterHistoryDialog(
    viewModel: FitViewModel,
    onDismiss: () -> Unit
) {
    val historyList by viewModel.allRegistrosDiarios.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Histórico de Hidratação", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            if (historyList.isEmpty()) {
                Text("Nenhum histórico registrado ainda.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (item.dateStr == viewModel.repository.getTodayDateStr()) "Hoje (${item.dateStr})" else item.dateStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.caloriasConsumidas} kcal consumidas",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${item.aguaMl} ml",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun AddSupplementDialog(
    initialSuplemento: Suplemento? = null,
    onDismiss: () -> Unit,
    onSave: (nome: String, dose: String, horario: String) -> Unit
) {
    var nome by remember { mutableStateOf(initialSuplemento?.nome ?: "") }
    var dose by remember { mutableStateOf(initialSuplemento?.dose ?: "") }
    var horario by remember { mutableStateOf(initialSuplemento?.horario ?: "09:00") }

    val context = LocalContext.current
    val parts = horario.split(":")
    val curHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val curMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerDialog = remember(context, horario) {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                horario = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
            },
            curHour,
            curMin,
            true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💊", fontSize = 20.sp)
                Text(
                    text = if (initialSuplemento == null) "Cadastrar Suplemento" else "Editar Suplemento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cadastre suas vitaminas, creatina ou suplementos diários para receber lembretes pontuais e registrar o consumo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do Suplemento") },
                    placeholder = { Text("Ex: Creatina Monohidratada") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_supplement_name")
                )

                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dosagem") },
                    placeholder = { Text("Ex: 5g, 1 scoop, 2 cápsulas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_supplement_dose")
                )

                OutlinedTextField(
                    value = horario,
                    onValueChange = { horario = it },
                    label = { Text("Horário do Lembrete (HH:mm)") },
                    placeholder = { Text("Ex: 06:00") },
                    supportingText = {
                        Text("Horário local (recorrência diária automática)")
                    },
                    trailingIcon = {
                        IconButton(onClick = { timePickerDialog.show() }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Selecionar Horário"
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_supplement_time")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.trim().isNotEmpty()) {
                        onSave(nome.trim(), dose.trim(), horario.trim())
                        onDismiss()
                    }
                },
                enabled = nome.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_supplement_button")
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun SupplementSection(
    viewModel: FitViewModel,
    onAddSupplementClick: () -> Unit
) {
    val context = LocalContext.current
    val suplementos by viewModel.allSuplementos.collectAsStateWithLifecycle()
    val selectedRegistroDiario by viewModel.selectedRegistroDiario.collectAsStateWithLifecycle()
    val suplementosTomados = selectedRegistroDiario?.suplementosTomados ?: emptyList()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("supplement_section_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💊", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "MINHA SUPLEMENTAÇÃO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Lembretes e registro diário",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onAddSupplementClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_supplement_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Suplemento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (suplementos.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("💊", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nenhum suplemento cadastrado",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cadastre sua Creatina, Whey ou Vitaminas para acompanhar diariamente.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suplementos.forEach { item ->
                        val isTomado = suplementosTomados.contains(item.id)

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTomado) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isTomado) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("supplement_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isTomado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isTomado) "✓" else "💊",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTomado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.nome,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isTomado) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "CONCLUÍDO ✔️",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (item.dose.isNotEmpty()) {
                                                Text(
                                                    text = item.dose,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (item.horario.isNotEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = item.horario,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.toggleSuplementoTomado(context, item, !isTomado)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isTomado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                            contentColor = if (isTomado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("toggle_supplement_button_${item.id}")
                                    ) {
                                        Text(
                                            text = if (isTomado) "Desfazer" else "Tomar ✔️",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteSuplemento(context, item.id) },
                                        modifier = Modifier.size(32.dp).testTag("delete_supplement_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remover",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GrowthDateCarousel(
    selectedDateStr: String,
    onDateSelected: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val dateList = remember(selectedDateStr, todayStr) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val list = mutableListOf<String>()

        for (i in 13 downTo 0) {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_YEAR, -i)
            list.add(sdf.format(c.time))
        }

        if (!list.contains(selectedDateStr)) {
            list.add(0, selectedDateStr)
        }
        list
    }

    val formattedSelectedDateLabel = remember(selectedDateStr, todayStr) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val parsed = sdf.parse(selectedDateStr)
            val ptBrLocale = java.util.Locale.Builder().setLanguage("pt").setRegion("BR").build()
            if (selectedDateStr == todayStr) {
                val daySdf = java.text.SimpleDateFormat("d 'de' MMMM", ptBrLocale)
                "Hoje, ${daySdf.format(parsed ?: java.util.Date())}"
            } else {
                val fullSdf = java.text.SimpleDateFormat("EEEE, d 'de' MMMM", ptBrLocale)
                val formatted = fullSdf.format(parsed ?: java.util.Date())
                formatted.replaceFirstChar { it.uppercase() }
            }
        } catch (e: Exception) {
            selectedDateStr
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // TOP ROW: TITLE & CALENDAR PICKER ICON
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nutrição & Diário",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formattedSelectedDateLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = {
                    val cal = java.util.Calendar.getInstance()
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val parsed = sdf.parse(selectedDateStr)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {
                        Log.e("DateCarousel", "Error parsing selected date", e)
                    }

                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val selectedCal = java.util.Calendar.getInstance().apply {
                                set(year, month, dayOfMonth)
                            }
                            val newDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(selectedCal.time)
                            onDateSelected(newDateStr)
                        },
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH),
                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .testTag("open_calendar_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Selecionar Data no Calendário",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // HORIZONTAL CAROUSEL OF DAYS
        val listState = rememberLazyListState()

        LaunchedEffect(selectedDateStr, dateList) {
            val index = dateList.indexOf(selectedDateStr)
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(dateList) { dateStr ->
                val isSelected = dateStr == selectedDateStr
                val isToday = dateStr == todayStr

                val (dayNameShort, dayNumberStr) = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val dateObj = sdf.parse(dateStr)
                    val cal = java.util.Calendar.getInstance().apply { time = dateObj ?: java.util.Date() }

                    val ptBrLocale = java.util.Locale.Builder().setLanguage("pt").setRegion("BR").build()
                    val dayOfWeekSdf = java.text.SimpleDateFormat("EEE", ptBrLocale)
                    val shortName = dayOfWeekSdf.format(cal.time).replace(".", "").uppercase()
                    val numStr = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
                    shortName to numStr
                } catch (e: Exception) {
                    "DIA" to "1"
                }

                Surface(
                    onClick = { onDateSelected(dateStr) },
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        width = if (isSelected || isToday) 1.5.dp else 1.dp,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        }
                    ),
                    modifier = Modifier
                        .width(58.dp)
                        .height(72.dp)
                        .testTag("date_chip_$dateStr")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isToday) "HOJE" else dayNameShort,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayNumberStr,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionTabScreen(viewModel: FitViewModel) {
    if (viewModel.generationState is GenerationUiState.Generating) {
        WorkoutSkeletonLoader()
        return
    }

    val selectedDateStr by viewModel.selectedDateStr.collectAsStateWithLifecycle()
    val selectedRegistroDiario by viewModel.selectedRegistroDiario.collectAsStateWithLifecycle()
    val meals by viewModel.allMeals.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allConsumoDiario by viewModel.allConsumoDiario.collectAsStateWithLifecycle()
    val waterRecords by viewModel.allRegistroAgua.collectAsStateWithLifecycle()
    val allRegistrosDiarios by viewModel.allRegistrosDiarios.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(0) }

    // Filter consumoList for the currently selected date
    val consumoList = remember(allConsumoDiario, selectedDateStr) {
        allConsumoDiario.filter { item ->
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val itemDateStr = sdf.format(java.util.Date(item.date))
            itemDateStr == selectedDateStr
        }
    }

    // Targets - programmatically calculated based on user biometrics
    val age = userProfile?.age ?: 28
    val weight = userProfile?.weight ?: 82.0
    val height = userProfile?.height ?: 180.0
    val gender = userProfile?.gender ?: "Masculino"
    val objective = userProfile?.objective ?: "Hipertrofia"

    val bmr = if (gender.equals("Feminino", ignoreCase = true)) {
        447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
    } else {
        88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
    }
    val tdee = bmr * 1.4
    val targetCalories = when (objective) {
        "Hipertrofia" -> (tdee + 400).toInt()
        "Emagrecimento" -> (tdee - 500).toInt().coerceAtLeast(1200)
        "Definição" -> (tdee - 200).toInt().coerceAtLeast(1400)
        "Saúde" -> tdee.toInt()
        else -> tdee.toInt()
    }

    val proteinPerKg = when (objective) {
        "Hipertrofia", "Definição" -> 2.0
        "Emagrecimento" -> 1.8
        "Saúde" -> 1.5
        else -> 1.8
    }
    val targetProtein = (weight * proteinPerKg).coerceAtLeast(40.0)

    val fatPerKg = when (objective) {
        "Emagrecimento" -> 0.8
        else -> 1.0
    }
    val targetFat = (weight * fatPerKg).coerceAtLeast(30.0)

    val proteinCalories = targetProtein * 4
    val fatCalories = targetFat * 9
    val remainingCals = (targetCalories - proteinCalories - fatCalories).coerceAtLeast(100.0)
    val targetCarbs = remainingCals / 4

    // Calcule actual total consumed for selected date
    val totalConsumedCalories = selectedRegistroDiario?.caloriasConsumidas ?: consumoList.sumOf { it.calories }
    val totalConsumedCarbs = consumoList.sumOf { it.carbs }
    val totalConsumedProtein = consumoList.sumOf { it.protein }
    val totalConsumedFat = consumoList.sumOf { it.fat }

    // Remaining calories
    val remainingCalories = (targetCalories - totalConsumedCalories).coerceAtLeast(0)

    // Water calculations for selected date
    val userWeight = userProfile?.weight ?: 82.0
    val targetWaterMl = viewModel.customWaterTargetMl ?: (userWeight * 35).toInt()
    val totalWaterConsumed = selectedRegistroDiario?.aguaMl ?: remember(waterRecords, selectedDateStr) {
        waterRecords.filter { item ->
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val itemDateStr = sdf.format(java.util.Date(item.date))
            itemDateStr == selectedDateStr
        }.sumOf { it.amountMl }
    }
    val waterProgress = if (targetWaterMl > 0) (totalWaterConsumed.toFloat() / targetWaterMl.toFloat()).coerceIn(0f, 1f) else 0f

    // Dialog state for adding food & managing items
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var showMealPhotoDialog by remember { mutableStateOf(false) }
    var selectedMealForPhoto by remember { mutableStateOf("Almoço") }
    var selectedMealForAdd by remember { mutableStateOf("") }
    var showEditWaterTargetDialog by remember { mutableStateOf(false) }
    var showEditTodayWaterDialog by remember { mutableStateOf(false) }
    var showWaterHistoryDialog by remember { mutableStateOf(false) }
    var showAddSupplementDialog by remember { mutableStateOf(false) }

    // Bottom Sheet & Edit/Move states
    var selectedConsumoForMenu by remember { mutableStateOf<ConsumoDiario?>(null) }
    var itemToEdit by remember { mutableStateOf<ConsumoDiario?>(null) }
    var itemToMove by remember { mutableStateOf<ConsumoDiario?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- TOP HEADER WITH GROWTH-STYLE DATE CAROUSEL ---
        item {
            GrowthDateCarousel(
                selectedDateStr = selectedDateStr,
                onDateSelected = { newDate ->
                    viewModel.setSelectedDate(newDate)
                }
            )
        }

        // --- SUB-TABS SELECTOR ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Diário & IA", "Métricas & Gráficos").forEachIndexed { index, title ->
                    val active = selectedSubTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { selectedSubTab = index }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            // --- SUB-TAB 0: DIÁRIO DE CONSUMO & HIDRATAÇÃO ---



            // SUPLEMENTAÇÃO SECTION
            item {
                SupplementSection(
                    viewModel = viewModel,
                    onAddSupplementClick = { showAddSupplementDialog = true }
                )
            }

            item {
                Text(
                    text = "Diário de Refeições & Sugestões IA",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // THE 5 MEAL CARDS (Café da manhã, Almoço, Pré Treino, Pós Treino, Jantar)
            val mealCategories = listOf("Café da manhã", "Almoço", "Pré Treino", "Pós Treino", "Jantar")
            items(mealCategories) { mealName ->
                val aiMealRecommendation = meals.find { it.name.equals(mealName, ignoreCase = true) }
                    ?: getFallbackMealSuggestion(mealName, userProfile?.objective)
                val mealConsumoList = consumoList.filter { it.mealName.equals(mealName, ignoreCase = true) }

                MealDetailTrackerCard(
                    mealName = mealName,
                    aiRecommendation = aiMealRecommendation,
                    consumoItems = mealConsumoList,
                    viewModel = viewModel,
                    onAddFoodClick = {
                        selectedMealForAdd = mealName
                        showAddFoodDialog = true
                    },
                    onPhotoSnapClick = {
                        selectedMealForPhoto = mealName
                        showMealPhotoDialog = true
                    },
                    onItemOptionsClick = { selectedConsumoForMenu = it }
                )
            }
        } else if (selectedSubTab == 1) {
            // --- SUB-TAB 1: MÉTRICAS & GRÁFICOS (Requirement 3) ---

            // PAINEL DE CONSUMO REAL (METAS DIÁRIAS)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CONSUMO REAL DIÁRIO",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Calories progress row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$totalConsumedCalories kcal",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Consumido",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "/ $targetCalories",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "Meta kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$remainingCalories kcal",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (remainingCalories > 0) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                                    )
                                )
                                Text(
                                    text = "Restante",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Macronutrients linear bars
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Carbs bar
                            MacroProgressBar(
                                label = "Carboidratos",
                                current = totalConsumedCarbs,
                                target = targetCarbs,
                                color = Color(0xFFFFA726)
                            )
                            // Protein bar
                            MacroProgressBar(
                                label = "Proteínas",
                                current = totalConsumedProtein,
                                target = targetProtein,
                                color = Color(0xFFEF5350)
                            )
                            // Fat bar
                            MacroProgressBar(
                                label = "Gorduras",
                                current = totalConsumedFat,
                                target = targetFat,
                                color = Color(0xFF26A69A)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                selectedMealForAdd = ""
                                showAddFoodDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_adicionar_alimento")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Adicionar Alimento",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adicionar Alimento",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // BANNER: FOTOGRAFAR PRATO & ESTIMAR CALORIAS
            item {
                SnapMealPhotoHeaderBanner(
                    onClick = {
                        selectedMealForPhoto = "Almoço"
                        showMealPhotoDialog = true
                    }
                )
            }

            // CONTADOR DE ÁGUA CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // TOP BLOCK: Header, Progress, Target Details (Full Width)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalDrink,
                                            contentDescription = "Água",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = "CONSUMO DE ÁGUA",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = { showEditTodayWaterDialog = true },
                                        modifier = Modifier.size(28.dp).testTag("edit_today_water_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar consumo de hoje",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showWaterHistoryDialog = true },
                                        modifier = Modifier.size(28.dp).testTag("water_history_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Histórico de água",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            LinearProgressIndicator(
                                progress = { waterProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showEditWaterTargetDialog = true }
                                    .testTag("edit_water_target_trigger")
                            ) {
                                Text(
                                    text = if (viewModel.customWaterTargetMl != null) 
                                        "$totalWaterConsumed ml de $targetWaterMl ml (Personalizado)" 
                                        else "$totalWaterConsumed ml de $targetWaterMl ml (Meta: 35ml × ${userWeight.toInt()}kg)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Editar meta de água",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // BOTTOM ROW: Quick Water Addition Buttons (+250ml, +500ml, custom)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick +250ml Button
                            Button(
                                onClick = { viewModel.addWater(250) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("quick_water_250_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalDrink,
                                    contentDescription = "+250ml",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+250ml",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick +500ml Button
                            Button(
                                onClick = { viewModel.addWater(500) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("quick_water_500_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalDrink,
                                    contentDescription = "+500ml",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+500ml",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            var dropdownExpanded by remember { mutableStateOf(false) }

                            // Custom amount selector button
                            Box {
                                OutlinedButton(
                                    onClick = { dropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("water_increment_selector")
                                ) {
                                    Text(
                                        text = "+${viewModel.customWaterIncrementMl}ml",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Selecionar quantidade de água",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    listOf(150, 200, 250, 300, 350, 400, 500, 750, 1000).forEach { amount ->
                                        DropdownMenuItem(
                                            text = { Text("+ ${amount}ml", fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                viewModel.updateCustomWaterIncrement(amount)
                                                viewModel.addWater(amount)
                                                dropdownExpanded = false
                                            },
                                            modifier = Modifier.testTag("water_increment_option_$amount")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CARD 1: DONUT DE MACRONUTRIENTES
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DIVISÃO DE MACROS CONSUMIDOS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val hasMacros = (totalConsumedCarbs + totalConsumedProtein + totalConsumedFat) > 0.0

                        // Canvas donut drawing
                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(140.dp)) {
                                val radius = size.width / 2
                                val strokeWidthValue = 20.dp.toPx()

                                if (!hasMacros) {
                                    // Balanced empty state
                                    drawArc(
                                        color = Color.LightGray.copy(alpha = 0.3f),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthValue)
                                    )
                                } else {
                                    val totalGrams = (totalConsumedCarbs + totalConsumedProtein + totalConsumedFat).coerceAtLeast(1.0)
                                    val carbAngle = 360f * (totalConsumedCarbs / totalGrams).toFloat()
                                    val proteinAngle = 360f * (totalConsumedProtein / totalGrams).toFloat()
                                    val fatAngle = 360f * (totalConsumedFat / totalGrams).toFloat()

                                    // Carbs arc (Orange)
                                    drawArc(
                                        color = Color(0xFFFFA726),
                                        startAngle = -90f,
                                        sweepAngle = carbAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthValue, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                    // Protein arc (Red)
                                    drawArc(
                                        color = Color(0xFFEF5350),
                                        startAngle = -90f + carbAngle,
                                        sweepAngle = proteinAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthValue, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                    // Fat arc (Teal)
                                    drawArc(
                                        color = Color(0xFF26A69A),
                                        startAngle = -90f + carbAngle + proteinAngle,
                                        sweepAngle = fatAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthValue, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (hasMacros) "${totalConsumedCalories.toInt()}" else "0",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "kcal totais",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Beautiful Legend row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val totalGrams = (totalConsumedCarbs + totalConsumedProtein + totalConsumedFat).coerceAtLeast(1.0)
                            val pCarb = if (hasMacros) (totalConsumedCarbs / totalGrams * 100).toInt() else 45
                            val pProt = if (hasMacros) (totalConsumedProtein / totalGrams * 100).toInt() else 35
                            val pFat = if (hasMacros) (totalConsumedFat / totalGrams * 100).toInt() else 20

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFA726)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Carb", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${totalConsumedCarbs.toInt()}g ($pCarb%)", fontSize = 11.sp, color = Color(0xFFFFA726), fontWeight = FontWeight.Bold)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF5350)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Prot", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${totalConsumedProtein.toInt()}g ($pProt%)", fontSize = 11.sp, color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF26A69A)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gord", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${totalConsumedFat.toInt()}g ($pFat%)", fontSize = 11.sp, color = Color(0xFF26A69A), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!hasMacros) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum alimento cadastrado hoje. Exibindo proporções sugeridas de base.",
                                fontSize = 11.sp,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // CARD 2: GRÁFICO HISTÓRICO DE ÁGUA (Últimos 5 dias)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "HISTÓRICO DE HIDRATAÇÃO (ML/DIA)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Acompanhamento do volume ingerido nos últimos 5 dias.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val waterHistory = remember(selectedDateStr, allRegistrosDiarios, waterRecords) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val refDate = try {
                                sdf.parse(selectedDateStr) ?: java.util.Date()
                            } catch (e: Exception) {
                                java.util.Date()
                            }

                            val dayMillis = 24 * 60 * 60 * 1000L
                            (0..4).map { daysAgo ->
                                val targetCal = java.util.Calendar.getInstance().apply {
                                    time = refDate
                                    add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
                                }
                                val targetDateStr = sdf.format(targetCal.time)

                                val reg = allRegistrosDiarios.find { it.dateStr == targetDateStr }
                                val dailySum = reg?.aguaMl ?: run {
                                    val startOfDay = targetCal.apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
                                    val endOfDay = startOfDay + dayMillis
                                    waterRecords.filter { it.date in startOfDay until endOfDay }.sumOf { it.amountMl }
                                }

                                val label = when (daysAgo) {
                                    0 -> if (targetDateStr == viewModel.repository.getTodayDateStr()) "Hoje" else {
                                        java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(targetCal.time)
                                    }
                                    1 -> "Ontem"
                                    else -> {
                                        when (targetCal.get(java.util.Calendar.DAY_OF_WEEK)) {
                                            java.util.Calendar.SUNDAY -> "Dom"
                                            java.util.Calendar.MONDAY -> "Seg"
                                            java.util.Calendar.TUESDAY -> "Ter"
                                            java.util.Calendar.WEDNESDAY -> "Qua"
                                            java.util.Calendar.THURSDAY -> "Qui"
                                            java.util.Calendar.FRIDAY -> "Sex"
                                            java.util.Calendar.SATURDAY -> "Sáb"
                                            else -> "Dia"
                                        }
                                    }
                                }
                                label to dailySum
                            }.reversed()
                        }

                        val highestWater = waterHistory.maxOfOrNull { it.second } ?: 0
                        val maxWater = maxOf(highestWater, targetWaterMl, 1000)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            waterHistory.forEach { (label, amt) ->
                                val pct = (amt.toFloat() / maxWater.toFloat()).coerceIn(0f, 1f)
                                val barHeight = (100.dp * pct).coerceIn(4.dp, 100.dp)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (amt >= 1000) String.format(java.util.Locale.US, "%.1fL", amt / 1000f) else "${amt}ml",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (amt >= targetWaterMl) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(26.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(26.dp)
                                                .height(barHeight)
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(
                                                    if (amt >= targetWaterMl) Color(0xFF2196F3)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // HISTÓRICO DE ALIMENTOS CONSUMIDOS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "HISTÓRICO DE ALIMENTOS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toque na lixeira para excluir e recalcular o consumo diário.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (consumoList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum alimento cadastrado hoje.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                consumoList.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (item.mealName.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = item.mealName,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${item.calories} kcal • Carb: ${item.carbs.toInt()}g • Prot: ${item.protein.toInt()}g • Gord: ${item.fat.toInt()}g",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteConsumoDiario(item.id) },
                                            modifier = Modifier.testTag("delete_btn_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remover",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedConsumoForMenu?.let { item ->
        ActionBottomSheet(
            item = item,
            onDismiss = { selectedConsumoForMenu = null },
            onEdit = {
                itemToEdit = item
            },
            onMove = {
                itemToMove = item
            },
            onCopy = {
                viewModel.addConsumoDiario(
                    name = "${item.name} (Cópia)",
                    calories = item.calories,
                    carbs = item.carbs,
                    protein = item.protein,
                    fat = item.fat,
                    mealName = item.mealName
                )
                android.widget.Toast.makeText(context, "'${item.name}' copiado!", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteConsumoDiario(item.id)
                android.widget.Toast.makeText(context, "'${item.name}' removido!", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    itemToEdit?.let { item ->
        EditFoodPortionDialog(
            initialItem = item,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                viewModel.updateConsumoDiario(updatedItem)
                itemToEdit = null
                android.widget.Toast.makeText(context, "Valores de '${updatedItem.name}' atualizados!", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    itemToMove?.let { item ->
        MoveFoodDialog(
            item = item,
            onDismiss = { itemToMove = null },
            onMove = { newMeal ->
                val updated = item.copy(mealName = newMeal)
                viewModel.updateConsumoDiario(updated)
                itemToMove = null
                android.widget.Toast.makeText(context, "'${item.name}' movido para $newMeal!", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddFoodDialog) {
        AddFoodDialog(
            viewModel = viewModel,
            mealName = selectedMealForAdd,
            onDismiss = { showAddFoodDialog = false },
            onFoodSelected = { foodName, calories, carbs, protein, fat, chosenMeal ->
                viewModel.addConsumoDiario(foodName, calories, carbs, protein, fat, chosenMeal)
                showAddFoodDialog = false
            }
        )
    }

    if (showMealPhotoDialog) {
        MealPhotoCalorieEstimationDialog(
            initialMealName = selectedMealForPhoto,
            viewModel = viewModel,
            onDismiss = { showMealPhotoDialog = false },
            onSaved = {
                android.widget.Toast.makeText(context, "Refeição adicionada ao diário!", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEditWaterTargetDialog) {
        var tempWaterTarget by remember { mutableStateOf(targetWaterMl.toString()) }
        AlertDialog(
            onDismissRequest = { showEditWaterTargetDialog = false },
            title = {
                Text(
                    text = "Personalizar Meta de Água",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Digite sua meta diária de consumo de água em mililitros (ml).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempWaterTarget,
                        onValueChange = { tempWaterTarget = it.filter { char -> char.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("custom_water_target_input"),
                        singleLine = true,
                        placeholder = { Text("Ex: 3500") },
                        suffix = { Text("ml") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTarget = tempWaterTarget.toIntOrNull()
                        viewModel.updateCustomWaterTarget(newTarget)
                        showEditWaterTargetDialog = false
                    },
                    modifier = Modifier.testTag("custom_water_target_save")
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.updateCustomWaterTarget(null) // Restore biometric default
                            showEditWaterTargetDialog = false
                        },
                        modifier = Modifier.testTag("custom_water_target_reset")
                    ) {
                        Text("Restaurar Padrão", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { showEditWaterTargetDialog = false },
                        modifier = Modifier.testTag("custom_water_target_cancel")
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    if (showEditTodayWaterDialog) {
        EditTodayWaterDialog(
            initialAmount = totalWaterConsumed,
            onDismiss = { showEditTodayWaterDialog = false },
            onSave = { newAmount -> viewModel.setTodayWaterAmount(newAmount) }
        )
    }

    if (showWaterHistoryDialog) {
        WaterHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showWaterHistoryDialog = false }
        )
    }

    if (showAddSupplementDialog) {
        val context = LocalContext.current
        AddSupplementDialog(
            onDismiss = { showAddSupplementDialog = false },
            onSave = { nome, dose, horario ->
                viewModel.saveSuplemento(
                    context = context,
                    suplemento = Suplemento(
                        nome = nome,
                        dose = dose,
                        horario = horario
                    )
                )
            }
        )
    }
}

data class IdentifiedFood(
    val name: String,
    val calories: Int,
    val carbs: Double,
    val protein: Double,
    val fat: Double,
    val emoji: String
)

val mockIdentifiedFoods = listOf(
    IdentifiedFood("Arroz, feijão e frango grelhado", 450, 50.0, 40.0, 10.0, "🍛"),
    IdentifiedFood("Tapioca recheada com ovo e queijo", 320, 35.0, 14.0, 12.0, "🌮"),
    IdentifiedFood("Salmão grelhado com purê de batata e brócolis", 520, 40.0, 38.0, 18.0, "🍣"),
    IdentifiedFood("Salada Caesar de frango com croutons", 290, 15.0, 25.0, 14.0, "🥗")
)

@Composable
fun NutritionMetricsView(
    totalCalories: Int,
    targetCalories: Int,
    totalCarbs: Double,
    targetCarbs: Double,
    totalProtein: Double,
    targetProtein: Double,
    totalFat: Double,
    targetFat: Double
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Métricas e Balanço Nutricional",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gráfico comparativo de consumo em relação à sua meta de macros.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Calories Progress Card
            val progressPct = if (targetCalories > 0) (totalCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f) else 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CALORIAS TOTAIS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalCalories / $targetCalories kcal",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progressPct },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${(progressPct * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DISTRIBUIÇÃO DE MACRONUTRIENTES",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Carbs
            MacroMetricBar(label = "Carboidratos", current = totalCarbs, target = targetCarbs, unit = "g", color = Color(0xFF4CAF50))
            Spacer(modifier = Modifier.height(12.dp))

            // Protein
            MacroMetricBar(label = "Proteínas", current = totalProtein, target = targetProtein, unit = "g", color = Color(0xFFFF9800))
            Spacer(modifier = Modifier.height(12.dp))

            // Fat
            MacroMetricBar(label = "Gorduras", current = totalFat, target = targetFat, unit = "g", color = Color(0xFFE91E63))
        }
    }
}

@Composable
fun MacroMetricBar(
    label: String,
    current: Double,
    target: Double,
    unit: String,
    color: Color
) {
    val pct = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = "${current.toInt()}${unit} / ${target.toInt()}${unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionCameraScannerView(viewModel: FitViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var descriptionText by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var plateAnalysisResult by remember { mutableStateOf<com.example.data.api.PlateAnalysisResultJson?>(null) }
    var showConfirmationModal by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                descriptionText = if (descriptionText.isBlank()) spokenText else "$descriptionText $spokenText"
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scanner de Prato IA (Multimodal)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(
                text = "Tire uma foto do prato e descreva os ingredientes para uma estimativa precisa por IA.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasCameraPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "O FitAI precisa de acesso à câmera para analisar o seu prato.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            try {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Erro ao solicitar permissão: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Dar Permissão", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (showSuccessMessage) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Prato Confirmado e Salvo!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Os alimentos foram salvos com sucesso no seu Diário de Consumo de hoje.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showSuccessMessage = false
                            capturedBitmap = null
                            descriptionText = ""
                            plateAnalysisResult = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Escanear Outro Prato")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto capturada do prato",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Box(modifier = Modifier.size(16.dp).align(Alignment.TopStart).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp)).background(Color.Transparent))
                        Box(modifier = Modifier.size(16.dp).align(Alignment.TopEnd).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 4.dp)).background(Color.Transparent))
                        Box(modifier = Modifier.size(16.dp).align(Alignment.BottomStart).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 4.dp)).background(Color.Transparent))
                        Box(modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(bottomEnd = 4.dp)).background(Color.Transparent))
                    }

                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.02f)
                                .align(Alignment.TopCenter)
                                .offset(y = (220 * laserOffset).dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Cyan.copy(alpha = 0.2f), Color.Cyan, Color.Cyan.copy(alpha = 0.2f))
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.Cyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "IA analisando foto e descrição do prato...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (capturedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            IconButton(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tirar outra foto", tint = Color.White)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.clickable {
                                try {
                                    cameraLauncher.launch(null)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Erro ao abrir câmera: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera viewfinder",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TOQUE PARA ABRIR CÂMERA",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                cameraLauncher.launch(null)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Erro ao abrir câmera: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (capturedBitmap != null) "Refazer Foto" else "Tirar Foto", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DESCRIÇÃO COMPLEMENTAR (OPCIONAL):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    placeholder = {
                        Text(
                            text = "Descreva o prato ou ingredientes (Ex: Arroz, feijão, 150g de frango grelhado)...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Fale os ingredientes do prato...")
                                    }
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Recurso de voz não disponível no dispositivo", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("voice_input_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Ditar por Voz",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plate_description_text_field"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (capturedBitmap == null && descriptionText.isBlank()) {
                            android.widget.Toast.makeText(context, "Tire uma foto ou descreva o prato para analisar.", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isScanning = true
                        coroutineScope.launch {
                            try {
                                val result = viewModel.analyzePlateImage(capturedBitmap, descriptionText)
                                plateAnalysisResult = result
                                showConfirmationModal = true
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Erro na análise: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                isScanning = false
                            }
                        }
                    },
                    enabled = !isScanning && (capturedBitmap != null || descriptionText.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("analyze_plate_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analisando com IA...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analisar Prato com IA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConfirmationModal) {
        plateAnalysisResult?.let { plateResult ->
            PlateConfirmationDialog(
                initialResult = plateResult,
                onDismiss = { showConfirmationModal = false },
                onConfirmAndSave = { chosenMeal, finalItems ->
                    val dateTimestamp = System.currentTimeMillis()
                    val consumoList = finalItems.map { item ->
                        ConsumoDiario(
                            name = "${item.name} (${item.quantityGrams.toInt()}g)",
                            calories = item.calories,
                            carbs = item.carbs,
                            protein = item.protein,
                            fat = item.fat,
                            mealName = chosenMeal,
                            date = dateTimestamp
                        )
                    }
                    viewModel.addConsumoDiarioList(consumoList)
                    showConfirmationModal = false
                    showSuccessMessage = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateConfirmationDialog(
    initialResult: com.example.data.api.PlateAnalysisResultJson,
    onDismiss: () -> Unit,
    onConfirmAndSave: (selectedMeal: String, items: List<com.example.data.api.FoodComponentJson>) -> Unit
) {
    var dishName by remember { mutableStateOf(initialResult.dishName) }
    var selectedMeal by remember { mutableStateOf("Almoço") }
    
    val itemsState = remember {
        mutableStateListOf<com.example.data.api.FoodComponentJson>().apply {
            addAll(initialResult.items)
        }
    }

    val basePerGramRatios = remember {
        initialResult.items.associate { item ->
            val grams = if (item.quantityGrams > 0) item.quantityGrams else 100.0
            item.name to BaseNutrientRatio(
                caloriesPerGram = item.calories / grams,
                proteinPerGram = item.protein / grams,
                carbsPerGram = item.carbs / grams,
                fatPerGram = item.fat / grams,
                fiberPerGram = item.fiber / grams
            )
        }.toMutableMap()
    }

    val totalCalories = itemsState.sumOf { it.calories }
    val totalProtein = itemsState.sumOf { it.protein }
    val totalCarbs = itemsState.sumOf { it.carbs }
    val totalFat = itemsState.sumOf { it.fat }
    val totalFiber = itemsState.sumOf { it.fiber }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Confirmação do Prato",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dishName,
                    onValueChange = { dishName = it },
                    label = { Text("Nome da Refeição") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "VINCULAR À REFEIÇÃO:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                val mealOptions = listOf("Café da manhã", "Almoço", "Lanche da Tarde", "Jantar", "Ceia")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mealOptions.forEach { m ->
                        val isSelected = selectedMeal == m
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMeal = m },
                            label = { Text(m, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALIMENTOS IDENTIFICADOS (${itemsState.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    TextButton(
                        onClick = {
                            val newName = "Item Adicional ${itemsState.size + 1}"
                            val newGrams = 100.0
                            val newItem = com.example.data.api.FoodComponentJson(
                                name = newName,
                                quantityGrams = newGrams,
                                calories = 120,
                                protein = 10.0,
                                carbs = 15.0,
                                fat = 2.0,
                                fiber = 1.0
                            )
                            basePerGramRatios[newName] = BaseNutrientRatio(
                                caloriesPerGram = 120.0 / 100.0,
                                proteinPerGram = 10.0 / 100.0,
                                carbsPerGram = 15.0 / 100.0,
                                fatPerGram = 2.0 / 100.0,
                                fiberPerGram = 1.0 / 100.0
                            )
                            itemsState.add(newItem)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Alimento", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                itemsState.forEachIndexed { index, item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { itemsState.removeAt(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quantidade (g):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val currentGrams = item.quantityGrams
                                            val newGrams = (currentGrams - 10.0).coerceAtLeast(10.0)
                                            val ratio = basePerGramRatios[item.name] ?: BaseNutrientRatio(
                                                item.calories / currentGrams, item.protein / currentGrams,
                                                item.carbs / currentGrams, item.fat / currentGrams, item.fiber / currentGrams
                                            )
                                            itemsState[index] = item.copy(
                                                quantityGrams = newGrams,
                                                calories = (ratio.caloriesPerGram * newGrams).toInt(),
                                                protein = ratio.proteinPerGram * newGrams,
                                                carbs = ratio.carbsPerGram * newGrams,
                                                fat = ratio.fatPerGram * newGrams,
                                                fiber = ratio.fiberPerGram * newGrams
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Diminuir 10g", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    Text(
                                        text = "${item.quantityGrams.toInt()}g",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val currentGrams = item.quantityGrams
                                            val newGrams = currentGrams + 10.0
                                            val ratio = basePerGramRatios[item.name] ?: BaseNutrientRatio(
                                                item.calories / currentGrams, item.protein / currentGrams,
                                                item.carbs / currentGrams, item.fat / currentGrams, item.fiber / currentGrams
                                            )
                                            itemsState[index] = item.copy(
                                                quantityGrams = newGrams,
                                                calories = (ratio.caloriesPerGram * newGrams).toInt(),
                                                protein = ratio.proteinPerGram * newGrams,
                                                carbs = ratio.carbsPerGram * newGrams,
                                                fat = ratio.fatPerGram * newGrams,
                                                fiber = ratio.fiberPerGram * newGrams
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Aumentar 10g", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.calories} kcal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("P: ${item.protein.toInt()}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("C: ${item.carbs.toInt()}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("G: ${item.fat.toInt()}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("F: ${item.fiber.toInt()}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "TOTAL ESTIMADO DA REFEIÇÃO:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InfoBlockNutrient(label = "Calorias", value = "$totalCalories kcal")
                            InfoBlockNutrient(label = "Proteínas", value = "${totalProtein.toInt()}g")
                            InfoBlockNutrient(label = "Carbos", value = "${totalCarbs.toInt()}g")
                            InfoBlockNutrient(label = "Gorduras", value = "${totalFat.toInt()}g")
                            InfoBlockNutrient(label = "Fibras", value = "${totalFiber.toInt()}g")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (itemsState.isEmpty()) {
                                return@Button
                            }
                            onConfirmAndSave(selectedMeal, itemsState.toList())
                        },
                        enabled = itemsState.isNotEmpty(),
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirmar e Salvar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class BaseNutrientRatio(
    val caloriesPerGram: Double,
    val proteinPerGram: Double,
    val carbsPerGram: Double,
    val fatPerGram: Double,
    val fiberPerGram: Double
)

@Composable
fun InfoBlockNutrient(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MacroProgressBar(
    label: String,
    current: Double,
    target: Double,
    color: Color
) {
    val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "${current.toInt()}g / ${target.toInt()}g",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

fun getFallbackMealSuggestion(mealName: String, objective: String?): Meal {
    val obj = objective ?: "Hipertrofia"
    return when (mealName) {
        "Pré Treino" -> {
            if (obj.contains("Emagrecimento", ignoreCase = true)) {
                Meal(
                    name = "Pré Treino",
                    description = "1 Banana prata amassada com 15g de aveia em flocos e canela em pó para energia rápida.",
                    calories = 150,
                    protein = "3g",
                    carbs = "30g",
                    fat = "1.5g"
                )
            } else {
                Meal(
                    name = "Pré Treino",
                    description = "150g de Batata doce cozida com 100g de peito de frango grelhado desfiado.",
                    calories = 350,
                    protein = "31g",
                    carbs = "50g",
                    fat = "4g"
                )
            }
        }
        "Pós Treino" -> {
            if (obj.contains("Emagrecimento", ignoreCase = true)) {
                Meal(
                    name = "Pós Treino",
                    description = "30g de Whey Protein concentrado diluído em água com 100g de morangos frescos.",
                    calories = 150,
                    protein = "25g",
                    carbs = "8g",
                    fat = "1.5g"
                )
            } else {
                Meal(
                    name = "Pós Treino",
                    description = "30g de Whey Protein, 40g de aveia em flocos e 1 banana média batidos com água.",
                    calories = 330,
                    protein = "28g",
                    carbs = "45g",
                    fat = "3g"
                )
            }
        }
        "Café da manhã" -> {
            Meal(
                name = "Café da manhã",
                description = "3 Ovos mexidos, 1 fatia de pão integral tostada e 1 xícara de melão picado com café puro.",
                calories = 310,
                protein = "22g",
                carbs = "25g",
                fat = "12g"
            )
        }
        "Almoço" -> {
            Meal(
                name = "Almoço",
                description = "150g de Filé de frango grelhado, 150g de arroz integral, brócolis cozido e salada à vontade.",
                calories = 450,
                protein = "38g",
                carbs = "45g",
                fat = "10g"
            )
        }
        "Jantar" -> {
            Meal(
                name = "Jantar",
                description = "140g de Peito de frango desfiado, 100g de batata doce cozida e mix de folhas verdes.",
                calories = 380,
                protein = "32g",
                carbs = "30g",
                fat = "8g"
            )
        }
        else -> {
            Meal(
                name = mealName,
                description = "Opção saudável: Salada verde com 100g de peito de frango grelhado e 1 colher de azeite.",
                calories = 250,
                protein = "25g",
                carbs = "5g",
                fat = "14g"
            )
        }
    }
}

@Composable
fun MacroBadge(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun MealDetailTrackerCard(
    mealName: String,
    aiRecommendation: Meal?,
    consumoItems: List<ConsumoDiario>,
    viewModel: FitViewModel,
    onAddFoodClick: () -> Unit,
    onPhotoSnapClick: () -> Unit = {},
    onItemOptionsClick: (ConsumoDiario) -> Unit
) {
    val mealCalories = consumoItems.sumOf { it.calories }
    val mealProt = consumoItems.sumOf { it.protein }
    val mealCarbs = consumoItems.sumOf { it.carbs }
    val mealFat = consumoItems.sumOf { it.fat }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (mealName) {
                            "Café da manhã" -> Icons.Default.Coffee
                            "Almoço" -> Icons.Default.Restaurant
                            "Pré Treino" -> Icons.Default.Bolt
                            "Pós Treino" -> Icons.Default.FitnessCenter
                            else -> Icons.Default.RestaurantMenu
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = mealName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (consumoItems.isNotEmpty()) {
                            Text(
                                text = "$mealCalories kcal • P: ${mealProt.toInt()}g • C: ${mealCarbs.toInt()}g • G: ${mealFat.toInt()}g",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                val context = androidx.compose.ui.platform.LocalContext.current

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPhotoSnapClick,
                        modifier = Modifier
                            .testTag("snap_photo_btn_")
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Foto com IA",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Foto IA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // "Copiar de Ontem" shortcut button (Requirement 1)
                    OutlinedButton(
                        onClick = {
                            viewModel.copyMealFromYesterday(mealName) { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .testTag("copy_yesterday_btn_$mealName")
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar de Ontem",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copiar de Ontem",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onAddFoodClick,
                        modifier = Modifier.testTag("add_food_btn_$mealName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Adicionar Alimento",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Recommendation Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUGESTÃO INTELIGENTE DA IA",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (aiRecommendation != null) {
                            Text(
                                text = "${aiRecommendation.calories} kcal",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (aiRecommendation != null) {
                        Text(
                            text = aiRecommendation.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("P: ${aiRecommendation.protein}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Text("C: ${aiRecommendation.carbs}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Text("G: ${aiRecommendation.fat}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val isLoadingAlternative = viewModel.loadingMealsState[aiRecommendation.id] ?: false
                        OutlinedButton(
                            onClick = { viewModel.suggestAlternativeMeal(aiRecommendation) },
                            enabled = !isLoadingAlternative,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            if (isLoadingAlternative) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Sugerir alternativa com IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Sem sugestão disponível. Toque em 'Gerar Treino' no onboarding ou crie um perfil para que a IA monte sua dieta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CONSUMED FOODS IN THIS MEAL
            Text(
                text = "ALIMENTOS NA REFEIÇÃO (${consumoItems.size})",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (consumoItems.isEmpty()) {
                Text(
                    text = "Nenhum alimento cadastrado nesta refeição. Toque em + para adicionar.",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    consumoItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.calories} kcal • P: ${item.protein.toInt()}g • C: ${item.carbs.toInt()}g • G: ${item.fat.toInt()}g",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            IconButton(
                                onClick = { onItemOptionsClick(item) },
                                modifier = Modifier.size(32.dp).testTag("food_item_options_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opções do alimento",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    item: ConsumoDiario,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "${item.mealName} • ${item.calories} kcal • P:${item.protein.toInt()}g C:${item.carbs.toInt()}g G:${item.fat.toInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0C0)
                )
            }

            Divider(color = Color(0xFF2D2D40))

            // Option 1: Editar alimento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        onEdit()
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color(0xFF00E676))
                Text("Editar alimento", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }

            // Option 2: Mover alimento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        onMove()
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Mover", tint = Color(0xFF4FC3F7))
                Text("Mover alimento", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }

            // Option 3: Copiar alimento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        onCopy()
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFFFFB74D))
                Text("Copiar alimento", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }

            // Option 4: Remover alimento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        onDelete()
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color(0xFFFF5252))
                Text("Remover alimento", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFFF5252))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun EditFoodPortionDialog(
    initialItem: ConsumoDiario,
    onDismiss: () -> Unit,
    onSave: (ConsumoDiario) -> Unit
) {
    var portionText by remember { mutableStateOf("100") }
    val grams = portionText.toDoubleOrNull() ?: 100.0

    val baseCals = initialItem.calories
    val baseProt = initialItem.protein
    val baseCarbs = initialItem.carbs
    val baseFat = initialItem.fat

    val calculatedCals = ((baseCals * grams) / 100.0).toInt()
    val calculatedProt = (baseProt * grams) / 100.0
    val calculatedCarbs = (baseCarbs * grams) / 100.0
    val calculatedFat = (baseFat * grams) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edição Dinâmica de Macros",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = initialItem.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Ajuste a porção em gramas (g). Os macronutrientes serão recalculados em tempo real:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick portion buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 100, 150, 200, 250).forEach { p ->
                        val isSelected = portionText == p.toString()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { portionText = p.toString() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${p}g",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Fine-tuning text field
                OutlinedTextField(
                    value = portionText,
                    onValueChange = { input -> portionText = input.filter { it.isDigit() } },
                    label = { Text("Porção exata (gramas)") },
                    suffix = { Text("g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_food_portion_input")
                )

                // Dynamic Macros display cards
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calorias recalculadas:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("$calculatedCals kcal", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MacroBadge("Proteínas", String.format(java.util.Locale.getDefault(), "%.1fg", calculatedProt), Color(0xFFEF5350))
                            MacroBadge("Carboidratos", String.format(java.util.Locale.getDefault(), "%.1fg", calculatedCarbs), Color(0xFFFFA726))
                            MacroBadge("Gorduras", String.format(java.util.Locale.getDefault(), "%.1fg", calculatedFat), Color(0xFF26A69A))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = initialItem.copy(
                        calories = calculatedCals,
                        protein = calculatedProt,
                        carbs = calculatedCarbs,
                        fat = calculatedFat
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_food_portion_btn")
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun MoveFoodDialog(
    item: ConsumoDiario,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var selectedMeal by remember { mutableStateOf(item.mealName) }
    val mealCategories = listOf("Café da manhã", "Almoço", "Pré Treino", "Pós Treino", "Jantar")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover Alimento", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selecione a refeição para onde deseja mover '${item.name}':", fontSize = 13.sp)
                mealCategories.forEach { cat ->
                    val isSelected = cat == selectedMeal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { selectedMeal = cat }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedMeal = cat })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onMove(selectedMeal) }) {
                Text("Mover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

data class FoodSelectionCartState(
    val food: LocalFoodItem,
    var portionGrams: Int,
    var quantity: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    viewModel: FitViewModel,
    mealName: String,
    onDismiss: () -> Unit,
    onFoodSelected: (String, Int, Double, Double, Double, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilterTab by remember { mutableStateOf(0) } // 0: Alimentos, 1: Recentes, 2: Favoritos, 3: Código de Barras
    var chosenMeal by remember { mutableStateOf(if (mealName.isNotBlank()) mealName else "Almoço") }

    // Barcode state
    var barcodeQuery by remember { mutableStateOf("") }
    var barcodeProductResult by remember { mutableStateOf<LocalFoodItem?>(null) }
    var isSearchingBarcode by remember { mutableStateOf(false) }
    var showCameraBarcodeScanner by remember { mutableStateOf(false) }

    // Multi-selection cart state
    val selectedFoods = remember { mutableStateMapOf<String, FoodSelectionCartState>() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val tacoFoods = remember(context) { com.example.data.repository.FoodSearchRepository.getTacoFoods(context) }
    val openFoodFactsResults by viewModel.openFoodFactsResults.collectAsStateWithLifecycle()
    val isSearchingApi by viewModel.isSearchingApi.collectAsStateWithLifecycle()
    val favoriteFoods by viewModel.favoriteFoods.collectAsStateWithLifecycle()
    val recentFoods by viewModel.recentFoods.collectAsStateWithLifecycle()
    val consumoList by viewModel.allConsumoDiario.collectAsStateWithLifecycle()

    // Trigger API search when query changes
    LaunchedEffect(searchQuery) {
        viewModel.searchOpenFoodFacts(searchQuery)
    }

    // Combined Foods list
    val currentTabFoods = remember(activeFilterTab, searchQuery, tacoFoods.size, openFoodFactsResults, favoriteFoods, recentFoods, consumoList.size) {
        val baseList: List<LocalFoodItem> = when (activeFilterTab) {
            1 -> {
                // Recentes
                if (recentFoods.isNotEmpty()) {
                    recentFoods
                } else {
                    val recentNames = consumoList.map { it.name.substringBefore(" (") }.distinct()
                    tacoFoods.filter { item -> recentNames.any { it.equals(item.name, ignoreCase = true) } }
                }
            }
            2 -> {
                // Favoritos
                favoriteFoods
            }
            else -> {
                // 0: Alimentos (TACO + API)
                if (searchQuery.isBlank()) {
                    tacoFoods
                } else {
                    val tacoFiltered = tacoFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    val apiFiltered = openFoodFactsResults.filter { apiItem ->
                        tacoFiltered.none { it.name.equals(apiItem.name, ignoreCase = true) }
                    }
                    tacoFiltered + apiFiltered
                }
            }
        }

        if (searchQuery.isNotBlank() && activeFilterTab != 0) {
            baseList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            baseList
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Buscar & Cadastrar Alimentos",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Base TACO + Open Food Facts API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Target meal selection chips
                Text(
                    text = "Refeição destino:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mealCategories = listOf("Café da manhã", "Almoço", "Pré Treino", "Pós Treino", "Jantar")
                    mealCategories.forEach { cat ->
                        val isSelected = cat.equals(chosenMeal, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { chosenMeal = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter Tabs ("Alimentos", "Recentes", "Favoritos", "Cód. Barras")
                ScrollableTabRow(
                    selectedTabIndex = activeFilterTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = activeFilterTab == 0,
                        onClick = { activeFilterTab = 0 },
                        text = { Text("Alimentos", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                    Tab(
                        selected = activeFilterTab == 1,
                        onClick = { activeFilterTab = 1 },
                        text = { Text("Recentes", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                    Tab(
                        selected = activeFilterTab == 2,
                        onClick = { activeFilterTab = 2 },
                        text = { Text("Favoritos", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                    Tab(
                        selected = activeFilterTab == 3,
                        onClick = { activeFilterTab = 3 },
                        text = { Text("Cód. Barras", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                }

                if (activeFilterTab != 3) {
                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            val hint = when (activeFilterTab) {
                                1 -> "Filtrar recentes..."
                                2 -> "Filtrar favoritos..."
                                else -> "Buscar alimento... (ex: Arroz, Whey, Omelete)"
                            }
                            Text(hint)
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSearchingApi && activeFilterTab == 0) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_food_search_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Food list view with visual tags and dynamic portion calculations
                    Box(modifier = Modifier.weight(1f)) {
                        if (currentTabFoods.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (isSearchingApi && activeFilterTab == 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Buscando na API Open Food Facts...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    val emptyMsg = when (activeFilterTab) {
                                        1 -> "Nenhum alimento recente registrado ainda.\nAdicione alimentos às refeições para vê-los aqui."
                                        2 -> "Nenhum alimento favorito ainda.\nToque na estrela para salvar seus alimentos favoritos."
                                        else -> "Nenhum alimento encontrado."
                                    }
                                    Text(
                                        text = emptyMsg,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(currentTabFoods, key = { "${it.source}_${it.name}" }) { food ->
                                    val cartItem = selectedFoods[food.name]
                                    val isSelected = cartItem != null
                                    val portion = cartItem?.portionGrams ?: 100
                                    val quantity = cartItem?.quantity ?: 1

                                    val factor = (portion / 100.0) * quantity
                                    val scaledCals = (food.calories * factor).toInt()
                                    val scaledProtein = food.protein * factor
                                    val scaledCarbs = food.carbs * factor
                                    val scaledFat = food.fat * factor
                                    val scaledFiber = food.fiber * factor

                                    val isTaco = food.source.equals("TACO", ignoreCase = true)
                                    val isFav = viewModel.isFavoriteFood(food.name)

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF1E2024)
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    // Source Visual Tag Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (isTaco) Color(0xFF1B5E20).copy(alpha = 0.25f) else Color(0xFFE65100).copy(alpha = 0.25f)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isTaco) "Oficial TACO" else "Marca / Industrializado",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isTaco) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(3.dp))

                                                    Text(
                                                        text = food.name,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    food.tip?.let { tipStr ->
                                                        Text(
                                                            text = tipStr,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Favorite Star Button
                                                    IconButton(
                                                        onClick = { viewModel.toggleFavoriteFood(food) },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = if (isFav) "Remover dos favoritos" else "Favoritar",
                                                            tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    // Multi-selection Add / Check button
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                            .clickable {
                                                                if (isSelected) {
                                                                    selectedFoods.remove(food.name)
                                                                } else {
                                                                    selectedFoods[food.name] = FoodSelectionCartState(food, portion, 1)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                                            contentDescription = if (isSelected) "Selecionado" else "Adicionar",
                                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Interactive Portion and Quantity Selectors when item is selected
                                            AnimatedVisibility(visible = isSelected) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF141619))
                                                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        // Portion Grams Field
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "Porção:",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            var portionInputText by remember(cartItem?.portionGrams) {
                                                                mutableStateOf(cartItem?.portionGrams?.toString() ?: "100")
                                                            }
                                                            BasicTextField(
                                                                value = portionInputText,
                                                                onValueChange = { input ->
                                                                    val digits = input.filter { it.isDigit() }.take(4)
                                                                    portionInputText = digits
                                                                    val grams = digits.toIntOrNull() ?: 100
                                                                    val safeGrams = grams.coerceAtLeast(1)
                                                                    cartItem?.let { item ->
                                                                        selectedFoods[food.name] = item.copy(portionGrams = safeGrams)
                                                                    }
                                                                },
                                                                textStyle = TextStyle(
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    textAlign = TextAlign.Center
                                                                ),
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                singleLine = true,
                                                                decorationBox = { innerTextField ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(62.dp)
                                                                            .height(32.dp)
                                                                            .background(Color(0xFF24272C), RoundedCornerShape(6.dp))
                                                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                                                            .padding(horizontal = 4.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        innerTextField()
                                                                    }
                                                                }
                                                            )
                                                            Text("g", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }

                                                        // Quantity Selector [-] 1x [+]
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Qtd:",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                    .clickable {
                                                                        cartItem?.let { item ->
                                                                            if (item.quantity > 1) {
                                                                                selectedFoods[food.name] = item.copy(quantity = item.quantity - 1)
                                                                            } else {
                                                                                selectedFoods.remove(food.name)
                                                                            }
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Remove,
                                                                    contentDescription = "Diminuir porções",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }

                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(6.dp),
                                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                                            ) {
                                                                Text(
                                                                    text = "${quantity}x",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                    .clickable {
                                                                        cartItem?.let { item ->
                                                                            selectedFoods[food.name] = item.copy(quantity = item.quantity + 1)
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = "Aumentar porções",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Recalculated Macros Bar (Calories, Protein, Carbs, Fat, Fiber)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF141619))
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                val totalGrams = portion * quantity
                                                val portionInfoText = if (quantity > 1) "${scaledCals} kcal • (${quantity}x ${portion}g = ${totalGrams}g)" else "${scaledCals} kcal • (${portion}g)"
                                                Text(
                                                    text = portionInfoText,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("P: ${String.format(java.util.Locale.getDefault(), "%.1f", scaledProtein)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("C: ${String.format(java.util.Locale.getDefault(), "%.1f", scaledCarbs)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("G: ${String.format(java.util.Locale.getDefault(), "%.1f", scaledFat)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("F: ${String.format(java.util.Locale.getDefault(), "%.1f", scaledFiber)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Action Bar for multi-selection confirm
                    AnimatedVisibility(visible = selectedFoods.isNotEmpty()) {
                        val totalSelCals = selectedFoods.values.sumOf { itemState ->
                            val f = (itemState.portionGrams / 100.0) * itemState.quantity
                            (itemState.food.calories * f).toInt()
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2024)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${selectedFoods.size} item(s) selecionado(s)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Total: $totalSelCals kcal",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        val itemsList = selectedFoods.values.map { itemState ->
                                            val factor = (itemState.portionGrams / 100.0) * itemState.quantity
                                            val qtyPart = if (itemState.quantity > 1) "${itemState.quantity}x " else ""
                                            val portionPart = if (itemState.portionGrams != 100) "${itemState.portionGrams}g" else ""
                                            val detailsStr = listOf(qtyPart, portionPart).filter { it.isNotBlank() }.joinToString("").trim()
                                            val nameWithPortion = if (detailsStr.isNotBlank()) "${itemState.food.name} ($detailsStr)" else itemState.food.name
                                            ConsumoDiario(
                                                name = nameWithPortion,
                                                calories = (itemState.food.calories * factor).toInt(),
                                                protein = itemState.food.protein * factor,
                                                carbs = itemState.food.carbs * factor,
                                                fat = itemState.food.fat * factor,
                                                mealName = chosenMeal
                                            )
                                        }
                                        viewModel.addConsumoDiarioList(itemsList)
                                        viewModel.addFoodsToRecents(selectedFoods.values.map { it.food })
                                        onDismiss()
                                        android.widget.Toast.makeText(context, "${itemsList.size} alimento(s) adicionado(s) a $chosenMeal!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("confirm_multi_select_btn")
                                ) {
                                    Text("Adicionar à Refeição", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Barcode Search Tab (Camera Scanner + Manual Entry)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Busca por Código de Barras (EAN-13 / UPC)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Escaneie o código de barras de qualquer alimento industrializado com a câmera ou digite os números para buscar na base de dados nutricional:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        // 1. Primary Scanner Card (Câmera Leitora)
                        Surface(
                            onClick = { showCameraBarcodeScanner = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().testTag("barcode_open_camera_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Escanear com Câmera",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Escanear com a Câmera",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Leitor integrado com mira e detecção em tempo real",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Divider with "OU DIGITAÇÃO MANUAL"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Text(
                                text = "OU DIGITAÇÃO MANUAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }

                        // 2. Manual Input Field & Search Button
                        OutlinedTextField(
                            value = barcodeQuery,
                            onValueChange = { input -> barcodeQuery = input.filter { it.isDigit() } },
                            placeholder = { Text("ex: 7891000100103", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (barcodeQuery.isNotEmpty()) {
                                    IconButton(onClick = { barcodeQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpar", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("barcode_manual_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                if (barcodeQuery.isNotBlank()) {
                                    isSearchingBarcode = true
                                    viewModel.searchBarcodeProduct(barcodeQuery) { result ->
                                        barcodeProductResult = result
                                        isSearchingBarcode = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("barcode_manual_search_button"),
                            shape = RoundedCornerShape(10.dp),
                            enabled = barcodeQuery.isNotBlank() && !isSearchingBarcode
                        ) {
                            if (isSearchingBarcode) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buscando produto...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buscar Produto", fontWeight = FontWeight.Bold)
                            }
                        }

                        barcodeProductResult?.let { found ->
                            var barcodePortionGrams by remember(found.name) { mutableStateOf(100) }
                            var barcodeQuantity by remember(found.name) { mutableStateOf(1) }

                            val factor = (barcodePortionGrams / 100.0) * barcodeQuantity
                            val cals = (found.calories * factor).toInt()
                            val prot = found.protein * factor
                            val carb = found.carbs * factor
                            val fat = found.fat * factor
                            val fiber = found.fiber * factor

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2024)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE65100).copy(alpha = 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Marca / Industrializado", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF9800))
                                    }

                                    Text(text = found.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                    // Portion and Quantity Controls Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF141619))
                                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Porção:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            var barcodePortionText by remember(barcodePortionGrams) { mutableStateOf(barcodePortionGrams.toString()) }
                                            BasicTextField(
                                                value = barcodePortionText,
                                                onValueChange = { input ->
                                                    val digits = input.filter { it.isDigit() }.take(4)
                                                    barcodePortionText = digits
                                                    val g = digits.toIntOrNull() ?: 100
                                                    barcodePortionGrams = g.coerceAtLeast(1)
                                                },
                                                textStyle = TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(62.dp)
                                                            .height(32.dp)
                                                            .background(Color(0xFF24272C), RoundedCornerShape(6.dp))
                                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                            Text("g", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Qtd:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { if (barcodeQuantity > 1) barcodeQuantity-- },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(14.dp))
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "${barcodeQuantity}x",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { barcodeQuantity++ },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    val portionLabel = if (barcodeQuantity > 1) "${barcodeQuantity}x ${barcodePortionGrams}g" else "${barcodePortionGrams}g"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF141619))
                                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${cals} kcal • (${portionLabel})",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("P: ${String.format(java.util.Locale.getDefault(), "%.1f", prot)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("C: ${String.format(java.util.Locale.getDefault(), "%.1f", carb)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("G: ${String.format(java.util.Locale.getDefault(), "%.1f", fat)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("F: ${String.format(java.util.Locale.getDefault(), "%.1f", fiber)}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.addConsumoDiario(
                                                name = "${found.name} (${portionLabel})",
                                                calories = cals,
                                                carbs = carb,
                                                protein = prot,
                                                fat = fat,
                                                mealName = chosenMeal
                                            )
                                            viewModel.addFoodToRecents(found)
                                            onDismiss()
                                            android.widget.Toast.makeText(context, "${found.name} adicionado a $chosenMeal!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Text("Adicionar a $chosenMeal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showCameraBarcodeScanner) {
        BarcodeCameraScannerDialog(
            onDismiss = { showCameraBarcodeScanner = false },
            onBarcodeDetected = { scannedCode ->
                barcodeQuery = scannedCode
                showCameraBarcodeScanner = false
                isSearchingBarcode = true
                viewModel.searchBarcodeProduct(scannedCode) { result ->
                    barcodeProductResult = result
                    isSearchingBarcode = false
                }
            }
        )
    }
}

@Composable
fun MacroIndicator(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (value.isEmpty()) "--" else value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Evolution / History Tab Screen ---

@Composable
fun CargasEvolutionChart(
    history: List<HistoryEntry>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    // Sort history chronologically
    val sortedHistory = remember(history) {
        history.sortedBy { it.date }
    }

    val maxWeight = remember(sortedHistory) {
        (sortedHistory.maxOfOrNull { it.weight } ?: 100.0).toFloat().coerceAtLeast(10f)
    }
    val minWeight = remember(sortedHistory) {
        (sortedHistory.minOfOrNull { it.weight } ?: 0.0).toFloat().coerceAtMost(maxWeight - 5f)
    }

    val weightRange = remember(maxWeight, minWeight) {
        val range = maxWeight - minWeight
        if (range == 0f) 10f else range
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "GRÁFICO DE EVOLUÇÃO (Supino Reto com Barra)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val paddingLeft = 50f
                val paddingRight = 40f
                val paddingTop = 15f
                val paddingBottom = 40f

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Draw Y-axis grid lines (3 levels)
                val gridLines = 3
                for (i in 0 until gridLines) {
                    val y = paddingTop + (chartHeight / (gridLines - 1)) * i
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(paddingLeft, y),
                        end = androidx.compose.ui.geometry.Offset(width - paddingRight, y),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // Draw Y labels
                    val weightLabel = maxWeight - (weightRange / (gridLines - 1)) * i
                    drawContext.canvas.nativeCanvas.drawText(
                        "${weightLabel.toInt()} kg",
                        paddingLeft - 10f,
                        y + 8f,
                        android.graphics.Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                    )
                }

                // Draw X-axis line
                drawLine(
                    color = gridColor.copy(alpha = 0.8f),
                    start = androidx.compose.ui.geometry.Offset(paddingLeft, paddingTop + chartHeight),
                    end = androidx.compose.ui.geometry.Offset(width - paddingRight, paddingTop + chartHeight),
                    strokeWidth = 4f
                )

                if (sortedHistory.size > 1) {
                    val points = sortedHistory.mapIndexed { index, entry ->
                        val x = paddingLeft + (chartWidth / (sortedHistory.size - 1)) * index
                        val normalizedWeight = (entry.weight.toFloat() - minWeight) / weightRange
                        val y = paddingTop + chartHeight - (normalizedWeight * chartHeight)
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    // Draw connection path
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )

                    // Draw gradient filling below the line
                    val fillPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, paddingTop + chartHeight)
                        for (point in points) {
                            lineTo(point.x, point.y)
                        }
                        lineTo(points.last().x, paddingTop + chartHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.25f), androidx.compose.ui.graphics.Color.Transparent)
                        )
                    )

                    // Draw points and labels
                    val ptBrLocale = java.util.Locale.Builder().setLanguage("pt").setRegion("BR").build()
                    val sdf = java.text.SimpleDateFormat("MMM", ptBrLocale)
                    points.forEachIndexed { index, point ->
                        // Draw outer glow circle
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.35f),
                            radius = 10f,
                            center = point
                        )
                        // Draw inner primary circle
                        drawCircle(
                            color = primaryColor,
                            radius = 5f,
                            center = point
                        )

                        // Draw value label above point
                        drawContext.canvas.nativeCanvas.drawText(
                            "${sortedHistory[index].weight.toInt()} kg",
                            point.x,
                            point.y - 14f,
                            android.graphics.Paint().apply {
                                color = primaryColor.toArgb()
                                textSize = 24f
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )

                        // Draw date label below X-axis
                        val dateStr = sdf.format(java.util.Date(sortedHistory[index].date)).uppercase()
                        drawContext.canvas.nativeCanvas.drawText(
                            dateStr,
                            point.x,
                            paddingTop + chartHeight + 30f,
                            android.graphics.Paint().apply {
                                color = labelColor.toArgb()
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeightEvolutionChart(
    pesoHistory: List<PesoHistoryEntry>,
    currentWeight: Double,
    modifier: Modifier = Modifier
) {
    val sortedHistory = remember(pesoHistory, currentWeight) {
        if (pesoHistory.isEmpty()) {
            listOf(
                PesoHistoryEntry(peso = currentWeight, data = System.currentTimeMillis() - 86400000 * 4),
                PesoHistoryEntry(peso = currentWeight, data = System.currentTimeMillis())
            )
        } else {
            pesoHistory.sortedBy { it.data }
        }
    }

    val maxWeight = remember(sortedHistory) {
        (sortedHistory.maxOfOrNull { it.peso } ?: currentWeight).toFloat() + 2f
    }
    val minWeight = remember(sortedHistory) {
        ((sortedHistory.minOfOrNull { it.peso } ?: currentWeight).toFloat() - 2f).coerceAtLeast(0f)
    }

    val weightRange = remember(maxWeight, minWeight) {
        val range = maxWeight - minWeight
        if (range == 0f) 10f else range
    }

    val primaryColor = MaterialTheme.colorScheme.primary // NeonLime
    val secondaryColor = MaterialTheme.colorScheme.secondary // ElectricBlue
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "EVOLUÇÃO DO PESO CORPORAL (KG)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val paddingLeft = 70f
                val paddingRight = 40f
                val paddingTop = 25f
                val paddingBottom = 40f

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Draw Y-axis grid lines (3 levels)
                val gridLines = 3
                for (i in 0 until gridLines) {
                    val y = paddingTop + (chartHeight / (gridLines - 1)) * i
                    drawLine(
                        color = gridColor.copy(alpha = 0.25f),
                        start = androidx.compose.ui.geometry.Offset(paddingLeft, y),
                        end = androidx.compose.ui.geometry.Offset(width - paddingRight, y),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // Draw Y labels
                    val weightLabel = maxWeight - (weightRange / (gridLines - 1)) * i
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(java.util.Locale.US, "%.1f kg", weightLabel),
                        paddingLeft - 10f,
                        y + 8f,
                        android.graphics.Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                    )
                }

                // Draw X-axis line
                drawLine(
                    color = gridColor.copy(alpha = 0.6f),
                    start = androidx.compose.ui.geometry.Offset(paddingLeft, paddingTop + chartHeight),
                    end = androidx.compose.ui.geometry.Offset(width - paddingRight, paddingTop + chartHeight),
                    strokeWidth = 4f
                )

                if (sortedHistory.isNotEmpty()) {
                    val points = sortedHistory.mapIndexed { index, entry ->
                        val x = if (sortedHistory.size > 1) {
                            paddingLeft + (chartWidth / (sortedHistory.size - 1)) * index
                        } else {
                            paddingLeft + chartWidth / 2f
                        }
                        val normalizedWeight = (entry.peso.toFloat() - minWeight) / weightRange
                        val y = paddingTop + chartHeight - (normalizedWeight * chartHeight)
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    // Draw connection path
                    if (points.size > 1) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 6f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )

                        // Draw gradient filling below the line
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points.first().x, paddingTop + chartHeight)
                            for (point in points) {
                                lineTo(point.x, point.y)
                            }
                            lineTo(points.last().x, paddingTop + chartHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.25f), androidx.compose.ui.graphics.Color.Transparent)
                            )
                        )
                    }

                    // Draw points and labels
                    val ptBrLocale = java.util.Locale.Builder().setLanguage("pt").setRegion("BR").build()
                    val sdf = java.text.SimpleDateFormat("dd/MM", ptBrLocale)
                    points.forEachIndexed { index, point ->
                        // Draw outer glow circle
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.35f),
                            radius = 12f,
                            center = point
                        )
                        // Draw inner primary circle
                        drawCircle(
                            color = primaryColor,
                            radius = 6f,
                            center = point
                        )

                        // Draw value label above point
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(java.util.Locale.US, "%.1f", sortedHistory[index].peso),
                            point.x,
                            point.y - 18f,
                            android.graphics.Paint().apply {
                                color = primaryColor.toArgb()
                                textSize = 22f
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )

                        // Draw date label below X-axis
                        val dateStr = sdf.format(java.util.Date(sortedHistory[index].data))
                        drawContext.canvas.nativeCanvas.drawText(
                            dateStr,
                            point.x,
                            paddingTop + chartHeight + 30f,
                            android.graphics.Paint().apply {
                                color = labelColor.toArgb()
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EvolutionTabScreen(viewModel: FitViewModel) {
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val pesoHistory by viewModel.pesoHistory.collectAsStateWithLifecycle()

    val currentWeight = userProfile?.weight ?: 80.0
    val objective = userProfile?.objective ?: "Hipertrofia"

    // Sort weight history chronologically
    val sortedPesoHistory = remember(pesoHistory) {
        pesoHistory.sortedBy { it.data }
    }

    // Calculate Highest and Lowest weight safely
    val maiorPeso = remember(sortedPesoHistory, currentWeight) {
        val maxFromHistory = sortedPesoHistory.maxOfOrNull { it.peso }
        if (maxFromHistory != null) {
            maxOf(maxFromHistory, currentWeight)
        } else {
            currentWeight
        }
    }

    val menorPeso = remember(sortedPesoHistory, currentWeight) {
        val minFromHistory = sortedPesoHistory.minOfOrNull { it.peso }
        if (minFromHistory != null && minFromHistory > 0) {
            minOf(minFromHistory, currentWeight)
        } else {
            currentWeight
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Evolução e Histórico",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Acompanhamento em tempo real do seu progresso corporal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- SUMMARY ROW AT THE TOP ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Maior peso Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Maior peso",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f kg", maiorPeso),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Menor peso Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Menor peso",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f kg", menorPeso),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Meta atual Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Meta atual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = objective,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weight Progress Chart
        WeightEvolutionChart(
            pesoHistory = sortedPesoHistory,
            currentWeight = currentWeight,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "TELEMETRIA DE RECORDE DE CARGAS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Insira novas cargas de exercícios para alimentar o histórico.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { entry ->
                    val currentExercise = remember(allExercises, entry.exerciseName) {
                        allExercises.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
                    }
                    val formattedDate = remember(entry.date) {
                        if (entry.date > 0L) {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(entry.date))
                        } else {
                            "Execução registrada"
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = entry.exerciseName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Histórico: $formattedDate${if (entry.reps.isNotBlank()) " • ${entry.reps} reps" else ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (currentExercise != null && currentExercise.currentWeight > 0.0) {
                                        Text(
                                            text = "Carga atual na ficha: ${currentExercise.currentWeight} kg",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${entry.weight}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "kg",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Text(
                                    text = "executado",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Profile Tab Screen ---

@Composable
fun ProfileTabScreen(viewModel: FitViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val uid = viewModel.currentUser.collectAsStateWithLifecycle().value?.uid ?: ""
    val localPhotoUri = remember(userProfile, uid) {
        if (uid.isNotBlank()) {
            val prefs = context.getSharedPreferences("user_prefs_$uid", android.content.Context.MODE_PRIVATE)
            prefs.getString("avatar_uri", null)
        } else {
            null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePicture(uri.toString())
        }
    }

    var showResetConfirmDialog by remember { mutableStateOf(false) }

    if (viewModel.showEditBiometricsDialog) {
        EditBiometricsScreen(viewModel = viewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            if (userProfile == null) {
                // Placeholder if no user profile
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nenhum perfil carregado.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.openEditProfile() }) {
                            Text("Criar Perfil")
                        }
                    }
                }
            } else {
                val profile = userProfile ?: UserProfile(
                    id = 1,
                    name = "Atleta FitAI",
                    age = 25,
                    weight = 70.0,
                    height = 175.0,
                    fitnessLevel = "Intermediário",
                    restrictions = "Nenhuma",
                    objective = "Hipertrofia",
                    trainingDays = "M,T,W,T,F",
                    notificationMessage = "Hora de treinar!",
                    gender = "Masculino",
                    avatarPreset = "avatar_1",
                    onboardingCompleted = true
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Circular athletic avatar matching exactly the design system
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            val displayPhotoModel = localPhotoUri ?: profile.avatarPreset
                            val isPreset = displayPhotoModel.startsWith("avatar_")
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPreset) {
                                    val avatarIcon = when (displayPhotoModel) {
                                        "avatar_1" -> Icons.AutoMirrored.Filled.DirectionsRun
                                        "avatar_2" -> Icons.Default.FitnessCenter
                                        "avatar_3" -> Icons.Default.Bolt
                                        "avatar_4" -> Icons.Default.Favorite
                                        "avatar_5" -> Icons.Default.Person
                                        else -> Icons.Default.Person
                                    }
                                    Icon(
                                        imageVector = avatarIcon,
                                        contentDescription = "Foto de perfil",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                } else {
                                    coil.compose.AsyncImage(
                                        model = displayPhotoModel,
                                        contentDescription = "Foto de perfil",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                // Small edit overlay icon badge
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar foto",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "ELITE MEMBER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    // Bento Stats Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.weight(1.0f)) {
                                    BentoStatCard(label = "AGE", value = "${profile.age}", unit = "yrs")
                                }
                                Box(modifier = Modifier.weight(1.0f)) {
                                    BentoStatCard(label = "WEIGHT", value = "${profile.weight}", unit = "kg")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.weight(1.0f)) {
                                    BentoStatCard(label = "HEIGHT", value = "${profile.height}", unit = "cm")
                                }
                                Box(modifier = Modifier.weight(1.0f)) {
                                    BentoStatCard(
                                        label = "FITNESS LEVEL",
                                        value = profile.fitnessLevel.uppercase(),
                                        unit = "",
                                        valueColor = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    // Health and Goals section
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "HEALTH & GOALS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            GoalDetailCard(
                                label = "Gênero / Sexo",
                                value = profile.gender,
                                icon = Icons.Default.Person,
                                iconColor = MaterialTheme.colorScheme.primary
                            )

                            GoalDetailCard(
                                label = "Primary Objective",
                                value = profile.objective,
                                icon = Icons.Default.TrackChanges,
                                iconColor = MaterialTheme.colorScheme.secondary
                            )

                            GoalDetailCard(
                                label = "Restrictions",
                                value = profile.restrictions,
                                icon = Icons.Default.MedicalServices,
                                iconColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Training Availability selection presentation
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "TRAINING AVAILABILITY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val trainingDaysStr = profile.trainingDays
                                val activeDaysList = remember(trainingDaysStr) {
                                    val list = mutableListOf(false, false, false, false, false, false, false)
                                    if (trainingDaysStr.contains("M")) list[0] = true
                                    val occurrencesT = trainingDaysStr.count { it == 'T' }
                                    if (occurrencesT >= 2) {
                                        list[1] = true
                                        list[3] = true
                                    } else if (occurrencesT == 1) {
                                        val idxT = trainingDaysStr.indexOf('T')
                                        val idxW = trainingDaysStr.indexOf('W')
                                        if (idxW != -1) {
                                            if (idxT < idxW) list[1] = true else list[3] = true
                                        } else {
                                            list[1] = true
                                        }
                                    }
                                    if (trainingDaysStr.contains("W")) list[2] = true
                                    if (trainingDaysStr.contains("F")) list[4] = true
                                    val occurrencesS = trainingDaysStr.count { it == 'S' }
                                    if (occurrencesS >= 2) {
                                        list[5] = true
                                        list[6] = true
                                    } else if (occurrencesS == 1) {
                                        list[5] = true
                                    }
                                    list
                                }
                                val allDays = listOf("M", "T", "W", "T", "F", "S", "S")
                                allDays.forEachIndexed { index, day ->
                                    val isActive = activeDaysList.getOrElse(index) { false }
                                    val limeGreen = androidx.compose.ui.graphics.Color(0xFFC0FF00)

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) limeGreen else androidx.compose.ui.graphics.Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isActive) limeGreen else MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Appearance / Theme Section
                    item {
                        val currentThemeMode by viewModel.themeModeState.collectAsStateWithLifecycle()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "APARÊNCIA DO APLICATIVO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val modes = listOf(
                                        Triple(AppThemeMode.DARK, "🌙 Escuro", Icons.Default.DarkMode),
                                        Triple(AppThemeMode.LIGHT, "☀️ Claro", Icons.Default.WbSunny),
                                        Triple(AppThemeMode.SYSTEM, "📱 Sistema", Icons.Default.Smartphone)
                                    )

                                    modes.forEach { (mode, label, icon) ->
                                        val isSelected = currentThemeMode == mode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                                )
                                                .clickable { viewModel.setThemeMode(mode) }
                                                .padding(horizontal = 4.dp)
                                                .testTag("theme_selector_${mode.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Buttons: Tutorial, Editar Dados and Logout
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restartTour() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("restart_tour_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = "Ajuda Tutorial",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Rever Tutorial Guiado (Tour do App)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.showEditBiometricsDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("edit_profile_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Editar Dados Biométricos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Button(
                                onClick = { viewModel.logout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Sair da Conta (Logout)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Resetar Dados e Apagar Conta?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = "Atenção: Esta ação apagarar permanentemente seu perfil, treinos, refeições, histórico, registros de água e suplementos do Firestore, além da sua conta no Firebase. Você será desconectado e redirecionado para a tela de Login/Cadastro.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetAndDeleteAccount(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_reset_data_button")
                ) {
                    Text("Sim, Apagar Tudo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun EditBiometricsScreen(viewModel: FitViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val profile = userProfile ?: UserProfile()

    var editName by remember(profile.name) { mutableStateOf(profile.name ?: "") }
    var editAge by remember(profile.age) { mutableStateOf(if ((profile.age ?: 0) > 0) "${profile.age}" else "") }
    var editWeight by remember(profile.weight) { mutableStateOf(if ((profile.weight ?: 0.0) > 0.0) "${profile.weight}" else "") }
    var editHeight by remember(profile.height) { mutableStateOf(if ((profile.height ?: 0.0) > 0.0) "${profile.height}" else "") }
    var editGender by remember(profile.gender) { mutableStateOf(if (!profile.gender.isNullOrBlank()) profile.gender else "Masculino") }
    var editObjective by remember(profile.objective) { mutableStateOf(if (!profile.objective.isNullOrBlank()) profile.objective else "Hipertrofia") }
    var editFitnessLevel by remember(profile.fitnessLevel) { mutableStateOf(if (!profile.fitnessLevel.isNullOrBlank()) profile.fitnessLevel else "Iniciante") }
    var genderExpanded by remember { mutableStateOf(false) }
    var objectiveExpanded by remember { mutableStateOf(false) }
    var fitnessLevelExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Editar Dados Biométricos",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Atualize seus dados biométricos. As metas de calorias, macronutrientes, água e o plano de treino/dieta serão recalculados automaticamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        FitInput(
            label = "NOME COMPLETO",
            value = editName,
            onValueChange = { editName = it },
            placeholder = "Ex: Alex Silva",
            testTag = "edit_biometrics_name"
        )

        FitInput(
            label = "IDADE",
            value = editAge,
            onValueChange = { editAge = it },
            placeholder = "Ex: 28",
            keyboardType = KeyboardType.Number,
            testTag = "edit_biometrics_age"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FitInput(
                    label = "PESO (KG)",
                    value = editWeight,
                    onValueChange = { editWeight = it },
                    placeholder = "Ex: 82.0",
                    keyboardType = KeyboardType.Decimal,
                    testTag = "edit_biometrics_weight"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FitInput(
                    label = "ALTURA (CM)",
                    value = editHeight,
                    onValueChange = { editHeight = it },
                    placeholder = "Ex: 180",
                    keyboardType = KeyboardType.Number,
                    testTag = "edit_biometrics_height"
                )
            }
        }

        // Gender Choice Selector
        Column {
            Text(
                text = "GÊNERO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { genderExpanded = !genderExpanded }
                        .padding(horizontal = 12.dp)
                        .testTag("edit_biometrics_gender_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = editGender,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Selecionar gênero",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf("Masculino", "Feminino").forEach { gender ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = gender,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (editGender == gender) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                editGender = gender
                                genderExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Objective Selector
        Column {
            Text(
                text = "OBJETIVO PRINCIPAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { objectiveExpanded = !objectiveExpanded }
                        .padding(horizontal = 12.dp)
                        .testTag("edit_biometrics_objective_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = editObjective,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Selecionar objetivo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = objectiveExpanded,
                    onDismissRequest = { objectiveExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf("Hipertrofia", "Emagrecimento", "Definição", "Saúde").forEach { objective ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = objective,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (editObjective == objective) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                editObjective = objective
                                objectiveExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Fitness Level Selector (Nível de Treino)
        Column {
            Text(
                text = "NÍVEL DE TREINO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { fitnessLevelExpanded = !fitnessLevelExpanded }
                        .padding(horizontal = 12.dp)
                        .testTag("edit_biometrics_fitness_level_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = editFitnessLevel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Selecionar nível",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = fitnessLevelExpanded,
                    onDismissRequest = { fitnessLevelExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    listOf("Iniciante", "Intermediário", "Avançado").forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = level,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (editFitnessLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                editFitnessLevel = level
                                fitnessLevelExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.showEditBiometricsDialog = false },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("cancel_edit_biometrics_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    val nameTrimmed = editName.trim()
                    val idadeInt = editAge.toIntOrNull() ?: 0
                    val pesoDouble = editWeight.toDoubleOrNull() ?: 0.0
                    val alturaDouble = editHeight.toDoubleOrNull() ?: 0.0

                    if (nameTrimmed.isEmpty()) {
                        errorMessage = "Por favor, preencha seu nome."
                    } else if (idadeInt <= 0 || idadeInt > 120) {
                        errorMessage = "Por favor, insira uma idade válida (ex: 28)."
                    } else if (pesoDouble <= 20.0 || pesoDouble > 400.0) {
                        errorMessage = "Por favor, insira um peso válido em kg (ex: 80.0)."
                    } else if (alturaDouble <= 50.0 || alturaDouble > 260.0) {
                        errorMessage = "Por favor, insira uma altura válida em cm (ex: 180)."
                    } else {
                        errorMessage = null
                        viewModel.saveEditedBiometrics(
                            name = nameTrimmed,
                            age = editAge,
                            weight = editWeight,
                            height = editHeight,
                            gender = editGender,
                            objective = editObjective,
                            fitnessLevel = editFitnessLevel
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("save_edit_biometrics_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Salvar")
            }
        }
    }
}

@Composable
fun BentoStatCard(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = valueColor
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GoalDetailCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = value.ifBlank { "Não informado" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
