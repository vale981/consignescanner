package com.codegalaxy.barcodescanner.view

import BarcodeScannerScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.room.*
import com.codegalaxy.barcodescanner.ui.theme.BarCodeScannerTheme
import com.codegalaxy.barcodescanner.viewmodel.BarCodeScannerViewModel

@Entity
data class Containers(
    @ColumnInfo(name="Producteur") val producer: String?,
    @ColumnInfo(name="Nom du produit") val name: String?,
    @ColumnInfo(name="Consigne") val refund: String,
    @ColumnInfo(name="Volume") val volume: String?,
    @ColumnInfo(name="Classification") val classification: String?,
    @ColumnInfo(name="Remplissage") val filling: String?,
    @ColumnInfo(name="Matériel") val material: String?,
    @PrimaryKey @ColumnInfo(name="Code Barre") val barcode: String,
    @ColumnInfo(name="Date de modification") val modificationDate: String?,
)

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers where `Code Barre` = :barcode LIMIT 1")
    fun fromBarcode(barcode: String): Containers?;
}

@Database(entities = [Containers::class], version = 1)
abstract class ContainerDatabase : RoomDatabase() {
    abstract fun containerDao(): ContainerDao
}


class MainActivity : ComponentActivity() {
    private val viewModel: BarCodeScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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

