package com.maca.bridge

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.NetworkInterface
import java.security.*
import java.util.*

/**
 * Manages the generation and storage of a self-signed SSL certificate.
 * Updated to include SAN and EKU extensions required by modern Apple devices.
 */
object CertificateManager {
    private const val ALIAS = "maca_bridge_ssl"
    private const val PASSWORD = "maca_password"

    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    fun getOrCreateKeyStore(context: Context): KeyStore {
        val keyStoreFile = File(context.filesDir, "maca_keystore_v4.p12")
        val keyStore = KeyStore.getInstance("PKCS12")

        if (keyStoreFile.exists()) {
            try {
                keyStore.load(keyStoreFile.inputStream(), PASSWORD.toCharArray())
                return keyStore
            } catch (e: Exception) {
                keyStoreFile.delete()
            }
        }

        keyStore.load(null, null)
        val keyPair = generateKeyPair()
        val cert = generateSelfSignedCertificate(keyPair)

        keyStore.setKeyEntry(ALIAS, keyPair.private, PASSWORD.toCharArray(), arrayOf(cert))
        keyStoreFile.outputStream().use { keyStore.store(it, PASSWORD.toCharArray()) }

        return keyStore
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair): java.security.cert.X509Certificate {
        val dnName = X500Name("CN=MacaBridge")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000)

        val builder = JcaX509v3CertificateBuilder(
            dnName, serial, notBefore, notAfter, dnName, keyPair.public
        )

        // 1. Add Extended Key Usage (Required by Apple for TLS)
        builder.addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))

        // 2. Add Subject Alternative Name (IP Address)
        val ip = getLocalIpAddress()
        if (ip != null) {
            val san = GeneralNames(GeneralName(GeneralName.iPAddress, ip))
            builder.addExtension(Extension.subjectAlternativeName, false, san)
        }

        val contentSigner = JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(contentSigner))
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    val host = addr.hostAddress
                    if (!addr.isLoopbackAddress && host != null && host.contains(".")) {
                        return host
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    fun getKeyPassword() = PASSWORD
}