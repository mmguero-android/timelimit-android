package io.timelimit.pinning

import okhttp3.CertificatePinner

object CertificatePinning {
    val configuration = CertificatePinner.Builder()
            .build()
}
