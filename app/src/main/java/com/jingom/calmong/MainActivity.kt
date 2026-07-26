package com.jingom.calmong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jingom.calmong.core.designsystem.theme.CalMongTheme
import com.jingom.calmong.core.ui.picker.BasicSnapWheel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalMongTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                ) {
                    val items = remember { List(100) { it.toString() } }
                    var centerItemIndex by remember { mutableIntStateOf(0) }
                    Text(
                        text = "center item : ${items[centerItemIndex.coerceIn(0, items.lastIndex)]}",
                    )
                    BasicSnapWheel(
                        items = items,
                        visibleItemCount = 5,
                        onCenterItemIndexChange = { centerItemIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
