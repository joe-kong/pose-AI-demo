package com.example.posedetection.ui

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.posedetection.R
import com.example.posedetection.ml.PoseLandmarkerHelper
import com.example.posedetection.ui.theme.CorrectPoseColor
import com.example.posedetection.ui.theme.IncorrectPoseColor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StretchDetectionScreen(navController: androidx.navigation.NavController? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // カメラ権限の状態
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // アプリの状態
    var isStarted by remember { mutableStateOf(false) }
    var currentSet by remember { mutableStateOf(1) }
    var currentSide by remember { mutableStateOf(PoseLandmarkerHelper.StretchMode.LEFT_LEG) }
    var isPoseCorrect by remember { mutableStateOf(false) }
    var timerValue by remember { mutableStateOf(30) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    
    // カメラプレビューView
    val previewView = remember { PreviewView(context) }
    
    // PoseLandmarkerHelper
    val poseLandmarkerHelper = remember {
        PoseLandmarkerHelper(
            context = context,
            poseLandmarkerListener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onPoseDetected(
                    result: PoseLandmarkerResult,
                    isCorrectPose: Boolean,
                    currentMode: PoseLandmarkerHelper.StretchMode,
                    input: Bitmap
                ) {
                    isPoseCorrect = isCorrectPose
                    
                    // 正しい姿勢であればタイマーを開始/再開
                    if (isStarted && !isCompleted) {
                        if (isCorrectPose && !isTimerRunning) {
                            isTimerRunning = true
                        } else if (!isCorrectPose && isTimerRunning) {
                            isTimerRunning = false
                        }
                    }
                }
                
                override fun onError(error: String) {
                    Log.e("StretchDetectionScreen", "Pose detection error: $error")
                }
            }
        )
    }
    
    // タイマー効果
    LaunchedEffect(isTimerRunning, timerValue, currentSet, currentSide) {
        if (isTimerRunning && timerValue > 0) {
            while (timerValue > 0 && isTimerRunning) {
                delay(1000)
                timerValue -= 1
            }
            
            // タイマーが0になった場合の処理
            if (timerValue == 0) {
                // 左足→右足の切り替え
                if (currentSide == PoseLandmarkerHelper.StretchMode.LEFT_LEG) {
                    currentSide = PoseLandmarkerHelper.StretchMode.RIGHT_LEG
                    poseLandmarkerHelper.setStretchMode(currentSide)
                    timerValue = 30
                    isTimerRunning = false
                } 
                // 右足が終わった場合、次のセットへ
                else {
                    if (currentSet < 3) {
                        currentSet += 1
                        currentSide = PoseLandmarkerHelper.StretchMode.LEFT_LEG
                        poseLandmarkerHelper.setStretchMode(currentSide)
                        timerValue = 30
                        isTimerRunning = false
                    } else {
                        // 全セット完了
                        isCompleted = true
                    }
                }
            }
        }
    }
    
    // リソース解放
    DisposableEffect(Unit) {
        onDispose {
            poseLandmarkerHelper.clearPoseLandmarker()
        }
    }
    
    // レイアウト
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // カメラ権限の確認と要求
        if (!cameraPermissionState.status.isGranted) {
            // 権限がない場合、権限を要求するUI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = context.getString(R.string.camera_permission_required),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text(text = context.getString(R.string.grant_permission))
                }
            }
        } else {
            // カメラプレビュー
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                ) { view ->
                    // カメラのセットアップ
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        // プレビュー設定
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(view.surfaceProvider)
                        }
                        
                        // 画像分析設定
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    Executors.newSingleThreadExecutor()
                                ) { imageProxy ->
                                    // コルーチンのスコープを作成して、その中で中断関数を呼び出す
                                    lifecycleOwner.lifecycleScope.launch {
                                        processImage(imageProxy, poseLandmarkerHelper, currentSide)
                                    }
                                }
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            Log.e("StretchDetectionScreen", "Camera bind error: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
                
                // 現在のストレッチと姿勢状態の表示
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (currentSide == PoseLandmarkerHelper.StretchMode.LEFT_LEG) 
                                context.getString(R.string.stretch_left_leg)
                              else 
                                context.getString(R.string.stretch_right_leg),
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "セット: $currentSet/3",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 姿勢フィードバックとタイマー表示
                if (isStarted && !isCompleted) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                if (isPoseCorrect) CorrectPoseColor.copy(alpha = 0.7f)
                                else IncorrectPoseColor.copy(alpha = 0.7f)
                            )
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isPoseCorrect) context.getString(R.string.pose_correct)
                                   else context.getString(R.string.pose_incorrect),
                            color = Color.White,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )

                        if (isPoseCorrect) {
                            Text(
                                text = "残り時間: ${timerValue}秒",
                                color = Color.White,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // 完了メッセージ
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.completed),
                            color = Color.White,
                            fontSize = 32.sp
                        )
                    }
                }
            }
            
            // コントロールボタン
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 戻るボタン（ナビゲーションコントローラーがある場合のみ表示）
                navController?.let {
                    Button(
                        onClick = {
                            navController.navigateUp()
                        }
                    ) {
                        Text("戻る")
                    }
                }
                
                Button(
                    onClick = {
                        if (isCompleted) {
                            // リセット
                            isStarted = false
                            isCompleted = false
                            currentSet = 1
                            currentSide = PoseLandmarkerHelper.StretchMode.LEFT_LEG
                            poseLandmarkerHelper.setStretchMode(currentSide)
                            timerValue = 30
                            isTimerRunning = false
                        } else {
                            // 開始/停止
                            isStarted = !isStarted
                            if (!isStarted) {
                                isTimerRunning = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = when {
                            isCompleted -> context.getString(R.string.reset)
                            isStarted -> "一時停止"
                            else -> context.getString(R.string.start)
                        }
                    )
                }
                
                if (isStarted && !isCompleted) {
                    Button(
                        onClick = {
                            // 現在のストレッチをスキップ
                            if (currentSide == PoseLandmarkerHelper.StretchMode.LEFT_LEG) {
                                currentSide = PoseLandmarkerHelper.StretchMode.RIGHT_LEG
                                poseLandmarkerHelper.setStretchMode(currentSide)
                            } else {
                                if (currentSet < 3) {
                                    currentSet += 1
                                    currentSide = PoseLandmarkerHelper.StretchMode.LEFT_LEG
                                    poseLandmarkerHelper.setStretchMode(currentSide)
                                } else {
                                    isCompleted = true
                                }
                            }
                            timerValue = 30
                            isTimerRunning = false
                        }
                    ) {
                        Text(text = "スキップ")
                    }
                }
            }
        }
    }
}

// 画像処理関数
// 画像処理関数の修正
private suspend fun processImage(
    imageProxy: ImageProxy,
    poseLandmarkerHelper: PoseLandmarkerHelper,
    currentSide: PoseLandmarkerHelper.StretchMode
) {
    try {
        withContext(Dispatchers.Default) {
            val bitmap = imageProxy.toBitmap()
            bitmap?.let {
                poseLandmarkerHelper.detectPose(it, currentSide)
            }
        }
    } finally {
        // 必ず実行される
        imageProxy.close()
    }
}

// ImageProxyをBitmapに変換する拡張関数
private fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 75, out)
    val imageBytes = out.toByteArray()
    
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
} 