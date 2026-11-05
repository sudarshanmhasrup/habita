package dev.habita.app.feature.expense.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.habita.app.feature.expense.data.UpiStatus
import dev.habita.app.feature.expense.data.upiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val smsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

private val requestedPermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) smsPermissions + Manifest.permission.POST_NOTIFICATIONS else smsPermissions

private fun Context.hasSmsPermission(): Boolean =
    smsPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

@Composable
internal actual fun rememberSmsAccess(): SmsAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasSmsPermission()) }
    var canOpen by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasSmsPermission()
    }
    // Both can change in the system settings while the app is away.
    LifecycleResumeEffect(Unit) {
        granted = context.hasSmsPermission()
        canOpen = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }
    return remember(granted, canOpen, launcher) {
        object : SmsAccess {
            override val supported = true
            override val granted = granted
            override val canOpenOnPayment = canOpen

            override fun request() = launcher.launch(requestedPermissions)

            override fun requestOpenOnPayment() {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            override suspend fun inbox(since: Long): List<InboxSms> = withContext(Dispatchers.IO) {
                if (!context.hasSmsPermission()) return@withContext emptyList()
                val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
                val messages = mutableListOf<InboxSms>()
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    columns,
                    "${Telephony.Sms.DATE} >= ?",
                    arrayOf(since.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val body = cursor.getString(1) ?: continue
                        messages += InboxSms(cursor.getString(0), body, cursor.getLong(2))
                    }
                }
                messages
            }
        }
    }
}

@Composable
internal actual fun rememberUpi(): UpiAccess {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(UpiStatus?) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        // Apps put "Status=SUCCESS&txnId=…" under "response"; some leave it out entirely.
        val data = result.data
        val response = data?.getStringExtra("response") ?: data?.getStringExtra("Status")?.let { "Status=$it" }
        callback(upiStatus(response))
    }
    return remember(launcher) {
        object : UpiAccess {
            override val supported = true

            override fun scan(onResult: (String?) -> Unit) {
                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { onResult(it.rawValue) }
                    .addOnCanceledListener { onResult(null) }
                    .addOnFailureListener { onResult(null) }
            }

            override fun pay(uri: String, onResult: (UpiStatus?) -> Unit) {
                pending[0] = onResult
                try {
                    launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                } catch (_: ActivityNotFoundException) {
                    pending[0] = null
                    onResult(null)
                }
            }
        }
    }
}

@Composable
internal actual fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<String>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val contents = pending[0]
        pending[0] = null
        if (uri == null || contents == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(contents.toByteArray()) }
        }.isSuccess
        onResult(saved)
    }
    return remember(launcher) {
        { fileName, contents ->
            pending[0] = contents
            launcher.launch(fileName)
        }
    }
}