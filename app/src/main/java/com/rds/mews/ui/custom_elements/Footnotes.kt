package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomBottomFootnote(text: String, modifier: Modifier = Modifier) {
    Text(text = text, fontWeight = FontWeight.Normal, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = modifier.padding(start = 20.dp, end = 20.dp))
}