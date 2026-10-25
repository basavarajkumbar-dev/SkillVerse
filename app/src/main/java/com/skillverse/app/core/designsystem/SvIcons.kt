package com.skillverse.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original line/solid icons for concepts the core Material icon set does not cover. Tinted by Icon(). */
object SvIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(block)
            .build()

    private val solid = SolidColor(Color.Black)

    private fun ImageVector.Builder.stroke(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(stroke = solid, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    val Flame: ImageVector by lazy {
        icon("Flame") {
            path(fill = solid) {
                moveTo(12f, 2f)
                curveTo(13f, 6f, 18f, 8f, 18f, 14f)
                curveTo(18f, 18.4f, 15.3f, 22f, 12f, 22f)
                curveTo(8.7f, 22f, 6f, 18.4f, 6f, 14.5f)
                curveTo(6f, 11.5f, 7.6f, 9.6f, 9f, 8.5f)
                curveTo(9f, 11f, 10f, 12.5f, 11.5f, 13f)
                curveTo(11f, 9f, 11f, 5f, 12f, 2f)
                close()
            }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            path(fill = solid) {
                moveTo(13.5f, 2f); lineTo(4.5f, 13.5f); lineTo(11f, 13.5f)
                lineTo(10f, 22f); lineTo(19.5f, 10f); lineTo(13f, 10f); close()
            }
        }
    }

    val Chart: ImageVector by lazy {
        icon("Chart") {
            path(fill = solid) {
                moveTo(4f, 13f); lineTo(8f, 13f); lineTo(8f, 20f); lineTo(4f, 20f); close()
                moveTo(10f, 8f); lineTo(14f, 8f); lineTo(14f, 20f); lineTo(10f, 20f); close()
                moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(16f, 20f); close()
            }
        }
    }

    val Tree: ImageVector by lazy {
        icon("SkillTree") {
            stroke {
                moveTo(12f, 6.5f); lineTo(12f, 11f)
                moveTo(12f, 11f); lineTo(6f, 15.5f)
                moveTo(12f, 11f); lineTo(18f, 15.5f)
            }
            path(fill = solid) {
                moveTo(12f, 1.5f); lineTo(15.5f, 4.5f); lineTo(12f, 7.5f); lineTo(8.5f, 4.5f); close()
                moveTo(6f, 14f); lineTo(9f, 17f); lineTo(6f, 20f); lineTo(3f, 17f); close()
                moveTo(18f, 14f); lineTo(21f, 17f); lineTo(18f, 20f); lineTo(15f, 17f); close()
            }
        }
    }

    val Trophy: ImageVector by lazy {
        icon("Trophy") {
            path(fill = solid) {
                moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 9f)
                curveTo(17f, 12f, 15f, 14f, 12f, 14f)
                curveTo(9f, 14f, 7f, 12f, 7f, 9f); close()
                moveTo(11f, 14f); lineTo(13f, 14f); lineTo(13f, 18f); lineTo(11f, 18f); close()
                moveTo(7.5f, 18f); lineTo(16.5f, 18f); lineTo(16.5f, 21f); lineTo(7.5f, 21f); close()
            }
            stroke {
                moveTo(7f, 5f); lineTo(4f, 5f); lineTo(4f, 7f); curveTo(4f, 9f, 5.5f, 10.5f, 7.2f, 10.5f)
                moveTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 7f); curveTo(20f, 9f, 18.5f, 10.5f, 16.8f, 10.5f)
            }
        }
    }

    val Code: ImageVector by lazy {
        icon("Code") {
            stroke {
                moveTo(8f, 7f); lineTo(3f, 12f); lineTo(8f, 17f)
                moveTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 17f)
                moveTo(13.5f, 5f); lineTo(10.5f, 19f)
            }
        }
    }

    val Bug: ImageVector by lazy {
        icon("Bug") {
            path(fill = solid, pathFillType = PathFillType.NonZero) {
                moveTo(12f, 7f)
                curveTo(15.3f, 7f, 17f, 9.5f, 17f, 13f)
                curveTo(17f, 17f, 15f, 20f, 12f, 20f)
                curveTo(9f, 20f, 7f, 17f, 7f, 13f)
                curveTo(7f, 9.5f, 8.7f, 7f, 12f, 7f); close()
            }
            stroke {
                moveTo(9f, 6f); lineTo(7.5f, 3.5f)
                moveTo(15f, 6f); lineTo(16.5f, 3.5f)
                moveTo(7f, 12f); lineTo(3.5f, 11f)
                moveTo(17f, 12f); lineTo(20.5f, 11f)
                moveTo(7.3f, 16f); lineTo(4f, 18f)
                moveTo(16.7f, 16f); lineTo(20f, 18f)
            }
        }
    }

    val Timer: ImageVector by lazy {
        icon("Timer") {
            stroke {
                moveTo(12f, 21f)
                curveTo(16.4f, 21f, 20f, 17.4f, 20f, 13f)
                curveTo(20f, 8.6f, 16.4f, 5f, 12f, 5f)
                curveTo(7.6f, 5f, 4f, 8.6f, 4f, 13f)
                curveTo(4f, 17.4f, 7.6f, 21f, 12f, 21f); close()
                moveTo(12f, 13f); lineTo(12f, 9f)
                moveTo(10f, 2.5f); lineTo(14f, 2.5f)
            }
        }
    }

    val Lightbulb: ImageVector by lazy {
        icon("Hint") {
            path(fill = solid) {
                moveTo(12f, 2f)
                curveTo(15.9f, 2f, 19f, 5.1f, 19f, 9f)
                curveTo(19f, 11.6f, 17.6f, 13.8f, 15.5f, 15f)
                lineTo(15.5f, 17f); lineTo(8.5f, 17f); lineTo(8.5f, 15f)
                curveTo(6.4f, 13.8f, 5f, 11.6f, 5f, 9f)
                curveTo(5f, 5.1f, 8.1f, 2f, 12f, 2f); close()
                moveTo(9f, 19f); lineTo(15f, 19f); lineTo(15f, 21f)
                curveTo(15f, 21.6f, 14.6f, 22f, 14f, 22f)
                lineTo(10f, 22f); curveTo(9.4f, 22f, 9f, 21.6f, 9f, 21f); close()
            }
        }
    }

    val Swords: ImageVector by lazy {
        icon("Boss") {
            stroke {
                moveTo(4f, 4f); lineTo(14f, 14f)
                moveTo(20f, 4f); lineTo(10f, 14f)
                moveTo(11f, 17f); lineTo(7f, 13f)
                moveTo(13f, 17f); lineTo(17f, 13f)
                moveTo(8.5f, 15.5f); lineTo(4.5f, 19.5f)
                moveTo(15.5f, 15.5f); lineTo(19.5f, 19.5f)
            }
        }
    }

    val Target: ImageVector by lazy {
        icon("Target") {
            stroke {
                moveTo(12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f); close()
            }
            path(fill = solid) {
                moveTo(12f, 8f)
                curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
                curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
                curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
                curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f); close()
            }
        }
    }

    val Review: ImageVector by lazy {
        icon("Review") {
            stroke {
                moveTo(20f, 12f)
                curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
                curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
                curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
                curveTo(14.6f, 4f, 16.9f, 5.2f, 18.3f, 7.2f)
                moveTo(19f, 3f); lineTo(19f, 7.5f); lineTo(14.5f, 7.5f)
            }
        }
    }

    val Project: ImageVector by lazy {
        icon("Project") {
            stroke {
                moveTo(4f, 7f); lineTo(10f, 7f); lineTo(12f, 9f); lineTo(20f, 9f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            }
        }
    }
}
