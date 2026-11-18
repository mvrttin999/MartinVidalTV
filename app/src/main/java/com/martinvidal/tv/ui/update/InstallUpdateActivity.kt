package com.martinvidal.tv.ui.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.data.repo.updateApkFile
import dagger.hilt.android.AndroidEntryPoint

/**
 * Abre el instalador del sistema para el APK ya verificado.
 *
 * Android no permite instalar apps de forma completamente silenciosa en un
 * equipo no rooteado: como muy tarde el sistema pide confirmación. Lo que sí
 * se automatiza es la descarga, la verificación y el salto al instalador.
 */
@AndroidEntryPoint
class InstallUpdateActivity : BaseActivity() {

    companion object {
        const val EXTRA_VERSION = "version"

        fun intent(context: Context, version: String): Intent =
            Intent(context, InstallUpdateActivity::class.java)
                .putExtra(EXTRA_VERSION, version)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = updateApkFile()
        if (!file.exists() || file.length() < 1000L) {
            Toast.makeText(this, R.string.update_not_ready, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_allow_unknown, Toast.LENGTH_LONG).show()
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            runCatching { startActivity(settings) }
            finish()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        @SuppressLint("StartActivityAndCollapseDeprecated")
        runCatching { startActivity(install) }
            .onFailure { Toast.makeText(this, R.string.update_install_error, Toast.LENGTH_LONG).show() }
        finish()
    }
}
