package com.vintogrophy.app.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.vintogrophy.app.R
import com.vintogrophy.app.filter.PhotoFilter
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var selectedFilter by remember { mutableStateOf(PhotoFilter.NONE) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        PermissionRequester(onResult = { hasPermission = it })
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.camera_permission_rationale))
        }
        return
    }

    LaunchedEffect(hasPermission) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).awaitSuspend()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            androidx.compose.ui.viewinterop.AndroidView(factory = { previewView })
            statusMessage?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                    color = Color.White,
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            items(PhotoFilter.entries) { filter ->
                FilterChip(
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    capturePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        filter = selectedFilter,
                        onResult = { statusMessage = it },
                    )
                },
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.capture),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRequester(onResult: (Boolean) -> Unit) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    filter: PhotoFilter,
    onResult: (String) -> Unit,
) {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "VINTO_${formatter.format(System.currentTimeMillis())}.jpg"
    val outputDir = File(context.getExternalFilesDir(null), "Vintogrophy")
    outputDir.mkdirs()
    val outputFile = File(outputDir, fileName)

    val metadata = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(
        metadata,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                if (filter != PhotoFilter.NONE) {
                    applyFilterInPlace(outputFile, filter)
                }
                onResult("Saved: ${outputFile.name} (${filter.label})")
            }

            override fun onError(exception: ImageCaptureException) {
                onResult("Capture failed: ${exception.message}")
            }
        },
    )
}

private suspend fun <T> ListenableFuture<T>.awaitSuspend(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            Executor { it.run() },
        )
    }

private fun applyFilterInPlace(file: File, filter: PhotoFilter) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val filtered = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(filtered)
    val paint = Paint().apply { colorFilter = filter.colorFilter() }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    file.outputStream().use { filtered.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    bitmap.recycle()
    filtered.recycle()
}
