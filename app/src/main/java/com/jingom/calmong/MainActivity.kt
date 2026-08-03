package com.jingom.calmong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jingom.calmong.core.designsystem.theme.CalMongTheme
import com.jingom.calmong.core.ui.picker.BasicSnapWheel
import com.jingom.calmong.core.ui.picker.rememberWheelPickerState
import kotlinx.coroutines.launch

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
                    val coroutineScope = rememberCoroutineScope()
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
                                    Modifier
                                        .selectable(
                                            selected = it == state.selectedOption,
                                            role = Role.Button,
                                            onClick = {
                                                coroutineScope.launch {
                                                    state.animateScrollToOption(it)
                                                }
                                            },
                                        ).semantics {
                                            val swipeLabel = "\"위로 스와이프\" 또는 \"아래로 스와이프\" 동작으로 조정"
                                            val selectedOptionLabel = "${items[it]}, 휠 옵션 변경창"
                                            contentDescription =
                                                if (it == state.selectedOption) {
                                                    "$selectedOptionLabel, $swipeLabel"
                                                } else {
                                                    selectedOptionLabel
                                                }
                                        }.clearAndSetSemantics {},
                            )
                        },
                    )
                }
            }
        }
    }
}
