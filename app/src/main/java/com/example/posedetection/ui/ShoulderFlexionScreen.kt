package com.example.posedetection.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.example.posedetection.ml.PoseLandmarkerHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.media.Image
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

enum class ShoulderSide {
    LEFT_ARM, RIGHT_ARM
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ShoulderFlexionScreen(navController: NavController? = null) {
    val cameraPermissionState: PermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    var imageAnalyzer: ImageAnalysis? by remember { mutableStateOf(null) }
    
    // カメラの設定
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    // 肩関節屈曲のセッション状態
    var currentSet by remember { mutableStateOf(1) }
    var totalSets by remember { mutableStateOf(3) }
    var currentSide by remember { mutableStateOf(ShoulderSide.RIGHT_ARM) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var timeRemaining by remember { mutableStateOf(5) }
    var isPoseCorrect by remember { mutableStateOf(false) }
    var lastProcessingTimeMs by remember { mutableStateOf(0L) }
    
    // プレビュー画像
    var bitmapBuffer by remember { mutableStateOf<Bitmap?>(null) }
    
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
                    bitmapBuffer = input
                }
                
                override fun onError(error: String) {
                    Log.e("ShoulderFlexionScreen", "ポーズ検出エラー: $error")
                }
            }
        )
    }
    
    // タイマーロジック
    LaunchedEffect(isPoseCorrect, isTimerRunning) {
        if (isPoseCorrect && !isTimerRunning && timeRemaining > 0) {
            isTimerRunning = true
            while (timeRemaining > 0 && isPoseCorrect) {
                delay(1000)
                timeRemaining--
            }
            
            if (timeRemaining == 0) {
                // 1セット完了
                if (currentSide == ShoulderSide.RIGHT_ARM) {
                    currentSide = ShoulderSide.LEFT_ARM
                    timeRemaining = 5
                } else {
                    currentSide = ShoulderSide.RIGHT_ARM
                    if (currentSet < totalSets) {
                        currentSet++
                    } else {
                        // TODO: 完了メッセージ表示
                    }
                    timeRemaining = 5
                }
            }
            isTimerRunning = false
        } else if (!isPoseCorrect) {
            isTimerRunning = false
        }
    }
    
    // カメラパーミッションがある場合はカメラプレビューを表示
    if (cameraPermissionState.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 上部のステータス表示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "肩関節屈曲",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("セット: $currentSet / $totalSets")
                        Text("側: ${if (currentSide == ShoulderSide.RIGHT_ARM) "右腕" else "左腕"}")
                    }
                    
                    if (isTimerRunning) {
                        Text(
                            text = "カウントダウン: $timeRemaining 秒",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (isPoseCorrect) {
                        Text(
                            text = "正しい姿勢です！キープしてください",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "腕をまっすぐ前に上げてください",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    // 処理時間表示（デバッグ用）
                    Text(
                        text = "処理時間: $lastProcessingTimeMs ms",
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 12.sp
                    )
                }
            }
            
            // カメラプレビュー
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // カメラプレビュー
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            
                            imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImage(
                                            imageProxy,
                                            currentSide,
                                            poseLandmarkerHelper
                                        ) { processingTimeMs ->
                                            lastProcessingTimeMs = processingTimeMs
                                        }
                                    }
                                }
                            
                            try {
                                cameraProvider.unbindAll()
                                
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    preview,
                                    imageCapture,
                                    imageAnalyzer
                                )
                            } catch (e: Exception) {
                                Log.e("ShoulderFlexionScreen", "カメラのバインドに失敗: ${e.message}")
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // 検出結果のオーバーレイ表示
                bitmapBuffer?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Pose Detection Result",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 下部のコントロール
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        navController?.navigateUp()
                    }
                ) {
                    Text("戻る")
                }
                
                Button(
                    onClick = {
                        // 側を切り替える
                        currentSide = if (currentSide == ShoulderSide.RIGHT_ARM) 
                            ShoulderSide.LEFT_ARM else ShoulderSide.RIGHT_ARM
                        timeRemaining = 5
                        isTimerRunning = false
                    }
                ) {
                    Text("側を切り替え")
                }
            }
        }
    } else {
        // カメラパーミッションが無い場合
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("このアプリはカメラ権限が必要です")
            Button(
                onClick = {
                    cameraPermissionState.launchPermissionRequest()
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("権限を許可する")
            }
        }
    }
    
    DisposableEffect(key1 = Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseLandmarkerHelper.clearPoseLandmarker()
        }
    }
}

private fun processImage(
    imageProxy: ImageProxy,
    currentSide: ShoulderSide,
    poseLandmarkerHelper: PoseLandmarkerHelper,
    onProcessingTimeCallback: (Long) -> Unit
) {
    val startTime = SystemClock.elapsedRealtime()
    
    val bitmap = imageProxy.toBitmap()
    
    // モードに応じて検出モードを設定
    val detectionMode = when (currentSide) {
        ShoulderSide.LEFT_ARM -> PoseLandmarkerHelper.StretchMode.LEFT_ARM
        ShoulderSide.RIGHT_ARM -> PoseLandmarkerHelper.StretchMode.RIGHT_ARM
    }
    
    // ポーズの検出
    poseLandmarkerHelper.detectPose(bitmap, detectionMode)
    
    // 処理時間を計算して通知
    val processingTime = SystemClock.elapsedRealtime() - startTime
    onProcessingTimeCallback(processingTime)
    
    imageProxy.close()
}

// ImageProxy から Bitmap への変換関数
private fun ImageProxy.toBitmap(): Bitmap {
    val image: Image = this.image ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    
    // YUV_420_888 から RGB への変換
    val planes = image.planes
    val buffer: ByteBuffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width
    
    // Bitmap の作成
    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride, 
        height, 
        Bitmap.Config.ARGB_8888
    )
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)
    
    return bitmap
} 