package io.github.sekademi.spotufi.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.sekademi.spotufi.R

@Composable
fun PlayerTopBar(
    navController: NavController,
    onMenuClick: () -> Unit,
    contextName: String = "",
    onBackClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Icon(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onBackClick()
            },
            painter = painterResource(id = R.drawable.ic_down),
            tint = Color.White,
            contentDescription = "Collapse"
        )

        // Spotify shows the source context here (album/playlist), not a generic label.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PLAYING FROM",
                color = Color(0xFFB3B3B3),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = contextName.ifBlank { "Now Playing" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                tint = Color.White,
                modifier = Modifier
                    .size(23.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onMenuClick() },
                contentDescription = "Options"
            )
        }
    }
}
