import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import space.protagon.consignationscanner.model.BarCodeAnalyzer
import space.protagon.consignationscanner.viewmodel.BarCodeScannerViewModel
import space.protagon.consignationscanner.BarScanState
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.unit.sp
import space.protagon.consignationscanner.R

@Composable
fun BarcodeScannerScreen(
    viewModel: BarCodeScannerViewModel
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreview(viewModel)
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.permissionRequired))
            Button(onClick = { launcher.launch(android.Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.grandPermission))
            }
        }
    }
}


@Composable
fun CameraPreview(viewModel: BarCodeScannerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var preview by remember { mutableStateOf<Preview?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val barScanState = viewModel.barScanState


    // Effect to unbind camera use cases when scan is successful
    LaunchedEffect(barScanState) {
        if (barScanState is BarScanState.ScanSuccess || barScanState is BarScanState.NoRefund) {
            cameraProvider?.unbindAll()
        }
    }

    Column {
        Box(
            modifier = Modifier
                .size(400.dp)
                .padding(16.dp)
        ) {
            if (barScanState !is BarScanState.ScanSuccess && barScanState !is BarScanState.NoRefund) {
                AndroidView(
                    factory = { androidViewContext ->
                        PreviewView(androidViewContext).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_START
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                        val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
                        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
                            ProcessCameraProvider.getInstance(context)

                        cameraProviderFuture.addListener({
                            preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val provider: ProcessCameraProvider = cameraProviderFuture.get()
                            cameraProvider = provider
                            val barcodeAnalyzer = BarCodeAnalyzer(viewModel)
                            val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor, barcodeAnalyzer)
                                }

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.d("CameraPreview", "Error: ${e.localizedMessage}")
                            }

                            lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                                override fun onDestroy(owner: LifecycleOwner) {
                                    cameraExecutor.shutdown()
                                }
                            })
                        }, ContextCompat.getMainExecutor(context))
                    }
                )
            }
        }

        when (barScanState) {
            is BarScanState.Ideal -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.positioning))
                }
            }

            is BarScanState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.scanning))
                }
            }

            is BarScanState.NoRefund -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AssistChip(
                        label = { Text( stringResource(R.string.nonrefundable)) },
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                stringResource(R.string.nonrefundable),
                                Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.resetState() }) {
                        Text( stringResource(R.string.retrythis))
                    }
                }
            }

            is BarScanState.ScanSuccess -> {
                // Regular barcode result
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AssistChip(
                        label = { Text( stringResource(R.string.refundable)) },
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                stringResource(R.string.refundable),
                                Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                    val container = barScanState.container;
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        Text(
                            text = container.name.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(8.dp),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("""
                            • ${container.material.toString()}
                            • ${stringResource(R.string.value)}: ${container.refund.toString()}$
                            • ${stringResource(R.string.lasteModification)}: ${container.modificationDate.toString()}
                        """.trimIndent(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp))
                    }
                    Button(onClick = { viewModel.resetState() }) {
                        Text(stringResource(R.string.scanOther))
                    }
                }
            }

            is BarScanState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${barScanState.error}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.resetState() }) {
                        Text(stringResource(R.string.retrythis))
                    }
                }
            }
        }
    }
}
