package space.protagon.consignationscanner


sealed interface BarScanState {
    data object Ideal : BarScanState
    data class ScanSuccess(
        val rawValue: String? = null,
        val format: String? = null
    ) : BarScanState
    data class Error(val error: String) : BarScanState
    data object Loading : BarScanState
}