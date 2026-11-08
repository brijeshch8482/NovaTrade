package com.novatrade.demo.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Withdraw mark: an arrow pointing up and right inside a ring, drawn to the same 2px stroke as
 * [DepositCircle] so the two buttons match.
 */
val WithdrawCircle: ImageVector = ImageVector.Builder("WithdrawCircle", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11.999f, y1 = 3f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.8f, 15.2f)
        lineTo(15f, 9f)
        moveTo(10.4f, 9f)
        lineTo(15f, 9f)
        lineTo(15f, 13.6f)
    }
}.build()

/** Deposit mark: a long arrow pointing down inside a ring, the pair of [WithdrawCircle]. */
val DepositCircle: ImageVector = ImageVector.Builder("DepositCircle", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11.999f, y1 = 3f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 7.2f)
        lineTo(12f, 16.2f)
        moveTo(8.6f, 12.8f)
        lineTo(12f, 16.2f)
        lineTo(15.4f, 12.8f)
    }
}.build()
