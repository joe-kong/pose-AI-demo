package com.example.posedetection.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// 運動メニューのデータクラス
data class ExerciseItem(
    val id: String,
    val title: String,
    val description: String
)

@Composable
fun HomeScreen(navController: NavController) {
    val exerciseList = listOf(
        ExerciseItem(
            id = "hamstring_stretch",
            title = "ハムストリングスストレッチ",
            description = "座った姿勢で足を伸ばし、上半身を前に倒すストレッチです。"
        ),
        ExerciseItem(
            id = "shoulder_flexion",
            title = "肩関節屈曲",
            description = "腕をまっすぐ前から上に持ち上げる運動です。"
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "ポーズ検出アプリ",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "運動メニュー",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(exerciseList) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onItemClick = {
                            navController.navigate(exercise.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseCard(exercise: ExerciseItem, onItemClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(onClick = onItemClick)
        ) {
            Text(
                text = exercise.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = exercise.description,
                fontSize = 14.sp
            )
        }
    }
} 