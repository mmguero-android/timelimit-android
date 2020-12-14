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
    // https://letsencrypt.org/certs/lets-encrypt-r3.pem
    private const val LETS_ENCRYPT_R3 = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\n" +
            "WhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n" +
            "RW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\n" +
            "R5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\n" +
            "sxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\n" +
            "NHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\n" +
            "Z3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\n" +
            "/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\n" +
            "AYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\n" +
            "Af8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\n" +
            "FHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\n" +
            "AoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\n" +
            "Oi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\n" +
            "gt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\n" +
            "PTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\n" +
            "ikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\n" +
            "CkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\n" +
            "lJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\n" +
            "avAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\n" +
            "yJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\n" +
            "yK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\n" +
            "hCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\n" +
            "HlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\n" +
            "MldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\n" +
            "nLRbwHOoq7hHwg==\n" +
            "-----END CERTIFICATE-----\n"

    // https://letsencrypt.org/certs/lets-encrypt-e1.pem
    private const val LETS_ENCRYPT_E1 = "-----BEGIN CERTIFICATE-----\n" +
            "MIICxjCCAk2gAwIBAgIRALO93/inhFu86QOgQTWzSkUwCgYIKoZIzj0EAwMwTzEL\n" +
            "MAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNo\n" +
            "IEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDIwHhcNMjAwOTA0MDAwMDAwWhcN\n" +
            "MjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3MgRW5j\n" +
            "cnlwdDELMAkGA1UEAxMCRTEwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAQkXC2iKv0c\n" +
            "S6Zdl3MnMayyoGli72XoprDwrEuf/xwLcA/TmC9N/A8AmzfwdAVXMpcuBe8qQyWj\n" +
            "+240JxP2T35p0wKZXuskR5LBJJvmsSGPwSSB/GjMH2m6WPUZIvd0xhajggEIMIIB\n" +
            "BDAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMB\n" +
            "MBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFFrz7Sv8NsI3eblSMOpUb89V\n" +
            "yy6sMB8GA1UdIwQYMBaAFHxClq7eS0g7+pL4nozPbYupcjeVMDIGCCsGAQUFBwEB\n" +
            "BCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3gyLmkubGVuY3Iub3JnLzAnBgNVHR8E\n" +
            "IDAeMBygGqAYhhZodHRwOi8veDIuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYG\n" +
            "Z4EMAQIBMA0GCysGAQQBgt8TAQEBMAoGCCqGSM49BAMDA2cAMGQCMHt01VITjWH+\n" +
            "Dbo/AwCd89eYhNlXLr3pD5xcSAQh8suzYHKOl9YST8pE9kLJ03uGqQIwWrGxtO3q\n" +
            "YJkgsTgDyj2gJrjubi1K9sZmHzOa25JK1fUpE8ZwYii6I4zPPS/Lgul/\n" +
            "-----END CERTIFICATE-----\n"

    // https://letsencrypt.org/certs/lets-encrypt-r4.pem
    private const val LETS_ENCRYPT_R4 = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFFjCCAv6gAwIBAgIRAIp5IlCr5SxSbO7Pf8lC3WIwDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\n" +
            "WhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n" +
            "RW5jcnlwdDELMAkGA1UEAxMCUjQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQCzKNx3KdPnkb7ztwoAx/vyVQslImNTNq/pCCDfDa8oPs3Gq1e2naQlGaXS\n" +
            "Mm1Jpgi5xy+hm5PFIEBrhDEgoo4wYCVg79kaiT8faXGy2uo/c0HEkG9m/X2eWNh3\n" +
            "z81ZdUTJoQp7nz8bDjpmb7Z1z4vLr53AcMX/0oIKr13N4uichZSk5gA16H5OOYHH\n" +
            "IYlgd+odlvKLg3tHxG0ywFJ+Ix5FtXHuo+8XwgOpk4nd9Z/buvHa4H6Xh3GBHhqC\n" +
            "VuQ+fBiiCOUWX6j6qOBIUU0YFKAMo+W2yrO1VRJrcsdafzuM+efZ0Y4STTMzAyrx\n" +
            "E+FCPMIuWWAubeAHRzNl39Jnyk2FAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\n" +
            "AYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\n" +
            "Af8CAQAwHQYDVR0OBBYEFDadPuCxQPYnLHy/jZ0xivZUpkYmMB8GA1UdIwQYMBaA\n" +
            "FHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\n" +
            "AoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\n" +
            "Oi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\n" +
            "gt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCJbu5CalWO+H+Az0lmIG14DXmlYHQE\n" +
            "k26umjuCyioWs2icOlZznPTcZvbfq02YPHGTCu3ctggVDULJ+fwOxKekzIqeyLNk\n" +
            "p8dyFwSAr23DYBIVeXDpxHhShvv0MLJzqqDFBTHYe1X5X2Y7oogy+UDJxV2N24/g\n" +
            "Z8lxG4Vr2/VEfUOrw4Tosl5Z+1uzOdvTyBcxD/E5rGgTLczmulctHy3IMTmdTFr0\n" +
            "FnU0/HMQoquWQuODhFqzMqNcsdbjANUBwOEQrKI8Sy6+b84kHP7PtO+S4Ik8R2k7\n" +
            "ZeMlE1JmxBi/PZU860YlwT8/qOYToCHVyDjhv8qutbf2QnUl3SV86th2I1QQE14s\n" +
            "0y7CdAHcHkw3sAEeYGkwCA74MO+VFtnYbf9B2JBOhyyWb5087rGzitu5MTAW41X9\n" +
            "DwTeXEg+a24tAeht+Y1MionHUwa4j7FB/trN3Fnb/r90+4P66ZETVIEcjseUSMHO\n" +
            "w6yqv10/H/dw/8r2EDUincBBX3o9DL3SadqragkKy96HtMiLcqMMGAPm0gti1b6f\n" +
            "bnvOdr0mrIVIKX5nzOeGZORaYLoSD4C8qvFT7U+Um6DMo36cVDNsPmkF575/s3C2\n" +
            "CxGiCPQqVxPgfNSh+2CPd2Xv04lNeuw6gG89DlOhHuoFKRlmPnom+gwqhz3ZXMfz\n" +
            "TfmvjrBokzCICA==\n" +
            "-----END CERTIFICATE-----\n"

    // https://letsencrypt.org/certs/lets-encrypt-e2.pem
    private const val LETS_ENCRYPT_E2 = "-----BEGIN CERTIFICATE-----\n" +
            "MIICxjCCAkygAwIBAgIQTtI99q9+x/mwxHJv+VEqdzAKBggqhkjOPQQDAzBPMQsw\n" +
            "CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg\n" +
            "R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw0y\n" +
            "NTA5MTUxNjAwMDBaMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNy\n" +
            "eXB0MQswCQYDVQQDEwJFMjB2MBAGByqGSM49AgEGBSuBBAAiA2IABCOaLO3lixmN\n" +
            "YVWex+ZVYOiTLgi0SgNWtU4hufk50VU4Zp/LbBVDxCsnsI7vuf4xp4Cu+ETNggGE\n" +
            "yBqJ3j8iUwe5Yt/qfSrRf1/D5R58duaJ+IvLRXeASRqEL+VkDXrW3qOCAQgwggEE\n" +
            "MA4GA1UdDwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEw\n" +
            "EgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUbZkq9U0C6+MRwWC6km+NPS7x\n" +
            "6kQwHwYDVR0jBBgwFoAUfEKWrt5LSDv6kviejM9ti6lyN5UwMgYIKwYBBQUHAQEE\n" +
            "JjAkMCIGCCsGAQUFBzAChhZodHRwOi8veDIuaS5sZW5jci5vcmcvMCcGA1UdHwQg\n" +
            "MB4wHKAaoBiGFmh0dHA6Ly94Mi5jLmxlbmNyLm9yZy8wIgYDVR0gBBswGTAIBgZn\n" +
            "gQwBAgEwDQYLKwYBBAGC3xMBAQEwCgYIKoZIzj0EAwMDaAAwZQIxAPJCN9qpyDmZ\n" +
            "tX8K3m8UYQvK51BrXclM6WfrdeZlUBKyhTXUmFAtJw4X6A0x9mQFPAIwJa/No+KQ\n" +
            "UAM1u34E36neL/Zba7ombkIOchSgx1iVxzqtFWGddgoG+tppRPWhuhhn\n" +
            "-----END CERTIFICATE-----\n"

    val certificatePinner: CertificatePinner = CertificatePinner.Builder()
            .add(
                    BuildConfig.serverDomain,
                    // legacy items
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r3.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e1.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/J2/oqMTsdhFWW/n85tys6b4yDBtb6idZayIEBx7QTxA=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r4.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/5VReIRNHJBiRxVSgOTTN6bdJZkpZ0m1hX+WPd5kPLQM=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e2.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/vZNucrIS7293MQLGt304+UKXMi78JTlrwyeUIuDIknA="
            )
            // This is theoretically not required because the fallback happens at
            // the DNS query level => original domain is assumed for certificate verification.
            // However, in case this is changed in the future, then there is already
            // a host pinning for the other domain.
            .add(
                    BuildConfig.backupServerDomain,
                    // legacy items
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r3.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e1.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/J2/oqMTsdhFWW/n85tys6b4yDBtb6idZayIEBx7QTxA=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r4.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/5VReIRNHJBiRxVSgOTTN6bdJZkpZ0m1hX+WPd5kPLQM=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e2.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/vZNucrIS7293MQLGt304+UKXMi78JTlrwyeUIuDIknA="
            )
            .add(
                    BuildConfig.updateServerDomain,
                    // legacy items
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r3.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e1.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/J2/oqMTsdhFWW/n85tys6b4yDBtb6idZayIEBx7QTxA=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-r4.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/5VReIRNHJBiRxVSgOTTN6bdJZkpZ0m1hX+WPd5kPLQM=",
                    // echo -n "sha256/"; curl -s https://letsencrypt.org/certs/lets-encrypt-e2.pem | openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    "sha256/vZNucrIS7293MQLGt304+UKXMi78JTlrwyeUIuDIknA="
            )
            .build()

    val certificates: HandshakeCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(LETS_ENCRYPT_R3.decodeCertificatePem())
            .addTrustedCertificate(LETS_ENCRYPT_E1.decodeCertificatePem())
            .addTrustedCertificate(LETS_ENCRYPT_R4.decodeCertificatePem())
            .addTrustedCertificate(LETS_ENCRYPT_E2.decodeCertificatePem())
            .addPlatformTrustedCertificates()
            .build()
}
