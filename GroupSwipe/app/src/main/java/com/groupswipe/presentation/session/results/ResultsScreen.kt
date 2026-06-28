package com.groupswipe.presentation.session.results

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.ProposalResult
import com.groupswipe.presentation.session.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.resultsState.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Czy animacja koła fortuny już się zakończyła (wynik można odsłonić).
    var tieRevealed by remember(state.tiebreakWinnerId) { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.loadResults(sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wyniki głosowania 🏆") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Obliczamy wyniki...")
                    }
                }
            }

            state.session?.status?.name == "VOTING" || state.session?.status?.name == "WAITING" -> {
                // Sesja jeszcze trwa
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Czekamy na zakończenie głosowania...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        state.session?.let { session ->
                            Text(
                                text = "${session.finishedVotingUids.size}/${session.participantIds.size} zagłosowało",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            state.results.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Remis -> koło fortuny rozstrzyga, kto wygrywa
                    if (state.isTie) {
                        item {
                            TiebreakWheel(
                                candidates = state.tiedProposals,
                                winnerId = state.tiebreakWinnerId,
                                revealed = tieRevealed,
                                onRevealed = { tieRevealed = true },
                                onSpin = { viewModel.spinTiebreak(sessionId) }
                            )
                        }
                    }

                    // Zwycięzca - wyróżniony baner. Przy remisie pokazujemy go DOPIERO
                    // po zakończeniu animacji koła fortuny.
                    if (!state.isTie || tieRevealed) {
                        state.winner?.let { winner ->
                            item { WinnerBanner(result = winner, onOpenLink = {
                                if (winner.proposal.detailUrl.isNotBlank()) {
                                    uriHandler.openUri(winner.proposal.detailUrl)
                                }
                            }) }
                        }
                    }

                    item {
                        Text(
                            text = "Wszystkie propozycje",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    itemsIndexed(state.results) { index, result ->
                        ResultItem(
                            position = index + 1,
                            result = result,
                            highlightWinner = !state.isTie || tieRevealed,
                            onOpenLink = {
                                if (result.proposal.detailUrl.isNotBlank()) {
                                    uriHandler.openUri(result.proposal.detailUrl)
                                }
                            }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            state.errorMessage != null -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.errorMessage ?: "Błąd")
                }
            }
        }
    }
}

@Composable
private fun WinnerBanner(result: ProposalResult, onOpenLink: () -> Unit) {
    val shimmerAnimation = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerAnimation.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD700).copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                AsyncImage(
                    model = result.proposal.imageUrl,
                    contentDescription = result.proposal.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                // Badge zwycięzcy
                Surface(
                    color = Color(0xFFFFD700),
                    shape = RoundedCornerShape(bottomEnd = 16.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "🏆 ZWYCIĘZCA",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = result.proposal.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${result.yesVotes} głosów TAK (${result.percentage}%)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                Text(
                    text = result.proposal.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (result.proposal.detailUrl.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenLink,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Zobacz szczegóły / Zarezerwuj")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultItem(
    position: Int,
    result: ProposalResult,
    highlightWinner: Boolean = true,
    onOpenLink: () -> Unit
) {
    val positionColor = when (position) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pozycja
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = positionColor.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$position",
                        fontWeight = FontWeight.ExtraBold,
                        color = positionColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Miniatura
            AsyncImage(
                model = result.proposal.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.proposal.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Pasek głosów
                LinearProgressIndicator(
                    progress = { result.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(6.dp),
                    color = if (result.isWinner && highlightWinner) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "${result.yesVotes} TAK · ${result.totalVotes - result.yesVotes} NIE · ${result.percentage}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.proposal.detailUrl.isNotBlank()) {
                IconButton(onClick = onOpenLink) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = "Otwórz link",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private val WHEEL_COLORS = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFCA28),
    Color(0xFFAB47BC), Color(0xFF26C6DA), Color(0xFFFF7043), Color(0xFF8D6E63)
)

/**
 * Koło fortuny rozstrzygające remis.
 *
 * Zwycięzca (winnerId) jest jeden dla całej sesji (zapisany w Firestore przez transakcję),
 * więc u każdego gracza koło zatrzyma się na tym samym polu.
 */
@Composable
private fun TiebreakWheel(
    candidates: List<Proposal>,
    winnerId: String?,
    revealed: Boolean,
    onRevealed: () -> Unit,
    onSpin: () -> Unit
) {
    if (candidates.isEmpty()) return
    val n = candidates.size
    val sweep = 360f / n
    val rotation = remember { Animatable(0f) }
    var requested by remember { mutableStateOf(false) }

    val winnerIndex = winnerId?.let { id -> candidates.indexOfFirst { it.id == id } } ?: -1

    LaunchedEffect(winnerId) {
        if (winnerId != null) {
            if (winnerIndex >= 0) {
                // Środek pola zwycięzcy ma trafić pod wskaźnik na górze (270°).
                val center = winnerIndex * sweep + sweep / 2f
                val target = 360f * 6 + (270f - center)
                rotation.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 3800, easing = FastOutSlowInEasing)
                )
            }
            onRevealed() // dopiero teraz odsłaniamy zwycięzcę
        }
    }

    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🎡 Remis! Koło fortuny zadecyduje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Box(contentAlignment = Alignment.TopCenter) {
                Canvas(modifier = Modifier.size(240.dp).rotate(rotation.value)) {
                    for (i in 0 until n) {
                        drawArc(
                            color = WHEEL_COLORS[i % WHEEL_COLORS.size],
                            startAngle = i * sweep,
                            sweepAngle = sweep,
                            useCenter = true
                        )
                    }
                }
                // Wskaźnik na górze koła (nie obraca się razem z kołem)
                Text(
                    "🔻",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.offset(y = (-10).dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Legenda: kolor pola -> nazwa propozycji
            candidates.forEachIndexed { i, p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(WHEEL_COLORS[i % WHEEL_COLORS.size])
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = p.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (revealed && p.id == winnerId) FontWeight.ExtraBold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                // Jeszcze nie kręcono – przycisk losowania
                winnerId == null -> {
                    Button(
                        onClick = { requested = true; onSpin() },
                        enabled = !requested,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (requested) "Losowanie..." else "Zakręć kołem fortuny 🎡")
                    }
                }
                // Wylosowano, ale koło się jeszcze kręci
                !revealed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Koło się kręci...", style = MaterialTheme.typography.titleMedium)
                    }
                }
                // Animacja zakończona – odsłaniamy zwycięzcę
                else -> {
                    val winnerTitle = candidates.firstOrNull { it.id == winnerId }?.title ?: ""
                    Text(
                        text = "🎉 Wygrywa: $winnerTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
