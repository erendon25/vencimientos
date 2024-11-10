package com.example.barcodescanner.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barcodescanner.R
import com.example.barcodescanner.databinding.FragmentScanBinding
import com.example.barcodescanner.ui.viewmodels.ScanOverlayView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {
    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private lateinit var scanOverlayView: ScanOverlayView

    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    private var barcodeDetected = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            startCamera()
        } else {
            Log.w(TAG, "Camera permission denied")
            Toast.makeText(context, "Permisos no otorgados por el usuario.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "Fragment view created")

        scanOverlayView = binding.scanOverlayView

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }


    }
    private fun toggleFlash() {
        try {
            camera?.let { camera ->
                if (camera.cameraInfo.hasFlashUnit()) {
                    val currentTorchState = camera.cameraInfo.torchState.value ?: TorchState.OFF
                    val newTorchState = if (currentTorchState == TorchState.ON) TorchState.OFF else TorchState.ON
                    camera.cameraControl.enableTorch(newTorchState == TorchState.ON)
                    Log.d(TAG, "Flash toggled to ${if (newTorchState == TorchState.ON) "ON" else "OFF"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flash: ${e.message}")
        }
    }
    private fun resetScanArea() {
        scanOverlayView.resetToInitialSize()
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                            if (!barcodeDetected) {
                                barcodeDetected = true
                                activity?.runOnUiThread {
                                    Log.d(TAG, "Barcode detected: $barcode")
                                    if (findNavController().currentDestination?.id == R.id.scanFragment) {
                                        findNavController().navigate(
                                            ScanFragmentDirections.actionScanFragmentToRegisterFragment(barcode)
                                        )
                                    } else {
                                        Log.w(TAG, "Navigation attempted from incorrect destination")
                                    }
                                }
                            }
                        })
                    }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                Log.d(TAG, "Camera started successfully")
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(context, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment view destroyed")
        cameraExecutor.shutdown()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        barcodeDetected = false
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val TAG = "ScanFragment"
    }

    private inner class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()
        private var lastProcessedTimestamp = 0L
        private var lastDetectedBarcode: String? = null
        private var detectionCount = 0

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastProcessedTimestamp < 500) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                try {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { rawValue ->
                                    if (isBarcodeWithinScanArea(barcode.boundingBox, imageProxy.width, imageProxy.height)) {
                                        if (rawValue == lastDetectedBarcode) {
                                            detectionCount++
                                            if (detectionCount >= 3) {
                                                Log.d(TAG, "Barcode confirmed: $rawValue")
                                                lastProcessedTimestamp = currentTimestamp
                                                onBarcodeDetected(rawValue)
                                                detectionCount = 0
                                                lastDetectedBarcode = null
                                                return@addOnSuccessListener
                                            }
                                        } else {
                                            detectionCount = 1
                                            lastDetectedBarcode = rawValue
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Barcode scanning failed: ${e.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image for barcode: ${e.message}")
                    imageProxy.close()
                }
            }
        }

        private fun isBarcodeWithinScanArea(barcodeBox: android.graphics.Rect?, imageWidth: Int, imageHeight: Int): Boolean {
            if (barcodeBox == null) return false

            val scanArea = scanOverlayView.getScanArea()
            val scaleX = imageWidth / scanOverlayView.width.toFloat()
            val scaleY = imageHeight / scanOverlayView.height.toFloat()

            val scaledScanArea = RectF(
                scanArea.left * scaleX,
                scanArea.top * scaleY,
                scanArea.right * scaleX,
                scanArea.bottom * scaleY
            )

            return barcodeBox.intersect(scaledScanArea.toRect())
        }
    }
}