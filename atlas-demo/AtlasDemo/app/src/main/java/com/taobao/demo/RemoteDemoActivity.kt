package com.taobao.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.util.*

/**
 * @ClassName RemoteActivity
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-17 16:03
 * @Version 1.0
 */

private const val PACKAGE_NAME = "com.taobao.demo"


private const val REMOTE_CLASSNAME = "com.taobao.remotebunle.RemoteBundleActivity"


private const val CONFIRMATION_REQUEST_CODE = 1

private const val TAG = "DynamicFeatures"

class RemoteActivity : RemoteBaseActivity() {
    /** Listener used to handle changes in state for install requests. */
    private val listener = SplitInstallStateUpdatedListener { state ->
        val multiInstall = state.moduleNames().size > 1

        val names =  state.moduleNames().joinToString(" - ")

        when (state.status()) {
            SplitInstallSessionStatus.DOWNLOADING -> {
                //  In order to see this, the application has to be uploaded to the Play Store.
                displayLoadingState(state, getString(R.string.downloading, names))
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                /*
                  This may occur when attempting to download a sufficiently large module.

                  In order to see this, the application has to be uploaded to the Play Store.
                  Then features can be requested until the confirmation path is triggered.
                 */
                manager.startConfirmationDialogForResult(state, this, CONFIRMATION_REQUEST_CODE)
            }
            SplitInstallSessionStatus.INSTALLED -> {
                if (langsInstall){
                    onSuccessfulLanguageLoad(names)
                } else {
                    onSuccessfulLoad(names, launch = !multiInstall)
                }
            }

            SplitInstallSessionStatus.INSTALLING -> displayLoadingState(
                    state,
                    getString(R.string.installing, names)
            )
            SplitInstallSessionStatus.FAILED -> {
                toastAndLog(getString(R.string.error_for_module, state.errorCode(),
                        state.moduleNames()))
            }
        }
    }

    /** This is needed to handle the result of the manager.startConfirmationDialogForResult
    request that can be made from SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION
    in the listener above. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CONFIRMATION_REQUEST_CODE) {
            // Handle the user's decision. For example, if the user selects "Cancel",
            // you may want to disable certain functionality that depends on the module.
            if (resultCode == Activity.RESULT_CANCELED) {
                toastAndLog(getString(R.string.user_cancelled))
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val moduleRemote by lazy { getString(R.string.remote_bundle) }


    private val clickListener by lazy {
        View.OnClickListener {
            when (it.id) {
                R.id.load_remote_bundle -> loadAndLaunchModule(moduleRemote)
//                R.id.btn_install_all_now -> installAllFeaturesNow()
//                R.id.btn_install_all_deferred -> installAllFeaturesDeferred()
//                R.id.btn_request_uninstall -> requestUninstall()
            }
        }
    }

    private lateinit var manager: SplitInstallManager

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.app_name)
        setContentView(R.layout.guild_remote_bundle)
        manager = SplitInstallManagerFactory.create(this)
        initializeViews()
    }

    override fun onResume() {
        // Listener can be registered even without directly triggering a download.
        manager.registerListener(listener)
        super.onResume()
    }

    override fun onPause() {
        // Make sure to dispose of the listener once it's no longer needed.
        manager.unregisterListener(listener)
        super.onPause()
    }

    /**
     * Load a feature by module name.
     * @param name The name of the feature module to load.
     */
    private fun loadAndLaunchModule(name: String) {
        updateProgressMessage(getString(R.string.loading_module, name))
        // Skip loading if the module already is installed. Perform success action directly.
        if (manager.installedModules.contains(name)) {
            updateProgressMessage(getString(R.string.already_installed))
            onSuccessfulLoad(name, launch = true)
            return
        }

        // Create request to install a feature module by name.
        val request = SplitInstallRequest.newBuilder()
                .addModule(name)
                .build()

        // Load and install the requested feature module.
        manager.startInstall(request)

        updateProgressMessage(getString(R.string.starting_install_for, name))
    }


    /** Install all features but do not launch any of them. */
    private fun installAllFeaturesNow() {
        // Request all known modules to be downloaded in a single session.
        val moduleNames = listOf(moduleRemote)
        val requestBuilder = SplitInstallRequest.newBuilder()

        moduleNames.forEach { name ->
            if (!manager.installedModules.contains(name)) {
                requestBuilder.addModule(name)
            }
        }

        val request = requestBuilder.build()

        manager.startInstall(request).addOnSuccessListener {
            toastAndLog("Loading ${request.moduleNames}")
        }.addOnFailureListener {
            toastAndLog("Failed loading ${request.moduleNames}")
        }
    }

    /** Install all features deferred. */
    private fun installAllFeaturesDeferred() {
        val modules = listOf(moduleRemote)

        manager.deferredInstall(modules).addOnSuccessListener {
            toastAndLog("Deferred installation of $modules")
        }

    }

    /** Request uninstall of all features. */
    private fun requestUninstall() {

        toastAndLog("Requesting uninstall of all modules." +
                "This will happen at some point in the future.")

        val installedModules = manager.installedModules.toList()
        manager.deferredUninstall(installedModules).addOnSuccessListener {
            toastAndLog("Uninstalling $installedModules")
        }.addOnFailureListener {
            toastAndLog("Failed installation of $installedModules")
        }
    }

    /**
     * Define what to do once a feature module is loaded successfully.
     * @param moduleName The name of the successfully loaded module.
     * @param launch `true` if the feature module should be launched, else `false`.
     */
    private fun onSuccessfulLoad(moduleName: String, launch: Boolean) {
        if (launch) {
            when (moduleName) {
                moduleRemote -> launchActivity(REMOTE_CLASSNAME)
            }
        }

        displayButtons()
    }

    private fun onSuccessfulLanguageLoad(lang: String) {
        LanguageHelper.language = lang
        recreate()
    }

    /** Launch an activity by its class name. */
    private fun launchActivity(className: String) {
        Intent().setClassName(this, className)
                .also {
                    startActivity(it)
                }
    }

    /** Display a loading state to the user. */
    private fun displayLoadingState(state: SplitInstallSessionState, message: String) {
        displayProgress()

        progressBar.max = state.totalBytesToDownload().toInt()
        progressBar.progress = state.bytesDownloaded().toInt()

        updateProgressMessage(message)
    }

    /** Set up all view variables. */
    private fun initializeViews() {
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        setupClickListener()
    }

    /** Set all click listeners required for the buttons on the UI. */
    private fun setupClickListener() {
        setClickListener(R.id.load_remote_bundle, clickListener)


    }

    private fun setClickListener(id: Int, listener: View.OnClickListener) {
        findViewById<View>(id).setOnClickListener(listener)
    }

    private fun updateProgressMessage(message: String) {
        if (progress.visibility != View.VISIBLE) displayProgress()
        progressText.text = message
    }

    /** Display progress bar and text. */
    private fun displayProgress() {
        progress.visibility = View.VISIBLE
        buttons.visibility = View.GONE
    }

    /** Display buttons to accept user input. */
    private fun displayButtons() {
        progress.visibility = View.GONE
        buttons.visibility = View.VISIBLE
    }

}

fun RemoteActivity.toastAndLog(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    Log.d(TAG, text)
}


