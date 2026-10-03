/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.dolby.R
import kotlin.math.abs

/** Everything one flashcard needs; built once, so pager pages don't re-resolve resources. */
@Immutable
private class ProfileCard(
    val value: Int,
    val name: String,
    val description: String,
    @DrawableRes val image: Int,
)

/** Profile ids (see dolby_profile_values) -> artwork. Unknown ids fall back to the Custom one. */
@DrawableRes
private fun imageFor(value: Int): Int =
    when (value) {
        0 -> R.drawable.img_profile_dynamic
        1 -> R.drawable.img_profile_video
        2 -> R.drawable.img_profile_music
        8 -> R.drawable.img_profile_voice
        else -> R.drawable.img_profile_custom
    }

/**
 * Horizontally swipeable profile flashcards. Swiping only browses; tapping a card (or its button)
 * applies the profile, so flicking past "Voice" doesn't briefly switch the audio chain.
 */
@Composable
internal fun ProfileCarousel(
    profile: Int,
    enabled: Boolean,
    onProfileChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descriptions = stringArrayResource(R.array.dolby_profile_descriptions).toList()
    val values = rememberChoices(R.array.dolby_profile_entries, R.array.dolby_profile_values)
    val cards =
        remember(values, descriptions) {
            List(values.size) { i ->
                ProfileCard(
                    value = values.values[i],
                    name = values.labels[i],
                    description = descriptions.getOrElse(i) { "" },
                    image = imageFor(values.values[i]),
                )
            }
        }

    val selectedIndex = cards.indexOfFirst { it.value == profile }
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceAtLeast(0)) { cards.size }

    // Follow changes made elsewhere (QS tile, tapping a peeking neighbour card).
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex != pagerState.settledPage) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
            userScrollEnabled = enabled,
            key = { cards[it].value },
        ) { page ->
            val card = cards[page]
            ProfileFlashcard(
                card = card,
                selected = card.value == profile,
                enabled = enabled,
                onSelect = { onProfileChange(card.value) },
                // Neighbours shrink/fade slightly for a "deck" feel.
                modifier =
                    Modifier.graphicsLayer {
                        val offset =
                            abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, 1f)
                        val s = 1f - 0.08f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.4f * offset
                    },
            )
        }
        PageDots(pagerState, Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ProfileFlashcard(
    card: ProfileCard,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container by
        animateColorAsState(
            if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
            label = "cardContainer",
        )
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface

    Card(
        onClick = onSelect,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = container,
                contentColor = content,
                // Dimming is done once, on the whole page.
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(card.image),
            contentDescription = null, // decorative; the name below says it all
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2.2f),
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                card.description,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f)
                    else colors.onSurfaceVariant,
                minLines = 3,
                maxLines = 3,
            )
            Row(Modifier.padding(top = 10.dp)) {
                if (selected) {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors =
                            ButtonDefaults.buttonColors(
                                disabledContainerColor = colors.primary,
                                disabledContentColor = colors.onPrimary,
                            ),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.dolby_selected),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    FilledTonalButton(onClick = onSelect, enabled = enabled) {
                        Text(stringResource(R.string.dolby_select))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(state: PagerState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.pageCount) { i ->
            val active = i == state.currentPage
            val color by
                animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    label = "dot",
                )
            Box(Modifier.size(if (active) 10.dp else 8.dp).clip(CircleShape).background(color))
        }
    }
}
