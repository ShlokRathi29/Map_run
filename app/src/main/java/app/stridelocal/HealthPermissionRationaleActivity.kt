package app.stridelocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthPermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(24.dp)) {
                    Text("Heart-rate access", style = MaterialTheme.typography.headlineSmall)
                    Text("Stride Local reads heart-rate samples from Health Connect only when you choose to import them for a completed run. Imported samples are stored in the app's private database. They are not uploaded or shared.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
