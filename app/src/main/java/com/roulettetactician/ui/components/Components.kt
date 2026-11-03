package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.ui.theme.Yellow500

@Composable
fun Chip(
    value: Int,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    showValue: Boolean = true,
    label: String? = null
) {
    val color = when (value) {
        1 -> Color(0xFFE2E8F0) // Slate 200
        5 -> Color(0xFFDC2626) // Red 600
        25 -> Color(0xFF16A34A) // Green 600
        100 -> Color(0xFF0F172A) // Slate 900
        500 -> Color(0xFF7E22CE) // Purple 700
        1000 -> Color(0xFFEAB308) // Yellow 500
        else -> Color(0xFF0284C7) // Cyan / Blue default for custom amounts
    }
    
    val textColor = if (value == 1 || value == 1000) Color.Black else Color.White
    val borderColor = if (value == 1000) Color.Black else Yellow500

    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .background(color, CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (showValue) {
            val textToDisplay = label ?: (if (value >= 1000) "${value/1000}k" else value.toString())
            Text(
                text = textToDisplay,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (textToDisplay.length > 3) (size.value * 0.32).sp else (size.value * 0.4).sp,
                modifier = Modifier.offset(y = (-1).dp)
            )
        }
    }
}

@Composable
fun SpinHistoryItem(
    number: Int,
    modifier: Modifier = Modifier
) {
    val bg = when {
        number == 0 || number == 37 -> Color(0xFF15803D) // Green
        setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36).contains(number) -> Color(0xFFDC2626) // Red
        else -> Color(0xFF0F172A) // Black
    }
    
    Box(
        modifier = modifier
            .size(36.dp)
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (number == 37) "00" else number.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
