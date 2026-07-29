package com.jingom.calmong

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jingom.calmong.core.designsystem.theme.CalMongTheme
import com.jingom.calmong.core.ui.picker.BasicSnapWheel
import com.jingom.calmong.core.ui.picker.rememberWheelPickerState

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
                            .background(color = CalMongTheme.colors.neutral.background.default)
                            .systemBarsPadding(),
                ) {
                    val items = remember { List(31) { it.toString() } }
                    val state = rememberWheelPickerState(items.size, 0)

                    Text(
                        text = "center item : ${items[state.selectedOption]}",
                        color = CalMongTheme.colors.neutral.foreground.default,
                    )
                    val current = LocalContext.current
                    BasicSnapWheel(
                        state = state,
                        gradientColor = CalMongTheme.colors.neutral.background.default,
                        visibleItemCount = 7,
                        modifier = Modifier.fillMaxWidth(),
                        optionContent = {
                            Text(
                                text = items[it],
                                color = CalMongTheme.colors.neutral.foreground.default,
                                modifier =
                                    Modifier.clickable {
                                        Toast.makeText(current, items[it], Toast.LENGTH_SHORT).show()
                                    },
                            )
                        },
                    )
                }
            }
        }
    }
}
