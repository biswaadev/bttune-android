// BTTUNE - About and Contributors Screen
package com.bt.bttune.ui.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.bt.bttune.utils.AppDownloadsManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bt.bttune.BuildConfig
import com.bt.bttune.LocalPlayerAwareWindowInsets
import com.bt.bttune.LocalPlayerConnection
import com.bt.bttune.R
import com.bt.bttune.ui.component.IconButton

// ==================== SHIMMER EFFECT ====================

@Composable
fun shimmerEffect(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmerEffect")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmerEffect"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

// ==================== USER CARD ====================

@Composable
fun SocialIconBadge(
    iconRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
fun UserCard(
    imageUrl: String,
    name: String,
    role: String,
    githubUrl: String? = null,
    telegramUrl: String? = null,
    instagramUrl: String? = null,
    whatsappUrl: String? = null,
    websiteUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        )
    )

    Card(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .height(240.dp)
            .scale(if (isPressed) 0.98f else 1f)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar - Centered at top
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.5.dp, borderBrush, CircleShape)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text - Centered
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = role,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Social Badges Row at the bottom of the card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contextUriHandler = LocalUriHandler.current
                if (githubUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.github,
                        onClick = { contextUriHandler.openUri(githubUrl) }
                    )
                }
                if (whatsappUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.ic_whatsapp,
                        onClick = { contextUriHandler.openUri(whatsappUrl) }
                    )
                }
                if (telegramUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.telegram,
                        onClick = { contextUriHandler.openUri(telegramUrl) }
                    )
                }
                if (instagramUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.instagram,
                        onClick = { contextUriHandler.openUri(instagramUrl) }
                    )
                }
                if (websiteUrl != null) {
                    SocialIconBadge(
                        iconRes = R.drawable.resource_public,
                        onClick = { contextUriHandler.openUri(websiteUrl) }
                    )
                }
            }
        }
    }
}



// ==================== MAIN ABOUT SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadsCount by AppDownloadsManager.downloadsCount.collectAsState()
    val shimmerBrush = shimmerEffect()
    var logoTapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        AppDownloadsManager.fetchLatestDownloadsCount(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )

    // Get player connection for album artwork
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 🎵 BLUR BACKGROUND
        val artworkUrl = mediaMetadata?.thumbnailUrl

        artworkUrl?.let { imageUrl ->
            com.bt.bttune.ui.component.BlurredBackground(
                model = imageUrl
            )

            val isDarkTheme =
                MaterialTheme.colorScheme.background.luminance() < 0.5f

            val overlayBrush = if (isDarkTheme) {
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush)
            )
        }

        // Main Scaffold with new TopAppBar
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                // U-Shaped TopAppBar - NO BACK BUTTON
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.about),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        Spacer(modifier = Modifier.width(48.dp))
                    },
                    actions = {
                        Spacer(modifier = Modifier.width(48.dp))
                    },
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 30.dp,
                                bottomEnd = 30.dp
                            )
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                )
                            )
                        )
                        .border(
                            width = 0.6.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.1f),
                                    Color.White.copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(
                                bottomStart = 30.dp,
                                bottomEnd = 30.dp
                            )
                        ),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            // Content with proper padding from Scaffold
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(innerPadding) // This pushes content below the top bar
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp), // Add small top padding for spacing
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo with shimmer
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(logoScale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(
                                    NavigationBarDefaults.Elevation
                                )
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_bttune_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    logoTapCount++
                                    if (logoTapCount >= 7) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Built with ❤️ by BTTUNE Team",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        logoTapCount = 0
                                    }
                                }
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(shimmerBrush)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // App Name
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(color = MaterialTheme.colorScheme.primary)
                            ) {
                                append("BT")
                            }
                            withStyle(
                                SpanStyle(color = MaterialTheme.colorScheme.secondary)
                            ) {
                                append("TUNE")
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Version and Downloads badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = BuildConfig.VERSION_NAME.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )

                        if (BuildConfig.DEBUG) {
                            Text(
                                text = "DEBUG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Downloads Count Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        val updated = AppDownloadsManager.fetchLatestDownloadsCount(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "$updated Total Downloads",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (downloadsCount == 1L) "1 Download" else "$downloadsCount Downloads",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Dev By Biswajit Tanti & Trisan Tanti",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    

                    // Developers Title - LEFT ALIGNED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.group),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Developers",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Developers Cards (2 Developers: Biswajit Tanti and Trisan Tanti)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UserCard(
                            imageUrl = "https://avatars.githubusercontent.com/u/301598560",
                            name = "Biswajit Tanti",
                            role = "Lead Developer",
                            githubUrl = "https://github.com/biswaadev",
                            whatsappUrl = "https://wa.me/919365015437",
                            websiteUrl = "https://biswadev.vercel.app/",
                            telegramUrl = "https://t.me/freek311",
                            modifier = Modifier.weight(1f),
                            onClick = { uriHandler.openUri("https://biswadev.vercel.app/") }
                        )
                        Spacer(Modifier.width(8.dp))
                        UserCard(
                            imageUrl = "https://avatars.githubusercontent.com/u/301598560",
                            name = "Trisan Tanti",
                            role = "Developer",
                            telegramUrl = "https://t.me/freek311",
                            modifier = Modifier.weight(1f),
                            onClick = { uriHandler.openUri("https://t.me/freek311") }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Contributors Title - LEFT ALIGNED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.favorite),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Contributors",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Contributors Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        UserCard(
                            imageUrl = "https://avatars.githubusercontent.com/u/300082004?v=4",
                            name = "bitzzdev",
                            role = "Contributor",
                            githubUrl = "https://github.com/bitzzdev",
                            instagramUrl = "https://instagram.com/bitz.dev",
                            websiteUrl = "https://bitz.is-a.dev",
                            modifier = Modifier.weight(1f),
                            onClick = { uriHandler.openUri("https://github.com/bitzzdev") }
                        )
                        Spacer(Modifier.width(8.dp))
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}
