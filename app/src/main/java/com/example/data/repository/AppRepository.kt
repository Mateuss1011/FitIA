package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.db.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.channels.awaitClose

class AppRepository(
    private val appDao: AppDao,
    private val apiService: GeminiApiService,
    private val moshi: Moshi
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error initializing FirebaseFirestore", e)
            null
        }
    }

    private fun parseUserProfileFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot, uid: String): UserProfile? {
        if (!doc.exists()) return null
        val diasTreinoList = (doc.get("diasDaSemana") ?: doc.get("diasTreino")) as? List<*>
        val trainingDaysParsed = if (diasTreinoList != null) {
            diasTreinoList.filterIsInstance<String>().joinToString(",")
        } else {
            doc.getString("trainingDays") ?: "Seg,Qua,Sex"
        }
        val fitnessLevelParsed = doc.getString("nivel")
            ?: doc.getString("fitnessLevel")
            ?: "Iniciante"
        val onboardingCompleted = doc.getBoolean("onboardingCompleted")
            ?: doc.getBoolean("onboarding_completed")
            ?: false
        val hasSeenTour = doc.getBoolean("hasSeenTour")
            ?: doc.getBoolean("has_seen_tour")
            ?: false

        val parsedAge = doc.getLong("age")?.toInt()
            ?: (doc.get("age") as? Number)?.toInt()
            ?: doc.getString("age")?.toIntOrNull()
        val parsedWeight = doc.getDouble("weight")
            ?: (doc.get("weight") as? Number)?.toDouble()
            ?: doc.getString("weight")?.toDoubleOrNull()
        val parsedHeight = doc.getDouble("height")
            ?: (doc.get("height") as? Number)?.toDouble()
            ?: doc.getString("height")?.toDoubleOrNull()

        return UserProfile(
            id = 1,
            uid = uid,
            name = doc.getString("name") ?: "",
            email = doc.getString("email") ?: "",
            age = parsedAge,
            weight = parsedWeight,
            height = parsedHeight,
            fitnessLevel = fitnessLevelParsed,
            restrictions = doc.getString("restrictions") ?: "Nenhuma",
            objective = doc.getString("objective") ?: "Hipertrofia",
            trainingDays = trainingDaysParsed,
            notificationMessage = doc.getString("notificationMessage") ?: "",
            gender = doc.getString("gender") ?: "Masculino",
            avatarPreset = doc.getString("avatarPreset") ?: "avatar_1",
            onboardingCompleted = onboardingCompleted,
            hasSeenTour = hasSeenTour
        )
    }

    suspend fun fetchUserProfileDirect(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val fs = firestore
        if (fs != null) {
            try {
                // 1. Tenta usuarios/{uid}/biometria/perfil
                val docBiometria = try {
                    Tasks.await(
                        fs.collection("usuarios").document(uid)
                            .collection("biometria").document("perfil")
                            .get()
                    )
                } catch (e: Exception) {
                    null
                }
                var profile = if (docBiometria != null && docBiometria.exists()) {
                    parseUserProfileFromDoc(docBiometria, uid)
                } else null

                // 2. Se não encontrado, tenta usuarios/{uid}
                if (profile == null) {
                    val docUsuario = try {
                        Tasks.await(fs.collection("usuarios").document(uid).get())
                    } catch (e: Exception) {
                        null
                    }
                    if (docUsuario != null && docUsuario.exists()) {
                        profile = parseUserProfileFromDoc(docUsuario, uid)
                    }
                }

                // 3. Se ainda não encontrado, tenta users/{uid}
                if (profile == null) {
                    val docUser = try {
                        Tasks.await(fs.collection("users").document(uid).get())
                    } catch (e: Exception) {
                        null
                    }
                    if (docUser != null && docUser.exists()) {
                        profile = parseUserProfileFromDoc(docUser, uid)
                    }
                }

                if (profile != null) {
                    try {
                        appDao.insertUserProfile(profile)
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Error caching fetched profile in Room", e)
                    }
                    return@withContext profile
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Error fetching direct userProfile from Firestore: ${e.message}", e)
            }
        }
        try {
            return@withContext appDao.getUserProfile().firstOrNull()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error reading local user profile from Room: ${e.message}", e)
            return@withContext null
        }
    }

    // --- Firestore Reactive Listeners ---

    fun getUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            val roomJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                appDao.getUserProfile().collect { roomProfile ->
                    trySend(roomProfile)
                }
            }
            awaitClose { roomJob.cancel() }
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("biometria").document("perfil")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to userProfile", error)
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val local = appDao.getUserProfile().firstOrNull()
                        trySend(local)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val profile = parseUserProfileFromDoc(snapshot, uid)
                        if (profile != null) {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    appDao.insertUserProfile(profile)
                                } catch (e: Exception) {
                                    Log.e("AppRepository", "Error caching userProfile in Room", e)
                                }
                            }
                            trySend(profile)
                        } else {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                val local = appDao.getUserProfile().firstOrNull()
                                trySend(local)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error parsing userProfile", e)
                    }
                } else {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        // Fallback check on root usuarios/users docs if biometria/perfil subdoc is missing
                        var fallbackProfile: UserProfile? = null
                        try {
                            val uDoc = Tasks.await(fs.collection("usuarios").document(uid).get())
                            if (uDoc != null && uDoc.exists()) {
                                fallbackProfile = parseUserProfileFromDoc(uDoc, uid)
                            }
                        } catch (_: Exception) {}
                        if (fallbackProfile == null) {
                            try {
                                val userDoc = Tasks.await(fs.collection("users").document(uid).get())
                                if (userDoc != null && userDoc.exists()) {
                                    fallbackProfile = parseUserProfileFromDoc(userDoc, uid)
                                }
                            } catch (_: Exception) {}
                        }
                        if (fallbackProfile != null) {
                            try {
                                appDao.insertUserProfile(fallbackProfile)
                            } catch (e: Exception) {
                                Log.e("AppRepository", "Error caching fallback profile in Room", e)
                            }
                            trySend(fallbackProfile)
                        } else {
                            val local = appDao.getUserProfile().firstOrNull()
                            trySend(local)
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun getAllExercises(uid: String): Flow<List<WorkoutExercise>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("treinos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to exercises", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            WorkoutExercise(
                                id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                                workoutDay = doc.getString("workoutDay") ?: "A",
                                name = doc.getString("name") ?: "",
                                series = doc.getLong("series")?.toInt() ?: 3,
                                reps = doc.getString("reps") ?: "10",
                                currentWeight = doc.getDouble("currentWeight") ?: 0.0,
                                completed = doc.getBoolean("completed") ?: false,
                                completedDate = doc.getString("completedDate"),
                                badge = doc.getString("badge"),
                                instructions = doc.getString("instructions"),
                                orderIndex = doc.getLong("orderIndex")?.toInt() ?: 0
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getAllMeals(uid: String): Flow<List<Meal>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("refeicoes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to meals", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Meal(
                                id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                                name = doc.getString("name") ?: "",
                                description = doc.getString("description") ?: "",
                                calories = doc.getLong("calories")?.toInt() ?: 0,
                                protein = doc.getString("protein") ?: "",
                                carbs = doc.getString("carbs") ?: "",
                                fat = doc.getString("fat") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getHistory(uid: String): Flow<List<HistoryEntry>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("historico")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to history", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            HistoryEntry(
                                id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                                exerciseName = doc.getString("exerciseName") ?: "",
                                weight = doc.getDouble("weight") ?: 0.0,
                                reps = doc.getString("reps") ?: "",
                                date = doc.getLong("date") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.date }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getAllConsumoDiario(uid: String): Flow<List<ConsumoDiario>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("historico_nutricao")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to consumo diario", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            ConsumoDiario(
                                id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                                name = doc.getString("name") ?: "",
                                calories = doc.getLong("calories")?.toInt() ?: 0,
                                protein = doc.getDouble("protein") ?: 0.0,
                                carbs = doc.getDouble("carbs") ?: 0.0,
                                fat = doc.getDouble("fat") ?: 0.0,
                                mealName = doc.getString("mealName") ?: "Geral",
                                date = doc.getLong("date") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.date }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getAllRegistroAgua(uid: String): Flow<List<RegistroAgua>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("registro_agua")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to registro agua", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            RegistroAgua(
                                id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                                amountMl = doc.getLong("amountMl")?.toInt() ?: 0,
                                date = doc.getLong("date") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.date }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- Write and Delete Actions ---

    suspend fun saveUserProfile(uid: String, profile: UserProfile, email: String = "") {
        withContext(Dispatchers.IO) {
            try {
                appDao.insertUserProfile(profile)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error saving user profile to Room", e)
            }
        }
        val daysList = profile.trainingDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val userEmail = if (email.isNotBlank()) email else (try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" } catch (e: Exception) { "" })
        val now = com.google.firebase.Timestamp.now()
        val data = hashMapOf(
            "email" to userEmail,
            "name" to profile.name,
            "age" to profile.age,
            "weight" to profile.weight,
            "height" to profile.height,
            "fitnessLevel" to profile.fitnessLevel,
            "nivel" to profile.fitnessLevel,
            "restrictions" to profile.restrictions,
            "objective" to profile.objective,
            "trainingDays" to profile.trainingDays,
            "diasTreino" to daysList,
            "diasDaSemana" to daysList,
            "notificationMessage" to profile.notificationMessage,
            "gender" to profile.gender,
            "avatarPreset" to profile.avatarPreset,
            "onboardingCompleted" to profile.onboardingCompleted,
            "onboarding_completed" to profile.onboardingCompleted,
            "hasSeenTour" to profile.hasSeenTour,
            "has_seen_tour" to profile.hasSeenTour,
            "createdAt" to now
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            try {
                // Write to users/{uid}
                val t1 = fs.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                com.google.android.gms.tasks.Tasks.await(t1)

                // Write to usuarios/{uid}/biometria/perfil
                val t2 = fs.collection("usuarios").document(uid)
                    .collection("biometria").document("perfil")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                com.google.android.gms.tasks.Tasks.await(t2)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error saving user profile to Firestore", e)
            }
        }
    }

    suspend fun markTourCompleted(uid: String, hasSeen: Boolean = true) {
        val data = hashMapOf<String, Any>(
            "hasSeenTour" to hasSeen,
            "has_seen_tour" to hasSeen
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            try {
                fs.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                fs.collection("usuarios").document(uid)
                    .collection("biometria").document("perfil")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("AppRepository", "Error updating hasSeenTour in Firestore", e)
            }
        }
    }

    suspend fun savePesoHistory(uid: String, peso: Double) {
        val data = hashMapOf(
            "peso" to peso,
            "data" to com.google.firebase.Timestamp.now()
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("historico_peso").document()
                .set(data)
        }
    }

    fun getPesoHistory(uid: String): Flow<List<PesoHistoryEntry>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("historico_peso")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to weight history", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val peso = doc.getDouble("peso") ?: return@mapNotNull null
                        val dataVal = doc.get("data")
                        val timestamp = when (dataVal) {
                            is Long -> dataVal
                            is com.google.firebase.Timestamp -> dataVal.toDate().time
                            is java.util.Date -> dataVal.time
                            else -> System.currentTimeMillis()
                        }
                        PesoHistoryEntry(
                            id = doc.id,
                            peso = peso,
                            data = timestamp
                        )
                    }
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateExercise(uid: String, exercise: WorkoutExercise) {
        val docId = exercise.id.toString()
        val data = hashMapOf(
            "id" to exercise.id,
            "workoutDay" to exercise.workoutDay,
            "name" to exercise.name,
            "series" to exercise.series,
            "reps" to exercise.reps,
            "currentWeight" to exercise.currentWeight,
            "completed" to exercise.completed,
            "completedDate" to exercise.completedDate,
            "badge" to exercise.badge,
            "instructions" to (exercise.instructions ?: getSpecificExerciseInstructions(exercise.name)),
            "orderIndex" to exercise.orderIndex
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("treinos").document(docId)
                .set(data)
        }
    }

    suspend fun reorderExercises(uid: String, list: List<WorkoutExercise>) {
        list.forEachIndexed { index, exercise ->
            val updated = exercise.copy(orderIndex = index)
            updateExercise(uid, updated)
        }
    }

    suspend fun insertExercise(uid: String, exercise: WorkoutExercise) {
        val finalId = if (exercise.id == 0) (100000..999999).random() else exercise.id
        val updated = exercise.copy(id = finalId)
        updateExercise(uid, updated)
    }

    suspend fun deleteExercise(uid: String, exercise: WorkoutExercise) {
        val docId = exercise.id.toString()
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("treinos").document(docId)
                .delete()
        }
    }

    suspend fun insertHistoryEntry(uid: String, entry: HistoryEntry) {
        val finalId = if (entry.id == 0) (100000..999999).random() else entry.id
        val data = hashMapOf(
            "id" to finalId,
            "exerciseName" to entry.exerciseName,
            "weight" to entry.weight,
            "reps" to entry.reps,
            "date" to entry.date
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("historico").document(finalId.toString())
                .set(data)
        }
    }

    suspend fun insertConsumoDiario(uid: String, consumo: ConsumoDiario) {
        val finalId = if (consumo.id == 0) (100000..999999).random() else consumo.id
        val data = hashMapOf(
            "id" to finalId,
            "name" to consumo.name,
            "calories" to consumo.calories,
            "protein" to consumo.protein,
            "carbs" to consumo.carbs,
            "fat" to consumo.fat,
            "mealName" to consumo.mealName,
            "date" to consumo.date
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("historico_nutricao").document(finalId.toString())
                .set(data)
        }
    }

    suspend fun deleteConsumoDiario(uid: String, id: Int) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("historico_nutricao").document(id.toString())
                .delete()
        }
    }

    suspend fun insertRegistroAgua(uid: String, agua: RegistroAgua) {
        val finalId = if (agua.id == 0) (100000..999999).random() else agua.id
        val data = hashMapOf(
            "id" to finalId,
            "amountMl" to agua.amountMl,
            "date" to agua.date
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("registro_agua").document(finalId.toString())
                .set(data)
        }
    }

    suspend fun clearAllConsumoDiario(uid: String) {
        withContext(Dispatchers.IO) {
            try {
                val fs = firestore ?: return@withContext
                val collection = fs.collection("usuarios").document(uid).collection("historico_nutricao")
                collection.get().addOnSuccessListener { snapshot ->
                    try {
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error deleting items in clearAllConsumoDiario", e)
                    }
                }.addOnFailureListener { e ->
                    Log.e("FitAI_Firestore", "Error fetching collection in clearAllConsumoDiario", e)
                }
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Exception in clearAllConsumoDiario", e)
            }
        }
    }

    suspend fun clearAllRegistroAgua(uid: String) {
        withContext(Dispatchers.IO) {
            try {
                val fs = firestore ?: return@withContext
                val collection = fs.collection("usuarios").document(uid).collection("registro_agua")
                collection.get().addOnSuccessListener { snapshot ->
                    try {
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error deleting items in clearAllRegistroAgua", e)
                    }
                }.addOnFailureListener { e ->
                    Log.e("FitAI_Firestore", "Error fetching collection in clearAllRegistroAgua", e)
                }
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Exception in clearAllRegistroAgua", e)
            }
        }
    }

    // --- Daily Storage (/usuarios/{uid}/registros_diarios/{YYYY-MM-DD}) ---

    fun getTodayDateStr(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    fun getTodayRegistroDiario(uid: String, dateStr: String = getTodayDateStr()): Flow<RegistroDiario> = callbackFlow {
        val fs = firestore ?: run {
            trySend(RegistroDiario(dateStr = dateStr, aguaMl = 0, caloriasConsumidas = 0))
            close()
            return@callbackFlow
        }
        val docRef = fs.collection("usuarios").document(uid)
            .collection("registros_diarios").document(dateStr)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FitAI_Firestore", "Error listening to today registro diario", error)
                trySend(RegistroDiario(dateStr = dateStr, aguaMl = 0, caloriasConsumidas = 0))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val agua = snapshot.getLong("aguaMl")?.toInt() ?: 0
                val cals = snapshot.getLong("caloriasConsumidas")?.toInt() ?: 0
                val prot = snapshot.getDouble("protein") ?: 0.0
                val carbs = snapshot.getDouble("carbs") ?: 0.0
                val fat = snapshot.getDouble("fat") ?: 0.0
                val ts = snapshot.getLong("timestamp") ?: System.currentTimeMillis()
                val supsList = (snapshot.get("suplementos_tomados") ?: snapshot.get("suplementosTomados")) as? List<*>
                val suplementosTomadosList = supsList?.filterIsInstance<String>() ?: emptyList()
                trySend(
                    RegistroDiario(
                        dateStr = dateStr,
                        aguaMl = agua,
                        caloriasConsumidas = cals,
                        protein = prot,
                        carbs = carbs,
                        fat = fat,
                        suplementosTomados = suplementosTomadosList,
                        timestamp = ts
                    )
                )
            } else {
                // Initialize new day entry with 0 water and 0 calories
                val initialData = hashMapOf(
                    "dateStr" to dateStr,
                    "aguaMl" to 0,
                    "caloriasConsumidas" to 0,
                    "protein" to 0.0,
                    "carbs" to 0.0,
                    "fat" to 0.0,
                    "suplementos_tomados" to emptyList<String>(),
                    "timestamp" to System.currentTimeMillis()
                )
                docRef.set(initialData)
                trySend(RegistroDiario(dateStr = dateStr, aguaMl = 0, caloriasConsumidas = 0))
            }
        }
        awaitClose { listener.remove() }
    }

    fun getAllRegistrosDiarios(uid: String): Flow<List<RegistroDiario>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("registros_diarios")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to registros diarios", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val date = doc.getString("dateStr") ?: doc.id
                        val agua = doc.getLong("aguaMl")?.toInt() ?: 0
                        val cals = doc.getLong("caloriasConsumidas")?.toInt() ?: 0
                        val prot = doc.getDouble("protein") ?: 0.0
                        val carbs = doc.getDouble("carbs") ?: 0.0
                        val fat = doc.getDouble("fat") ?: 0.0
                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val supsList = (doc.get("suplementos_tomados") ?: doc.get("suplementosTomados")) as? List<*>
                        val suplementosTomadosList = supsList?.filterIsInstance<String>() ?: emptyList()
                        RegistroDiario(
                            dateStr = date,
                            aguaMl = agua,
                            caloriasConsumidas = cals,
                            protein = prot,
                            carbs = carbs,
                            fat = fat,
                            suplementosTomados = suplementosTomadosList,
                            timestamp = ts
                        )
                    }.sortedByDescending { it.dateStr }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveWaterForDate(uid: String, dateStr: String, amountMl: Int) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            val docRef = fs.collection("usuarios").document(uid)
                .collection("registros_diarios").document(dateStr)
            val data = hashMapOf(
                "dateStr" to dateStr,
                "aguaMl" to amountMl,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            insertRegistroAgua(uid, RegistroAgua(amountMl = amountMl, date = System.currentTimeMillis()))
        }
    }

    // --- Suplementação Methods ---

    fun getAllSuplementos(uid: String): Flow<List<Suplemento>> = callbackFlow {
        val fs = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("suplementos_cadastrados")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error fetching suplementos", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Suplemento(
                                id = doc.getString("id") ?: doc.id,
                                nome = doc.getString("nome") ?: "",
                                dose = doc.getString("dose") ?: "",
                                horario = doc.getString("horario") ?: "09:00",
                                ativo = doc.getBoolean("ativo") ?: true
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveSuplemento(uid: String, suplemento: Suplemento) {
        val id = if (suplemento.id.isEmpty()) java.util.UUID.randomUUID().toString() else suplemento.id
        val data = hashMapOf(
            "id" to id,
            "nome" to suplemento.nome,
            "dose" to suplemento.dose,
            "horario" to suplemento.horario,
            "ativo" to suplemento.ativo
        )
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("suplementos_cadastrados").document(id)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    suspend fun deleteSuplemento(uid: String, suplementoId: String) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            fs.collection("usuarios").document(uid)
                .collection("suplementos_cadastrados").document(suplementoId)
                .delete()
        }
    }

    suspend fun toggleSuplementoTomado(uid: String, dateStr: String, suplementoId: String, tomado: Boolean) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            val docRef = fs.collection("usuarios").document(uid)
                .collection("registros_diarios").document(dateStr)

            if (tomado) {
                val data = hashMapOf(
                    "dateStr" to dateStr,
                    "suplementos_tomados" to com.google.firebase.firestore.FieldValue.arrayUnion(suplementoId),
                    "timestamp" to System.currentTimeMillis()
                )
                docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            } else {
                docRef.update("suplementos_tomados", com.google.firebase.firestore.FieldValue.arrayRemove(suplementoId))
            }
        }
    }

    suspend fun deleteAllUserData(uid: String) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            val userDocRef = fs.collection("usuarios").document(uid)
            val subcollections = listOf(
                "biometria",
                "treinos",
                "refeicoes",
                "historico",
                "historico_nutricao",
                "registro_agua",
                "water_logs",
                "historico_peso",
                "peso_history",
                "registros_diarios",
                "suplementos_cadastrados",
                "fichas_nomes"
            )

            for (col in subcollections) {
                try {
                    val queryTask = userDocRef.collection(col).get()
                    val snapshot = com.google.android.gms.tasks.Tasks.await(queryTask)
                    for (doc in snapshot.documents) {
                        val deleteTask = doc.reference.delete()
                        com.google.android.gms.tasks.Tasks.await(deleteTask)
                    }
                } catch (e: Exception) {
                    Log.e("FitAI_Firestore", "Error deleting subcollection $col for $uid", e)
                }
            }

            try {
                val deleteDocTask = userDocRef.delete()
                com.google.android.gms.tasks.Tasks.await(deleteDocTask)
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Error deleting user doc $uid", e)
            }

            try {
                val deleteUsersDocTask = fs.collection("users").document(uid).delete()
                com.google.android.gms.tasks.Tasks.await(deleteUsersDocTask)
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Error deleting users collection doc $uid", e)
            }
        }
    }

    suspend fun clearAllLocalRoomData() {
        withContext(Dispatchers.IO) {
            try {
                appDao.clearAllExercises()
                appDao.clearAllMeals()
                appDao.clearUserProfile()
                appDao.clearAllHistory()
                appDao.clearAllConsumoDiario()
                appDao.clearAllRegistroAgua()
                appDao.clearAllWorkoutFichaNames()
            } catch (e: Exception) {
                Log.e("AppRepository", "Error clearing local Room database", e)
            }
        }
    }

    suspend fun addWaterIncrementForDate(uid: String, dateStr: String, incrementMl: Int, currentAmount: Int) {
        val newTotal = (currentAmount + incrementMl).coerceAtLeast(0)
        saveWaterForDate(uid, dateStr, newTotal)
    }

    suspend fun updateConsumoDiarioInRegistrosDiarios(
        uid: String,
        dateStr: String,
        addedCalories: Int,
        addedProtein: Double,
        addedCarbs: Double,
        addedFat: Double,
        currentCals: Int,
        currentProt: Double,
        currentCarbs: Double,
        currentFat: Double
    ) {
        withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext
            val docRef = fs.collection("usuarios").document(uid)
                .collection("registros_diarios").document(dateStr)
            val data = hashMapOf(
                "dateStr" to dateStr,
                "caloriasConsumidas" to (currentCals + addedCalories).coerceAtLeast(0),
                "protein" to (currentProt + addedProtein).coerceAtLeast(0.0),
                "carbs" to (currentCarbs + addedCarbs).coerceAtLeast(0.0),
                "fat" to (currentFat + addedFat).coerceAtLeast(0.0),
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    suspend fun addCustomWorkoutFicha(uid: String, dayName: String, exercises: List<WorkoutExercise>) {
        withContext(Dispatchers.IO) {
            try {
                val fs = firestore ?: return@withContext
                val collection = fs.collection("usuarios").document(uid).collection("treinos")
                collection.whereEqualTo("workoutDay", dayName).get().addOnSuccessListener { snapshot ->
                    try {
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                        for (ex in exercises) {
                            val finalId = if (ex.id == 0) (100000..999999).random() else ex.id
                            val data = hashMapOf(
                                "id" to finalId,
                                "workoutDay" to ex.workoutDay,
                                "name" to ex.name,
                                "series" to ex.series,
                                "reps" to ex.reps,
                                "currentWeight" to ex.currentWeight,
                                "completed" to ex.completed,
                                "badge" to ex.badge,
                                "instructions" to (ex.instructions ?: getSpecificExerciseInstructions(ex.name))
                            )
                            collection.document(finalId.toString()).set(data)
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error saving exercises in addCustomWorkoutFicha", e)
                    }
                }.addOnFailureListener { e ->
                    Log.e("FitAI_Firestore", "Error querying day in addCustomWorkoutFicha", e)
                }
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Exception in addCustomWorkoutFicha", e)
            }
        }
    }

    fun getWorkoutFichaNames(uid: String): Flow<Map<String, String>> = callbackFlow {
        val fs = firestore
        if (fs == null || uid.isEmpty()) {
            val roomJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                appDao.getAllWorkoutFichaNamesFlow().collect { roomList ->
                    trySend(roomList.associate { it.workoutDay to it.name })
                }
            }
            awaitClose { roomJob.cancel() }
            return@callbackFlow
        }
        val listener = fs.collection("usuarios").document(uid)
            .collection("fichas_nomes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FitAI_Firestore", "Error listening to fichas_nomes", error)
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val local = appDao.getAllWorkoutFichaNamesDirect()
                        trySend(local.associate { it.workoutDay to it.name })
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val map = mutableMapOf<String, String>()
                    val roomEntities = mutableListOf<WorkoutFichaName>()
                    for (doc in snapshot.documents) {
                        val day = doc.getString("workoutDay") ?: doc.id
                        val name = doc.getString("name") ?: ""
                        if (day.isNotEmpty() && name.isNotEmpty()) {
                            map[day] = name
                            roomEntities.add(WorkoutFichaName(workoutDay = day, name = name))
                        }
                    }
                    if (roomEntities.isNotEmpty()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                for (entity in roomEntities) {
                                    appDao.insertWorkoutFichaName(entity)
                                }
                            } catch (e: Exception) {
                                Log.e("AppRepository", "Error caching ficha names in Room", e)
                            }
                        }
                    }
                    trySend(map)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveWorkoutFichaName(uid: String, day: String, name: String) {
        withContext(Dispatchers.IO) {
            try {
                appDao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = day, name = name))
            } catch (e: Exception) {
                Log.e("AppRepository", "Error saving ficha name to Room", e)
            }
            val fs = firestore
            if (fs != null && uid.isNotEmpty()) {
                try {
                    val data = hashMapOf(
                        "workoutDay" to day,
                        "name" to name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    fs.collection("usuarios").document(uid)
                        .collection("fichas_nomes").document(day)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    Log.e("AppRepository", "Error saving ficha name to Firestore", e)
                }
            }
        }
    }

    suspend fun deleteWorkoutFichaName(uid: String, day: String) {
        withContext(Dispatchers.IO) {
            try {
                appDao.deleteWorkoutFichaName(day)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error deleting ficha name from Room", e)
            }
            val fs = firestore
            if (fs != null && uid.isNotEmpty()) {
                try {
                    fs.collection("usuarios").document(uid)
                        .collection("fichas_nomes").document(day)
                        .delete()
                } catch (e: Exception) {
                    Log.e("AppRepository", "Error deleting ficha name from Firestore", e)
                }
            }
        }
    }

    suspend fun generateAndSavePlan(
        uid: String,
        name: String,
        age: Int,
        weight: Double,
        height: Double,
        fitnessLevel: String,
        restrictions: String,
        objective: String,
        trainingDays: String,
        gender: String = "Masculino",
        avatarPreset: String = "avatar_1"
    ): FitAiGenerationResultJson = withContext(Dispatchers.IO) {
        val daysList = trainingDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val numDays = if (daysList.size in 1..7) daysList.size else 3
        val lettersList = ('A'..'Z').take(numDays).map { "$it" }
        val lettersStr = lettersList.joinToString(", ") { "'$it'" }

        val systemInstructionText = """
            Você é o FitAI em 2026, um Personal Trainer de elite e Nutricionista hiper-tecnológico.
            Você cria planos altamente individualizados focados em biometria e metas de alta performance.
            O usuário selecionou exatamente $numDays dias de treino na semana ($trainingDays).
            Você deve SEMPRE retornar um JSON que siga estritamente o seguinte formato de esquema:
            {
              "workoutPlan": [
                {
                  "workoutDay": "A",
                  "name": "Nome do Exercício em Português",
                  "series": 4,
                  "reps": "8 a 10",
                  "badge": "EXPLOSIVO"
                }
              ],
              "meals": [
                {
                  "name": "Nome da Refeição (ex: Café da Manhã, Almoço, Lanche, Jantar)",
                  "description": "Descrição detalhada dos alimentos com foco em performance",
                  "calories": 450,
                  "protein": "30g",
                  "carbs": "40g",
                  "fat": "12g"
                }
              ],
              "notificationMessage": "Mensagem motivacional de alto impacto para hoje!"
            }

            Restrições e Diretrizes de Treino (CRÍTICAS):
            1. MAPEAMENTO DIRETO 1:1 DE DIAS PARA FICHAS: O número de dias de treino selecionados é $numDays ($trainingDays). Você DEVE criar EXATAMENTE $numDays fichas de treino distintas no array 'workoutPlan', identificadas no campo 'workoutDay' pelas letras $lettersStr.
               - 3 dias selecionados = 3 Fichas (Treino A, Treino B, Treino C)
               - 4 dias selecionados = 4 Fichas (Treino A, Treino B, Treino C, Treino D)
               - 5 dias selecionados = 5 Fichas (Treino A, Treino B, Treino C, Treino D, Treino E)
               - 6 dias selecionados = 6 Fichas (Treino A, Treino B, Treino C, Treino D, Treino E, Treino F)
               - NUNCA limite as fichas a apenas 3 (ABC) quando o usuário selecionar 4 ou mais dias.
            2. VOLUME DE TREINO (EXATAMENTE 7 EXERCÍCIOS POR FICHA): CADA ficha de treino (cada letra em $lettersStr) DEVE conter OBRIGATORIAMENTE EXATAMENTE 7 exercícios distintos.
            3. DISTRIBUIÇÃO MUSCULAR: Distribua os 7 exercícios de forma coerente entre os agrupamentos musculares trabalhados naquela ficha (exemplo para dia de Peito e Tríceps: 4 exercícios para Peito + 3 exercícios para Tríceps; para Costas e Bíceps: 4 para Costas + 3 para Bíceps; para Pernas: 4 para Quadríceps/Glúteos + 3 para Posteriores/Panturrilha).
            4. ADAPTE os exercícios às restrições médicas informadas pelo usuário. Se ele tiver lesões ou dores (ex: dor no joelho), substitua exercícios prejudiciais por alternativas seguras e funcionais.
            5. Baseie as calorias e macronutrientes das exatamente 4 refeições estritamente no objetivo informado (ex: Hipertrofia = superávit calórico, Emagrecimento = déficit calórico saudável, Condicionamento = equilíbrio denso em nutrientes).
            6. O campo 'badge' pode conter marcações curtas como 'EXPLOSIVO', 'BI-SET', 'FALHA', 'DROPSET', ou null se não houver indicador específico. NUNCA utilize porcentagens ou caracteres estranhos.
            7. Retorne APENAS o JSON válido. Não inclua markdown como ```json ou qualquer outro texto explicativo fora do JSON.
        """.trimIndent()

        val userPrompt = """
            Dados Biométricos do Usuário de 2026:
            - Nome: $name
            - Idade: $age anos
            - Peso: $weight kg
            - Altura: $height cm
            - Nível/Tempo de Academia: $fitnessLevel
            - Restrições Médicas/Dores: $restrictions
            - Objetivo Principal: $objective
            - Dias de Treino Marcados na Semana ($numDays dias): $trainingDays

            Gere o plano integrado com EXATAMENTE $numDays fichas de treino distintas ($lettersStr), onde CADA ficha DEVE possuir EXATAMENTE 7 exercícios distintos, além das refeições e notificação motivacional personalizada agora.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.7f
            )
        )

        try {
            val response = apiService.generateContent(request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Backend de IA retornou uma resposta sem conteúdo.")

            val adapter = moshi.adapter(FitAiGenerationResultJson::class.java)
            val cleanJson = cleanJsonString(jsonText)
            val result = adapter.fromJson(cleanJson)
                ?: throw IllegalStateException("Falha ao analisar o JSON gerado pelo backend de IA.")

            saveResultToDb(uid, result, name, age, weight, height, fitnessLevel, restrictions, objective, trainingDays, gender, avatarPreset)
            return@withContext result

        } catch (e: Exception) {
            Log.w("FitAI_Warning", "Falha ao conectar ou processar resposta do backend de IA, acionando Fallback local: ${e.message}")
            val fallbackResult = getMockPlan(objective, name, gender, numDays)
            saveResultToDb(uid, fallbackResult, name, age, weight, height, fitnessLevel, restrictions, objective, trainingDays, gender, avatarPreset)
            return@withContext fallbackResult
        }
    }

    suspend fun suggestMealAlternative(
        uid: String,
        meal: Meal,
        restrictions: String,
        objective: String
    ): Meal = withContext(Dispatchers.IO) {
        val resultMeal = try {
            val prompt = """
                Você é um Nutricionista de alta performance.
                O usuário deseja uma alternativa culinária de substituição equivalente para a seguinte refeição específica:
                - Nome original: ${meal.name}
                - Descrição: ${meal.description}
                - Calorias estimadas: ${meal.calories} kcal
                - Proteínas: ${meal.protein}
                - Carboidratos: ${meal.carbs}
                - Gorduras: ${meal.fat}

                Dados adicionais do usuário:
                - Objetivo: $objective
                - Restrições alimentares: $restrictions

                Por favor, sugira uma substituição nutritiva, saborosa e que mantenha praticamente as mesmas calorias (Margem de +-30kcal) e distribuição de macronutrientes.
                Você DEVE responder estritamente no seguinte formato JSON:
                {
                  "description": "Nova descrição dos alimentos substitutos com foco em performance e bem-estar",
                  "calories": ${meal.calories},
                  "protein": "g de proteínas aproximadas",
                  "carbs": "g de carboidratos aproximados",
                  "fat": "g de gorduras aproximadas"
                }
                Retorne APENAS o JSON válido, sem qualquer formatação Markdown ou texto explicativo extra.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.8f
                )
            )

            val response = apiService.generateContent(request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Não foi possível gerar uma alternativa.")

            val cleanJson = cleanJsonString(jsonText)
            val adapter = moshi.adapter(MealAlternativeJson::class.java)
            val result = adapter.fromJson(cleanJson)
                ?: throw IllegalStateException("Falha ao processar a alternativa gerada.")

            meal.copy(
                description = result.description,
                calories = result.calories,
                protein = result.protein,
                carbs = result.carbs,
                fat = result.fat
            )
        } catch (e: Exception) {
            val mockAlt = getMockMealAlternative(meal)
            meal.copy(
                description = mockAlt.description,
                calories = mockAlt.calories,
                protein = mockAlt.protein,
                carbs = mockAlt.carbs,
                fat = mockAlt.fat
            )
        }

        val data = hashMapOf(
            "id" to resultMeal.id,
            "name" to resultMeal.name,
            "description" to resultMeal.description,
            "calories" to resultMeal.calories,
            "protein" to resultMeal.protein,
            "carbs" to resultMeal.carbs,
            "fat" to resultMeal.fat
        )
        firestore?.collection("usuarios")?.document(uid)
            ?.collection("refeicoes")?.document(resultMeal.id.toString())
            ?.set(data)

        resultMeal
    }

    private suspend fun saveResultToDb(
        uid: String,
        result: FitAiGenerationResultJson,
        name: String,
        age: Int,
        weight: Double,
        height: Double,
        fitnessLevel: String,
        restrictions: String,
        objective: String,
        trainingDays: String,
        gender: String = "Masculino",
        avatarPreset: String = "avatar_1"
    ) {
        val profile = UserProfile(
            name = name,
            age = age,
            weight = weight,
            height = height,
            fitnessLevel = fitnessLevel,
            restrictions = restrictions,
            objective = objective,
            trainingDays = trainingDays,
            notificationMessage = result.notificationMessage,
            gender = gender,
            avatarPreset = avatarPreset,
            onboardingCompleted = true
        )
        saveUserProfile(uid, profile)
        savePesoHistory(uid, weight)

        withContext(Dispatchers.IO) {
            try {
                val roomExercises = result.workoutPlan.mapIndexed { idx, it ->
                    val initialWeight = when (it.name) {
                        "Supino Reto com Barra" -> 70.0
                        "Supino Inclinado com Halteres" -> 24.0
                        "Crucifixo Máquina" -> 50.0
                        "Tríceps Pulley (Corda)" -> 40.0
                        "Tríceps Testa" -> 20.0
                        "Puxada Alta (Pulldown)" -> 60.0
                        "Remada Curvada" -> 50.0
                        "Remada Baixa Triângulo" -> 45.0
                        "Rosca Direta com Barra" -> 25.0
                        "Rosca Martelo com Halteres" -> 14.0
                        "Agachamento Livre" -> 80.0
                        "Leg Press 45°" -> 160.0
                        "Cadeira Extensora" -> 55.0
                        "Desenvolvimento com Halteres" -> 18.0
                        "Elevação Lateral" -> 10.0
                        else -> 0.0
                    }
                    WorkoutExercise(
                        id = (100000..999999).random(),
                        workoutDay = it.workoutDay,
                        name = it.name,
                        series = it.series,
                        reps = it.reps,
                        currentWeight = initialWeight,
                        completed = false,
                        badge = it.badge,
                        instructions = it.instructions ?: getSpecificExerciseInstructions(it.name),
                        orderIndex = idx
                    )
                }
                appDao.clearAllExercises()
                appDao.insertExercises(roomExercises)

                val roomMeals = result.meals.map { it ->
                    Meal(
                        id = (100000..999999).random(),
                        name = it.name,
                        description = it.description,
                        calories = it.calories,
                        protein = it.protein,
                        carbs = it.carbs,
                        fat = it.fat
                    )
                }
                appDao.clearAllMeals()
                appDao.insertMeals(roomMeals)
            } catch (e: Exception) {
                Log.e("AppRepository", "Error saving exercises/meals to Room", e)
            }

            val fs = firestore ?: return@withContext
            try {
                val treinosCol = fs.collection("usuarios").document(uid).collection("treinos")
                treinosCol.get().addOnSuccessListener { snapshot ->
                    try {
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                        for (it in result.workoutPlan) {
                            val initialWeight = when (it.name) {
                                "Supino Reto com Barra" -> 70.0
                                "Supino Inclinado com Halteres" -> 24.0
                                "Crucifixo Máquina" -> 50.0
                                "Tríceps Pulley (Corda)" -> 40.0
                                "Tríceps Testa" -> 20.0
                                "Puxada Alta (Pulldown)" -> 60.0
                                "Remada Curvada" -> 50.0
                                "Remada Baixa Triângulo" -> 45.0
                                "Rosca Direta com Barra" -> 25.0
                                "Rosca Martelo com Halteres" -> 14.0
                                "Agachamento Livre" -> 80.0
                                "Leg Press 45°" -> 160.0
                                "Cadeira Extensora" -> 55.0
                                "Desenvolvimento com Halteres" -> 18.0
                                "Elevação Lateral" -> 10.0
                                else -> 0.0
                            }
                            val finalId = (100000..999999).random()
                            val data = hashMapOf(
                                "id" to finalId,
                                "workoutDay" to it.workoutDay,
                                "name" to it.name,
                                "series" to it.series,
                                "reps" to it.reps,
                                "currentWeight" to initialWeight,
                                "completed" to false,
                                "badge" to it.badge,
                                "instructions" to (it.instructions ?: getSpecificExerciseInstructions(it.name))
                            )
                            treinosCol.document(finalId.toString()).set(data)
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error saving treinos to Firestore", e)
                    }
                }.addOnFailureListener { e ->
                    Log.e("FitAI_Firestore", "Error querying treinos in saveResultToDb", e)
                }

                val refeicoesCol = fs.collection("usuarios").document(uid).collection("refeicoes")
                refeicoesCol.get().addOnSuccessListener { snapshot ->
                    try {
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                        for (it in result.meals) {
                            val finalId = (100000..999999).random()
                            val data = hashMapOf(
                                "id" to finalId,
                                "name" to it.name,
                                "description" to it.description,
                                "calories" to it.calories,
                                "protein" to it.protein,
                                "carbs" to it.carbs,
                                "fat" to it.fat
                            )
                            refeicoesCol.document(finalId.toString()).set(data)
                        }
                    } catch (e: Exception) {
                        Log.e("FitAI_Firestore", "Error saving refeicoes to Firestore", e)
                    }
                }.addOnFailureListener { e ->
                    Log.e("FitAI_Firestore", "Error querying refeicoes in saveResultToDb", e)
                }
            } catch (e: Exception) {
                Log.e("FitAI_Firestore", "Exception in saveResultToDb Firestore ops", e)
            }
        }
    }

    companion object {
        fun getMockPlan(objective: String, name: String, gender: String = "Masculino", numDays: Int = 3): FitAiGenerationResultJson {
        val notificationMessage = when (objective) {
            "Hipertrofia" -> "Hora de construir fibras, $name! Foco na sobrecarga progressiva e descanso."
            "Emagrecimento" -> "Cárdio e disciplina, $name! Déficit calórico estratégico ativo para hoje."
            else -> "Condicionamento de alta performance ativado, $name! Vamos dominar o treino."
        }

        val letters = ('A'..'Z').take(numDays).map { "$it" }

        val allMockExercises = if (gender.equals("Feminino", ignoreCase = true)) {
            listOf(
                // Ficha A (Quadríceps e Glúteos - 7 exercícios)
                WorkoutPlanExerciseJson("A", "Agachamento Livre", 4, "10-12", "DROPSET"),
                WorkoutPlanExerciseJson("A", "Leg Press 45°", 4, "12", "BI-SET"),
                WorkoutPlanExerciseJson("A", "Cadeira Extensora", 3, "15", "FALHA"),
                WorkoutPlanExerciseJson("A", "Agachamento Sumô com Halter", 3, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("A", "Cadeira Adutora", 3, "12", null),
                WorkoutPlanExerciseJson("A", "Afundo no Banco / Passada", 3, "12 cada perna", "BI-SET"),
                WorkoutPlanExerciseJson("A", "Panturrilha no Leg Press", 4, "15", "FALHA"),

                // Ficha B (Superiores e Core - 7 exercícios)
                WorkoutPlanExerciseJson("B", "Puxada Alta (Pulldown)", 4, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("B", "Remada Baixa com Triângulo", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("B", "Supino Inclinado com Halteres", 3, "12", null),
                WorkoutPlanExerciseJson("B", "Desenvolvimento de Ombros", 3, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("B", "Tríceps Corda na Polia", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("B", "Rosca Direta com Halteres", 3, "12", null),
                WorkoutPlanExerciseJson("B", "Prancha Abdominal", 3, "1 min", "ISOMETRIA"),

                // Ficha C (Glúteos e Posteriores - 7 exercícios)
                WorkoutPlanExerciseJson("C", "Elevação Pélvica com Barra", 4, "10", "FALHA"),
                WorkoutPlanExerciseJson("C", "Cadeira Flexora", 4, "12", "BI-SET"),
                WorkoutPlanExerciseJson("C", "Stiff com Halteres", 4, "10", null),
                WorkoutPlanExerciseJson("C", "Glúteo Coice no Cabo", 3, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("C", "Mesa Flexora", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("C", "Cadeira Abdutora", 4, "15", "FALHA"),
                WorkoutPlanExerciseJson("C", "Abdominal Infra", 4, "15", null),

                // Ficha D (Ombros, Abdômen e Cardio - 7 exercícios)
                WorkoutPlanExerciseJson("D", "Elevação Lateral de Ombros", 4, "15", "FALHA"),
                WorkoutPlanExerciseJson("D", "Elevação Frontal com Halter", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("D", "Crucifixo Invertido na Polia", 3, "15", null),
                WorkoutPlanExerciseJson("D", "Abdominal Supra com Carga", 4, "20", "DROPSET"),
                WorkoutPlanExerciseJson("D", "Abdominal Oblíquo no Cabo", 3, "15", null),
                WorkoutPlanExerciseJson("D", "Prancha Lateral", 3, "45s cada lado", "ISOMETRIA"),
                WorkoutPlanExerciseJson("D", "Corrida Intervalada na Esteira", 1, "20 min", "HIIT"),

                // Ficha E (Posteriores, Panturrilha e Glúteos - 7 exercícios)
                WorkoutPlanExerciseJson("E", "Levantamento Terra Sumô", 4, "10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("E", "Cadeira Flexora Unilateral", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("E", "Elevação Pélvica Unilateral", 3, "12 cada perna", "FALHA"),
                WorkoutPlanExerciseJson("E", "Panturrilhas em Pé", 4, "15", "BI-SET"),
                WorkoutPlanExerciseJson("E", "Cadeira Extensora Unilateral", 3, "12", null),
                WorkoutPlanExerciseJson("E", "Abdominal Remador", 4, "20", null),
                WorkoutPlanExerciseJson("E", "Caminhada na Esteira Inclinada", 1, "30 min", "CARDIO"),

                // Ficha F (Full Body Funcional - 7 exercícios)
                WorkoutPlanExerciseJson("F", "Agachamento com Desenvolvimento (Thruster)", 4, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("F", "Remada Curvada com Halteres", 4, "12", null),
                WorkoutPlanExerciseJson("F", "Flexão de Braços de Joelhos", 3, "12", "FALHA"),
                WorkoutPlanExerciseJson("F", "Passada Avançando com Halteres", 3, "12 cada perna", "BI-SET"),
                WorkoutPlanExerciseJson("F", "Tríceps Banco / Mergulho", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("F", "Rosca Martelo Alternada", 3, "12", null),
                WorkoutPlanExerciseJson("F", "Mountain Climbers", 3, "45s", "HIIT"),

                // Ficha G (Cardio e Regenerativo - 7 exercícios)
                WorkoutPlanExerciseJson("G", "Ciclismo / Bike Ergométrica", 1, "25 min", "CARDIO"),
                WorkoutPlanExerciseJson("G", "Prancha Isométrica", 3, "1 min", "ISOMETRIA"),
                WorkoutPlanExerciseJson("G", "Abdominal Bicicleta", 4, "20", null),
                WorkoutPlanExerciseJson("G", "Alongamento Dinâmico de Pernas", 3, "1 min", null),
                WorkoutPlanExerciseJson("G", "Polichinelos", 3, "1 min", "HIIT"),
                WorkoutPlanExerciseJson("G", "Elevação de Joelhos", 3, "45s", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("G", "Caminhada Regenerativa", 1, "20 min", "CARDIO")
            )
        } else {
            listOf(
                // Ficha A (Peito e Tríceps - 7 exercícios)
                WorkoutPlanExerciseJson("A", "Supino Reto com Barra", 4, "8-10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("A", "Supino Inclinado com Halteres", 4, "10", "BI-SET"),
                WorkoutPlanExerciseJson("A", "Crucifixo Inclinado na Máquina", 3, "12", "FALHA"),
                WorkoutPlanExerciseJson("A", "Crossover Polia Alta", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("A", "Tríceps Pulley com Corda", 4, "12", "DROPSET"),
                WorkoutPlanExerciseJson("A", "Tríceps Testa com Barra W", 3, "10", null),
                WorkoutPlanExerciseJson("A", "Tríceps Francês com Halter", 3, "12", "EXPLOSIVO"),

                // Ficha B (Costas e Bíceps - 7 exercícios)
                WorkoutPlanExerciseJson("B", "Puxada Alta (Pulldown)", 4, "10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("B", "Remada Curvada com Barra", 4, "8-10", "FALHA"),
                WorkoutPlanExerciseJson("B", "Remada Baixa Triângulo", 3, "12", null),
                WorkoutPlanExerciseJson("B", "Pulldown com Corda no Crossover", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("B", "Rosca Direta com Barra", 4, "10", "DROPSET"),
                WorkoutPlanExerciseJson("B", "Rosca Martelo com Halteres", 3, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("B", "Rosca Scott na Máquina", 3, "12", "FALHA"),

                // Ficha C (Pernas Completo e Panturrilha - 7 exercícios)
                WorkoutPlanExerciseJson("C", "Agachamento Livre", 4, "8-10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("C", "Leg Press 45°", 4, "10-12", "BI-SET"),
                WorkoutPlanExerciseJson("C", "Cadeira Extensora", 3, "15", "DROPSET"),
                WorkoutPlanExerciseJson("C", "Mesa Flexora / Cadeira Flexora", 4, "12", "FALHA"),
                WorkoutPlanExerciseJson("C", "Stiff com Halteres", 3, "10", null),
                WorkoutPlanExerciseJson("C", "Gêmeos em Pé (Panturrilha)", 4, "15", "FALHA"),
                WorkoutPlanExerciseJson("C", "Panturrilha Sentado", 3, "20", "DROPSET"),

                // Ficha D (Ombros, Trapézio e Abdômen - 7 exercícios)
                WorkoutPlanExerciseJson("D", "Desenvolvimento com Halteres", 4, "10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("D", "Elevação Lateral com Halteres", 4, "12-15", "FALHA"),
                WorkoutPlanExerciseJson("D", "Elevação Frontal na Polia", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("D", "Crucifixo Invertido no Peck Deck", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("D", "Encolhimento de Ombros com Halteres", 4, "12", null),
                WorkoutPlanExerciseJson("D", "Abdominal Supra na Polia", 4, "15", null),
                WorkoutPlanExerciseJson("D", "Prancha Isométrica", 3, "1 min", "ISOMETRIA"),

                // Ficha E (Braços Completo - Bíceps, Tríceps e Antebraço - 7 exercícios)
                WorkoutPlanExerciseJson("E", "Rosca Direta com Barra W", 4, "10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("E", "Tríceps Paralelas ou Graviton", 4, "10", "FALHA"),
                WorkoutPlanExerciseJson("E", "Rosca Alternada com Halteres", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("E", "Tríceps Coice na Polia", 3, "12", "DROPSET"),
                WorkoutPlanExerciseJson("E", "Rosca Concentrada no Banco", 3, "12", null),
                WorkoutPlanExerciseJson("E", "Tríceps Banco / Mergulho", 3, "12", "FALHA"),
                WorkoutPlanExerciseJson("E", "Rosca Inversa para Antebraço", 3, "15", null),

                // Ficha F (Upper Body - Peito, Costas e Core - 7 exercícios)
                WorkoutPlanExerciseJson("F", "Supino Inclinado com Barra", 4, "8-10", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("F", "Barra Fixa ou Graviton", 4, "8-10", "FALHA"),
                WorkoutPlanExerciseJson("F", "Peck Deck / Crucifixo Reto", 3, "12", "BI-SET"),
                WorkoutPlanExerciseJson("F", "Remada Unilateral (Serrote)", 3, "10", null),
                WorkoutPlanExerciseJson("F", "Dips / Paralelas para Peito", 3, "10", "DROPSET"),
                WorkoutPlanExerciseJson("F", "Remada Cavalinho", 3, "12", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("F", "Abdominal Infra na Barra", 4, "15", "ISOMETRIA"),

                // Ficha G (Posteriores, Glúteos e Cardio - 7 exercícios)
                WorkoutPlanExerciseJson("G", "Levantamento Terra", 4, "8", "EXPLOSIVO"),
                WorkoutPlanExerciseJson("G", "Cadeira Flexora", 4, "12", "DROPSET"),
                WorkoutPlanExerciseJson("G", "Elevação Pélvica na Máquina", 4, "10", "FALHA"),
                WorkoutPlanExerciseJson("G", "Afundo no Smith / Passada", 3, "12 cada perna", "BI-SET"),
                WorkoutPlanExerciseJson("G", "Cadeira Abdutora", 3, "15", null),
                WorkoutPlanExerciseJson("G", "Abdominal Remador", 4, "20", null),
                WorkoutPlanExerciseJson("G", "Corrida Intervalada na Esteira", 1, "20 min", "HIIT")
            )
        }

        val workoutPlan = allMockExercises.filter { it.workoutDay in letters }.map {
            it.copy(instructions = getSpecificExerciseInstructions(it.name))
        }

        val meals = when (objective) {
            "Hipertrofia" -> listOf(
                MealJson("Café da Manhã", "4 Ovos mexidos, 2 fatias de pão integral com creme de ricota, 1 banana média e café puro.", 550, "32g", "55g", "18g"),
                MealJson("Almoço", "180g de Filé de frango grelhado, 200g de arroz branco, 100g de feijão carioca e salada de folhas à vontade.", 680, "45g", "75g", "12g"),
                MealJson("Lanche da Tarde", "Hipercalórico Caseiro: 300ml de leite desnatado, 30g de Whey Protein, 40g de aveia em flocos e 1 colher de pasta de amendoim.", 500, "35g", "48g", "15g"),
                MealJson("Jantar", "150g de Carne patinho moída, 250g de batata doce assada, brócolis ao vapor regado com 1 fio de azeite.", 610, "40g", "60g", "14g")
            )
            "Emagrecimento" -> listOf(
                MealJson("Café da Manhã", "3 Ovos mexidos (sendo 2 claras), 1 fatia de pão integral tostado, 100g de mamão formosa com chia.", 320, "22g", "25g", "10g"),
                MealJson("Almoço", "150g de Filé de peixe (tilápia) grelhado, 100g de arroz integral, brócolis e abobrinha refogados à vontade com mínimo azeite.", 420, "35g", "30g", "8g"),
                MealJson("Lanche da Tarde", "Iogurte natural desnatado (200g), 15g de Whey Protein sabor morango e 80g de morangos picados.", 220, "25g", "15g", "2g"),
                MealJson("Jantar", "140g de Peito de frango grelhado desfiado, mix de salada verde (alface, rúcula, pepino) com limão e 80g de abóbora cabotiá cozida.", 350, "32g", "20g", "7g")
            )
            else -> listOf(
                MealJson("Café da Manhã", "3 Ovos cozidos inteiros, 1 fatia de pão de centeio, 1 xícara de melão picado e chá verde.", 380, "24g", "30g", "12g"),
                MealJson("Almoço", "150g de Filé de frango grelhado, 150g de batata baroa cozida, salada colorida (tomate, cenoura, beterraba rala) e azeite.", 480, "38g", "42g", "10g"),
                MealJson("Lanche da Tarde", "Iogurte natural integral with 20g de aveia, 1 punhado de castanhas de caju (20g) e 1 maçã.", 310, "12g", "35g", "14g"),
                MealJson("Jantar", "150g de Salmão grelhado, aspargos e cogumelos grelhados, 80g de quinoa cozida.", 490, "34g", "25g", "18g")
            )
        }

        return FitAiGenerationResultJson(workoutPlan, meals, notificationMessage)
    }
    }

    private fun getMockMealAlternative(meal: Meal): MealAlternativeJson {
        val lowerName = meal.name.lowercase()
        return when {
            lowerName.contains("café") || lowerName.contains("cafe") || lowerName.contains("desjejum") -> {
                MealAlternativeJson(
                    description = "Vitamina Cremosa Fallback: 250ml de leite desnatado ou bebida de amêndoas, 1 banana congelada, 30g de aveia em flocos e 30g de Whey Protein de baunilha.",
                    calories = meal.calories,
                    protein = "28g",
                    carbs = "35g",
                    fat = "6g"
                )
            }
            lowerName.contains("almoço") || lowerName.contains("almoco") -> {
                MealAlternativeJson(
                    description = "Iscas de coxão mole Fallback: 150g de carne grelhada acebolada, 180g de purê de batata doce rústico, aspargos salteados e salada verde com tomate cereja.",
                    calories = meal.calories,
                    protein = "40g",
                    carbs = "45g",
                    fat = "12g"
                )
            }
            lowerName.contains("lanche") || lowerName.contains("merenda") || lowerName.contains("tarde") -> {
                MealAlternativeJson(
                    description = "Wrap Proteico Fallback: 1 folha de wrap integral recheada com 100g de peito de frango desfiado, 1 colher de requeijão light e fatias de tomate.",
                    calories = meal.calories,
                    protein = "26g",
                    carbs = "24g",
                    fat = "7g"
                )
            }
            lowerName.contains("jantar") || lowerName.contains("janta") || lowerName.contains("ceia") -> {
                MealAlternativeJson(
                    description = "Omelete de forno Fallback: 3 ovos batidos com espinafre picado, cubos de peito de peru (50g) e queijo branco light (30g), acompanhado de salada de rúcula.",
                    calories = meal.calories,
                    protein = "30g",
                    carbs = "10g",
                    fat = "15g"
                )
            }
            else -> {
                MealAlternativeJson(
                    description = "Omelete proteica Fallback: 3 ovos recheados com queijo cottage e espinafre fresco, servida com uma fatia de pão de centeio integral tostado.",
                    calories = meal.calories,
                    protein = "25g",
                    carbs = "22g",
                    fat = "12g"
                )
            }
        }
    }

    private fun cleanJsonString(raw: String): String {
        var clean = raw.trim()
        val codeBlockRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(clean)
        if (match != null) {
            clean = match.groupValues[1].trim()
        } else {
            clean = clean.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1)
        }
        return clean.trim()
    }

    suspend fun generateExerciseInstructions(exerciseName: String): String = withContext(Dispatchers.IO) {
        val prompt = "Escreva uma instrução extremamente curta e direta (máximo 2 parágrafos pequenos) de como executar o exercício $exerciseName"

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.5f
            )
        )

        try {
            val response = apiService.generateContent(request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            text?.trim() ?: getSpecificExerciseInstructions(exerciseName)
        } catch (e: Exception) {
            getSpecificExerciseInstructions(exerciseName)
        }
    }

    suspend fun estimateFoodMacros(foodQuery: String, objective: String): FoodItemJson = withContext(Dispatchers.IO) {
        val defaultTip = when (objective) {
            "Hipertrofia" -> "Excelente para bater sua meta de proteínas do dia e focar na construção muscular."
            "Emagrecimento" -> "Ótimo aliado de baixa densidade calórica para manter a saciedade no déficit."
            "Definição" -> "Excelente proporção de macros para preservar massa magra enquanto queima gordura."
            else -> "Uma ótima opção equilibrada para a manutenção de uma vida saudável."
        }

        val prompt = """
            O usuário tem o objetivo de $objective. Com base estritamente nisso, ao buscar ou sugerir o alimento $foodQuery, formate a porção ideal e adicione uma mini-dica de ouro de 1 frase focada nesse objetivo (Ex para Hipertrofia: 'Excelente para bater sua meta de proteínas do dia e focar na construção muscular').
            Estime as informações nutricionais para esse prato ou porção com foco em $objective.
            Retorne um objeto JSON estritamente no seguinte formato:
            {
              "name": "Nome amigável com porção ideal (ex: Frango grelhado (Porção ideal de 150g) ou Aveia em flocos (Porção ideal de 40g))",
              "calories": 123,
              "protein": 12.5,
              "carbs": 34.0,
              "fat": 5.6,
              "tip": "Sua mini-dica de ouro de 1 frase focada em $objective aqui"
            }
            Atenção: Não adicione explicações ou markdown fora do bloco JSON. Forneça apenas o JSON válido.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = apiService.generateContent(request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Backend de IA retornou uma resposta vazia.")
            
            val cleanJson = cleanJsonString(jsonText)
            val adapter = moshi.adapter(FoodItemJson::class.java)
            adapter.fromJson(cleanJson) ?: throw IllegalStateException("Falha ao parsear o JSON de macros.")
        } catch (e: Exception) {
            Log.w("FitAI_Warning", "Erro ao obter macros com IA no backend: ${e.message}")
            FoodItemJson(
                name = "$foodQuery (Estimado)",
                calories = 180,
                protein = 15.0,
                carbs = 10.0,
                fat = 8.0,
                tip = defaultTip
            )
        }
    }

    suspend fun analyzePlateImageWithContext(bitmap: android.graphics.Bitmap?, textDescription: String): PlateAnalysisResultJson = withContext(Dispatchers.IO) {
        val prompt = """
            Analise a imagem do prato de comida usando o texto fornecido pelo usuário como contexto de confirmação. Identifique os alimentos exatos, estime as quantidades em gramas e calcule as calorias e macronutrientes (proteínas, carboidratos, gorduras e fibras).
            Texto de contexto do usuário: "${textDescription.ifBlank { "Sem descrição em texto" }}"

            Retorne estritamente um objeto JSON no seguinte formato:
            {
              "dishName": "Nome amigável e resumido do prato (ex: Prato Feito de Frango com Arroz e Feijão)",
              "totalCalories": 520,
              "totalProtein": 42.0,
              "totalCarbs": 55.0,
              "totalFat": 12.0,
              "totalFiber": 6.0,
              "items": [
                {
                  "name": "Nome do alimento (ex: Arroz branco)",
                  "quantityGrams": 150.0,
                  "calories": 195,
                  "protein": 4.0,
                  "carbs": 42.0,
                  "fat": 0.5,
                  "fiber": 1.0
                },
                {
                  "name": "Peito de frango grelhado",
                  "quantityGrams": 150.0,
                  "calories": 235,
                  "protein": 32.0,
                  "carbs": 0.0,
                  "fat": 4.5,
                  "fiber": 0.0
                }
              ]
            }
            Atenção: Não adicione explicações ou markdown fora do JSON. Forneça apenas o JSON válido.
        """.trimIndent()

        val partsList = mutableListOf<Part>()
        partsList.add(Part(text = prompt))

        if (bitmap != null) {
            try {
                val maxDim = 1024
                val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val targetW = if (ratio > 1) maxDim else (maxDim * ratio).toInt().coerceAtLeast(1)
                    val targetH = if (ratio > 1) (maxDim / ratio).toInt().coerceAtLeast(1) else maxDim
                    android.graphics.Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                } else {
                    bitmap
                }
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64)))
            } catch (e: Exception) {
                Log.e("FitAI_Error", "Erro ao converter imagem para base64: ${e.message}")
            }
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = partsList)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = apiService.generateContent(request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Backend de IA retornou resposta vazia")
            val cleanJson = cleanJsonString(jsonText)
            val adapter = moshi.adapter(PlateAnalysisResultJson::class.java)
            adapter.fromJson(cleanJson) ?: throw IllegalStateException("Falha ao parsear JSON do prato")
        } catch (e: Exception) {
            Log.w("FitAI_Warning", "Erro ao analisar prato via IA multimodal no backend: ${e.message}")
            generateFallbackPlateAnalysis(textDescription)
        }
    }

    private fun generateFallbackPlateAnalysis(textDescription: String): PlateAnalysisResultJson {
        val lowerText = textDescription.lowercase()
        return when {
            lowerText.contains("frango") || lowerText.contains("arroz") || lowerText.contains("feijão") || lowerText.contains("feijao") -> {
                PlateAnalysisResultJson(
                    dishName = "Prato Principal (Arroz, Feijão e Frango)",
                    totalCalories = 485,
                    totalProtein = 42.0,
                    totalCarbs = 52.0,
                    totalFat = 9.5,
                    totalFiber = 6.0,
                    items = listOf(
                        FoodComponentJson("Arroz branco cozido", 150.0, 195, 4.0, 42.0, 0.5, 1.0),
                        FoodComponentJson("Feijão carioca cozido", 100.0, 90, 6.0, 14.0, 0.5, 5.0),
                        FoodComponentJson("Peito de frango grelhado", 150.0, 200, 32.0, 0.0, 4.0, 0.0),
                        FoodComponentJson("Salada de alface e tomate", 50.0, 15, 0.8, 3.0, 0.2, 0.8)
                    )
                )
            }
            lowerText.contains("ovo") || lowerText.contains("tapioca") || lowerText.contains("pão") || lowerText.contains("pao") -> {
                PlateAnalysisResultJson(
                    dishName = "Refeição Proteica (Ovos com Pão/Tapioca)",
                    totalCalories = 350,
                    totalProtein = 22.0,
                    totalCarbs = 30.0,
                    totalFat = 14.0,
                    totalFiber = 2.5,
                    items = listOf(
                        FoodComponentJson("Ovos mexidos (3 unidades)", 150.0, 220, 18.0, 1.5, 14.0, 0.0),
                        FoodComponentJson("Pão integral fatiado", 50.0, 130, 4.0, 28.5, 1.0, 2.5)
                    )
                )
            }
            lowerText.contains("salmão") || lowerText.contains("salmao") || lowerText.contains("peixe") -> {
                PlateAnalysisResultJson(
                    dishName = "Filé de Peixe com Acompanhamentos",
                    totalCalories = 510,
                    totalProtein = 38.0,
                    totalCarbs = 35.0,
                    totalFat = 18.0,
                    totalFiber = 4.0,
                    items = listOf(
                        FoodComponentJson("Filé de Peixe Grelhado", 160.0, 240, 34.0, 0.0, 10.0, 0.0),
                        FoodComponentJson("Purê de Batata Doce", 150.0, 180, 2.0, 31.0, 4.0, 3.0),
                        FoodComponentJson("Brócolis ao vapor", 80.0, 90, 2.0, 4.0, 4.0, 1.0)
                    )
                )
            }
            else -> {
                val userTitle = if (textDescription.isNotBlank()) "Prato: $textDescription" else "Prato Analisado por IA"
                PlateAnalysisResultJson(
                    dishName = userTitle,
                    totalCalories = 450,
                    totalProtein = 35.0,
                    totalCarbs = 45.0,
                    totalFat = 11.0,
                    totalFiber = 5.0,
                    items = listOf(
                        FoodComponentJson("Proteína Principal", 150.0, 220, 30.0, 0.0, 8.0, 0.0),
                        FoodComponentJson("Acompanhamento Carboidrato", 150.0, 180, 4.0, 38.0, 1.0, 2.0),
                        FoodComponentJson("Vegetais e Salada", 80.0, 50, 1.0, 7.0, 2.0, 3.0)
                    )
                )
            }
        }
    }
}

fun getSpecificExerciseInstructions(exerciseName: String): String {
    val nameLower = exerciseName.lowercase().trim()
    return when {
        // PEITO
        nameLower.contains("supino reto") ->
            "1. Deite-se no banco plano com os pés firmes no chão.\n2. Segure a barra com pegada um pouco mais larga que os ombros.\n3. Desça a barra de forma controlada até tocar suavemente o peito.\n4. Empurre a barra para cima estendendo os braços com força no peitoral."
        nameLower.contains("supino inclinado") ->
            "1. Ajuste o banco em um ângulo de 30° a 45°.\n2. Segure os halteres ou barra na altura da parte superior do peito.\n3. Empurre a carga para cima até a extensão quase completa dos braços.\n4. Desça de forma cadenciada sentindo o alongamento do peitoral superior."
        nameLower.contains("crucifixo") || nameLower.contains("crossover") || nameLower.contains("peck deck") ->
            "1. Posicione as mãos alinhadas à altura do peitoral.\n2. Mantenha os cotovelos levemente flexionados durante todo o movimento.\n3. Aproxime as mãos à frente do peito contraindo ao máximo a musculatura.\n4. Abra os braços devagar resistindo ao peso."
        nameLower.contains("flexão") ->
            "1. Apoie as mãos no chão na largura dos ombros e mantenha o corpo ereto.\n2. Desça o peito em direção ao chão dobrando os cotovelos a 45°.\n3. Empurre o chão com força até estender totalmente os braços.\n4. Mantenha o abdômen contraído do início ao fim."
        nameLower.contains("dips") || nameLower.contains("paralelas para peito") ->
            "1. Segure nas barras paralelas com o corpo inclinado ligeiramente para a frente.\n2. Desça o tronco dobrando os cotovelos até formar um ângulo de 90°.\n3. Empurre o corpo para cima focando a força na parte inferior do peitoral."

        // COSTAS
        nameLower.contains("puxada alta") || nameLower.contains("pulldown") ->
            "1. Sente-se no aparelho e segure a barra com pegada aberta.\n2. Puxe a barra em direção à parte superior do peito, projetando os cotovelos para baixo.\n3. Mantenha o peito aberto e tronco levemente inclinado.\n4. Retorne a barra devagar até alongar bem a dorsal."
        nameLower.contains("remada curvada") ->
            "1. Incline o tronco a 45° com a coluna perfeitamente ereta.\n2. Puxe a barra ou halteres em direção ao umbigo puxando com os cotovelos.\n3. Comprima as escápulas no topo da remada.\n4. Desça a carga de forma lenta e controlada."
        nameLower.contains("remada baixa") || nameLower.contains("triângulo") ->
            "1. Sente-se com as pernas levemente flexionadas e coluna ereta.\n2. Puxe o cabo até encostar no abdômen aproximando as escápulas.\n3. Mantenha os ombros longe das orelhas.\n4. Estenda os braços devagar sem projetar o tronco à frente."
        nameLower.contains("barra fixa") || nameLower.contains("graviton") ->
            "1. Segure a barra fixa com as palmas voltadas para a frente.\n2. Suba o corpo até o queixo passar da barra contraindo as costas.\n3. Evite chutar ou dar impulso com as pernas.\n4. Desça o corpo de forma cadenciada."
        nameLower.contains("serrote") || nameLower.contains("unilateral") ->
            "1. Apoie um joelho e mão no banco e segure o halter com a outra mão.\n2. Puxe o halter em direção ao quadril trazendo o cotovelo bem rente ao corpo.\n3. Mantenha o tronco firme sem girar a coluna.\n4. Desça o halter até alongar completamente a dorsal."
        nameLower.contains("cavalinho") ->
            "1. Posicione-se na máquina de barra T com os pés bem firmes.\n2. Puxe a manopla em direção ao peitoral com os cotovelos para trás.\n3. Mantenha a lombar travada e abdômen ativado.\n4. Desça a carga controladamente."

        // BÍCEPS & ANTEBRAÇO
        nameLower.contains("rosca direta") ->
            "1. Mantenha a postura ereta e segure a barra com as palmas para cima.\n2. Flexione os cotovelos subindo a barra em direção aos ombros.\n3. Mantenha os cotovelos fixos ao lado das costelas sem balançar o corpo.\n4. Desça a barra de forma lenta até estender totalmente os braços."
        nameLower.contains("rosca martelo") ->
            "1. Segure os halteres ao lado do corpo com pegada neutra (palmas voltadas para dentro).\n2. Suba os halteres até a altura dos ombros focando na contração do antebraço e bíceps.\n3. Mantenha os cotovelos imóveis.\n4. Abaixe a carga de forma controlada."
        nameLower.contains("rosca scott") ->
            "1. Apoie os braços no banco Scott ajustando a altura da almofada.\n2. Flexione os cotovelos puxando o peso para cima.\n3. Mantenha os ombros relaxados sem levantar do apoio.\n4. Desça devagar até o braço ficar quase estendido."
        nameLower.contains("rosca concentrada") ->
            "1. Sente-se no banco e apoie o cotovelo na parte interna da coxa.\n2. Suba o halter flexionando o cotovelo e aperte o bíceps no topo por 1 segundo.\n3. Desça o peso devagar mantendo o cotovelo fixo na coxa."
        nameLower.contains("rosca alternada") ->
            "1. Segure os halteres ao lado do corpo com braços estendidos.\n2. Flexione um braço por vez girando a palma da mão para cima durante a subida.\n3. Alterne os braços mantendo o tronco firme e sem impulso."
        nameLower.contains("antebraço") || nameLower.contains("rosca inversa") ->
            "1. Segure a barra com as palmas voltadas para baixo (pegada pronada).\n2. Eleve a barra flexionando os punhos e cotovelos.\n3. Desça a barra devagar sentindo a musculatura do antebraço."

        // TRÍCEPS
        nameLower.contains("tríceps pulley") || nameLower.contains("tríceps corda") ->
            "1. Mantenha os cotovelos colados ao lado do corpo e tronco firme.\n2. Empurre a corda ou barra para baixo estendendo totalmente os cotovelos.\n3. Abra as pontas da corda no final da descida para máxima contração.\n4. Retorne as mãos até a altura do peito de forma controlada."
        nameLower.contains("tríceps testa") ->
            "1. Deite-se no banco plano e segure a barra acima do peito.\n2. Flexione apenas os cotovelos trazendo a barra na direção da testa.\n3. Mantenha os cotovelos apontados para o teto e imóveis.\n4. Empurre a barra de volta para o alto contraindo o tríceps."
        nameLower.contains("tríceps francês") ->
            "1. Segure o halter acima da cabeça com as duas mãos.\n2. Desça o halter por trás da cabeça dobrando os cotovelos.\n3. Mantenha os cotovelos voltados para a frente sem abri-los.\n4. Estenda os braços voltando ao topo."
        nameLower.contains("coice") ->
            "1. Incline o tronco para a frente e alinhe o braço paralelamente ao chão.\n2. Estenda o cotovelo empurrando o halter para trás.\n3. Mantenha o braço fixo e mova apenas o antebraço.\n4. Retorne à posição inicial devagar."
        nameLower.contains("paralelas") || nameLower.contains("mergulho") || nameLower.contains("banco") ->
            "1. Apoie as mãos firmes na barra paralela ou na borda do banco.\n2. Desça o corpo dobrando os cotovelos até um ângulo de 90°.\n3. Empurre com as palmas das mãos estendendo os braços de volta ao topo."

        // OMBROS & TRAPÉZIO
        nameLower.contains("desenvolvimento") ->
            "1. Sente-se no banco com o tronco ereto a 90°.\n2. Segure os halteres na altura das orelhas com cotovelos a 90°.\n3. Empurre os halteres para cima até estender os braços sobre a cabeça.\n4. Desça de forma controlada até a linha das orelhas."
        nameLower.contains("elevação lateral") ->
            "1. Em pé, segure os halteres ao lado das coxas com cotovelos levemente flexionados.\n2. Suba os braços lateralmente até a altura dos ombros.\n3. Mantenha as palmas para baixo e evite balançar o tronco.\n4. Desça os halteres devagar resistindo ao peso."
        nameLower.contains("elevação frontal") ->
            "1. Segure o halter ou barra à frente das coxas com braços estendidos.\n2. Eleve a carga à frente até a linha dos olhos.\n3. Baixe o peso devagar mantendo o abdômen contraído."
        nameLower.contains("encolhimento") ->
            "1. Segure halteres pesados ao lado do corpo com os braços estendidos.\n2. Suba os ombros em direção às orelhas sem dobrar os cotovelos.\n3. Segure o topo por 1 a 2 segundos.\n4. Desça os ombros completamente alongando o trapézio."

        // PERNAS / QUADRÍCEPS / GLÚTEOS / POSTERIORES
        nameLower.contains("agachamento") ->
            "1. Posicione os pés na largura dos ombros com as pontas levemente para fora.\n2. Agache empurrando o quadril para trás como se fosse sentar em uma cadeira.\n3. Mantenha os joelhos alinhados com os pés e peito estufado.\n4. Empurre o chão com os calcanhares para retornar."
        nameLower.contains("leg press") ->
            "1. Sente-se na plataforma apoiando as costas e quadril por completo.\n2. Coloque os pés na plataforma na largura dos ombros.\n3. Destrave e desça a plataforma até os joelhos formarem 90°.\n4. Empurre com os calcanhares sem travar os joelhos no topo."
        nameLower.contains("extensora") ->
            "1. Ajuste o rolo de apoio na altura dos tornozelos.\n2. Estenda os joelhos subindo o peso até a extensão completa.\n3. Contraia os quadríceps no topo por 1 segundo.\n4. Desça a carga de forma lenta e cadenciada."
        nameLower.contains("flexora") ->
            "1. Posicione os tornozelos sob o rolo de apoio da máquina.\n2. Puxe o rolo em direção aos glúteos dobrando os joelhos.\n3. Mantenha o quadril firme no banco sem levantar a lombar.\n4. Retorne controlando a descida do peso."
        nameLower.contains("stiff") || nameLower.contains("terra") ->
            "1. Segure a barra ou halteres à frente das coxas com joelhos levemente flexionados.\n2. Incline o tronco empurrando o quadril para trás e mantendo a coluna ereta.\n3. Desça até sentir o alongamento do posterior de coxa.\n4. Suba contraindo os glúteos e posteriores."
        nameLower.contains("pélvica") ->
            "1. Apoie as escápulas no banco e a barra sobre a linha do quadril.\n2. Suba o quadril empurrando com os calcanhares até alinhar tronco e coxas.\n3. Aperte os glúteos com força no topo por 1 segundo.\n4. Desça o quadril de forma controlada sem tocar o chão."
        nameLower.contains("abdutora") ->
            "1. Sente-se com o quadril colado no encosto da máquina.\n2. Abra as pernas empurrando as almofadas para fora ao máximo.\n3. Segure a abertura total por 1 segundo focando nos glúteos.\n4. Feche as pernas devagar mantendo a tensão."
        nameLower.contains("adutora") ->
            "1. Sente-se na máquina com as pernas abertas apoiadas nas almofadas.\n2. Feche as pernas com força trazendo os joelhos para o centro.\n3. Sinta a ativação na parte interna das coxas.\n4. Abra as pernas controladamente sem soltar o peso."
        nameLower.contains("panturrilha") || nameLower.contains("gêmeos") ->
            "1. Apoie a ponta dos pés na borda do degrau com calcanhares livres.\n2. Empurre o corpo para cima subindo o máximo na ponta dos pés.\n3. Segure o topo por 1 segundo no pico de contração.\n4. Desça os calcanhares abaixo da linha do degrau alongando bem."

        // CARDIO / ABDÔMEN / OUTROS
        nameLower.contains("prancha") ->
            "1. Apoie os antebraços e pontas dos pés no chão.\n2. Mantenha o corpo alinhado em linha reta da cabeça aos pés.\n3. Contraia o abdômen e glúteos firmemente.\n4. Mantenha a respiração constante sem deixar o quadril cair."
        nameLower.contains("abdominal") ->
            "1. Deite-se de costas com joelhos dobrados e pés firmes no chão.\n2. Eleve o tronco tirando as escápulas do chão e contraindo o abdômen.\n3. Expire durante a subida sem puxar o pescoço com as mãos.\n4. Desça suavemente até encostar as costas no colchonete."
        nameLower.contains("esteira") || nameLower.contains("corrida") || nameLower.contains("bike") || nameLower.contains("ciclismo") || nameLower.contains("cardio") ->
            "1. Inicie com 3 minutos de aquecimento moderado.\n2. Mantenha a frequência cardíaca na zona de treino estipulada.\n3. Mantenha a postura ereta e passos firmes.\n4. Reduza o ritmo gradualmente ao final para desaquecimento."

        else ->
            "1. Posicione-se de forma estável ajustando a postura do corpo.\n2. Execute o movimento de $exerciseName com foco na amplitude correta.\n3. Mantenha o abdômen ativado e a coluna bem protegida.\n4. Controle a velocidade tanto na fase de força quanto no retorno."
    }
}
