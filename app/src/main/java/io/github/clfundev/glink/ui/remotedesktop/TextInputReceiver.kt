package io.github.clfundev.glink.ui.remotedesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private const val SENTINEL = "\u200B"

@Composable
fun TextInputReceiver(onTextInput: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = SENTINEL))
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            if (textFieldValue.text == SENTINEL && it.text.isEmpty()) {
                // 哨兵在无其他内容时被删除，即用户在输入框为空时继续按退格，发送退格键
                onBackspace()
            }
            val newText = SENTINEL + it.text.replace(SENTINEL, "")
            val newSelectionStart =
                it.selection.start.coerceIn(1..newText.length)
            val newSelectionEnd =
                it.selection.end.coerceIn(newSelectionStart..newText.length)
            textFieldValue = it.copy(
                text = newText,
                selection = TextRange(newSelectionStart, newSelectionEnd)
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Send
        ),
        keyboardActions = KeyboardActions(onSend = {
            val text = textFieldValue.text.replace(SENTINEL, "")
            if (text.isEmpty()) {
                // 发送换行符可能无效，应发送回车键
                onEnter()
            } else {
                onTextInput(text)
            }
            textFieldValue = textFieldValue.copy(
                text = SENTINEL,
                selection = TextRange(1)
            )
        }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(percent = 100)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Text input area",
                    color = Color.Gray,
                    modifier = Modifier.visible(textFieldValue.text == SENTINEL)
                )
                innerTextField()
            }
        }
    )
}