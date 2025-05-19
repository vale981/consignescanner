package space.protagon.consignationscanner.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import space.protagon.consignationscanner.BarScanState
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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


class BarCodeScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var _barScanState by mutableStateOf<BarScanState>(BarScanState.Ideal)
    private val context = application.applicationContext
    private val database = Room.databaseBuilder(context, ContainerDatabase::class.java,
        "consigne_${context.packageManager.getPackageInfo(context.packageName, 0).versionName}}.db")
        .allowMainThreadQueries()
        .fallbackToDestructiveMigration(true)
        .createFromAsset("consignation.db")
        .build()

    private val containerDao = database.containerDao()

    val barScanState: BarScanState get() = _barScanState

    fun onBarCodeDetected(barcodes: List<Barcode>) {
        viewModelScope.launch {
            if (barcodes.isEmpty()) {
                _barScanState = BarScanState.Error("No barcode detected")
                return@launch
            }

            _barScanState = BarScanState.Loading

            barcodes.forEach { barcode ->
                barcode.rawValue?.let { barcodeValue ->
                    try {
                        val container = containerDao.fromBarcode(barcodeValue)
                        if (container != null)
                            _barScanState = BarScanState.ScanSuccess(
                              container = container
                            )
                        else
                            _barScanState = BarScanState.Error("Pas de consigne")

                    } catch (e: Exception) {
                        Log.e("BarCodeScanner", "Error processing barcode", e)
                        _barScanState = BarScanState.Error("Erreur: ${e.message}")
                    }
                    return@launch
                }
            }
            _barScanState = BarScanState.Error("No valid barcode value")
        }
    }

    private fun getBarcodeFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_CODE_39 -> "CODE 39"
            Barcode.FORMAT_CODE_93 -> "CODE 93"
            Barcode.FORMAT_CODE_128 -> "CODE 128"
            Barcode.FORMAT_DATA_MATRIX -> "DATA MATRIX"
            Barcode.FORMAT_EAN_8 -> "EAN 8"
            Barcode.FORMAT_EAN_13 -> "EAN 13"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_UPC_A -> "UPC A"
            Barcode.FORMAT_UPC_E -> "UPC E"
            else -> "Unknown"
        }
    }

    fun resetState() {
        _barScanState = BarScanState.Ideal
    }
}