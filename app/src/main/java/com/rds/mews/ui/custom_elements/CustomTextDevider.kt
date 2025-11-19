package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R

@Composable
fun CustomTextDivider(text: String? = null, dateString: String? = null, date: Boolean = false) {
    val text = when (date) {
        true -> {
            val formattedDate = dateString?.split(".") ?: "00".split(".")

            when (formattedDate.last().toInt()) {
                1 -> stringResource(R.string.date_01, formattedDate.first().toInt())
                2 -> stringResource(R.string.date_02, formattedDate.first().toInt())
                3 -> stringResource(R.string.date_03, formattedDate.first().toInt())
                4 -> stringResource(R.string.date_04, formattedDate.first().toInt())
                5 -> stringResource(R.string.date_05, formattedDate.first().toInt())
                6 -> stringResource(R.string.date_06, formattedDate.first().toInt())
                7 -> stringResource(R.string.date_07, formattedDate.first().toInt())
                8 -> stringResource(R.string.date_08, formattedDate.first().toInt())
                9 -> stringResource(R.string.date_09, formattedDate.first().toInt())
                10 -> stringResource(R.string.date_10, formattedDate.first().toInt())
                11 -> stringResource(R.string.date_11, formattedDate.first().toInt())
                12 -> stringResource(R.string.date_12, formattedDate.first().toInt())
                else -> stringResource(R.string.wrong_date)
            }
        }
        else -> text ?: "null"
    }

    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 4.dp, end = 50.dp))
}