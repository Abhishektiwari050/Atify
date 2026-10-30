package io.github.sekademi.spotufi.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.sekademi.spotufi.ui.components.MiniPlayer

@Composable
fun MainBottomNavigation(
    navController: NavHostController,
    bottomBarState: MutableState<Boolean>,
    bottomBarPlayerState: MutableState<Boolean>
) {
    val navItems = listOf(
        Routes.Home,
        Routes.Search,
        Routes.Library
    )

    AnimatedVisibility(
        visible = bottomBarState.value,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(140)),
        content = {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.92f),
                                Color.Black
                            ),
                            startY = 0f
                        )
                    )
            ) {
                Column {
                    AnimatedVisibility(
                        visible = bottomBarPlayerState.value,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            )
                        ) + fadeIn(tween(180)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(140)),
                        content = {
                            MiniPlayer(navController)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NavigationBar(
                        modifier = Modifier
                            .padding(30.dp, 0.dp)
                            .fillMaxWidth(),
                        containerColor = Color.Transparent,
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                    ) {
                        val navStack by navController.currentBackStackEntryAsState()
                        val currentRoute = navStack?.destination?.route
                        val haptic = LocalHapticFeedback.current

                        navItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.08f else 0.94f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "navIconScale"
                            )
                            NavigationBarItem(
                                selected = isSelected,
                                icon = {
                                    Icon(
                                        painter = painterResource(id = item.icon),
                                        contentDescription = item.label,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                    )
                                },
                                label = {
                                    Text(
                                        color = if (isSelected) Color.White else Color(0xFFB3B3B3),
                                        text = item.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    unselectedIconColor = Color(0xFFB3B3B3),
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}
