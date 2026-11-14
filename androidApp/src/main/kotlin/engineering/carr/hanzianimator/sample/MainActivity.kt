package engineering.carr.hanzianimator.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import engineering.carr.hanzianimator.data.androidBundledDataSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleApp(dataSource = remember { androidBundledDataSource(assets) })
        }
    }
}
