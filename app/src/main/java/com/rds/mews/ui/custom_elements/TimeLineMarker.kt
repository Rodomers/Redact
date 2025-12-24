import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.localcore.getFormattedTimeUnix

@Composable
fun TimelineMarker(
    time: Long,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    topOffset: Dp = 30.dp
) {
    Row(modifier = modifier.fillMaxHeight()) {
        Text(
            text = getFormattedTimeUnix(time).split(":").joinToString("\n"),
            modifier = Modifier
                .width(30.dp)
                .padding(top = 16.dp, end = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            val lineColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.3f)
            val dotColor = MaterialTheme.colorScheme.onSecondaryContainer

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotRadius = 5.dp.toPx()
                val lineWidth = 2.dp.toPx()
                val lineColor = lineColor
                val dotColor = dotColor

                val yOffsetPx = topOffset.toPx()
                val centerX = size.width / 2

                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, yOffsetPx),
                        strokeWidth = lineWidth
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, yOffsetPx),
                        end = Offset(centerX, size.height),
                        strokeWidth = lineWidth
                    )
                }

                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(centerX, yOffsetPx)
                )
            }
        }
    }
}