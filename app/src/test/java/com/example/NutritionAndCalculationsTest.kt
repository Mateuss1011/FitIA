package com.example

import com.example.data.repository.AppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionAndCalculationsTest {

    @Test
    fun `test calorie calculation from macronutrients`() {
        val carbs = 50.0   // 50 * 4 = 200 kcal
        val protein = 40.0 // 40 * 4 = 160 kcal
        val fats = 15.0    // 15 * 9 = 135 kcal

        val expectedCalories = (carbs * 4.0) + (protein * 4.0) + (fats * 9.0)
        assertEquals(495.0, expectedCalories, 0.01)

        // Zero macros should produce 0 calories
        val zeroCalories = (0.0 * 4.0) + (0.0 * 4.0) + (0.0 * 9.0)
        assertEquals(0.0, zeroCalories, 0.001)
    }

    @Test
    fun `test daily water target recommendation based on body weight`() {
        val userWeightKg = 75.0
        val standardMlPerKg = 35.0

        val targetWaterMl = (userWeightKg * standardMlPerKg).toInt()
        assertEquals(2625, targetWaterMl)

        // Water consumption percentage
        val consumedWaterMl = 1500
        val percentage = (consumedWaterMl.toFloat() / targetWaterMl.toFloat()) * 100f
        assertEquals(57.14f, percentage, 0.1f)
    }

    @Test
    fun `test workout fallback exercises distribution produces 7 exercises per sheet`() {
        // Test masculine fallback plan for 4 training days
        val mockPlan4Days = AppRepository.getMockPlan(
            objective = "Hipertrofia",
            name = "Alex",
            gender = "Masculino",
            numDays = 4
        )

        val exercises = mockPlan4Days.workoutPlan
        val sheets = exercises.map { it.workoutDay }.distinct().sorted()
        assertEquals(listOf("A", "B", "C", "D"), sheets)

        // Verify that EVERY workout sheet has exactly 7 exercises
        for (sheet in sheets) {
            val exercisesForSheet = exercises.filter { it.workoutDay == sheet }
            assertEquals("Sheet $sheet must have exactly 7 exercises", 7, exercisesForSheet.size)
        }
    }

    @Test
    fun `test feminine workout fallback creates expected sheets`() {
        val femininePlan3Days = AppRepository.getMockPlan(
            objective = "Definição",
            name = "Mariana",
            gender = "Feminino",
            numDays = 3
        )

        val exercises = femininePlan3Days.workoutPlan
        val sheets = exercises.map { it.workoutDay }.distinct().sorted()
        assertEquals(listOf("A", "B", "C"), sheets)

        for (sheet in sheets) {
            val exercisesForSheet = exercises.filter { it.workoutDay == sheet }
            assertEquals(7, exercisesForSheet.size)
        }
    }

    @Test
    fun `test meal fallback plan generates valid meals with positive nutritional values`() {
        val mockPlan = AppRepository.getMockPlan(
            objective = "Hipertrofia",
            name = "Carlos",
            gender = "Masculino",
            numDays = 4
        )

        val defaultMeals = mockPlan.meals
        assertTrue("Fallback diet must have meals", defaultMeals.isNotEmpty())

        var totalCalories = 0.0

        for (meal in defaultMeals) {
            assertTrue("Meal name cannot be blank", meal.name.isNotBlank())
            assertTrue("Meal calories must be > 0", meal.calories > 0)
            assertTrue("Meal description cannot be blank", meal.description.isNotBlank())

            totalCalories += meal.calories
        }

        assertTrue("Daily total calories must be substantial for Hypertrophy", totalCalories >= 1500.0)
    }
}
