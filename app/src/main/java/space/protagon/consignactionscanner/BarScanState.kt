package space.protagon.consignactionscanner

import space.protagon.consignactionscanner.viewmodel.Containers


sealed interface BarScanState {
    data object Ideal : BarScanState
    data class ScanSuccess(
        val container: Containers
    ) : BarScanState
    data object NoRefund : BarScanState
    data class Error(val error: String) : BarScanState
    data object Loading : BarScanState
}