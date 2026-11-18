package com.martinvidal.tv.ui.update

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.martinvidal.tv.BuildConfig
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.data.repo.UpdateScheduler
import com.martinvidal.tv.data.repo.updateApkFile
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.databinding.ActivityUpdateBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de las actualizaciones y comprobación manual. */
@AndroidEntryPoint
class UpdateActivity : BaseActivity() {

    private lateinit var binding: ActivityUpdateBinding

    @Inject
    lateinit var scheduler: UpdateScheduler

    @Inject
    lateinit var session: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.currentVersion.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.back.setOnClickListener { finish() }
        binding.check.setOnClickListener {
            binding.status.text = getString(R.string.update_checking)
            binding.check.isEnabled = false
            scheduler.checkNow()
        }
        binding.install.setOnClickListener {
            startActivity(InstallUpdateActivity.intent(this, ""))
        }

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(UpdateScheduler.NOW)
            .observe(this, Observer { infos ->
                val info = infos.firstOrNull() ?: return@Observer
                if (info.state == WorkInfo.State.SUCCEEDED || info.state == WorkInfo.State.FAILED) {
                    val message = info.outputData.getString(UpdateWorker.KEY_MESSAGE)
                    if (!message.isNullOrBlank()) {
                        binding.status.text = message
                    } else {
                        binding.status.text = if (info.state == WorkInfo.State.SUCCEEDED) {
                            getString(R.string.update_done)
                        } else {
                            getString(R.string.update_failed)
                        }
                    }
                    binding.check.isEnabled = true
                    lifecycleScope.launch { refreshAvailability() }
                }
            })

        binding.check.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val state = session.snapshot()
            refreshAvailability()
            binding.status.text = if (isReady(state.updateVersion)) {
                getString(R.string.update_ready_version, state.updateVersion)
            } else {
                getString(R.string.update_none)
            }
        }
    }

    /** Actualiza fuente y botón sin borrar un mensaje de resultado/error. */
    private suspend fun refreshAvailability() {
        val state = session.snapshot()
        binding.source.text = state.updateUrl.ifBlank { BuildConfig.UPDATE_URL }.ifBlank {
            getString(R.string.update_no_source)
        }
        binding.install.isEnabled = isReady(state.updateVersion)
    }

    private fun isReady(version: String): Boolean =
        version.isNotBlank() && updateApkFile().exists()
}
