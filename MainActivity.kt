package com.example.lect8testdebug

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lect8testdebug.ui.theme.Lect8TestDebugTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Fallback map center used until a real fix comes in: NTNU Trondheim campus.
private const val DEFAULT_LAT = 63.4181
private const val DEFAULT_LNG = 10.4025

fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

enum class AppScreen { Start, Report }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lect8TestDebugTheme {
                var screen by remember { mutableStateOf(AppScreen.Start) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        AppScreen.Start -> StartScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStart = { screen = AppScreen.Report }
                        )
                        AppScreen.Report -> AccidentReporterScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StartScreen(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Accident Reporter", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Report an accident with a photo and its location",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStart) { Text("Start") }
    }
}

/**
 * The report we're trying to submit: a photo and a location, both optional
 * until the user actually provides them.
 */
data class AccidentReport(
    val location: Location?,
    val photoUri: Uri?,
    val description: String = ""
)

/**
 * Pure validation logic — no Compose, no Android framework, no ViewModel.
 * This is what we unit-test directly (AAA pattern, positive/negative/boundary
 * cases) once it's pulled into its own file in the lab.
 */
fun isReportValid(report: AccidentReport): Boolean {
    return report.location != null && report.photoUri != null
}

/**
 * Architecture Considerations demo: production code depends on this
 * interface, not on a concrete location API — so a future test can swap in
 * a fake without touching real GPS/location services.
 */
interface LocationProvider {
    suspend fun getCurrentLocation(): Location?
}

private suspend fun FusedLocationProviderClient.awaitCurrentLocation(priority: Int): Location? =
    suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        getCurrentLocation(priority, cts.token)
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

class RealLocationProvider(
    private val fusedClient: FusedLocationProviderClient
) : LocationProvider {
    override suspend fun getCurrentLocation(): Location? {
        return try {
            fusedClient.awaitCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)
        } catch (e: SecurityException) {
            Log.e(TAG, "location permission missing", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "failed to get location", e)
            null
        }
    }

    companion object {
        private const val TAG = "RealLocationProvider"
    }
}

class AccidentViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }
    private val locationProvider: LocationProvider by lazy { RealLocationProvider(fusedLocationClient) }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri: StateFlow<Uri?> = _photoUri.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _statusMessage = MutableStateFlow("No report submitted yet")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val context = getApplication<Application>()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder().build()
            imageCapture = capture
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                )
            } catch (e: Exception) {
                Log.e(TAG, "camera bind failed", e)
                _statusMessage.value = "Camera failed to start: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        val context = getApplication<Application>()
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "AccidentPhoto_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _photoUri.value = output.savedUri
                    Log.d(TAG, "photo saved: ${output.savedUri}")
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "photo capture failed", exc)
                    _statusMessage.value = "Photo capture failed: ${exc.message}"
                }
            }
        )
    }

    fun fetchLocation() {
        val context = getApplication<Application>()
        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.w(TAG, "location permission not granted")
            _statusMessage.value = "Location permission not granted"
            return
        }
        viewModelScope.launch {
            val result = locationProvider.getCurrentLocation()
            _location.value = result
            if (result == null) {
                Log.w(TAG, "no location fix available")
                _statusMessage.value = "Could not get a location fix"
            } else {
                Log.d(TAG, "location received: $result")
            }
        }
    }

    /**
     * LIVE DEMO TARGET.
     *
     * Set a breakpoint on the `val latitude = ...` line below and step through:
     * is `_location.value` null? is `_photoUri.value` set? Then tap Submit
     * before fetching a location to reproduce a real NullPointerException and
     * practice reading it straight from Logcat / the stack trace — this
     * function has no null check yet, on purpose.
     */
    fun submitReport() {
        val report = AccidentReport(location = _location.value, photoUri = _photoUri.value)
        Log.d(TAG, "submitReport() called with report=$report")

        val latitude = report.location!!.latitude // <- breakpoint here; crashes if location is null

        _statusMessage.value = "Report submitted at lat=$latitude"
        Log.d(TAG, "report submitted successfully")
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "AccidentViewModel"
    }
}

@Composable
fun AccidentReporterScreen(
    viewModel: AccidentViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val photoUri by viewModel.photoUri.collectAsState()
    val location by viewModel.location.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var hasLocationPermission by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.fetchLocation()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LNG), 12f)
    }

    LaunchedEffect(location) {
        location?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Accident Reporter", style = MaterialTheme.typography.headlineSmall)

        // --- Camera ---
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        viewModel.bindCamera(lifecycleOwner, previewView)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Button(onClick = { viewModel.takePhoto() }) { Text("Take Photo") }
        } else {
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Camera Permission")
            }
        }
        Text("Photo: ${photoUri ?: "not attached"}", style = MaterialTheme.typography.bodySmall)

        // --- Location + Map ---
        Button(onClick = {
            if (hasLocationPermission) viewModel.fetchLocation()
            else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }) {
            Text("Get Current Location")
        }
        Text(
            "Location: ${location?.let { "${it.latitude}, ${it.longitude}" } ?: "not set"}",
            style = MaterialTheme.typography.bodySmall
        )

        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            location?.let {
                Marker(
                    state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "Accident location"
                )
            }
        }
        Text(
            "Tip: on the emulator, set a location under Extended Controls \u2192 Location first.",
            style = MaterialTheme.typography.bodySmall
        )

        // --- Submit ---
        Text("Status: $statusMessage")
        Button(onClick = { viewModel.submitReport() }) { Text("Submit Report") }
    }
}

@ComposePreview(showBackground = true)
@Composable
fun StartScreenPreview() {
    Lect8TestDebugTheme {
        StartScreen(onStart = {})
    }
}
