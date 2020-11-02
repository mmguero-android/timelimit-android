/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.flavor

import io.timelimit.android.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem

object SslConfig {
    // https://letsencrypt.org/certs/letsencryptauthorityx3.pem
    private const val LETS_ENCRYPT_X3 = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFjTCCA3WgAwIBAgIRANOxciY0IzLc9AUoUSrsnGowDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTYxMDA2MTU0MzU1\n" +
            "WhcNMjExMDA2MTU0MzU1WjBKMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n" +
            "RW5jcnlwdDEjMCEGA1UEAxMaTGV0J3MgRW5jcnlwdCBBdXRob3JpdHkgWDMwggEi\n" +
            "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCc0wzwWuUuR7dyXTeDs2hjMOrX\n" +
            "NSYZJeG9vjXxcJIvt7hLQQWrqZ41CFjssSrEaIcLo+N15Obzp2JxunmBYB/XkZqf\n" +
            "89B4Z3HIaQ6Vkc/+5pnpYDxIzH7KTXcSJJ1HG1rrueweNwAcnKx7pwXqzkrrvUHl\n" +
            "Npi5y/1tPJZo3yMqQpAMhnRnyH+lmrhSYRQTP2XpgofL2/oOVvaGifOFP5eGr7Dc\n" +
            "Gu9rDZUWfcQroGWymQQ2dYBrrErzG5BJeC+ilk8qICUpBMZ0wNAxzY8xOJUWuqgz\n" +
            "uEPxsR/DMH+ieTETPS02+OP88jNquTkxxa/EjQ0dZBYzqvqEKbbUC8DYfcOTAgMB\n" +
            "AAGjggFnMIIBYzAOBgNVHQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADBU\n" +
            "BgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEBATAwMC4GCCsGAQUFBwIB\n" +
            "FiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQub3JnMB0GA1UdDgQWBBSo\n" +
            "SmpjBH3duubRObemRWXv86jsoTAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8vY3Js\n" +
            "LnJvb3QteDEubGV0c2VuY3J5cHQub3JnMHIGCCsGAQUFBwEBBGYwZDAwBggrBgEF\n" +
            "BQcwAYYkaHR0cDovL29jc3Aucm9vdC14MS5sZXRzZW5jcnlwdC5vcmcvMDAGCCsG\n" +
            "AQUFBzAChiRodHRwOi8vY2VydC5yb290LXgxLmxldHNlbmNyeXB0Lm9yZy8wHwYD\n" +
            "VR0jBBgwFoAUebRZ5nu25eQBc4AIiMgaWPbpm24wDQYJKoZIhvcNAQELBQADggIB\n" +
            "ABnPdSA0LTqmRf/Q1eaM2jLonG4bQdEnqOJQ8nCqxOeTRrToEKtwT++36gTSlBGx\n" +
            "A/5dut82jJQ2jxN8RI8L9QFXrWi4xXnA2EqA10yjHiR6H9cj6MFiOnb5In1eWsRM\n" +
            "UM2v3e9tNsCAgBukPHAg1lQh07rvFKm/Bz9BCjaxorALINUfZ9DD64j2igLIxle2\n" +
            "DPxW8dI/F2loHMjXZjqG8RkqZUdoxtID5+90FgsGIfkMpqgRS05f4zPbCEHqCXl1\n" +
            "eO5HyELTgcVlLXXQDgAWnRzut1hFJeczY1tjQQno6f6s+nMydLN26WuU4s3UYvOu\n" +
            "OsUxRlJu7TSRHqDC3lSE5XggVkzdaPkuKGQbGpny+01/47hfXXNB7HntWNZ6N2Vw\n" +
            "p7G6OfY+YQrZwIaQmhrIqJZuigsrbe3W+gdn5ykE9+Ky0VgVUsfxo52mwFYs1JKY\n" +
            "2PGDuWx8M6DlS6qQkvHaRUo0FMd8TsSlbF0/v965qGFKhSDeQoMpYnwcmQilRh/0\n" +
            "ayLThlHLN81gSkJjVrPI0Y8xCVPB4twb1PFUd2fPM3sA1tJ83sZ5v8vgFv2yofKR\n" +
            "PB0t6JzUA81mSqM3kxl5e+IZwhYAyO0OTg3/fs8HqGTNKd9BqoUwSRBzp06JMg5b\n" +
            "rUCGwbCUDI0mxadJ3Bz4WxR6fyNpBK2yAinWEsikxqEt\n" +
            "-----END CERTIFICATE-----\n"

    // https://letsencrypt.org/certs/letsencryptauthorityx4.pem
    private const val LETS_ENCRYPT_X4 = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFjTCCA3WgAwIBAgIRAJObmZ6kjhYNW0JZtD0gE9owDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTYxMDA2MTU0NDM0\n" +
            "WhcNMjExMDA2MTU0NDM0WjBKMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n" +
            "RW5jcnlwdDEjMCEGA1UEAxMaTGV0J3MgRW5jcnlwdCBBdXRob3JpdHkgWDQwggEi\n" +
            "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDhJHRCe7eRMdlz/ziq2M5EXLc5\n" +
            "CtxErg29RbmXN2evvVBPX9MQVGv3QdqOY+ZtW8DoQKmMQfzRA4n/YmEJYNYHBXia\n" +
            "kL0aZD5P3M93L4lry2evQU3FjQDAa/6NhNy18pUxqOj2kKBDSpN0XLM+Q2lLiSJH\n" +
            "dFE+mWTDzSQB+YQvKHcXIqfdw2wITGYvN3TFb5OOsEY3FmHRUJjIsA9PWFN8rPba\n" +
            "LZZhUK1D3AqmT561Urmcju9O30azMdwg/GnCoyB1Puw4GzZOZmbS3/VmpJMve6YO\n" +
            "lD5gPUpLHG+6tE0cPJFYbi9NxNpw2+0BOXbASefpNbUUBpDB5ZLiEP1rubSFAgMB\n" +
            "AAGjggFnMIIBYzAOBgNVHQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADBU\n" +
            "BgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEBATAwMC4GCCsGAQUFBwIB\n" +
            "FiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQub3JnMB0GA1UdDgQWBBTF\n" +
            "satOTLHNZDCTfsGEmQWr5gPiJTAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8vY3Js\n" +
            "LnJvb3QteDEubGV0c2VuY3J5cHQub3JnMHIGCCsGAQUFBwEBBGYwZDAwBggrBgEF\n" +
            "BQcwAYYkaHR0cDovL29jc3Aucm9vdC14MS5sZXRzZW5jcnlwdC5vcmcvMDAGCCsG\n" +
            "AQUFBzAChiRodHRwOi8vY2VydC5yb290LXgxLmxldHNlbmNyeXB0Lm9yZy8wHwYD\n" +
            "VR0jBBgwFoAUebRZ5nu25eQBc4AIiMgaWPbpm24wDQYJKoZIhvcNAQELBQADggIB\n" +
            "AF4tI1yGjZgld9lP01+zftU3aSV0un0d2GKUMO7GxvwTLWAKQz/eT+u3J4+GvpD+\n" +
            "BMfopIxkJcDCzMChjjZtZZwJpIY7BatVrO6OkEmaRNITtbZ/hCwNkUnbk3C7EG3O\n" +
            "GJZlo9b2wzA8v9WBsPzHpTvLfOr+dS57LLPZBhp3ArHaLbdk33lIONRPt9sseDEk\n" +
            "mdHnVmGmBRf4+J0Wy67mddOvz5rHH8uzY94raOayf20gzzcmqmot4hPXtDG4Y49M\n" +
            "oFMMT2kcWck3EOTAH6QiGWkGJ7cxMfSL3S0niA6wgFJtfETETOZu8AVDgENgCJ3D\n" +
            "S0bz/dhVKvs3WRkaKuuR/W0nnC2VDdaFj4+CRF8LGtn/8ERaH48TktH5BDyDVcF9\n" +
            "zfJ75Scxcy23jAL2N6w3n/t3nnqoXt9Im4FprDr+mP1g2Z6Lf2YA0jE3kZalgZ6l\n" +
            "NHu4CmvJYoOTSJw9X2qlGl1K+B4U327rG1tRxgjM76pN6lIS02PMECoyKJigpOSB\n" +
            "u4V8+LVaUMezCJH9Qf4EKeZTHddQ1t96zvNd2s9ewSKx/DblXbKsBDzIdHJ+qi6+\n" +
            "F9DIVM5/ICdtDdulOO+dr/BXB+pBZ3uVxjRANvJKKpdxkePyluITSNZHbanWRN07\n" +
            "gMvwBWOL060i4VrL9er1sBQrRjU9iNpZQGTnLVAxQVFu\n" +
            "-----END CERTIFICATE-----\n"

    val certificatePinner: CertificatePinner = CertificatePinner.Builder()
            .add(
                    BuildConfig.serverDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            // This is theoretically not required because the fallback happens at
            // the DNS query level => original domain is assumed for certificate verification.
            // However, in case this is changed in the future, then there is already
            // a host pinning for the other domain.
            .add(
                    BuildConfig.backupServerDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            .add(
                    BuildConfig.updateServerDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            .build()

    val certificates: HandshakeCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(LETS_ENCRYPT_X3.decodeCertificatePem())
            .addTrustedCertificate(LETS_ENCRYPT_X4.decodeCertificatePem())
            .addPlatformTrustedCertificates()
            .build()
}
