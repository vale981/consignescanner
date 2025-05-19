package space.protagon.consignationscanner.view

import BarcodeScannerScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import space.protagon.consignationscanner.ui.theme.BarCodeScannerTheme
import space.protagon.consignationscanner.viewmodel.BarCodeScannerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BarCodeScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.safeDrawingPadding()) {
                BarCodeScannerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BarcodeScannerScreen(viewModel)
                    }
                }
            }
        }
    }
}

