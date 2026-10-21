package nz.mckenzie.trailday.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import nz.mckenzie.trailday.photo.WaypointPhotos

/**
 * The optional photograph on a spot.
 *
 * Two ways in, because a camera is not always the right one: the system camera for the spot in
 * front of the walker, and the phone's pictures for a photograph already taken. Both come back as
 * the same compressed bytes, so the place's page and its editor do not care which was used.
 */
@Composable
internal fun WaypointPhotoField(
    photo: ByteArray?,
    onPhoto: (ByteArray?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var cameraTarget by remember { mutableStateOf<WaypointPhotos.CameraTarget?>(null) }

    fun read(uri: android.net.Uri, cleanup: File? = null) {
        scope.launch {
            busy = true
            problem = null
            val bytes = runCatching { WaypointPhotos.fromUri(context, uri) }.getOrNull()
            cleanup?.delete()
            if (bytes == null) problem = "That picture could not be read"
            else onPhoto(bytes)
            busy = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { taken ->
        val target = cameraTarget
        cameraTarget = null
        if (taken && target != null) {
            read(target.uri, target.file)
        } else {
            target?.file?.delete()
        }
    }

    val choosePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(::read) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        photo?.let { bytes ->
            val image = remember(bytes) { WaypointPhotos.bitmap(bytes)?.asImageBitmap() }
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Spot photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    problem = null
                    val target = runCatching { WaypointPhotos.newCameraTarget(context) }
                        .getOrNull()
                    if (target == null) {
                        problem = "The camera could not be opened"
                    } else {
                        cameraTarget = target
                        runCatching { takePicture.launch(target.uri) }
                            .onFailure {
                                target.file.delete()
                                cameraTarget = null
                                problem = it.message ?: "The camera could not be opened"
                            }
                    }
                }
            ) {
                Text(if (photo == null) "Take photo" else "Replace photo")
            }
            TextButton(
                enabled = !busy,
                onClick = {
                    problem = null
                    choosePicture.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("Choose")
            }
            if (photo != null) {
                TextButton(enabled = !busy, onClick = { onPhoto(null) }) { Text("Remove") }
            }
        }

        if (busy) {
            Text("Preparing photo…", style = MaterialTheme.typography.bodySmall)
        }
        problem?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
