package com.example.posedetection.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.lang.IllegalStateException

class PoseLandmarkerHelper(
    private val context: Context,
    private val poseLandmarkerListener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null
    
    // モデルファイル名
    private val modelName = "pose_landmarker_full.task"
    
    // 現在の姿勢がストレッチの正しい姿勢かどうかを判定する結果
    private var isCorrectStretchPose = false

    // ストレッチのモード（左足か右足か、または左腕か右腕か）
    private var currentStretchMode = StretchMode.LEFT_LEG
    
    init {
        setupPoseLandmarker()
    }
    
    enum class StretchMode {
        LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM
    }
    
    interface LandmarkerListener {
        fun onPoseDetected(
            result: PoseLandmarkerResult,
            isCorrectPose: Boolean,
            currentMode: StretchMode,
            input: Bitmap
        )
        
        fun onError(error: String)
    }
    
    // PoseLandmarkerの初期化
    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(modelName)
                .build()
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerListener?.onError(
                "PoseLandmarkerの初期化エラー: ${e.message}"
            )
            Log.e(TAG, "MediaPipe PoseLandmarkerの初期化エラー: ${e.message}")
        } catch (e: Exception) {
            poseLandmarkerListener?.onError(
                "PoseLandmarkerの初期化エラー: ${e.message}"
            )
            Log.e(TAG, "MediaPipe PoseLandmarkerの初期化エラー: ${e.message}")
        }
    }
    
    // 指定されたビットマップからポーズを検出
    fun detectPose(image: Bitmap, mode: StretchMode) {
        currentStretchMode = mode
        
        if (poseLandmarker == null) {
            setupPoseLandmarker()
        }

        // Bitmapから MPImage を作成
        val options = ImageProcessingOptions.builder().build()

        val mpImage = BitmapImageBuilder(image).build()

        val poseResult = poseLandmarker?.detect(mpImage,options)

        // ポーズ検出結果があれば、ストレッチの姿勢かどうかを判定
        poseResult?.let { result ->
            // 検出結果の検証
            isCorrectStretchPose = if (result.landmarks().isNotEmpty()) {
                when (currentStretchMode) {
                    StretchMode.LEFT_LEG, StretchMode.RIGHT_LEG -> {
                        // ハムストリングストレッチの姿勢判定
                        detectHamstringStretchPose(result)
                    }
                    StretchMode.LEFT_ARM, StretchMode.RIGHT_ARM -> {
                        // 肩関節屈曲のポーズ判定
                        detectShoulderFlexionPose(result)
                    }
                }
            } else {
                false
            }
            
            // リスナーに結果を通知
            poseLandmarkerListener?.onPoseDetected(
                result,
                isCorrectStretchPose,
                currentStretchMode,
                image
            )
        }
    }
    
    // ハムストリングストレッチの姿勢判定
    private fun detectHamstringStretchPose(result: PoseLandmarkerResult): Boolean {
        // ランドマークのリスト
        val landmarks = result.landmarks()[0]
        
        // 膝、股関節、足首のインデックス
        // 左: 左膝 = 25, 左股関節 = 23, 左足首 = 27
        // 右: 右膝 = 26, 右股関節 = 24, 右足首 = 28
        val (kneeIndex, hipIndex, ankleIndex) = 
            if (currentStretchMode == StretchMode.LEFT_LEG) {
                Triple(25, 23, 27) // 左足
            } else {
                Triple(26, 24, 28) // 右足
            }
        
        // 肩のインデックス
        val shoulderIndex = if (currentStretchMode == StretchMode.LEFT_LEG) 11 else 12
        
        // 腰のインデックス（中間点）
        val waistIndex = 0  // NoseはMediaPipeで0
        
        // 姿勢の基準値
        // 1. 前に出した脚の膝が伸びているか（一直線か）
        val kneeAngle = calculateAngle(
            landmarks[hipIndex].x(), landmarks[hipIndex].y(),
            landmarks[kneeIndex].x(), landmarks[kneeIndex].y(),
            landmarks[ankleIndex].x(), landmarks[ankleIndex].y()
        )
        
        // 2. 体が前傾しているか
        val torsoAngle = calculateAngle(
            landmarks[shoulderIndex].x(), landmarks[shoulderIndex].y(),
            landmarks[hipIndex].x(), landmarks[hipIndex].y(),
            landmarks[hipIndex].x() + 1f, landmarks[hipIndex].y()  // 水平線の参照点
        )
        
        // ハムストリングストレッチの姿勢として適切かどうか判定
        val isKneeExtended = kneeAngle >= 160.0  // 膝がほぼ伸びている（160度以上）
        val isTorsoLeaning = torsoAngle in 30.0..90.0  // 体が前傾している（30〜90度）
        
        return isKneeExtended && isTorsoLeaning
    }
    
    // 肩関節屈曲のポーズ判定
    private fun detectShoulderFlexionPose(result: PoseLandmarkerResult): Boolean {
        // ランドマークのリスト
        val landmarks = result.landmarks()[0]
        
        // 腕、肩、肘のインデックス
        // MediaPipeのランドマークインデックス：
        // 左肩 = 11, 左肘 = 13, 左手首 = 15
        // 右肩 = 12, 右肘 = 14, 右手首 = 16
        val (shoulderIndex, elbowIndex, wristIndex) = 
            if (currentStretchMode == StretchMode.LEFT_ARM) {
                Triple(11, 13, 15) // 左腕
            } else {
                Triple(12, 14, 16) // 右腕
            }
        
        // 股関節のインデックス（胴体の基準点）
        val hipIndex = if (currentStretchMode == StretchMode.LEFT_ARM) 23 else 24
        
        // 1. 腕の上昇角度（肩から肘、肘から手首のベクトルが垂直方向とどれだけ近いか）
        val armAngle = calculateAngle(
            landmarks[shoulderIndex].x(), landmarks[shoulderIndex].y(),
            landmarks[elbowIndex].x(), landmarks[elbowIndex].y(),
            landmarks[wristIndex].x(), landmarks[wristIndex].y()
        )
        
        // 2. 腕の高さ（肩から手首までの高さ）
        val armElevation = calculateAngle(
            landmarks[hipIndex].x(), landmarks[hipIndex].y(),
            landmarks[shoulderIndex].x(), landmarks[shoulderIndex].y(),
            landmarks[wristIndex].x(), landmarks[wristIndex].y() - 1f  // 垂直線の参照点
        )
        
        // 肩関節屈曲の姿勢として適切かどうか判定
        val isArmStraight = armAngle >= 160.0  // 腕がほぼまっすぐ伸びている（160度以上）
        val isArmElevated = armElevation >= 80.0  // 腕がほぼ垂直に上がっている（80度以上）
        
        // 判定結果をログに出力（デバッグ用）
        Log.d(TAG, "肩関節屈曲 - 腕角度: $armAngle, 腕の高さ: $armElevation, 判定: ${isArmStraight && isArmElevated}")
        
        return isArmStraight && isArmElevated
    }
    
    // 3点間の角度を計算（度数法）
    private fun calculateAngle(
        x1: Float, y1: Float,  // 第1点
        x2: Float, y2: Float,  // 第2点（角度を計算する点）
        x3: Float, y3: Float   // 第3点
    ): Double {
        // ベクトル1（点1から点2）
        val vector1X = x1 - x2
        val vector1Y = y1 - y2
        
        // ベクトル2（点3から点2）
        val vector2X = x3 - x2
        val vector2Y = y3 - y2
        
        // 内積
        val dotProduct = vector1X * vector2X + vector1Y * vector2Y
        
        // 各ベクトルの大きさ
        val magnitude1 = Math.sqrt((vector1X * vector1X + vector1Y * vector1Y).toDouble())
        val magnitude2 = Math.sqrt((vector2X * vector2X + vector2Y * vector2Y).toDouble())
        
        // 角度の計算（ラジアン）
        val angleRad = Math.acos(dotProduct / (magnitude1 * magnitude2))
        
        // ラジアンから度数法へ変換
        return Math.toDegrees(angleRad)
    }
    
    // 現在の姿勢が正しいかどうかを取得
    fun isCorrectPose(): Boolean = isCorrectStretchPose
    
    fun setStretchMode(mode: StretchMode) {
        currentStretchMode = mode
    }
    
    // リソース解放
    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
    
    companion object {
        private const val TAG = "PoseLandmarkerHelper"
    }
} 