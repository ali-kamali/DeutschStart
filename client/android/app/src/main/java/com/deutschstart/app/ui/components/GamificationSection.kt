package com.deutschstart.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deutschstart.app.data.local.UserProgressEntity
import com.deutschstart.app.data.model.Badge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Composable
fun GamificationSection(
    userProgress: UserProgressEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Streak Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated Flame Logic
                val isStreakActive = userProgress.currentStreak > 0
                val streakScale by animateFloatAsState(
                    targetValue = if (isStreakActive) 1f + (userProgress.currentStreak * 0.05f).coerceAtMost(0.5f) else 1f,
                    animationSpec = tween(1000)
                )
                
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = if (isStreakActive) Color(0xFFFF5722) else Color.Gray,
                    modifier = Modifier
                        .size(32.dp)
                        .scale(streakScale)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "${userProgress.currentStreak} Day Streak!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (userProgress.streakFreezes > 0) {
                        Text(
                            text = "${userProgress.streakFreezes} Freezes Available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Spacer(Modifier.weight(1f))
                
                Text(
                     text = "Longest: ${userProgress.longestStreak}",
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // XP Progress Bar
            val xpProgress = (userProgress.dailyXp.toFloat() / userProgress.dailyGoal.toFloat()).coerceIn(0f, 1f)
            
            Row(
                 modifier = Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.SpaceBetween
            ) {
                 Text(
                     text = "${userProgress.dailyXp}/${userProgress.dailyGoal} XP Today",
                     style = MaterialTheme.typography.labelMedium,
                     fontWeight = FontWeight.SemiBold
                 )
                 Text(
                     text = "${(xpProgress * 100).toInt()}%",
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.primary
                 )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Badges Shelf
            val earnedIds = try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson<List<String>>(userProgress.badges, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
            
            val earnedBadges = earnedIds.mapNotNull { Badge.fromId(it) }
            
            if (earnedBadges.isNotEmpty()) {
                Text(
                     text = "Badges",
                     style = MaterialTheme.typography.labelLarge,
                     modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(earnedBadges) { badge ->
                        BadgeItem(badge)
                    }
                }
            } else {
                 Text(
                     text = "No badges yet. Keep learning!",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                 )
            }
        }
    }
}

@Composable
fun BadgeItem(badge: Badge) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badge.icon,
                contentDescription = badge.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
