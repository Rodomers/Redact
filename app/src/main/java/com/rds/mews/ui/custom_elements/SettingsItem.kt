package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.ui.theme.Shapes

@Composable
fun SettingsItem(text: String, modifier: Modifier = Modifier, item: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = Shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Left,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            item()
        }
    }
}