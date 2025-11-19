package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.getFormattedTimeUnix

@Composable
fun CustomBottomFootnote(text: String) {
    Text(text = text, fontWeight = FontWeight.Normal, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp))
}

@Composable
fun CustomTimeMark(time: Long) {
    Text(text = getFormattedTimeUnix(time).split(":").joinToString("\n"), fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Start,
        modifier = Modifier
            .padding(top = 4.dp, end = 10.dp)
            .width(30.dp))
}