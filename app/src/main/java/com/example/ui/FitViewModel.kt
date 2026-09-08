package com.example.ui

import com.example.data.repository.getSpecificExerciseInstructions
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.util.Log
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.media.RingtoneManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibratorManager
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.api.FoodItemJson
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryEntry
import com.example.data.db.Meal
import com.example.data.db.UserProfile
import com.example.data.db.WorkoutExercise
import com.example.data.db.ConsumoDiario
import com.example.data.db.RegistroAgua
import com.example.data.db.RegistroDiario
import com.example.data.db.PesoHistoryEntry
import com.example.data.db.Suplemento
import com.example.SupplementWorker
import com.example.SupplementScheduler
import com.example.data.repository.AppRepository
import com.example.data.repository.FoodSearchRepository
import com.example.data.repository.ThemeRepository
import com.example.data.repository.AppThemeMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface GenerationUiState {
    object Idle : GenerationUiState
    object Generating : GenerationUiState
    data class Success(val message: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

enum class FitTab {
    TRAINING, NUTRITION, EVOLUTION, PROFILE
}

class FitViewModel(application: Application) : AndroidViewModel(application) {

    val repository: AppRepository
    private val themeRepository = ThemeRepository(application)
    val themeModeState: StateFlow<AppThemeMode> = themeRepository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppThemeMode.DARK
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error initializing FirebaseAuth: ${e.message}", e)
            null
        }
    }

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private val _currentUser = MutableStateFlow<FirebaseUser?>(
        try {
            auth?.currentUser
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error reading initial auth?.currentUser: ${e.message}", e)
            null
        }
    )
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        val database = try {
            AppDatabase.getDatabase(application)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error initializing Room database in FitViewModel: ${e.message}", e)
            androidx.room.Room.inMemoryDatabaseBuilder(
                application,
                AppDatabase::class.java
            ).addMigrations(*com.example.data.db.ALL_MIGRATIONS).build()
        }

        repository = AppRepository(
            appDao = database.appDao(),
            apiService = RetrofitClient.service,
            moshi = RetrofitClient.moshiParser
        )

        // Reactively listen to FirebaseAuth state changes safely
        try {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                try {
                    val user = firebaseAuth.currentUser
                    // If an active login/register flow is executing, wait for the profile check to finish
                    if (!isAuthLoading) {
                        _currentUser.value = user
                        if (user != null) {
                            loadFavoriteAndRecentFoods(user.uid)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("FitAI_Error", "Error updating _currentUser in authStateListener: ${e.message}", e)
                }
            }
            authStateListener = listener
            auth?.addAuthStateListener(listener)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error adding auth state listener: ${e.message}", e)
        }
    }

    // --- Database Flows dynamically derived from active Firebase User ---

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userProfile: StateFlow<UserProfile?> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            try {
                repository.getUserProfile(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error requesting getUserProfile flow: ${e.message}", e)
                flowOf(null)
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "userProfile catch flow error: ${e.message}", e)
        emit(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allExercises: StateFlow<List<WorkoutExercise>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllExercises(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error requesting getAllExercises flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allExercises catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allMeals: StateFlow<List<Meal>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllMeals(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error requesting getAllMeals flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allMeals catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val historyEntries: StateFlow<List<HistoryEntry>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getHistory(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error requesting getHistory flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "historyEntries catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Daily Date Selection State ---
    val selectedDateStr = MutableStateFlow(
        try {
            repository.getTodayDateStr()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error getting today date string: ${e.message}", e)
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        }
    )

    fun setSelectedDate(dateStr: String) {
        selectedDateStr.value = dateStr
    }

    private fun getTimestampForDateStr(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val parsed = sdf.parse(dateStr)
            parsed?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedRegistroDiario: StateFlow<RegistroDiario?> = combine(currentUser, selectedDateStr) { user, dateStr ->
        user to dateStr
    }.flatMapLatest { (user, dateStr) ->
        if (user == null) {
            flowOf(null)
        } else {
            try {
                repository.getTodayRegistroDiario(user.uid, dateStr)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getTodayRegistroDiario flow: ${e.message}", e)
                flowOf(null)
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "selectedRegistroDiario catch flow error: ${e.message}", e)
        emit(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayRegistroDiario: StateFlow<RegistroDiario?> = selectedRegistroDiario

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allRegistrosDiarios: StateFlow<List<RegistroDiario>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllRegistrosDiarios(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getAllRegistrosDiarios flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allRegistrosDiarios catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allConsumoDiario: StateFlow<List<ConsumoDiario>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllConsumoDiario(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getAllConsumoDiario flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allConsumoDiario catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allRegistroAgua: StateFlow<List<RegistroAgua>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllRegistroAgua(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getAllRegistroAgua flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allRegistroAgua catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pesoHistory: StateFlow<List<PesoHistoryEntry>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getPesoHistory(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getPesoHistory flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "pesoHistory catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allSuplementos: StateFlow<List<Suplemento>> = currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            try {
                repository.getAllSuplementos(user.uid)
            } catch (e: Throwable) {
                Log.e("FitAI_Error", "Error in getAllSuplementos flow: ${e.message}", e)
                flowOf(emptyList())
            }
        }
    }.catch { e ->
        Log.e("FitAI_Error", "allSuplementos catch flow error: ${e.message}", e)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Hybrid Food Search (TACO + Open Food Facts) ---
    private val _openFoodFactsResults = MutableStateFlow<List<LocalFoodItem>>(emptyList())
    val openFoodFactsResults: StateFlow<List<LocalFoodItem>> = _openFoodFactsResults.asStateFlow()

    private val _isSearchingApi = MutableStateFlow(false)
    val isSearchingApi: StateFlow<Boolean> = _isSearchingApi.asStateFlow()

    private var apiSearchJob: kotlinx.coroutines.Job? = null

    fun searchOpenFoodFacts(query: String) {
        apiSearchJob?.cancel()
        if (query.isBlank() || query.length < 2) {
            _openFoodFactsResults.value = emptyList()
            _isSearchingApi.value = false
            return
        }
        apiSearchJob = viewModelScope.launch {
            _isSearchingApi.value = true
            kotlinx.coroutines.delay(350)
            val results = FoodSearchRepository.searchOpenFoodFacts(query)
            _openFoodFactsResults.value = results
            _isSearchingApi.value = false
        }
    }

    fun searchBarcodeProduct(barcode: String, onResult: (LocalFoodItem?) -> Unit) {
        viewModelScope.launch {
            _isSearchingApi.value = true
            val item = FoodSearchRepository.getProductByBarcode(barcode)
            _isSearchingApi.value = false
            onResult(item)
        }
    }

    // --- Favorite & Recent Foods Management ---
    private val _favoriteFoods = MutableStateFlow<List<LocalFoodItem>>(emptyList())
    val favoriteFoods: StateFlow<List<LocalFoodItem>> = _favoriteFoods.asStateFlow()

    private val _recentFoods = MutableStateFlow<List<LocalFoodItem>>(emptyList())
    val recentFoods: StateFlow<List<LocalFoodItem>> = _recentFoods.asStateFlow()

    private fun serializeFoodList(list: List<LocalFoodItem>): String {
        return try {
            val array = org.json.JSONArray()
            list.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("name", item.name)
                obj.put("calories", item.calories)
                obj.put("protein", item.protein)
                obj.put("carbs", item.carbs)
                obj.put("fat", item.fat)
                obj.put("fiber", item.fiber)
                obj.put("source", item.source)
                obj.put("tip", item.tip ?: "")
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error serializing foods list", e)
            "[]"
        }
    }

    private fun deserializeFoodList(json: String?): List<LocalFoodItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LocalFoodItem>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LocalFoodItem(
                        name = obj.getString("name"),
                        calories = obj.getInt("calories"),
                        protein = obj.getDouble("protein"),
                        carbs = obj.getDouble("carbs"),
                        fat = obj.getDouble("fat"),
                        fiber = obj.optDouble("fiber", 0.0),
                        source = obj.optString("source", "TACO"),
                        tip = obj.optString("tip", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error deserializing foods list", e)
        }
        return list
    }

    fun loadFavoriteAndRecentFoods(uid: String) {
        if (uid.isBlank()) return
        try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            val favJson = prefs.getString("favorite_foods_$uid", null)
            val recJson = prefs.getString("recent_foods_$uid", null)
            _favoriteFoods.value = deserializeFoodList(favJson)
            _recentFoods.value = deserializeFoodList(recJson)
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error loading favorite/recent foods", e)
        }
    }

    fun toggleFavoriteFood(food: LocalFoodItem) {
        val uid = currentUser.value?.uid ?: auth?.currentUser?.uid ?: return
        val currentList = _favoriteFoods.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(food.name, ignoreCase = true) }
        if (existingIndex >= 0) {
            currentList.removeAt(existingIndex)
        } else {
            currentList.add(0, food)
        }
        _favoriteFoods.value = currentList
        try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("favorite_foods_$uid", serializeFoodList(currentList)).apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error saving favorite foods", e)
        }
    }

    fun isFavoriteFood(foodName: String): Boolean {
        return _favoriteFoods.value.any { it.name.equals(foodName, ignoreCase = true) }
    }

    fun addFoodToRecents(food: LocalFoodItem) {
        addFoodsToRecents(listOf(food))
    }

    fun addFoodsToRecents(foods: List<LocalFoodItem>) {
        if (foods.isEmpty()) return
        val uid = currentUser.value?.uid ?: auth?.currentUser?.uid ?: return
        val currentList = _recentFoods.value.toMutableList()
        for (food in foods) {
            currentList.removeAll { it.name.equals(food.name, ignoreCase = true) }
            currentList.add(0, food)
        }
        val trimmed = currentList.take(50)
        _recentFoods.value = trimmed
        try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("recent_foods_$uid", serializeFoodList(trimmed)).apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error saving recent foods", e)
        }
    }

    fun saveSuplemento(context: Context, suplemento: Suplemento) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            val id = if (suplemento.id.isEmpty()) java.util.UUID.randomUUID().toString() else suplemento.id
            val finalSuplemento = suplemento.copy(id = id)
            repository.saveSuplemento(uid, finalSuplemento)
            if (finalSuplemento.ativo) {
                SupplementWorker.scheduleSupplementWork(
                    context = context,
                    id = id,
                    nome = finalSuplemento.nome,
                    dose = finalSuplemento.dose,
                    horario = finalSuplemento.horario
                )
            } else {
                SupplementWorker.cancelSupplementWork(context, id)
            }
        }
    }

    fun deleteSuplemento(context: Context, suplementoId: String) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            repository.deleteSuplemento(uid, suplementoId)
            SupplementWorker.cancelSupplementWork(context, suplementoId)
        }
    }

    fun toggleSuplementoTomado(context: Context, suplemento: Suplemento, tomado: Boolean) {
        val uid = currentUser.value?.uid ?: return
        val dateStr = selectedDateStr.value
        viewModelScope.launch {
            repository.toggleSuplementoTomado(uid, dateStr, suplemento.id, tomado)
            if (suplemento.ativo) {
                // Keep daily recurrence scheduled for the next day/cycle
                SupplementScheduler.scheduleSupplementReminder(
                    context = context,
                    id = suplemento.id,
                    nome = suplemento.nome,
                    dose = suplemento.dose,
                    horario = suplemento.horario
                )
            }
        }
    }

    // --- Firebase Authentication State & Methods ---

    sealed interface AuthUiState {
        object Idle : AuthUiState
        object Loading : AuthUiState
        object Success : AuthUiState
        data class Error(val message: String) : AuthUiState
    }

    var authUiState by mutableStateOf<AuthUiState>(AuthUiState.Idle)
    var isAuthLoading by mutableStateOf(false)
    var profileLoadError by mutableStateOf<String?>(null)

    fun resetAuthError() {
        if (authUiState is AuthUiState.Error) {
            authUiState = AuthUiState.Idle
        }
    }

    fun retryLoadProfileOrReturnToAuth() {
        profileLoadError = null
        val user = _currentUser.value ?: auth?.currentUser
        if (user == null) {
            isCheckingProfile = false
            showWelcomeScreen = true
        } else {
            isCheckingProfile = false
        }
    }

    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val a = auth
        if (a == null) {
            val msg = "Serviço de Autenticação indisponível no momento"
            authUiState = AuthUiState.Error(msg)
            isAuthLoading = false
            try {
                onError(msg)
            } catch (t: Throwable) {
                Log.e("FitViewModel", "Error invoking onError callback", t)
            }
            return
        }

        // Prevent duplicate login triggers while processing
        if (isAuthLoading || authUiState is AuthUiState.Loading) {
            return
        }

        authUiState = AuthUiState.Loading
        isAuthLoading = true
        isCheckingProfile = true // Ensure UI does not prematurely render onboarding

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val task = a.signInWithEmailAndPassword(email, password)
                    com.google.android.gms.tasks.Tasks.await(task)
                }
                val user = result.user ?: a.currentUser
                if (user != null) {
                    val uid = user.uid
                    // Wipe any residual local Room cache from previous user sessions before loading current user profile
                    try {
                        repository.clearAllLocalRoomData()
                    } catch (e: Exception) {
                        Log.e("FitViewModel", "Error clearing residual local room data on login", e)
                    }

                    try {
                        loadFavoriteAndRecentFoods(uid)
                    } catch (e: Exception) {
                        Log.e("FitViewModel", "Error loading foods on login", e)
                    }

                    // 1. Fetch user profile from Firestore / Room with safe timeout
                    val existingProfile = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        withContext(Dispatchers.IO) {
                            try {
                                repository.fetchUserProfileDirect(uid)
                            } catch (e: Exception) {
                                Log.e("FitViewModel", "Error fetching direct profile on login: ${e.message}", e)
                                null
                            }
                        }
                    }

                    val isComplete = isProfileComplete(existingProfile, uid)

                    if (existingProfile != null && isComplete) {
                        // Profile is complete -> Route straight to Dashboard
                        hasCompletedOnboarding = true
                        showOnboarding = false
                        setOnboardingCompletedInPrefs(uid, true)

                        if (!existingProfile.name.isNullOrBlank()) formName = existingProfile.name
                        if ((existingProfile.age ?: 0) > 0) formAge = existingProfile.age.toString()
                        if ((existingProfile.weight ?: 0.0) > 0.0) formWeight = existingProfile.weight.toString()
                        if ((existingProfile.height ?: 0.0) > 0.0) formHeight = existingProfile.height.toString()
                        if (!existingProfile.fitnessLevel.isNullOrBlank()) formFitnessLevel = existingProfile.fitnessLevel
                        if (!existingProfile.restrictions.isNullOrBlank() && existingProfile.restrictions != "Nenhuma") formRestrictions = existingProfile.restrictions
                        if (!existingProfile.objective.isNullOrBlank()) formObjective = existingProfile.objective
                        if (!existingProfile.trainingDays.isNullOrBlank()) formTrainingDays = existingProfile.trainingDays
                        if (!existingProfile.gender.isNullOrBlank()) formGender = existingProfile.gender
                        if (!existingProfile.avatarPreset.isNullOrBlank()) formAvatarPreset = existingProfile.avatarPreset

                        val hasSeenInProfile = existingProfile.hasSeenTour
                        val hasSeenInLocal = isTourSeenInPrefs(uid)
                        if (!hasSeenInProfile && !hasSeenInLocal && !isTourPreChecked) {
                            showAppTour = true
                            isTourPreChecked = true
                        }
                    } else if (existingProfile != null && !isComplete) {
                        // Profile exists but biometrics are incomplete -> Route to Onboarding
                        hasCompletedOnboarding = false
                        showOnboarding = true
                        onboardingStep = 1
                        setOnboardingCompletedInPrefs(uid, false)

                        if (!existingProfile.name.isNullOrBlank()) formName = existingProfile.name
                        if ((existingProfile.age ?: 0) > 0) formAge = existingProfile.age.toString()
                        if ((existingProfile.weight ?: 0.0) > 0.0) formWeight = existingProfile.weight.toString()
                        if ((existingProfile.height ?: 0.0) > 0.0) formHeight = existingProfile.height.toString()
                    } else {
                        // Profile not found in Firestore or Room
                        if (isOnboardingCompletedInPrefs(uid)) {
                            hasCompletedOnboarding = true
                            showOnboarding = false
                        } else {
                            // New user / fresh account -> Route to Onboarding
                            hasCompletedOnboarding = false
                            showOnboarding = true
                            onboardingStep = 1
                            setOnboardingCompletedInPrefs(uid, false)
                            formName = user.displayName ?: email.substringBefore("@")
                        }
                    }

                    // Synchronously update session state before unlocking UI
                    _currentUser.value = user
                    showWelcomeScreen = false
                    currentTab = FitTab.TRAINING
                    authUiState = AuthUiState.Success
                    isAuthLoading = false
                    isCheckingProfile = false

                    try {
                        onSuccess()
                    } catch (t: Throwable) {
                        Log.e("FitViewModel", "Error executing onSuccess callback after login", t)
                    }
                } else {
                    val msg = "Usuário não encontrado. Verifique seu e-mail e senha."
                    authUiState = AuthUiState.Error(msg)
                    isAuthLoading = false
                    isCheckingProfile = false
                    try {
                        onError(msg)
                    } catch (t: Throwable) {
                        Log.e("FitViewModel", "Error invoking onError callback", t)
                    }
                }
            } catch (rawException: Throwable) {
                isAuthLoading = false
                isCheckingProfile = false
                val e = (rawException as? java.util.concurrent.ExecutionException)?.cause ?: rawException
                Log.e("FitViewModel", "Exception during loginUser: ${e.message}") // Safe: never logs credentials
                val msg = when {
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e is java.io.IOException ||
                    e is FirebaseNetworkException ->
                        "Erro de conexão com a internet. Verifique sua rede e tente novamente."
                    e.message?.contains("invalid-credential", ignoreCase = true) == true ->
                        "E-mail ou senha incorretos."
                    e.message?.contains("user-not-found", ignoreCase = true) == true ->
                        "Usuário não encontrado. Verifique seu e-mail."
                    e.message?.contains("wrong-password", ignoreCase = true) == true ->
                        "Senha incorreta. Tente novamente."
                    e.message?.contains("invalid-email", ignoreCase = true) == true ->
                        "E-mail com formato inválido."
                    e.message?.contains("too-many-requests", ignoreCase = true) == true ->
                        "Muitas tentativas sem sucesso. Tente novamente mais tarde."
                    else -> e.localizedMessage ?: "Erro ao fazer login. Verifique suas credenciais."
                }
                authUiState = AuthUiState.Error(msg)
                try {
                    onError(msg)
                } catch (t: Throwable) {
                    Log.e("FitViewModel", "Error invoking onError callback", t)
                }
            }
        }
    }

    fun registerUser(
        email: String,
        password: String,
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onEmailAlreadyExists: (() -> Unit)? = null
    ) {
        val a = auth
        if (a == null) {
            onError("Serviço de Autenticação indisponível no momento.")
            return
        }
        performCreateUser(a, email, password, name, onSuccess, onError, onEmailAlreadyExists)
    }

    private fun performCreateUser(
        a: FirebaseAuth,
        email: String,
        password: String,
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onEmailAlreadyExists: (() -> Unit)?
    ) {
        if (isAuthLoading || authUiState is AuthUiState.Loading) return
        authUiState = AuthUiState.Loading
        isAuthLoading = true
        isCheckingProfile = true
        viewModelScope.launch {
            try {
                // Ensure state and temporary caches are completely clean before creating new account
                resetForm()
                clearTemporaryCaches()

                val result = withContext(Dispatchers.IO) {
                    val task = a.createUserWithEmailAndPassword(email, password)
                    com.google.android.gms.tasks.Tasks.await(task)
                }

                val user = result.user ?: a.currentUser
                if (user != null) {
                    // Configure initial user flags in preferences
                    setOnboardingCompletedInPrefs(user.uid, false)
                    hasCompletedOnboarding = false
                    showOnboarding = true
                    onboardingStep = 1
                    formName = name.trim()

                    // Clear local Room database for clean isolation
                    try {
                        repository.clearAllLocalRoomData()
                    } catch (e: Exception) {
                        Log.e("FitViewModel", "Error clearing local room data", e)
                    }

                    // Save initial user profile
                    val initialProfile = UserProfile(
                        id = 1,
                        uid = user.uid,
                        name = name.trim(),
                        email = email,
                        age = null,
                        weight = null,
                        height = null,
                        fitnessLevel = "Iniciante",
                        restrictions = "",
                        objective = "Hipertrofia",
                        gender = "Masculino",
                        onboardingCompleted = false
                    )
                    try {
                        repository.saveUserProfile(user.uid, initialProfile, email = email)
                    } catch (e: Exception) {
                        Log.e("FitViewModel", "Error saving initial user profile", e)
                    }

                    // Only after profile is 100% saved, emit user and complete navigation
                    _currentUser.value = user
                    showWelcomeScreen = false
                    isCheckingProfile = false
                    isAuthLoading = false
                    authUiState = AuthUiState.Success
                    try {
                        onSuccess()
                    } catch (t: Throwable) {
                        Log.e("FitViewModel", "Error executing onSuccess callback after register", t)
                    }
                } else {
                    val msg = "Não foi possível criar a conta. Tente novamente."
                    authUiState = AuthUiState.Error(msg)
                    isAuthLoading = false
                    isCheckingProfile = false
                    try {
                        onError(msg)
                    } catch (t: Throwable) {
                        Log.e("FitViewModel", "Error invoking onError callback", t)
                    }
                }
            } catch (rawException: Throwable) {
                isAuthLoading = false
                isCheckingProfile = false
                val e = (rawException as? java.util.concurrent.ExecutionException)?.cause ?: rawException
                Log.e("FitViewModel", "Exception during performCreateUser: ${e.message}")

                val isCollision = e is FirebaseAuthUserCollisionException ||
                        e.message?.contains("email-already-in-use", ignoreCase = true) == true ||
                        e.message?.contains("already in use", ignoreCase = true) == true ||
                        e.message?.contains("EMAIL_EXISTS", ignoreCase = true) == true ||
                        e.message?.contains("409", ignoreCase = true) == true ||
                        e.message?.contains("auth/email-already-in-use", ignoreCase = true) == true

                if (isCollision) {
                    val msg = "Essa conta já existe! Você já possui um cadastro com este e-mail."
                    authUiState = AuthUiState.Error(msg)
                    if (onEmailAlreadyExists != null) {
                        onEmailAlreadyExists()
                    } else {
                        onError(msg)
                    }
                } else {
                    val msg = when {
                        e.message?.contains("network", ignoreCase = true) == true ||
                        e is java.io.IOException ||
                        e is FirebaseNetworkException ->
                            "Erro de conexão com a internet. Verifique sua rede e tente novamente."
                        e is FirebaseAuthWeakPasswordException ||
                        e.message?.contains("weak-password", ignoreCase = true) == true ->
                            "A senha digitada é muito fraca (mínimo de 6 caracteres)."
                        e is FirebaseAuthInvalidCredentialsException ||
                        e.message?.contains("invalid-email", ignoreCase = true) == true ->
                            "E-mail com formato inválido."
                        else -> e.localizedMessage ?: "Erro ao criar conta. Tente novamente."
                    }
                    authUiState = AuthUiState.Error(msg)
                    onError(msg)
                }
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val a = auth
        if (a == null) {
            onError("Serviço de Autenticação indisponível no momento")
            return
        }
        if (email.isBlank()) {
            onError("Por favor, informe seu e-mail.")
            return
        }
        a.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.localizedMessage ?: "Erro ao enviar e-mail de redefinição de senha.")
            }
    }

    fun logoutUser() {
        val departingUid = _currentUser.value?.uid ?: auth?.currentUser?.uid

        // 1. Stop any running in-memory timers or modal states immediately
        try {
            cancelTimer()
            dismissDailyMessageModal()
            dismissTimerAlert()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error canceling timers/modals on logout", e)
        }

        // 2. Cancel scheduled meal and supplement notifications
        try {
            val app = getApplication<Application>()
            com.example.MealNotificationScheduler.cancelAllMealReminders(app)
            SupplementScheduler.cancelAllSupplementReminders(app)
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error cancelling notifications on logout", e)
        }

        // 3. Set _currentUser to null first to detach active Firestore snapshot listeners safely
        _currentUser.value = null
        _customFichaNames.value = emptyMap()
        isCheckingProfile = false
        isAuthLoading = false
        showWelcomeScreen = true
        showOnboarding = false
        hasCompletedOnboarding = false
        currentTab = FitTab.TRAINING
        resetForm()

        // 4. Safely sign out from Firebase Auth
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error signing out", e)
        }

        // 5. Clear caches and Room database asynchronously
        clearTemporaryCaches(departingUid)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearAllLocalRoomData()
            } catch (e: Exception) {
                Log.e("FitViewModel", "Error clearing local room data on logout", e)
            }
        }
    }

    // --- Navigation & Flow States ---
    var showWelcomeScreen by mutableStateOf(true)
    var isCheckingProfile by mutableStateOf(true)
    var showOnboarding by mutableStateOf(false)
    var hasCompletedOnboarding by mutableStateOf(false)
    var showEditBiometricsDialog by mutableStateOf(false)
    var showWaterHistoryDialog by mutableStateOf(false)
    var showEditTodayWaterDialog by mutableStateOf(false)
    var onboardingStep by mutableStateOf(1)
    var currentTab by mutableStateOf(FitTab.TRAINING)
    var generationState by mutableStateOf<GenerationUiState>(GenerationUiState.Idle)

    // --- Daily Workout Tab State ---
    var selectedWorkoutDay by mutableStateOf("A")

    // Active tabs of workouts (dynamic based on available exercise days)
    val availableWorkoutDays: StateFlow<List<String>> = allExercises
        .map { exercises ->
            exercises.map { it.workoutDay }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("A", "B", "C"))

    // --- Custom Workout Ficha Names (Persistent sheet names e.g. "Tríceps e Bíceps") ---
    private val _customFichaNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val customFichaNames: StateFlow<Map<String, String>> = _customFichaNames.asStateFlow()

    private fun getFichaPrefs(uid: String? = null): SharedPreferences {
        val targetUid = uid ?: currentUser.value?.uid ?: auth?.currentUser?.uid ?: ""
        val prefsName = if (targetUid.isNotBlank()) "fitai_ficha_names_$targetUid" else "fitai_ficha_names"
        return getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    private fun loadCachedFichaNames(uid: String? = null): Map<String, String> {
        return try {
            val prefs = getFichaPrefs(uid)
            val all = prefs.all
            val map = mutableMapOf<String, String>()
            for ((key, value) in all) {
                if (key.startsWith("ficha_name_") && value is String && value.isNotBlank()) {
                    val day = key.removePrefix("ficha_name_")
                    map[day] = value
                }
            }
            map
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error loading cached ficha names", e)
            emptyMap()
        }
    }

    private fun saveCachedFichaName(day: String, name: String, uid: String? = null) {
        try {
            getFichaPrefs(uid).edit().putString("ficha_name_$day", name).apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error caching ficha name in prefs", e)
        }
    }

    private fun removeCachedFichaName(day: String, uid: String? = null) {
        try {
            getFichaPrefs(uid).edit().remove("ficha_name_$day").apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error removing cached ficha name from prefs", e)
        }
    }

    fun getDefaultWorkoutFichaName(day: String, isFeminino: Boolean = false): String {
        return if (isFeminino) {
            when(day) {
                "A" -> "Quadríceps & Adutores"
                "B" -> "Membros Superiores Completo"
                "C" -> "Glúteos & Isquiotibiais"
                "D" -> "Ombros, Core & Cardio"
                "E" -> "Posteriores & Panturrilhas"
                "F" -> "Treino de Força & Resistência"
                else -> "Treino $day"
            }
        } else {
            when(day) {
                "A" -> "Peito & Tríceps"
                "B" -> "Costas & Bíceps"
                "C" -> "Pernas Completo (Quadríceps e Panturrilhas)"
                "D" -> "Ombros & Trapézio"
                "E" -> "Core & Performance"
                "F" -> "Treino de Força & Resistência"
                else -> "Treino $day"
            }
        }
    }

    fun getWorkoutFichaName(day: String, isFeminino: Boolean = false): String {
        val custom = _customFichaNames.value[day]
        if (!custom.isNullOrBlank()) {
            return custom
        }
        return getDefaultWorkoutFichaName(day, isFeminino)
    }

    fun saveWorkoutFichaName(day: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val validName = if (trimmed.length > 40) trimmed.substring(0, 40) else trimmed
        val uid = currentUser.value?.uid ?: ""
        _customFichaNames.value = _customFichaNames.value + (day to validName)
        saveCachedFichaName(day, validName, uid)
        viewModelScope.launch {
            repository.saveWorkoutFichaName(uid, day, validName)
        }
    }

    // --- Onboarding / Edit Profile Form States (Default to 100% clean / empty) ---
    var formName by mutableStateOf("")
    var formAge by mutableStateOf("")
    var formWeight by mutableStateOf("")
    var formHeight by mutableStateOf("")
    var formFitnessLevel by mutableStateOf("Iniciante")
    var formRestrictions by mutableStateOf("")
    var formObjective by mutableStateOf("Hipertrofia")
    var formTrainingDays by mutableStateOf("M,T,W,T,F")
    var formGender by mutableStateOf("Masculino")
    var formAvatarPreset by mutableStateOf("avatar_1")

    /**
     * Resets all onboarding and user profile form input states back to clean defaults.
     */
    fun resetForm() {
        formName = ""
        formAge = ""
        formWeight = ""
        formHeight = ""
        formFitnessLevel = "Iniciante"
        formRestrictions = ""
        formObjective = "Hipertrofia"
        formTrainingDays = "M,T,W,T,F"
        formGender = "Masculino"
        formAvatarPreset = "avatar_1"
        onboardingStep = 1
        generationState = GenerationUiState.Idle
        showAppTour = false
        isTourPreChecked = false
    }

    /**
     * Clears temporary draft data, onboarding preferences, and cached forms in storage.
     */
    fun clearTemporaryCaches(explicitUid: String? = null) {
        try {
            val app = getApplication<Application>()
            app.getSharedPreferences("fitai_onboarding_draft", Context.MODE_PRIVATE).edit().clear().apply()
            app.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            app.getSharedPreferences("user_data", Context.MODE_PRIVATE).edit().clear().apply()
            app.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            app.getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            app.getSharedPreferences("fitai_ficha_names", Context.MODE_PRIVATE).edit().clear().apply()
            val uid = explicitUid ?: currentUser.value?.uid ?: auth?.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                app.getSharedPreferences("fitai_ficha_names_$uid", Context.MODE_PRIVATE).edit().clear().apply()
            }
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error clearing temporary caches", e)
        }
        _favoriteFoods.value = emptyList()
        _recentFoods.value = emptyList()
        _customFichaNames.value = emptyMap()
    }

    // Notification unread state
    var hasUnreadNotification by mutableStateOf(false)

    // --- Interactive App Tour Guided State (Rule: 1st time visit) ---
    var showAppTour by mutableStateOf(false)
    var isTourPreChecked by mutableStateOf(false)

    fun isTourSeenInPrefs(uid: String): Boolean {
        if (uid.isBlank()) return false
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("has_seen_tour_$uid", false)
        } catch (e: Exception) {
            false
        }
    }

    fun setTourSeenInPrefs(uid: String, seen: Boolean) {
        if (uid.isBlank()) return
        try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_seen_tour_$uid", seen).apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error saving tour seen in prefs", e)
        }
    }

    fun completeTour() {
        showAppTour = false
        val uid = currentUser.value?.uid ?: auth?.currentUser?.uid
        if (uid != null) {
            setTourSeenInPrefs(uid, true)
            viewModelScope.launch {
                themeRepository.setHasSeenTour(true)
                repository.markTourCompleted(uid, true)
            }
        }
    }

    fun skipTour() {
        completeTour()
    }

    fun restartTour() {
        showAppTour = true
    }

    // --- Persistence Helpers for Onboarding Status ---
    fun isOnboardingCompletedInPrefs(uid: String): Boolean {
        if (uid.isBlank()) return false
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("onboarding_completed_$uid", false)
        } catch (e: Exception) {
            false
        }
    }

    fun setOnboardingCompletedInPrefs(uid: String, completed: Boolean) {
        if (uid.isBlank()) return
        try {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("onboarding_completed_$uid", completed).apply()
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error saving onboarding completed state to prefs", e)
        }
    }

    fun isProfileComplete(profile: UserProfile?, uid: String): Boolean {
        if (profile == null) return false
        val isCompletedInPrefs = isOnboardingCompletedInPrefs(uid)
        val isCompletedInProfile = profile.onboardingCompleted
        val hasValidBiometrics = (profile.age ?: 0) > 0 && (profile.weight ?: 0.0) > 0.0 && (profile.height ?: 0.0) > 0.0
        return (isCompletedInProfile || isCompletedInPrefs) && hasValidBiometrics
    }

    // Populate form states when user profile updates & handle auth persistence correctly
    init {
        // Pre-check onboarding completion state from SharedPreferences before network/flow emission
        try {
            val initUser = auth?.currentUser
            if (initUser != null) {
                if (isOnboardingCompletedInPrefs(initUser.uid)) {
                    hasCompletedOnboarding = true
                    showOnboarding = false
                }
                loadFavoriteAndRecentFoods(initUser.uid)
            }
        } catch (e: Exception) {
            Log.e("FitViewModel", "Error checking initial onboarding status from prefs", e)
        }

        viewModelScope.launch {
            try {
                userProfile.collect { profile ->
                    try {
                        val user = _currentUser.value ?: auth?.currentUser
                        if (user != null) {
                            val uid = user.uid
                            showWelcomeScreen = false

                            if (profile != null) {
                                val completed = isProfileComplete(profile, uid)
                                if (completed || hasCompletedOnboarding) {
                                    if (!profile.name.isNullOrBlank()) formName = profile.name
                                    if ((profile.age ?: 0) > 0) formAge = profile.age.toString()
                                    if ((profile.weight ?: 0.0) > 0.0) formWeight = profile.weight.toString()
                                    if ((profile.height ?: 0.0) > 0.0) formHeight = profile.height.toString()
                                    if (!profile.fitnessLevel.isNullOrBlank()) formFitnessLevel = profile.fitnessLevel
                                    if (!profile.restrictions.isNullOrBlank() && profile.restrictions != "Nenhuma") formRestrictions = profile.restrictions
                                    if (!profile.objective.isNullOrBlank()) formObjective = profile.objective
                                    if (!profile.trainingDays.isNullOrBlank()) formTrainingDays = profile.trainingDays
                                    if (!profile.gender.isNullOrBlank()) formGender = profile.gender
                                    if (!profile.avatarPreset.isNullOrBlank()) formAvatarPreset = profile.avatarPreset
                                } else {
                                    // In the middle of onboarding or registration: do NOT overwrite active user input with blank/zero defaults
                                    if (formName.isBlank() && !profile.name.isNullOrBlank()) formName = profile.name
                                    if (formAge.isBlank() && (profile.age ?: 0) > 0) formAge = profile.age.toString()
                                    if (formWeight.isBlank() && (profile.weight ?: 0.0) > 0.0) formWeight = profile.weight.toString()
                                    if (formHeight.isBlank() && (profile.height ?: 0.0) > 0.0) formHeight = profile.height.toString()
                                    if (formFitnessLevel.isBlank() && !profile.fitnessLevel.isNullOrBlank()) formFitnessLevel = profile.fitnessLevel
                                }

                                val msg = profile.notificationMessage ?: ""
                                if (uid.isNotEmpty() && msg.isNotEmpty()) {
                                    try {
                                        val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
                                        val lastReadMsg = prefs.getString("last_read_msg_$uid", null)
                                        val isRead = prefs.getBoolean("is_read_msg_$uid", false)
                                        hasUnreadNotification = (lastReadMsg != msg || !isRead)
                                    } catch (e: Exception) {
                                        Log.e("FitViewModel", "Error reading preferences during boot", e)
                                        hasUnreadNotification = false
                                    }
                                } else {
                                    hasUnreadNotification = false
                                }

                                if (completed || isOnboardingCompletedInPrefs(uid)) {
                                    showOnboarding = false
                                    hasCompletedOnboarding = true
                                    setOnboardingCompletedInPrefs(uid, true)

                                    val hasSeenInProfile = profile.hasSeenTour
                                    val hasSeenInLocal = isTourSeenInPrefs(uid)
                                    if (!hasSeenInProfile && !hasSeenInLocal && !isTourPreChecked) {
                                        showAppTour = true
                                        isTourPreChecked = true
                                    }
                                } else {
                                    if (!hasCompletedOnboarding && !isCheckingProfile && !isAuthLoading) {
                                        showOnboarding = true
                                        hasCompletedOnboarding = false
                                    }
                                }
                            } else {
                                if (isOnboardingCompletedInPrefs(uid) || hasCompletedOnboarding) {
                                    hasCompletedOnboarding = true
                                    showOnboarding = false
                                } else if (!isCheckingProfile && !isAuthLoading) {
                                    hasCompletedOnboarding = false
                                    showOnboarding = true
                                }
                            }
                        } else {
                            if (!isAuthLoading) {
                                showWelcomeScreen = true
                                showOnboarding = false
                                hasCompletedOnboarding = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FitViewModel", "Error processing profile state during boot", e)
                    } finally {
                        if (!isAuthLoading) {
                            isCheckingProfile = false
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("FitViewModel", "Error collecting userProfile flow", e)
                if (!isAuthLoading) {
                    isCheckingProfile = false
                }
            }
        }

        // Safety timeout to prevent screen getting stuck forever on loading profile during boot
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            if (isCheckingProfile && !isAuthLoading) {
                isCheckingProfile = false
            }
        }
    }

    // Ensure selectedWorkoutDay switches if it becomes invalid
    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                val uid = user?.uid ?: ""
                if (uid.isNotBlank()) {
                    val cached = loadCachedFichaNames(uid)
                    _customFichaNames.value = cached
                    repository.getWorkoutFichaNames(uid).collect { repoNames ->
                        _customFichaNames.value = _customFichaNames.value + repoNames
                    }
                } else {
                    _customFichaNames.value = emptyMap()
                }
            }
        }
        viewModelScope.launch {
            availableWorkoutDays.collect { days ->
                if (days.isNotEmpty() && !days.contains(selectedWorkoutDay)) {
                    selectedWorkoutDay = days.first()
                }
            }
        }
    }

    // --- Nutrition write methods ---
    fun setTodayWaterAmount(amountMl: Int) {
        val uid = currentUser.value?.uid ?: return
        val dateStr = selectedDateStr.value
        viewModelScope.launch {
            repository.saveWaterForDate(uid, dateStr, amountMl)
        }
    }

    fun addWater(amountMl: Int) {
        val uid = currentUser.value?.uid ?: return
        val dateStr = selectedDateStr.value
        val currentAmount = selectedRegistroDiario.value?.aguaMl ?: 0
        viewModelScope.launch {
            repository.addWaterIncrementForDate(uid, dateStr, amountMl, currentAmount)
        }
    }

    fun addConsumoDiario(name: String, calories: Int, carbs: Double, protein: Double, fat: Double, mealName: String) {
        val uid = currentUser.value?.uid ?: return
        val dateStr = selectedDateStr.value
        val dateTimestamp = getTimestampForDateStr(dateStr)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertConsumoDiario(
                    uid,
                    ConsumoDiario(
                        name = name,
                        calories = calories,
                        carbs = carbs,
                        protein = protein,
                        fat = fat,
                        mealName = mealName,
                        date = dateTimestamp
                    )
                )
                val currentReg = selectedRegistroDiario.value
                repository.updateConsumoDiarioInRegistrosDiarios(
                    uid = uid,
                    dateStr = dateStr,
                    addedCalories = calories,
                    addedProtein = protein,
                    addedCarbs = carbs,
                    addedFat = fat,
                    currentCals = currentReg?.caloriasConsumidas ?: 0,
                    currentProt = currentReg?.protein ?: 0.0,
                    currentCarbs = currentReg?.carbs ?: 0.0,
                    currentFat = currentReg?.fat ?: 0.0
                )
            }
        }
    }

    suspend fun estimateFoodMacros(foodQuery: String): FoodItemJson {
        val objective = userProfile.value?.objective ?: "Hipertrofia"
        return repository.estimateFoodMacros(foodQuery, objective)
    }

    suspend fun analyzePlateImage(bitmap: android.graphics.Bitmap?, textDescription: String): com.example.data.api.PlateAnalysisResultJson {
        return repository.analyzePlateImageWithContext(bitmap, textDescription)
    }

    fun updateConsumoDiario(item: ConsumoDiario) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertConsumoDiario(uid, item)
            }
        }
    }

    fun addConsumoDiarioList(items: List<ConsumoDiario>) {
        val uid = currentUser.value?.uid ?: return
        val dateStr = selectedDateStr.value
        val dateTimestamp = getTimestampForDateStr(dateStr)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var totalCals = 0
                var totalProt = 0.0
                var totalCarbs = 0.0
                var totalFat = 0.0

                items.forEach { consumo ->
                    val c = consumo.copy(id = 0, date = dateTimestamp)
                    repository.insertConsumoDiario(uid, c)
                    totalCals += c.calories
                    totalProt += c.protein
                    totalCarbs += c.carbs
                    totalFat += c.fat
                }

                val currentReg = selectedRegistroDiario.value
                repository.updateConsumoDiarioInRegistrosDiarios(
                    uid = uid,
                    dateStr = dateStr,
                    addedCalories = totalCals,
                    addedProtein = totalProt,
                    addedCarbs = totalCarbs,
                    addedFat = totalFat,
                    currentCals = currentReg?.caloriasConsumidas ?: 0,
                    currentProt = currentReg?.protein ?: 0.0,
                    currentCarbs = currentReg?.carbs ?: 0.0,
                    currentFat = currentReg?.fat ?: 0.0
                )
            }
        }
    }

    fun copyMealFromYesterday(mealName: String, onResult: ((String) -> Unit)? = null) {
        if (currentUser.value?.uid == null) return
        val dateStr = selectedDateStr.value
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        try {
            val date = sdf.parse(dateStr)
            if (date != null) cal.time = date
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error parsing selectedDateStr", e)
        }
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayDateStr = sdf.format(cal.time)

        val yesterdayItems = allConsumoDiario.value.filter { item ->
            val itemDateStr = sdf.format(java.util.Date(item.date))
            itemDateStr == yesterdayDateStr && item.mealName.equals(mealName, ignoreCase = true)
        }

        if (yesterdayItems.isNotEmpty()) {
            val newItems = yesterdayItems.map { it.copy(id = 0) }
            addConsumoDiarioList(newItems)
            onResult?.invoke("${yesterdayItems.size} alimento(s) copiado(s) de ontem para $mealName!")
        } else {
            onResult?.invoke("Nenhum alimento registrado em '$mealName' no dia anterior.")
        }
    }

    fun deleteConsumoDiario(id: Int) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteConsumoDiario(uid, id)
            }
        }
    }

    // --- Resting Timer States ---
    private var countDownTimer: CountDownTimer? = null
    var isTimerActive by mutableStateOf(false)
    var timerSecondsRemaining by mutableStateOf(60)
    var timerMessage by mutableStateOf("")
    var showTimerAlert by mutableStateOf(false)
    var timerExercise by mutableStateOf<WorkoutExercise?>(null)
    var timerCompletedSeries by mutableStateOf(0)
    var scrollToExerciseIndexEvent by mutableStateOf<Int?>(null)
    var triggerShowWorkoutCompletedEvent by mutableStateOf(false)
    var showDailyMessageModal by mutableStateOf(false)
    var customRestTimerSeconds by mutableStateOf(60)
    var customWaterTargetMl by mutableStateOf<Int?>(null)
    var customWaterIncrementMl by mutableStateOf(250)

    // --- Custom Routine Modal States ---
    var showCustomWorkoutModal by mutableStateOf(false)
    var showEditWorkoutModal by mutableStateOf(false)
    var editingWorkoutDayState by mutableStateOf<String?>(null)
    var customWorkoutName by mutableStateOf("Treino D")
    var customExercisesList = mutableStateOf<List<Pair<String, String>>>(listOf("" to ""))

    var customExerciseNameState by mutableStateOf("")
    var customExerciseInstructionsState by mutableStateOf("")
    var customExerciseSeries = mutableStateOf<List<Pair<Double, String>>>(listOf(0.0 to "10"))
    var editingExerciseId by mutableStateOf<Int?>(null)
    var isGeneratingInstructionsState by mutableStateOf(false)

    // --- Meal Alternative Loading States ---
    var loadingMealsState = mutableStateMapOf<Int, Boolean>()

    fun startRestTimer(exercise: WorkoutExercise, completedSeries: Int) {
        countDownTimer?.cancel()
        timerExercise = exercise
        timerCompletedSeries = completedSeries
        timerSecondsRemaining = customRestTimerSeconds
        timerMessage = "Descanso para: ${exercise.name}"
        isTimerActive = true
        showTimerAlert = false

        countDownTimer = object : CountDownTimer(customRestTimerSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerSecondsRemaining = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                isTimerActive = false
                timerSecondsRemaining = 0
                showTimerAlert = true
                playTimerAlertEffect()
            }
        }.start()
    }

    fun startRestTimer(exerciseName: String) {
        val matched = allExercises.value.firstOrNull { it.name.equals(exerciseName, ignoreCase = true) }
        if (matched != null) {
            startRestTimer(matched, 1)
        } else {
            val fallback = WorkoutExercise(name = exerciseName, series = 3, reps = "10", currentWeight = 0.0, workoutDay = selectedWorkoutDay)
            startRestTimer(fallback, 1)
        }
    }

    var isTimerSoundEnabled by mutableStateOf(
        try {
            getApplication<Application>()
                .getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
                .getBoolean("timer_sound_enabled", true)
        } catch (e: Exception) {
            true
        }
    )

    fun toggleTimerSound() {
        isTimerSoundEnabled = !isTimerSoundEnabled
        try {
            getApplication<Application>()
                .getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("timer_sound_enabled", isTimerSoundEnabled)
                .apply()
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error saving timer sound preference", e)
        }
    }

    private fun playTimerAlertEffect() {
        // 1. Device Vibration alert (pulse-pause-pulse-pause-long burst)
        try {
            val context = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 250, 120, 250, 120, 450)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    try {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } catch (e: Exception) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 250, 120, 250, 120, 450), -1)
                }
            }
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error vibrating on timer finish", e)
        }

        // 2. Synthesized Audible Beep Sound Alert (if sound is enabled)
        if (!isTimerSoundEnabled) return

        viewModelScope.launch(Dispatchers.IO) {
            var toneGen: ToneGenerator? = null
            try {
                // High-priority audio stream with maximum clear fidelity
                toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                // Pulse 1: high beep (160ms)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                delay(200)
                // Pulse 2: high beep (160ms)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                delay(200)
                // Pulse 3: final workout alarm chime (380ms)
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 380)
                delay(420)
            } catch (e: Exception) {
                Log.e("FitAI_ViewModel", "ToneGenerator error, falling back to system ringtone", e)
                try {
                    val context = getApplication<Application>()
                    val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                    ringtone?.play()
                } catch (ex: Exception) {
                    Log.e("FitAI_ViewModel", "Error playing fallback sound on timer finish", ex)
                }
            } finally {
                try {
                    toneGen?.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    fun getLastWorkoutForExercise(exerciseName: String): HistoryEntry? {
        return historyEntries.value.firstOrNull { it.exerciseName.equals(exerciseName, ignoreCase = true) }
    }

    fun getPersonalRecordWeight(exerciseName: String): Double {
        return historyEntries.value
            .filter { it.exerciseName.equals(exerciseName, ignoreCase = true) }
            .map { it.weight }
            .maxOrNull() ?: 0.0
    }

    fun setExerciseCompleted(exercise: WorkoutExercise, isCompleted: Boolean) {
        val uid = currentUser.value?.uid ?: return
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val isCurrentlyCompletedToday = exercise.completed && exercise.completedDate == todayStr
        if (isCurrentlyCompletedToday == isCompleted) return
        val updated = exercise.copy(
            completed = isCompleted,
            completedDate = if (isCompleted) todayStr else null
        )
        viewModelScope.launch {
            repository.updateExercise(uid, updated)
        }
    }

    fun dismissTimerAlert() {
        showTimerAlert = false
    }

    fun openDailyMessageModal() {
        markNotificationAsRead()
        showDailyMessageModal = true
    }

    fun dismissDailyMessageModal() {
        showDailyMessageModal = false
    }

    fun markNotificationAsRead() {
        hasUnreadNotification = false
        val uid = currentUser.value?.uid ?: ""
        val currentMsg = userProfile.value?.notificationMessage ?: ""
        if (uid.isNotEmpty()) {
            val prefs = getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_read_msg_$uid", currentMsg)
                .putBoolean("is_read_msg_$uid", true)
                .apply()
        }
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        isTimerActive = false
    }

    fun updateCustomWaterTarget(target: Int?) {
        customWaterTargetMl = target
    }

    fun updateCustomWaterIncrement(increment: Int) {
        customWaterIncrementMl = increment
    }

    fun toggleExerciseCompleted(exercise: WorkoutExercise) {
        val uid = currentUser.value?.uid ?: return
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val isCurrentlyCompletedToday = exercise.completed && exercise.completedDate == todayStr
        val newCompleted = !isCurrentlyCompletedToday
        val updated = exercise.copy(
            completed = newCompleted,
            completedDate = if (newCompleted) todayStr else null
        )
        viewModelScope.launch {
            repository.updateExercise(uid, updated)
            if (newCompleted) {
                startRestTimer(exercise.name)
                if (exercise.currentWeight > 0.0) {
                    repository.insertHistoryEntry(
                        uid,
                        HistoryEntry(
                            exerciseName = exercise.name,
                            weight = exercise.currentWeight,
                            reps = exercise.reps,
                            date = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun updateExerciseWeight(exercise: WorkoutExercise, weight: Double) {
        val uid = currentUser.value?.uid ?: return
        val roundedWeight = if (weight.isNaN() || weight.isInfinite() || weight <= 0.0) 0.0 else Math.round(weight * 10.0) / 10.0
        val updated = exercise.copy(currentWeight = roundedWeight)
        viewModelScope.launch {
            repository.updateExercise(uid, updated)
        }
    }

    fun logExerciseExecution(exerciseName: String, weight: Double, reps: String) {
        val uid = currentUser.value?.uid ?: return
        val roundedWeight = if (weight.isNaN() || weight.isInfinite() || weight <= 0.0) 0.0 else Math.round(weight * 10.0) / 10.0
        if (roundedWeight <= 0.0) return
        viewModelScope.launch {
            repository.insertHistoryEntry(
                uid,
                HistoryEntry(
                    exerciseName = exerciseName,
                    weight = roundedWeight,
                    reps = reps,
                    date = System.currentTimeMillis()
                )
            )
        }
    }

    fun generatePlanFromAi() {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            generationState = GenerationUiState.Generating
            try {
                val ageInt = formAge.toIntOrNull() ?: 28
                val weightDouble = formWeight.toDoubleOrNull() ?: 80.0
                val heightDouble = formHeight.toDoubleOrNull() ?: 175.0
                val fitnessLevelStr = if (formFitnessLevel.isNotBlank()) formFitnessLevel else "Iniciante"
                val nameStr = if (formName.trim().isEmpty()) "Alex Silva" else formName.trim()
                val objectiveStr = if (formObjective.isNotBlank()) formObjective else "Hipertrofia"
                val trainingDaysStr = if (formTrainingDays.isNotBlank()) formTrainingDays else "M,T,W,T,F"
                val genderStr = if (formGender.isNotBlank()) formGender else "Masculino"
                val restrictionsStr = if (formRestrictions.isNotBlank()) formRestrictions else "Nenhuma"

                // Create profile object with EXACT user inputs immediately
                val updatedProfile = UserProfile(
                    id = 1,
                    name = nameStr,
                    age = ageInt,
                    weight = weightDouble,
                    height = heightDouble,
                    fitnessLevel = fitnessLevelStr,
                    restrictions = restrictionsStr,
                    objective = objectiveStr,
                    trainingDays = trainingDaysStr,
                    gender = genderStr,
                    avatarPreset = formAvatarPreset,
                    onboardingCompleted = true
                )

                // Save profile directly to Room and Firestore immediately
                repository.saveUserProfile(uid, updatedProfile)
                repository.savePesoHistory(uid, weightDouble)

                repository.generateAndSavePlan(
                    uid = uid,
                    name = nameStr,
                    age = ageInt,
                    weight = weightDouble,
                    height = heightDouble,
                    fitnessLevel = fitnessLevelStr,
                    restrictions = restrictionsStr,
                    objective = objectiveStr,
                    trainingDays = trainingDaysStr,
                    gender = genderStr,
                    avatarPreset = formAvatarPreset
                )

                generationState = GenerationUiState.Success("Treino e Dieta criados com sucesso pela IA!")
                hasCompletedOnboarding = true
                showOnboarding = false
                setOnboardingCompletedInPrefs(uid, true)
                currentTab = FitTab.TRAINING
                selectedWorkoutDay = "A"
                if (!isTourSeenInPrefs(uid)) {
                    showAppTour = true
                    isTourPreChecked = true
                }
            } catch (e: Exception) {
                generationState = GenerationUiState.Error(e.message ?: "Erro desconhecido ao gerar plano com Gemini.")
            }
        }
    }

    fun suggestAlternativeMeal(meal: Meal) {
        val uid = currentUser.value?.uid ?: return
        val mealId = meal.id
        loadingMealsState[mealId] = true
        viewModelScope.launch {
            try {
                repository.suggestMealAlternative(
                    uid = uid,
                    meal = meal,
                    restrictions = formRestrictions,
                    objective = formObjective
                )
            } catch (e: Exception) {
                Log.e("FitAI_ViewModel", "Erro ao sugerir alternativa de refeição", e)
            } finally {
                loadingMealsState[mealId] = false
            }
        }
    }

    fun openEditProfile() {
        showOnboarding = true
    }

    fun closeOnboarding() {
        hasCompletedOnboarding = true
        showOnboarding = false
        val user = currentUser.value ?: auth?.currentUser
        if (user != null) {
            setOnboardingCompletedInPrefs(user.uid, true)
        }
        currentTab = FitTab.TRAINING
    }

    fun saveEditedBiometrics(
        name: String,
        age: String,
        weight: String,
        height: String,
        gender: String,
        objective: String,
        fitnessLevel: String = "Iniciante"
    ) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            try {
                val current = userProfile.value
                val newWeight = weight.toDoubleOrNull() ?: (current?.weightSafe ?: 80.0)
                val newAge = age.toIntOrNull() ?: (current?.ageSafe ?: 28)
                val newHeight = height.toDoubleOrNull() ?: (current?.heightSafe ?: 175.0)

                if (current != null) {
                    val updated = current.copy(
                        name = name,
                        age = newAge,
                        weight = newWeight,
                        height = newHeight,
                        gender = gender,
                        objective = objective,
                        fitnessLevel = fitnessLevel
                    )
                    repository.saveUserProfile(uid, updated)
                    repository.savePesoHistory(uid, newWeight)
                } else {
                    val newProfile = UserProfile(
                        id = 1,
                        uid = uid,
                        name = name,
                        age = newAge,
                        weight = newWeight,
                        height = newHeight,
                        gender = gender,
                        objective = objective,
                        fitnessLevel = fitnessLevel,
                        onboardingCompleted = true
                    )
                    repository.saveUserProfile(uid, newProfile)
                    repository.savePesoHistory(uid, newWeight)
                }
            } catch (e: Exception) {
                Log.e("FitAI_ViewModel", "Error saving edited biometrics", e)
            } finally {
                formName = name
                formAge = age
                formWeight = weight
                formHeight = height
                formGender = gender
                formObjective = objective
                formFitnessLevel = fitnessLevel

                showEditBiometricsDialog = false
            }
        }
    }

    private fun copyUriToLocalFile(uriString: String, uid: String): String {
        try {
            val context = getApplication<Application>()
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "content") {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "profile_picture_${uid}.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    return android.net.Uri.fromFile(file).toString()
                }
            }
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error copying content URI to local file", e)
        }
        return uriString
    }

    fun updateProfilePicture(presetName: String) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            val localUri = if (presetName.startsWith("content://")) {
                copyUriToLocalFile(presetName, uid)
            } else {
                presetName
            }
            
            // Save in local SharedPreferences for fast, secure access
            try {
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("user_prefs_$uid", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("avatar_uri", localUri).apply()
            } catch (e: Exception) {
                Log.e("FitAI_ViewModel", "Error saving image URI to SharedPreferences", e)
            }

            val current = userProfile.value
            if (current != null) {
                val updated = current.copy(avatarPreset = localUri)
                repository.saveUserProfile(uid, updated)
            }
        }
    }

    fun updateProfileGender(gender: String) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            val current = userProfile.value
            if (current != null) {
                val updated = current.copy(gender = gender)
                repository.saveUserProfile(uid, updated)
            }
        }
    }

    fun addSeriesToCustomExercise() {
        customExerciseSeries.value = customExerciseSeries.value + (0.0 to "10")
    }

    fun removeSeriesFromCustomExercise(index: Int) {
        val current = customExerciseSeries.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(index)
            customExerciseSeries.value = current
        }
    }

    fun updateSeriesCarga(index: Int, carga: Double) {
        val current = customExerciseSeries.value.toMutableList()
        if (index in current.indices) {
            val roundedCarga = if (carga.isNaN() || carga.isInfinite() || carga <= 0.0) 0.0 else Math.round(carga * 10.0) / 10.0
            current[index] = roundedCarga to current[index].second
            customExerciseSeries.value = current
        }
    }

    fun updateSeriesReps(index: Int, reps: String) {
        val current = customExerciseSeries.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].first to reps
            customExerciseSeries.value = current
        }
    }

    fun updateSeriesCount(newCount: Int) {
        val count = newCount.coerceIn(1, 10)
        val current = customExerciseSeries.value.toMutableList()
        if (current.size < count) {
            val lastReps = current.lastOrNull()?.second ?: "10"
            val lastCarga = current.lastOrNull()?.first ?: 0.0
            while (current.size < count) {
                current.add(lastCarga to lastReps)
            }
        } else if (current.size > count) {
            while (current.size > count) {
                current.removeAt(current.size - 1)
            }
        }
        customExerciseSeries.value = current
    }

    fun updateAllReps(newReps: String) {
        val current = customExerciseSeries.value.map { (carga, _) -> carga to newReps }
        customExerciseSeries.value = current
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            repository.deleteExercise(uid, exercise)
        }
    }

    fun generateInstructionsForExercise(exerciseName: String) {
        val name = exerciseName.trim()
        if (name.isEmpty()) return
        isGeneratingInstructionsState = true
        viewModelScope.launch {
            try {
                val instructions = repository.generateExerciseInstructions(name)
                customExerciseInstructionsState = instructions
            } catch (e: Exception) {
                Log.e("FitAI_ViewModel", "Erro ao gerar instruções", e)
            } finally {
                isGeneratingInstructionsState = false
            }
        }
    }

    fun saveCustomExerciseToDay(day: String) {
        val uid = currentUser.value?.uid ?: return
        val name = customExerciseNameState.trim()
        if (name.isEmpty()) return

        val seriesCount = customExerciseSeries.value.size
        val repsString = customExerciseSeries.value.map { it.second.toIntOrNull() ?: 0 }.joinToString("-")
        val rawAverage = if (customExerciseSeries.value.isNotEmpty()) customExerciseSeries.value.map { it.first }.average() else 0.0
        val averageWeight = if (rawAverage.isNaN() || rawAverage.isInfinite() || rawAverage <= 0.0) 0.0 else Math.round(rawAverage * 10.0) / 10.0
        val inst = customExerciseInstructionsState.trim()

        val existingOrderIndex = if (editingExerciseId != null) {
            allExercises.value.find { it.id == editingExerciseId }?.orderIndex ?: 0
        } else {
            allExercises.value.count { it.workoutDay.equals(day, ignoreCase = true) }
        }

        viewModelScope.launch {
            val ex = WorkoutExercise(
                id = editingExerciseId ?: 0,
                workoutDay = day,
                name = name,
                series = seriesCount,
                reps = repsString,
                currentWeight = averageWeight,
                completed = false,
                badge = if (editingExerciseId != null) "EDITADO" else "MANUAL",
                instructions = inst.ifBlank { getSpecificExerciseInstructions(name) },
                orderIndex = existingOrderIndex
            )
            if (editingExerciseId != null) {
                repository.updateExercise(uid, ex)
            } else {
                repository.insertExercise(uid, ex)
            }

            customExerciseNameState = ""
            customExerciseInstructionsState = ""
            customExerciseSeries.value = listOf(0.0 to "10")
            editingExerciseId = null
        }
    }

    fun moveExerciseUp(exercise: WorkoutExercise) {
        val uid = currentUser.value?.uid ?: return
        val dayExercises = allExercises.value
            .filter { it.workoutDay.equals(exercise.workoutDay, ignoreCase = true) }
            .sortedBy { it.orderIndex }
            .toMutableList()

        val index = dayExercises.indexOfFirst { it.id == exercise.id }
        if (index > 0) {
            val item = dayExercises.removeAt(index)
            dayExercises.add(index - 1, item)
            viewModelScope.launch {
                repository.reorderExercises(uid, dayExercises)
            }
        }
    }

    fun moveExerciseDown(exercise: WorkoutExercise) {
        val uid = currentUser.value?.uid ?: return
        val dayExercises = allExercises.value
            .filter { it.workoutDay.equals(exercise.workoutDay, ignoreCase = true) }
            .sortedBy { it.orderIndex }
            .toMutableList()

        val index = dayExercises.indexOfFirst { it.id == exercise.id }
        if (index >= 0 && index < dayExercises.size - 1) {
            val item = dayExercises.removeAt(index)
            dayExercises.add(index + 1, item)
            viewModelScope.launch {
                repository.reorderExercises(uid, dayExercises)
            }
        }
    }

    fun startEditingExercise(exercise: WorkoutExercise) {
        editingExerciseId = exercise.id
        customExerciseNameState = exercise.name
        customExerciseInstructionsState = exercise.instructions ?: getSpecificExerciseInstructions(exercise.name)
        val repsList = try {
            exercise.reps.split("-").map { it.trim() }
        } catch (e: Exception) {
            listOf("10")
        }
        val initialWeight = if (exercise.currentWeight.isNaN() || exercise.currentWeight.isInfinite() || exercise.currentWeight <= 0.0) 0.0 else Math.round(exercise.currentWeight * 10.0) / 10.0
        customExerciseSeries.value = repsList.map { initialWeight to it }
    }

    fun cancelEditingExercise() {
        editingExerciseId = null
        customExerciseNameState = ""
        customExerciseInstructionsState = ""
        customExerciseSeries.value = listOf(0.0 to "10")
    }

    fun addCustomWorkoutFichaWithTimer() {
        val uid = currentUser.value?.uid ?: return
        val name = customWorkoutName.trim()
        if (name.isEmpty()) return

        val exercises = customExercisesList.value.filter { it.first.trim().isNotEmpty() }.mapIndexed { index, (exName, seriesReps) ->
            var series = 3
            var reps = "10"
            try {
                val digits = "\\d+".toRegex().findAll(seriesReps).mapNotNull { it.value.toIntOrNull() }.toList()
                if (digits.isNotEmpty()) series = digits[0]
                if (digits.size > 1) reps = digits[1].toString()
            } catch (e: Exception) {
                // Default
            }

            WorkoutExercise(
                workoutDay = name,
                name = exName,
                series = series,
                reps = reps,
                currentWeight = 0.0,
                completed = false,
                badge = "PERSONALIZADO",
                instructions = getSpecificExerciseInstructions(exName),
                orderIndex = index
            )
        }

        if (exercises.isNotEmpty()) {
            viewModelScope.launch {
                repository.addCustomWorkoutFicha(uid, name, exercises)
                showCustomWorkoutModal = false
                selectedWorkoutDay = name
                customWorkoutName = "Treino D"
                customExercisesList.value = listOf("" to "")
            }
        }
    }

    fun addExerciseFieldToCustomModal() {
        customExercisesList.value = customExercisesList.value + ("" to "3 séries de 10 reps")
    }

    fun removeExerciseFieldFromCustomModal(index: Int) {
        val current = customExercisesList.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(index)
            customExercisesList.value = current
        }
    }

    fun updateCustomExerciseField(index: Int, name: String, seriesAndReps: String) {
        val current = customExercisesList.value.toMutableList()
        current[index] = name to seriesAndReps
        customExercisesList.value = current
    }

    fun saveCustomWorkoutFicha() {
        val uid = currentUser.value?.uid ?: return
        val name = customWorkoutName.trim()
        if (name.isEmpty()) return

        val exercises = customExercisesList.value.filter { it.first.trim().isNotEmpty() }.map { (exName, seriesReps) ->
            var series = 3
            var reps = "10"
            try {
                val digits = "\\d+".toRegex().findAll(seriesReps).mapNotNull { it.value.toIntOrNull() }.toList()
                if (digits.isNotEmpty()) series = digits[0]
                if (digits.size > 1) reps = digits[1].toString()
            } catch (e: Exception) {
                // Keep default
            }

            WorkoutExercise(
                workoutDay = name,
                name = exName,
                series = series,
                reps = reps,
                currentWeight = 0.0,
                completed = false,
                badge = "PERSONALIZADO",
                instructions = getSpecificExerciseInstructions(exName)
            )
        }

        if (exercises.isNotEmpty()) {
            viewModelScope.launch {
                repository.addCustomWorkoutFicha(uid, name, exercises)
                showCustomWorkoutModal = false
                selectedWorkoutDay = name
                customWorkoutName = "Treino D"
                customExercisesList.value = listOf("" to "")
            }
        }
    }

    fun deleteCustomWorkoutDay(day: String) {
        val uid = currentUser.value?.uid ?: return
        _customFichaNames.value = _customFichaNames.value - day
        removeCachedFichaName(day, uid)
        viewModelScope.launch {
            repository.deleteWorkoutFichaName(uid, day)
            // Delete exercises in Firestore for this day
            try {
                val db = AppDatabase.getDatabase(getApplication())
                db.appDao().deleteExercisesForDay(day)
            } catch (e: Exception) {
                // Ignore Room errors as Room is deprecated in favor of Firestore
            }
            // Real delete is performed by updating or re-saving. We also delete them via custom query:
            withContext(Dispatchers.IO) {
                try {
                    val col = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("usuarios").document(uid).collection("treinos")
                    col.whereEqualTo("workoutDay", day).get().addOnSuccessListener { snapshot ->
                        try {
                            for (doc in snapshot.documents) {
                                doc.reference.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("FitViewModel", "Error deleting documents in deleteCustomWorkoutDay", e)
                        }
                    }.addOnFailureListener { e ->
                        Log.e("FitViewModel", "Error querying day in deleteCustomWorkoutDay", e)
                    }
                } catch (e: Exception) {
                    Log.e("FitViewModel", "Exception in deleteCustomWorkoutDay Firestore ops", e)
                }
            }
        }
    }

    fun saveCustomWorkoutFichaWithSeries() {
        val uid = currentUser.value?.uid ?: return
        val name = customWorkoutName.trim()
        val exName = customExerciseNameState.trim()
        if (name.isNotEmpty() && exName.isNotEmpty()) {
            val seriesCount = customExerciseSeries.value.size
            val repsString = customExerciseSeries.value.map { it.second.toIntOrNull() ?: 0 }.joinToString("-")
            val rawAverage = if (customExerciseSeries.value.isNotEmpty()) customExerciseSeries.value.map { it.first }.average() else 0.0
            val averageWeight = if (rawAverage.isNaN() || rawAverage.isInfinite() || rawAverage <= 0.0) 0.0 else Math.round(rawAverage * 10.0) / 10.0

            val exercises = listOf(
                WorkoutExercise(
                    workoutDay = name,
                    name = exName,
                    series = seriesCount,
                    reps = repsString,
                    currentWeight = averageWeight,
                    completed = false,
                    badge = "PERSONALIZADO"
                )
            )

            viewModelScope.launch {
                repository.addCustomWorkoutFicha(uid, name, exercises)
                showCustomWorkoutModal = false
                selectedWorkoutDay = name
                customWorkoutName = "Treino D"
                customExerciseNameState = ""
                customExerciseSeries.value = listOf(0.0 to "10")
            }
        }
    }

    fun logout() {
        logoutUser()
    }

    fun resetAndDeleteAccount(context: Context, onComplete: (() -> Unit)? = null) {
        val user = auth?.currentUser
        val uid = user?.uid ?: ""

        viewModelScope.launch {
            isCheckingProfile = true

            // 1. Delete all user documents and subcollections in Firestore
            if (uid.isNotBlank()) {
                try {
                    repository.deleteAllUserData(uid)
                } catch (e: Exception) {
                    Log.e("FitAI", "Error deleting Firestore user data", e)
                }
            }

            // 2. Clear local Room SQLite Database (all tables)
            try {
                repository.clearAllLocalRoomData()
            } catch (e: Exception) {
                Log.e("FitAI", "Error clearing local Room database", e)
            }

            // 3. Clear all local SharedPreferences
            try {
                if (uid.isNotBlank()) {
                    context.getSharedPreferences("user_prefs_$uid", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    setOnboardingCompletedInPrefs(uid, false)
                }
                getApplication<Application>().getSharedPreferences("fitai_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
            } catch (e: Exception) {
                Log.e("FitAI", "Error clearing user shared preferences", e)
            }

            // 4. Clear DataStore local preferences
            try {
                themeRepository.clearAllPreferences()
            } catch (e: Exception) {
                Log.e("FitAI", "Error clearing theme dataStore", e)
            }

            // 5. Delete Firebase Auth account if available
            if (user != null) {
                try {
                    val deleteTask = user.delete()
                    com.google.android.gms.tasks.Tasks.await(deleteTask)
                } catch (e: Exception) {
                    Log.e("FitAI", "Error deleting FirebaseAuth account", e)
                }
            }

            // 6. Sign out & cancel all scheduled background notifications/reminders
            try {
                val app = getApplication<Application>()
                com.example.MealNotificationScheduler.cancelAllMealReminders(app)
                SupplementScheduler.cancelAllSupplementReminders(app)
            } catch (e: Exception) {
                Log.e("FitViewModel", "Error cancelling notifications on resetAndDeleteAccount", e)
            }

            try {
                auth?.signOut()
            } catch (e: Exception) {
                Log.e("FitAI", "Error signing out", e)
            }

            // 7. Reset UI and navigation states to redirect immediately to Welcome / Login screen
            resetForm()
            clearTemporaryCaches(uid)
            _customFichaNames.value = emptyMap()
            isCheckingProfile = false
            showWelcomeScreen = true
            showOnboarding = false
            currentTab = FitTab.TRAINING

            onComplete?.invoke()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            countDownTimer?.cancel()
            countDownTimer = null
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error cancelling countdown timer onCleared", e)
        }

        try {
            authStateListener?.let { listener ->
                auth?.removeAuthStateListener(listener)
            }
            authStateListener = null
        } catch (e: Exception) {
            Log.e("FitAI_ViewModel", "Error removing authStateListener onCleared", e)
        }
    }
}

fun <K, V> mutableStateMapOf() = androidx.compose.runtime.mutableStateMapOf<K, V>()
