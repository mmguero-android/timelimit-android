package io.timelimit.pinning

import io.timelimit.android.BuildConfig
import okhttp3.CertificatePinner

object CertificatePinning {
    val configuration = CertificatePinner.Builder()
            .add(
                    BuildConfig.serverDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            .build()
}
