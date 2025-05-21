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
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.IDNA.Info
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import space.protagon.consignationscanner.R
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichText
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@OptIn(ExperimentalMaterial3Api::class)
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

    var showInfo by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    SmallFloatingActionButton(
                        onClick = {
                            showInfo = !showInfo;
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ) {
                        if (showInfo) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.go_back),
                            )
                        } else {
                            Icon(
                                Icons.Filled.Info,
                                stringResource(R.string.info),
                            )
                        }
                    }
                },
                title = {
                    Text(stringResource(R.string.app_name))
                }
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (hasCameraPermission) {
                if (showInfo) {
                    InfoScreen(onBack = { showInfo = false })
                } else {
                    CameraPreview(viewModel)
                }
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

    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    BackHandler { onBack() }
    Column {
        Text(
            text = "About",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp),
        )
        RichText(
            modifier = Modifier.padding(16.dp)
        ) {
            Markdown(
                context.applicationContext.assets.open("README.md").bufferedReader().use {
                    it.readText().split("<!--INAPP-->")[1]
                }
            )
        }

        Text(
            text = "Open Source Licenses",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp),
        )
        LibrariesContainer(
            modifier = Modifier.fillMaxSize(),
            showAuthor = true,
            showDescription = true,
            showVersion = true,
            showLicenseBadges = true,
        )
    }
}



@Composable
fun CameraPreview(viewModel: BarCodeScannerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var preview by remember { mutableStateOf<Preview?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val barScanState = viewModel.barScanState


    // Effect to unbind camera use cases when scan is successful
    LaunchedEffect(barScanState) {
        if (barScanState is BarScanState.ScanSuccess || barScanState is BarScanState.NoRefund) {
            cameraProvider?.unbindAll()
        }
    }


    Box(
        modifier = Modifier
            .size(400.dp)
            .padding(16.dp)
    ) {
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

                if (barScanState !is BarScanState.ScanSuccess && barScanState !is BarScanState.NoRefund) {
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

                        lifecycleOwner.lifecycle.addObserver(object :
                            DefaultLifecycleObserver {
                            override fun onDestroy(owner: LifecycleOwner) {
                                cameraExecutor.shutdown()
                            }
                        })
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(35.dp))

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
                Spacer(modifier = Modifier.height(10.dp))
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
                    label = { Text(stringResource(R.string.nonrefundable)) },
                    onClick = {},
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            stringResource(R.string.nonrefundable),
                            Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )

                Button(onClick = { viewModel.resetState() }) {
                    Text(stringResource(R.string.retrythis))
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
                    label = { Text(stringResource(R.string.refundable)) },
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
                    Text(
                        """
                            • ${container.material.toString()}
                            • ${stringResource(R.string.value)}: ${container.refund.toString()}$
                            • ${stringResource(R.string.lasteModification)}: ${container.modificationDate.toString()}
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
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


