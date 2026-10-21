package com.buy7.mapper

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AndroidUtils
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import java.util.concurrent.Executors

/** Llaves y conexión de depuración inalámbrica (como hace GG Mouse 3). */
class AdbKeys private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val privateKeyValue: PrivateKey
    private val certValue: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(ctx.filesDir, "private.key")
        val certFile = File(ctx.filesDir, "cert.pem")
        var pk: PrivateKey? = null
        var ce: Certificate? = null
        if (keyFile.exists() && certFile.exists()) {
            try {
                pk = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                ce = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            } catch (e: Exception) {
                pk = null
                ce = null
            }
        }
        if (pk == null || ce == null) {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            val pair = kpg.generateKeyPair()
            val publicKey = pair.public
            pk = pair.private

            val algorithm = "SHA512withRSA"
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 86400000)
            val extensions = CertificateExtensions()
            extensions.set(
                "SubjectKeyIdentifier",
                SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
            )
            val name = X500Name("CN=BUY7 MAPPER")
            extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(publicKey))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", extensions)
            val impl = X509CertImpl(info)
            impl.sign(pk, algorithm)
            ce = impl

            keyFile.writeBytes(pk!!.encoded)
            val b64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(ce.encoded)
            certFile.writeText("-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----\n")
        }
        privateKeyValue = pk!!
        certValue = ce!!
    }

    override fun getPrivateKey(): PrivateKey = privateKeyValue
    override fun getCertificate(): Certificate = certValue
    override fun getDeviceName(): String = "BUY7 MAPPER"

    companion object {
        @Volatile
        private var inst: AdbKeys? = null

        fun get(ctx: Context): AdbKeys =
            inst ?: synchronized(this) {
                inst ?: AdbKeys(ctx.applicationContext).also { inst = it }
            }
    }
}

object Backend {
    private val exec = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    /** Solo intenta hablar con un servidor que ya esté corriendo. */
    fun checkQuiet(ctx: Context, done: () -> Unit) {
        val app = ctx.applicationContext
        exec.execute {
            InputClient.connect(Prefs.token(app))
            ui.post { done() }
        }
    }

    /** Conecta por depuración inalámbrica y arranca el servidor de toques. */
    fun activate(ctx: Context, onStatus: (String) -> Unit, onDone: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        exec.execute {
            fun say(s: String) { ui.post { onStatus(s) } }
            var ok = false
            try {
                val token = Prefs.token(app)
                if (InputClient.connect(token)) {
                    ok = true
                } else {
                    say("Conectando con la depuración inalámbrica…")
                    val mgr = AdbKeys.get(app)
                    var connected = mgr.isConnected
                    if (!connected) {
                        try {
                            connected = mgr.autoConnect(app, 8000)
                        } catch (e: AdbPairingRequiredException) {
                            say("Falta emparejar. Pulsa «1. Emparejar».")
                        } catch (e: Exception) {
                            say("No encontré la depuración inalámbrica. ¿Está activada y tienes Wi‑Fi?")
                        }
                    }
                    if (connected) {
                        say("Arrancando el servidor de toques…")
                        val apk = app.applicationInfo.sourceDir
                        val cmd = "CLASSPATH=$apk setsid nohup app_process / " +
                            "com.buy7.mapper.server.InputServer ${Mapper.PORT} $token " +
                            ">/data/local/tmp/buy7.log 2>&1 &"
                        val st = mgr.openStream("shell:$cmd")
                        Thread.sleep(600)
                        try { st.close() } catch (_: Exception) {}
                        var tries = 0
                        while (!ok && tries < 24) {
                            Thread.sleep(250)
                            ok = InputClient.connect(token)
                            tries++
                        }
                        if (!ok) say("El servidor no arrancó. Intenta de nuevo.")
                    }
                }
            } catch (e: Throwable) {
                say("Error: ${e.message}")
            }
            if (ok) say("Modo avanzado: ✔ conectado")
            ui.post { onDone(ok) }
        }
    }

    fun pair(ctx: Context, port: Int, code: String, onResult: (Boolean, String?) -> Unit) {
        val app = ctx.applicationContext
        exec.execute {
            try {
                val ok = AdbKeys.get(app).pair(AndroidUtils.getHostIpAddress(app), port, code)
                ui.post { onResult(ok, null) }
            } catch (e: Throwable) {
                ui.post { onResult(false, e.message) }
            }
        }
    }

    fun stopServer() {
        InputClient.send("QUIT")
    }
}
