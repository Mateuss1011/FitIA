package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.WorkoutExercise
import com.example.data.repository.getSpecificExerciseInstructions

/**
 * Anatomical muscle target zones for realistic human musculature rendering.
 */
enum class MuscleZone {
    // Anterior upper body
    CHEST_UPPER,
    CHEST_MID,
    CHEST_LOWER,
    DELTOID_ANTERIOR,
    DELTOID_LATERAL,
    BICEPS,
    FOREARMS,
    ABS_UPPER,
    ABS_LOWER,
    OBLIQUES,

    // Posterior upper body
    TRAPS_UPPER,
    TRAPS_MIDDLE_LOWER,
    DELTOID_POSTERIOR,
    LATS,
    TERES_RHOMBOIDS,
    TRICEPS,
    LOWER_BACK,

    // Lower body anterior
    QUADS,
    ADDUCTORS,
    TIBIALIS,

    // Lower body posterior
    GLUTES,
    HAMSTRINGS,
    CALVES,

    // Cardiovascular / Full Body
    CARDIO_FULL
}

data class ExerciseAnatomyInfo(
    val primaryMuscleGroup: String,
    val specificTargetName: String,
    val primaryZones: Set<MuscleZone>,
    val secondaryZones: Set<MuscleZone> = emptySet(),
    val secondaryMuscleGroup: String? = null,
    val instructions: List<String> = emptyList()
)

/**
 * Maps exercise names to detailed muscle anatomy information and steps.
 */
fun getExerciseAnatomyInfo(exerciseName: String): ExerciseAnatomyInfo {
    val nameLower = exerciseName.lowercase()

    val (primaryGroup, specificTarget, primaryZones, secondaryZones, secondaryGroup) = when {
        // --- CHEST (PEITORAL) ---
        nameLower.contains("crossover") -> Quintuple(
            "Peito (Peitoral Maior)",
            "Peitoral Maior (Fibras Esternocostais e Inferiores)",
            setOf(MuscleZone.CHEST_LOWER, MuscleZone.CHEST_MID),
            setOf(MuscleZone.DELTOID_ANTERIOR, MuscleZone.BICEPS),
            "Deltoide Anterior e Bíceps"
        )
        nameLower.contains("supino inclinado") -> Quintuple(
            "Peito Superior",
            "Peitoral Clavicular (Porção Superior)",
            setOf(MuscleZone.CHEST_UPPER),
            setOf(MuscleZone.DELTOID_ANTERIOR, MuscleZone.TRICEPS),
            "Deltoide Anterior e Tríceps"
        )
        nameLower.contains("supino") || nameLower.contains("crucifixo") || nameLower.contains("peck deck") || nameLower.contains("flexão") -> Quintuple(
            "Peito (Peitoral Geral)",
            "Peitoral Maior (Sternal e Clavicular)",
            setOf(MuscleZone.CHEST_MID, MuscleZone.CHEST_UPPER, MuscleZone.CHEST_LOWER),
            setOf(MuscleZone.TRICEPS, MuscleZone.DELTOID_ANTERIOR),
            "Tríceps e Deltoide Anterior"
        )
        nameLower.contains("dips") || (nameLower.contains("paralelas") && nameLower.contains("peito")) -> Quintuple(
            "Peito Inferior",
            "Peitoral Inferior & Tríceps",
            setOf(MuscleZone.CHEST_LOWER, MuscleZone.TRICEPS),
            setOf(MuscleZone.DELTOID_ANTERIOR),
            "Deltoide Anterior"
        )

        // --- BACK (COSTAS / DORSAIS) ---
        nameLower.contains("puxada") || nameLower.contains("pulldown") || nameLower.contains("barra fixa") || nameLower.contains("graviton") -> Quintuple(
            "Costas (Grande Dorsal)",
            "Latíssimo do Dorso & Redondo Maior",
            setOf(MuscleZone.LATS, MuscleZone.TERES_RHOMBOIDS),
            setOf(MuscleZone.BICEPS, MuscleZone.TRAPS_MIDDLE_LOWER),
            "Bíceps Braquial e Trapézio"
        )
        nameLower.contains("remada") || nameLower.contains("serrote") || nameLower.contains("cavalinho") -> Quintuple(
            "Costas (Dorsal & Romboides)",
            "Latíssimo, Romboides & Trapézio Médio",
            setOf(MuscleZone.LATS, MuscleZone.TERES_RHOMBOIDS, MuscleZone.TRAPS_MIDDLE_LOWER),
            setOf(MuscleZone.BICEPS, MuscleZone.LOWER_BACK),
            "Bíceps e Lombar"
        )
        nameLower.contains("encolhimento") -> Quintuple(
            "Trapézio Superior",
            "Trapézio Superior (Elevação de Escápula)",
            setOf(MuscleZone.TRAPS_UPPER),
            setOf(MuscleZone.DELTOID_LATERAL, MuscleZone.FOREARMS),
            "Deltoide e Antebraço"
        )

        // --- SHOULDERS (OMBROS / DELTOIDES) ---
        nameLower.contains("elevação lateral") -> Quintuple(
            "Ombros (Deltoide Lateral)",
            "Deltoide Lateral (Acromial)",
            setOf(MuscleZone.DELTOID_LATERAL),
            setOf(MuscleZone.TRAPS_UPPER),
            "Trapézio Superior"
        )
        nameLower.contains("elevação frontal") -> Quintuple(
            "Ombros (Deltoide Anterior)",
            "Deltoide Clavicular (Anterior)",
            setOf(MuscleZone.DELTOID_ANTERIOR),
            setOf(MuscleZone.CHEST_UPPER),
            "Peitoral Superior"
        )
        nameLower.contains("crucifixo invertido") || nameLower.contains("face pull") || nameLower.contains("deltoide posterior") -> Quintuple(
            "Ombros Posteriores",
            "Deltoide Posterior & Manguito Rotador",
            setOf(MuscleZone.DELTOID_POSTERIOR, MuscleZone.TERES_RHOMBOIDS),
            setOf(MuscleZone.TRAPS_MIDDLE_LOWER),
            "Romboides e Trapézio"
        )
        nameLower.contains("desenvolvimento") || nameLower.contains("ombro") || nameLower.contains("militar") -> Quintuple(
            "Ombros (Deltoides)",
            "Deltoide Anterior, Lateral & Tríceps",
            setOf(MuscleZone.DELTOID_ANTERIOR, MuscleZone.DELTOID_LATERAL),
            setOf(MuscleZone.TRICEPS, MuscleZone.TRAPS_UPPER),
            "Tríceps e Trapézio"
        )

        // --- ARMS: BICEPS & FOREARMS ---
        nameLower.contains("rosca martelo") || nameLower.contains("inversa") || nameLower.contains("antebraço") -> Quintuple(
            "Braços (Braquial & Antebraço)",
            "Braquiorradial, Braquial & Bíceps",
            setOf(MuscleZone.FOREARMS, MuscleZone.BICEPS),
            setOf(MuscleZone.BICEPS),
            "Flexores de Punho"
        )
        nameLower.contains("rosca") || nameLower.contains("bíceps") || nameLower.contains("scott") || nameLower.contains("concentrada") -> Quintuple(
            "Braços (Bíceps)",
            "Bíceps Braquial (Cabeças Curta e Longa)",
            setOf(MuscleZone.BICEPS),
            setOf(MuscleZone.FOREARMS),
            "Braquiorradial e Antebraço"
        )

        // --- ARMS: TRICEPS ---
        nameLower.contains("tríceps testa") || nameLower.contains("francês") -> Quintuple(
            "Braços (Tríceps Cabeça Longa)",
            "Tríceps Braquial (Cabeça Longa)",
            setOf(MuscleZone.TRICEPS),
            setOf(MuscleZone.DELTOID_POSTERIOR),
            "Estabilizadores do Ombro"
        )
        nameLower.contains("tríceps") || nameLower.contains("pulley") || nameLower.contains("coice") || nameLower.contains("mergulho") || nameLower.contains("paralelas") -> Quintuple(
            "Braços (Tríceps Braquial)",
            "Tríceps Braquial (Cabeças Lateral e Medial)",
            setOf(MuscleZone.TRICEPS),
            setOf(MuscleZone.FOREARMS, MuscleZone.CHEST_LOWER),
            "Peitoral Inferior e Antebraço"
        )

        // --- LEGS: QUADS & ADDUCTORS ---
        nameLower.contains("extensora") -> Quintuple(
            "Pernas (Quadríceps Isolado)",
            "Reto Femoral & Vastos Lateral/Medial",
            setOf(MuscleZone.QUADS),
            setOf(MuscleZone.TIBIALIS),
            "Tibial Anterior"
        )
        nameLower.contains("adutora") -> Quintuple(
            "Pernas (Adutores)",
            "Adutor Longo, Magno & Grácil",
            setOf(MuscleZone.ADDUCTORS),
            setOf(MuscleZone.QUADS),
            "Quadríceps"
        )
        nameLower.contains("agachamento") || nameLower.contains("leg press") || nameLower.contains("smith") || nameLower.contains("afundo") || nameLower.contains("passada") || nameLower.contains("avanço") || nameLower.contains("hack") -> Quintuple(
            "Pernas (Quadríceps & Glúteos)",
            "Quadríceps, Glúteo Máximo & Isquiotibiais",
            setOf(MuscleZone.QUADS, MuscleZone.GLUTES),
            setOf(MuscleZone.HAMSTRINGS, MuscleZone.CALVES, MuscleZone.ABS_LOWER),
            "Posteriores, Panturrilhas e Core"
        )

        // --- LEGS: POSTERIORS & GLUTES ---
        nameLower.contains("flexora") || nameLower.contains("stiff") -> Quintuple(
            "Pernas (Posteriores de Coxa)",
            "Isquiotibiais (Bíceps Femoral e Semitendíneo)",
            setOf(MuscleZone.HAMSTRINGS, MuscleZone.GLUTES),
            setOf(MuscleZone.LOWER_BACK, MuscleZone.CALVES),
            "Glúteos e Lombar"
        )
        nameLower.contains("terra") || nameLower.contains("deadlift") -> Quintuple(
            "Cadeia Posterior Completa",
            "Glúteos, Isquiotibiais, Dorsal & Lombar",
            setOf(MuscleZone.GLUTES, MuscleZone.HAMSTRINGS, MuscleZone.LOWER_BACK, MuscleZone.TRAPS_MIDDLE_LOWER),
            setOf(MuscleZone.QUADS, MuscleZone.FOREARMS, MuscleZone.TRAPS_UPPER),
            "Quadríceps, Trapézio e Antebraço"
        )
        nameLower.contains("pélvica") || nameLower.contains("abdutora") || nameLower.contains("glúteo") -> Quintuple(
            "Glúteos (Glúteo Máximo & Médio)",
            "Glúteo Máximo, Glúteo Médio & Isquiotibiais",
            setOf(MuscleZone.GLUTES),
            setOf(MuscleZone.HAMSTRINGS, MuscleZone.LOWER_BACK),
            "Posteriores de Coxa e Lombar"
        )

        // --- CALVES ---
        nameLower.contains("panturrilha") || nameLower.contains("gêmeos") || nameLower.contains("sóleo") -> Quintuple(
            "Panturrilhas",
            "Gastrocnêmio (Cabeças Medial/Lateral) & Sóleo",
            setOf(MuscleZone.CALVES),
            setOf(MuscleZone.TIBIALIS),
            "Tibial Anterior e Pés"
        )

        // --- CORE / ABDÔMEN ---
        nameLower.contains("abdominal") || nameLower.contains("prancha") || nameLower.contains("infra") || nameLower.contains("supra") || nameLower.contains("remador") || nameLower.contains("core") -> Quintuple(
            "Core (Abdômen)",
            "Reto Abdominal, Oblíquos & Transverso",
            setOf(MuscleZone.ABS_UPPER, MuscleZone.ABS_LOWER, MuscleZone.OBLIQUES),
            setOf(MuscleZone.LOWER_BACK),
            "Eretores da Espinha"
        )

        // --- CARDIO / AEROBIC ---
        nameLower.contains("corrida") || nameLower.contains("esteira") || nameLower.contains("bicicleta") || nameLower.contains("bike") || nameLower.contains("aeróbico") || nameLower.contains("caminhada") || nameLower.contains("hiit") || nameLower.contains("polichinelo") -> Quintuple(
            "Sistema Cardiorrespiratório",
            "Cardiovascular, Quadríceps & Panturrilhas",
            setOf(MuscleZone.CARDIO_FULL, MuscleZone.QUADS, MuscleZone.CALVES),
            setOf(MuscleZone.HAMSTRINGS, MuscleZone.GLUTES),
            "Corpo Inteiro"
        )

        else -> Quintuple(
            "Musculatura Integrada",
            "Músculos Principais e Estabilizadores",
            setOf(MuscleZone.ABS_UPPER, MuscleZone.ABS_LOWER),
            setOf(MuscleZone.QUADS, MuscleZone.LATS),
            "Core e Postura"
        )
    }

    val instructionsText = getSpecificExerciseInstructions(exerciseName)
    val steps = instructionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    return ExerciseAnatomyInfo(
        primaryMuscleGroup = primaryGroup,
        specificTargetName = specificTarget,
        primaryZones = primaryZones,
        secondaryZones = secondaryZones,
        secondaryMuscleGroup = secondaryGroup,
        instructions = steps
    )
}

/**
 * Backwards compatible helper returning tuple of group and instructions.
 */
fun getTargetMuscleAndInstructions(exerciseName: String): Pair<String, List<String>> {
    val info = getExerciseAnatomyInfo(exerciseName)
    return info.primaryMuscleGroup to info.instructions
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * Detailed Anatomical Muscle Map Component for Exercise Execution Guide.
 * Displays side-by-side Anterior (Front) and Posterior (Back) views
 * with vivid red highlights matching the Crossover Polia Alta standard.
 */
@Composable
fun AnatomicalMuscleMap(
    exerciseName: String,
    modifier: Modifier = Modifier,
    anatomyInfo: ExerciseAnatomyInfo = remember(exerciseName) { getExerciseAnatomyInfo(exerciseName) }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "muscle_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1116)),
        border = BorderStroke(1.dp, Color(0xFF262C38)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Anterior and Posterior Diagram Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1A202C),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "ANTERIOR (FRENTE)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = Color(0xFF1A202C),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "POSTERIOR (COSTAS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dual Body Anatomical Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Positions: Anterior on Left, Posterior on Right
                    val centerXAnterior = w * 0.28f
                    val centerXPosterior = w * 0.72f
                    val scaleY = (h / 155f).coerceAtLeast(0.5f)

                    // Draw Anterior Body
                    drawRealisticAnatomyBody(
                        drawScope = this,
                        centerX = centerXAnterior,
                        isPosterior = false,
                        scaleY = scaleY,
                        primaryZones = anatomyInfo.primaryZones,
                        secondaryZones = anatomyInfo.secondaryZones,
                        glowIntensity = pulseGlow,
                        isThumbnail = false
                    )

                    // Draw Posterior Body
                    drawRealisticAnatomyBody(
                        drawScope = this,
                        centerX = centerXPosterior,
                        isPosterior = true,
                        scaleY = scaleY,
                        primaryZones = anatomyInfo.primaryZones,
                        secondaryZones = anatomyInfo.secondaryZones,
                        glowIntensity = pulseGlow,
                        isThumbnail = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Target Muscle Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1315))
                    .border(1.dp, Color(0xFFFF1E38).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1E38))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MÚSCULO ALVO: ${anatomyInfo.specificTargetName.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF334B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Compact Anatomical Thumbnail for Exercise Cards in Workouts list.
 * Replaces generic dumbbell icons with realistic red-highlighted muscle previews.
 */
@Composable
fun AnatomicalMuscleThumbnail(
    exerciseName: String,
    modifier: Modifier = Modifier,
    isPersonalRecord: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val anatomyInfo = remember(exerciseName) { getExerciseAnatomyInfo(exerciseName) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F1116))
            .border(
                width = if (isPersonalRecord) 1.5.dp else 1.dp,
                color = if (isPersonalRecord) Color(0xFF00E676) else Color(0xFF262C38),
                shape = RoundedCornerShape(8.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height
            val scaleY = (h / 155f).coerceAtLeast(0.2f)

            // In thumbnail, draw dual mini figures side by side
            val centerXAnterior = w * 0.30f
            val centerXPosterior = w * 0.70f

            drawRealisticAnatomyBody(
                drawScope = this,
                centerX = centerXAnterior,
                isPosterior = false,
                scaleY = scaleY,
                primaryZones = anatomyInfo.primaryZones,
                secondaryZones = anatomyInfo.secondaryZones,
                glowIntensity = 1.0f,
                isThumbnail = true
            )

            drawRealisticAnatomyBody(
                drawScope = this,
                centerX = centerXPosterior,
                isPosterior = true,
                scaleY = scaleY,
                primaryZones = anatomyInfo.primaryZones,
                secondaryZones = anatomyInfo.secondaryZones,
                glowIntensity = 1.0f,
                isThumbnail = true
            )
        }
    }
}

/**
 * High-definition Canvas drawing for human anatomical musculature.
 * Renders head, traps, deltoids, pectorals/lats, biceps/triceps, core, quads, glutes, hamstrings and calves.
 */
fun drawRealisticAnatomyBody(
    drawScope: DrawScope,
    centerX: Float,
    isPosterior: Boolean,
    scaleY: Float,
    primaryZones: Set<MuscleZone>,
    secondaryZones: Set<MuscleZone>,
    glowIntensity: Float,
    isThumbnail: Boolean
) {
    with(drawScope) {
        val redPrimary = Color(0xFFFF1E38)
        val redSecondary = Color(0xFFFF6D00)
        val redGlow = Color(0xFFFF1E38).copy(alpha = 0.38f * glowIntensity)
        val baseBody = Color(0xFF222632)
        val baseBodyDark = Color(0xFF191D26)
        val lineOutline = Color(0xFF384050)
        val strokeWidth = if (isThumbnail) 0.75.dp.toPx() else 1.2.dp.toPx()

        fun isHighlighted(zone: MuscleZone): Boolean = primaryZones.contains(zone)
        fun isSecondary(zone: MuscleZone): Boolean = secondaryZones.contains(zone)

        fun getZoneColor(zone: MuscleZone): Color {
            return when {
                isHighlighted(zone) || (primaryZones.contains(MuscleZone.CARDIO_FULL) && zone != MuscleZone.CARDIO_FULL) -> redPrimary
                isSecondary(zone) -> redSecondary
                else -> baseBody
            }
        }

        fun getZoneGlow(zone: MuscleZone): Boolean {
            return isHighlighted(zone) || primaryZones.contains(MuscleZone.CARDIO_FULL)
        }

        // Y-axis landmark coordinates
        val hY = 12f * scaleY
        val neckY = 22f * scaleY
        val clavicleY = 25f * scaleY
        val chestTopY = 28f * scaleY
        val chestCenterY = 38f * scaleY
        val waistY = 64f * scaleY
        val hipY = 78f * scaleY
        val kneeY = 112f * scaleY
        val ankleY = 142f * scaleY

        // 1. Head & Neck
        drawCircle(
            color = baseBodyDark,
            radius = 8.5f * scaleY,
            center = Offset(centerX, hY)
        )
        drawCircle(
            color = lineOutline,
            radius = 8.5f * scaleY,
            center = Offset(centerX, hY),
            style = Stroke(width = strokeWidth)
        )
        // Neck
        drawRect(
            color = baseBody,
            topLeft = Offset(centerX - 4.5f * scaleY, hY + 6f * scaleY),
            size = Size(9f * scaleY, 7f * scaleY)
        )

        // 2. Traps (Trapézio Superior)
        val trapsUpperZone = MuscleZone.TRAPS_UPPER
        val trapsColor = getZoneColor(trapsUpperZone)
        val trapsPath = Path().apply {
            moveTo(centerX - 4.5f * scaleY, neckY)
            lineTo(centerX + 4.5f * scaleY, neckY)
            lineTo(centerX + 18f * scaleY, clavicleY)
            lineTo(centerX - 18f * scaleY, clavicleY)
            close()
        }
        drawPath(trapsPath, color = trapsColor)
        drawPath(trapsPath, color = lineOutline, style = Stroke(strokeWidth))

        // 3. Shoulders (Deltoides)
        val deltZone = if (!isPosterior) MuscleZone.DELTOID_ANTERIOR else MuscleZone.DELTOID_POSTERIOR
        val deltLatZone = MuscleZone.DELTOID_LATERAL
        val deltsColor = if (isHighlighted(deltZone) || isHighlighted(deltLatZone)) redPrimary
        else if (isSecondary(deltZone) || isSecondary(deltLatZone)) redSecondary
        else baseBody

        // Left & Right Deltoids
        drawRoundRect(
            color = deltsColor,
            topLeft = Offset(centerX - 24f * scaleY, clavicleY),
            size = Size(8f * scaleY, 15f * scaleY),
            cornerRadius = CornerRadius(4f * scaleY)
        )
        drawRoundRect(
            color = lineOutline,
            topLeft = Offset(centerX - 24f * scaleY, clavicleY),
            size = Size(8f * scaleY, 15f * scaleY),
            cornerRadius = CornerRadius(4f * scaleY),
            style = Stroke(strokeWidth)
        )

        drawRoundRect(
            color = deltsColor,
            topLeft = Offset(centerX + 16f * scaleY, clavicleY),
            size = Size(8f * scaleY, 15f * scaleY),
            cornerRadius = CornerRadius(4f * scaleY)
        )
        drawRoundRect(
            color = lineOutline,
            topLeft = Offset(centerX + 16f * scaleY, clavicleY),
            size = Size(8f * scaleY, 15f * scaleY),
            cornerRadius = CornerRadius(4f * scaleY),
            style = Stroke(strokeWidth)
        )

        // Deltoids Glow if active
        if (getZoneGlow(deltZone) || getZoneGlow(deltLatZone)) {
            drawCircle(redGlow, radius = 10f * scaleY, center = Offset(centerX - 20f * scaleY, clavicleY + 7f * scaleY))
            drawCircle(redGlow, radius = 10f * scaleY, center = Offset(centerX + 20f * scaleY, clavicleY + 7f * scaleY))
        }

        // 4. Arms (Biceps on Anterior / Triceps on Posterior)
        val armZone = if (!isPosterior) MuscleZone.BICEPS else MuscleZone.TRICEPS
        val armColor = getZoneColor(armZone)

        // Upper Arms
        drawRoundRect(
            color = armColor,
            topLeft = Offset(centerX - 27f * scaleY, chestTopY + 8f * scaleY),
            size = Size(6.5f * scaleY, 20f * scaleY),
            cornerRadius = CornerRadius(3f * scaleY)
        )
        drawRoundRect(
            color = lineOutline,
            topLeft = Offset(centerX - 27f * scaleY, chestTopY + 8f * scaleY),
            size = Size(6.5f * scaleY, 20f * scaleY),
            cornerRadius = CornerRadius(3f * scaleY),
            style = Stroke(strokeWidth)
        )

        drawRoundRect(
            color = armColor,
            topLeft = Offset(centerX + 20.5f * scaleY, chestTopY + 8f * scaleY),
            size = Size(6.5f * scaleY, 20f * scaleY),
            cornerRadius = CornerRadius(3f * scaleY)
        )
        drawRoundRect(
            color = lineOutline,
            topLeft = Offset(centerX + 20.5f * scaleY, chestTopY + 8f * scaleY),
            size = Size(6.5f * scaleY, 20f * scaleY),
            cornerRadius = CornerRadius(3f * scaleY),
            style = Stroke(strokeWidth)
        )

        if (getZoneGlow(armZone)) {
            drawCircle(redGlow, radius = 9f * scaleY, center = Offset(centerX - 24f * scaleY, chestTopY + 18f * scaleY))
            drawCircle(redGlow, radius = 9f * scaleY, center = Offset(centerX + 24f * scaleY, chestTopY + 18f * scaleY))
        }

        // Forearms (Antebraço)
        val forearmZone = MuscleZone.FOREARMS
        val forearmColor = getZoneColor(forearmZone)
        drawRoundRect(
            color = forearmColor,
            topLeft = Offset(centerX - 29f * scaleY, chestTopY + 28f * scaleY),
            size = Size(5.5f * scaleY, 18f * scaleY),
            cornerRadius = CornerRadius(2.5f * scaleY)
        )
        drawRoundRect(
            color = forearmColor,
            topLeft = Offset(centerX + 23.5f * scaleY, chestTopY + 28f * scaleY),
            size = Size(5.5f * scaleY, 18f * scaleY),
            cornerRadius = CornerRadius(2.5f * scaleY)
        )

        if (!isPosterior) {
            // ==========================================
            // ANTERIOR VIEW (FRENTE)
            // ==========================================

            // Torso Background
            val torsoPath = Path().apply {
                moveTo(centerX - 16f * scaleY, clavicleY)
                lineTo(centerX + 16f * scaleY, clavicleY)
                lineTo(centerX + 13f * scaleY, waistY)
                lineTo(centerX + 15f * scaleY, hipY)
                lineTo(centerX - 15f * scaleY, hipY)
                lineTo(centerX - 13f * scaleY, waistY)
                close()
            }
            drawPath(torsoPath, color = baseBody)
            drawPath(torsoPath, color = lineOutline, style = Stroke(strokeWidth))

            // Pectorals (Peitorais Maior - Clavicular, Médio e Inferior)
            val chestUpper = isHighlighted(MuscleZone.CHEST_UPPER) || isSecondary(MuscleZone.CHEST_UPPER)
            val chestMid = isHighlighted(MuscleZone.CHEST_MID) || isSecondary(MuscleZone.CHEST_MID)
            val chestLower = isHighlighted(MuscleZone.CHEST_LOWER) || isSecondary(MuscleZone.CHEST_LOWER)
            val isAnyChest = chestUpper || chestMid || chestLower

            val pecColor = if (isAnyChest) redPrimary else baseBody

            // Left Pectoral
            val leftPec = Path().apply {
                moveTo(centerX - 1f * scaleY, clavicleY + 3f * scaleY)
                lineTo(centerX - 15f * scaleY, clavicleY + 3f * scaleY)
                lineTo(centerX - 16f * scaleY, chestCenterY + 4f * scaleY)
                quadraticTo(
                    centerX - 8f * scaleY, chestCenterY + 10f * scaleY,
                    centerX - 1f * scaleY, chestCenterY + 7f * scaleY
                )
                close()
            }
            drawPath(leftPec, color = pecColor)
            drawPath(leftPec, color = lineOutline, style = Stroke(strokeWidth))

            // Right Pectoral
            val rightPec = Path().apply {
                moveTo(centerX + 1f * scaleY, clavicleY + 3f * scaleY)
                lineTo(centerX + 15f * scaleY, clavicleY + 3f * scaleY)
                lineTo(centerX + 16f * scaleY, chestCenterY + 4f * scaleY)
                quadraticTo(
                    centerX + 8f * scaleY, chestCenterY + 10f * scaleY,
                    centerX + 1f * scaleY, chestCenterY + 7f * scaleY
                )
                close()
            }
            drawPath(rightPec, color = pecColor)
            drawPath(rightPec, color = lineOutline, style = Stroke(strokeWidth))

            // Glowing aura for Pectorals (like in Crossover Polia Alta)
            if (isAnyChest) {
                drawCircle(redGlow, radius = 18f * scaleY, center = Offset(centerX, chestCenterY + 5f * scaleY))
            }

            // Abdominals (Reto Abdominal 6-pack & Oblíquos)
            val absColor = if (isHighlighted(MuscleZone.ABS_UPPER) || isHighlighted(MuscleZone.ABS_LOWER)) redPrimary
            else if (isSecondary(MuscleZone.ABS_UPPER) || isSecondary(MuscleZone.ABS_LOWER)) redSecondary
            else baseBody

            // 6-pack grid
            for (row in 0..2) {
                val packY = chestCenterY + 12f * scaleY + (row * 6.5f * scaleY)
                drawRoundRect(
                    color = absColor,
                    topLeft = Offset(centerX - 6.5f * scaleY, packY),
                    size = Size(5.5f * scaleY, 5f * scaleY),
                    cornerRadius = CornerRadius(1.5f * scaleY)
                )
                drawRoundRect(
                    color = absColor,
                    topLeft = Offset(centerX + 1f * scaleY, packY),
                    size = Size(5.5f * scaleY, 5f * scaleY),
                    cornerRadius = CornerRadius(1.5f * scaleY)
                )
            }

            // Obliques
            val obliquesColor = getZoneColor(MuscleZone.OBLIQUES)
            drawRoundRect(
                color = obliquesColor,
                topLeft = Offset(centerX - 13f * scaleY, chestCenterY + 14f * scaleY),
                size = Size(5f * scaleY, 15f * scaleY),
                cornerRadius = CornerRadius(2f * scaleY)
            )
            drawRoundRect(
                color = obliquesColor,
                topLeft = Offset(centerX + 8f * scaleY, chestCenterY + 14f * scaleY),
                size = Size(5f * scaleY, 15f * scaleY),
                cornerRadius = CornerRadius(2f * scaleY)
            )

            // Quadriceps (Front Thighs)
            val quadsColor = getZoneColor(MuscleZone.QUADS)
            val quadsGlow = getZoneGlow(MuscleZone.QUADS)

            // Left Quad (Rectus Femoris + Vastus Lateralis/Medialis)
            val leftQuadPath = Path().apply {
                moveTo(centerX - 15f * scaleY, hipY)
                lineTo(centerX - 2f * scaleY, hipY)
                lineTo(centerX - 3.5f * scaleY, kneeY)
                lineTo(centerX - 13f * scaleY, kneeY)
                close()
            }
            drawPath(leftQuadPath, color = quadsColor)
            drawPath(leftQuadPath, color = lineOutline, style = Stroke(strokeWidth))

            // Right Quad
            val rightQuadPath = Path().apply {
                moveTo(centerX + 2f * scaleY, hipY)
                lineTo(centerX + 15f * scaleY, hipY)
                lineTo(centerX + 13f * scaleY, kneeY)
                lineTo(centerX + 3.5f * scaleY, kneeY)
                close()
            }
            drawPath(rightQuadPath, color = quadsColor)
            drawPath(rightQuadPath, color = lineOutline, style = Stroke(strokeWidth))

            if (quadsGlow) {
                drawCircle(redGlow, radius = 18f * scaleY, center = Offset(centerX - 8f * scaleY, hipY + 16f * scaleY))
                drawCircle(redGlow, radius = 18f * scaleY, center = Offset(centerX + 8f * scaleY, hipY + 16f * scaleY))
            }

            // Tibialis / Front Shins & Calves
            val tibialisColor = getZoneColor(MuscleZone.TIBIALIS)
            drawRoundRect(
                color = tibialisColor,
                topLeft = Offset(centerX - 11f * scaleY, kneeY + 4f * scaleY),
                size = Size(7.5f * scaleY, 26f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX - 11f * scaleY, kneeY + 4f * scaleY),
                size = Size(7.5f * scaleY, 26f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY),
                style = Stroke(strokeWidth)
            )

            drawRoundRect(
                color = tibialisColor,
                topLeft = Offset(centerX + 3.5f * scaleY, kneeY + 4f * scaleY),
                size = Size(7.5f * scaleY, 26f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX + 3.5f * scaleY, kneeY + 4f * scaleY),
                size = Size(7.5f * scaleY, 26f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY),
                style = Stroke(strokeWidth)
            )

        } else {
            // ==========================================
            // POSTERIOR VIEW (COSTAS)
            // ==========================================

            // 1. Latissimus Dorsi & Trapezius Diamond
            val latsColor = getZoneColor(MuscleZone.LATS)
            val latsGlow = getZoneGlow(MuscleZone.LATS)
            val trapsMidColor = getZoneColor(MuscleZone.TRAPS_MIDDLE_LOWER)

            // Back V-Taper Torso
            val backPath = Path().apply {
                moveTo(centerX - 16f * scaleY, clavicleY)
                lineTo(centerX + 16f * scaleY, clavicleY)
                lineTo(centerX + 12f * scaleY, waistY)
                lineTo(centerX + 15f * scaleY, hipY)
                lineTo(centerX - 15f * scaleY, hipY)
                lineTo(centerX - 12f * scaleY, waistY)
                close()
            }
            drawPath(backPath, color = baseBody)
            drawPath(backPath, color = lineOutline, style = Stroke(strokeWidth))

            // Large Latissimus Dorsi Wings (Left & Right)
            val leftLat = Path().apply {
                moveTo(centerX - 2f * scaleY, clavicleY + 4f * scaleY)
                lineTo(centerX - 17f * scaleY, clavicleY + 8f * scaleY)
                lineTo(centerX - 13f * scaleY, waistY)
                lineTo(centerX - 2f * scaleY, waistY - 6f * scaleY)
                close()
            }
            drawPath(leftLat, color = latsColor)
            drawPath(leftLat, color = lineOutline, style = Stroke(strokeWidth))

            val rightLat = Path().apply {
                moveTo(centerX + 2f * scaleY, clavicleY + 4f * scaleY)
                lineTo(centerX + 17f * scaleY, clavicleY + 8f * scaleY)
                lineTo(centerX + 13f * scaleY, waistY)
                lineTo(centerX + 2f * scaleY, waistY - 6f * scaleY)
                close()
            }
            drawPath(rightLat, color = latsColor)
            drawPath(rightLat, color = lineOutline, style = Stroke(strokeWidth))

            if (latsGlow) {
                drawCircle(redGlow, radius = 20f * scaleY, center = Offset(centerX, chestCenterY + 8f * scaleY))
            }

            // Rhomboids / Mid Traps
            val rhomboidsPath = Path().apply {
                moveTo(centerX, clavicleY + 4f * scaleY)
                lineTo(centerX + 7f * scaleY, chestCenterY)
                lineTo(centerX, waistY - 8f * scaleY)
                lineTo(centerX - 7f * scaleY, chestCenterY)
                close()
            }
            drawPath(rhomboidsPath, color = trapsMidColor)
            drawPath(rhomboidsPath, color = lineOutline, style = Stroke(strokeWidth))

            // Lower Back (Eretores da Espinha / Lombar)
            val lowerBackColor = getZoneColor(MuscleZone.LOWER_BACK)
            drawRoundRect(
                color = lowerBackColor,
                topLeft = Offset(centerX - 5.5f * scaleY, waistY - 4f * scaleY),
                size = Size(4.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(2f * scaleY)
            )
            drawRoundRect(
                color = lowerBackColor,
                topLeft = Offset(centerX + 1f * scaleY, waistY - 4f * scaleY),
                size = Size(4.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(2f * scaleY)
            )

            // Glutes (Glúteo Máximo & Médio)
            val glutesColor = getZoneColor(MuscleZone.GLUTES)
            val glutesGlow = getZoneGlow(MuscleZone.GLUTES)

            // Left Glute
            drawRoundRect(
                color = glutesColor,
                topLeft = Offset(centerX - 15f * scaleY, hipY - 2f * scaleY),
                size = Size(13.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(6f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX - 15f * scaleY, hipY - 2f * scaleY),
                size = Size(13.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(6f * scaleY),
                style = Stroke(strokeWidth)
            )

            // Right Glute
            drawRoundRect(
                color = glutesColor,
                topLeft = Offset(centerX + 1.5f * scaleY, hipY - 2f * scaleY),
                size = Size(13.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(6f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX + 1.5f * scaleY, hipY - 2f * scaleY),
                size = Size(13.5f * scaleY, 16f * scaleY),
                cornerRadius = CornerRadius(6f * scaleY),
                style = Stroke(strokeWidth)
            )

            if (glutesGlow) {
                drawCircle(redGlow, radius = 16f * scaleY, center = Offset(centerX, hipY + 6f * scaleY))
            }

            // Hamstrings (Posteriores de Coxa)
            val hamstringsColor = getZoneColor(MuscleZone.HAMSTRINGS)
            val hamstringsGlow = getZoneGlow(MuscleZone.HAMSTRINGS)

            // Left Hamstring
            drawRoundRect(
                color = hamstringsColor,
                topLeft = Offset(centerX - 14f * scaleY, hipY + 14f * scaleY),
                size = Size(11.5f * scaleY, 18f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX - 14f * scaleY, hipY + 14f * scaleY),
                size = Size(11.5f * scaleY, 18f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY),
                style = Stroke(strokeWidth)
            )

            // Right Hamstring
            drawRoundRect(
                color = hamstringsColor,
                topLeft = Offset(centerX + 2.5f * scaleY, hipY + 14f * scaleY),
                size = Size(11.5f * scaleY, 18f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY)
            )
            drawRoundRect(
                color = lineOutline,
                topLeft = Offset(centerX + 2.5f * scaleY, hipY + 14f * scaleY),
                size = Size(11.5f * scaleY, 18f * scaleY),
                cornerRadius = CornerRadius(3f * scaleY),
                style = Stroke(strokeWidth)
            )

            if (hamstringsGlow) {
                drawCircle(redGlow, radius = 14f * scaleY, center = Offset(centerX - 8f * scaleY, hipY + 23f * scaleY))
                drawCircle(redGlow, radius = 14f * scaleY, center = Offset(centerX + 8f * scaleY, hipY + 23f * scaleY))
            }

            // Calves (Gastrocnêmio Medial/Lateral & Sóleo)
            val calvesColor = getZoneColor(MuscleZone.CALVES)
            val calvesGlow = getZoneGlow(MuscleZone.CALVES)

            // Left Calf
            val leftCalf = Path().apply {
                moveTo(centerX - 12f * scaleY, kneeY + 4f * scaleY)
                lineTo(centerX - 4f * scaleY, kneeY + 4f * scaleY)
                lineTo(centerX - 5.5f * scaleY, ankleY)
                lineTo(centerX - 10.5f * scaleY, ankleY)
                close()
            }
            drawPath(leftCalf, color = calvesColor)
            drawPath(leftCalf, color = lineOutline, style = Stroke(strokeWidth))

            // Right Calf
            val rightCalf = Path().apply {
                moveTo(centerX + 4f * scaleY, kneeY + 4f * scaleY)
                lineTo(centerX + 12f * scaleY, kneeY + 4f * scaleY)
                lineTo(centerX + 10.5f * scaleY, ankleY)
                lineTo(centerX + 5.5f * scaleY, ankleY)
                close()
            }
            drawPath(rightCalf, color = calvesColor)
            drawPath(rightCalf, color = lineOutline, style = Stroke(strokeWidth))

            if (calvesGlow) {
                drawCircle(redGlow, radius = 12f * scaleY, center = Offset(centerX - 8f * scaleY, kneeY + 14f * scaleY))
                drawCircle(redGlow, radius = 12f * scaleY, center = Offset(centerX + 8f * scaleY, kneeY + 14f * scaleY))
            }
        }
    }
}

/**
 * Execution Guide Dialog with responsive Anatomical Muscle Map, Step-by-Step, and smooth transitions.
 */
@Composable
fun ExecutionGuideDialog(
    exercise: WorkoutExercise,
    onDismiss: () -> Unit
) {
    val anatomyInfo = remember(exercise.name) { getExerciseAnatomyInfo(exercise.name) }
    val steps = if (!exercise.instructions.isNullOrBlank()) {
        exercise.instructions.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        anatomyInfo.instructions
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                                text = "Como Executar",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // High Quality Anatomical Muscle Diagram Box (Responsive 195dp height)
                AnatomicalMuscleMap(
                    exerciseName = exercise.name,
                    anatomyInfo = anatomyInfo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PASSO A PASSO DA EXECUÇÃO:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(steps) { step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = step,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (anatomyInfo.secondaryMuscleGroup != null) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Músculos Sinergistas: ${anatomyInfo.secondaryMuscleGroup}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Entendido / Iniciar Exercício", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
