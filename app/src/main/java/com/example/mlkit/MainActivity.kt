package com.example.mlkit

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var resultInfo: TextView
    private lateinit var firstPageView: ImageView
    private lateinit var pageLimitInputView: EditText
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var enableGalleryImport = true
    private val FULL_MODE = "FULL"
    private val BASE_MODE = "BASE"
    private val BASE_MODE_WITH_FILTER = "BASE_WITH_FILTER"
    private var selectedMode = FULL_MODE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        resultInfo = findViewById<TextView>(R.id.result_info)!!
        firstPageView = findViewById<ImageView>(R.id.first_page_view)!!
        pageLimitInputView = findViewById(R.id.page_limit_input)

        scannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                handleActivityResult(result)
            }
        populateModeSelector()

    }

    fun onEnableGalleryImportCheckboxClicked(view: View) {
        enableGalleryImport = (view as CheckBox).isChecked
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScanButtonClicked(unused: View) {
        resultInfo.text = null
        Glide.with(this).clear(firstPageView)

        val options =
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setGalleryImportAllowed(enableGalleryImport)

        when (selectedMode) {
            FULL_MODE -> options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            BASE_MODE -> options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            BASE_MODE_WITH_FILTER ->
                options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)

            else -> Log.e(TAG, "Unknown selectedMode: $selectedMode")
        }

        val pageLimitInputText = pageLimitInputView.text.toString()
        if (pageLimitInputText.isNotEmpty()) {
            try {
                val pageLimit = pageLimitInputText.toInt()
                options.setPageLimit(pageLimit)
            } catch (e: Throwable) {
                resultInfo.text = e.message
                return
            }
        }

        GmsDocumentScanning.getClient(options.build())
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender: IntentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener() { e: Exception ->
                resultInfo.setText(getString(R.string.error_default_message, e.message))
            }
    }

    private fun populateModeSelector() {
        val featureSpinner = findViewById<Spinner>(R.id.mode_selector)
        val options: MutableList<String> = ArrayList()
        options.add(FULL_MODE)
        options.add(BASE_MODE)
        options.add(BASE_MODE_WITH_FILTER)



        // Creating adapter for featureSpinner
        val dataAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Attaching data adapter to spinner
        featureSpinner.adapter = dataAdapter

        featureSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parentView: AdapterView<*>,
                    selectedItemView: View,
                    pos: Int,
                    id: Long
                ) {
                    selectedMode = parentView.getItemAtPosition(pos).toString()
                }

                override fun onNothingSelected(arg0: AdapterView<*>?) {}
            }
    }

    private fun handleActivityResult(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        if (resultCode == Activity.RESULT_OK && result != null) {
            resultInfo.setText(getString(R.string.scan_result, result))

            val pages = result.pages
            if (pages != null && pages.isNotEmpty()) {
                Glide.with(this).load(pages[0].imageUri).into(firstPageView)
            }

            result.pdf?.uri?.path?.let { path ->
                val externalUri =
                    FileProvider.getUriForFile(this, packageName + ".provider", File(path))
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, externalUri)
                        type = "application/pdf"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                startActivity(Intent.createChooser(shareIntent, "share pdf"))
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            resultInfo.text = getString(R.string.error_scanner_cancelled)
        } else {
            resultInfo.text = getString(R.string.error_default_message)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}