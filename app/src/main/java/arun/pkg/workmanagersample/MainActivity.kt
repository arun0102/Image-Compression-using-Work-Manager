package arun.pkg.workmanagersample

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.pkg.workmanagersample.ui.theme.WorkManageSampleTheme
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PhotoCompressViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)
        setContent {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WorkManageSampleTheme {
                    val state = viewModel.state.collectAsStateWithLifecycle()
                    when (state.value) {
                        is CompressionStatus.Idle -> Text(text = "Idle")
                        is CompressionStatus.Finished -> {
                            (state.value as CompressionStatus.Finished).apply {
                                ShowImages(
                                    uncompressedUri,
                                    compressedUri,
                                    false
                                )
                            }
                        }

                        is CompressionStatus.Compressing -> {
                            (state.value as CompressionStatus.Compressing).apply {
                                ShowImages(
                                    uncompressedUri,
                                    null,
                                    true
                                )
                            }
                        }

                        is CompressionStatus.Error -> Text(
                            text = "Failed : ${state.value}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ShowImages(uncompressedUri: Uri?, compressedUri: Uri?, isCompressing: Boolean) {
        uncompressedUri?.let { uri ->
            Text(text = "Uncompressed Photo")
            AsyncImage(model = uri, contentDescription = null)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (!isCompressing) {
            compressedUri?.let { uri ->
                Text(text = "Compressed Photo")
                AsyncImage(model = uri, contentDescription = null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: return
            viewModel.compressImage(uri)
        }
    }
}