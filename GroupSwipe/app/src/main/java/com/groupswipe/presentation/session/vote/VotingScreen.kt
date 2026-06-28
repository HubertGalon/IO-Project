package com.groupswipe.presentation.session.vote

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionStatus
import com.groupswipe.presentation.session.SessionViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Próg przesunięcia (jako część szerokości ekranu) po którym karta "odpada"
 */
private const val SWIPE_THRESHOLD_FRACTION = 0.35f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotingScreen(
    sessionId: String,
    onVotingFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.votingState.collectAsState()

    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId) }
    LaunchedEffect(state.isFinished) { if (state.isFinished) onVotingFinished() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.session?.title ?: "Głosowanie")
                        state.session?.let { session ->
                            Text(
                                text = "${session.finishedVotingUids.size}/${session.participantIds.size} gotowych",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Ładowanie propozycji...")
                        }
                    }
                }

                state.session?.status == SessionStatus.WAITING -> {
                    WaitingForHostContent(
                        session = state.session!!,
                        onStartVoting = { viewModel.startVoting(sessionId) }
                    )
                }

                !state.hasMore && state.proposals.isNotEmpty() -> {
                    AllVotedContent()
                }

                state.currentProposal != null -> {
                    VotingContent(
                        current = state.currentProposal!!,
                        next = state.nextProposal,
                        votedCount = state.votedCount,
                        total = state.totalActive,
                        canVeto = state.canVeto,
                        onVote = { isYes -> viewModel.castVote(isYes) },
                        onVeto = { viewModel.vetoCurrent() }
                    )
                }

                state.errorMessage != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Text(state.errorMessage ?: "Nieznany błąd")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingForHostContent(
    session: com.groupswipe.domain.model.Session,
    onStartVoting: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⏳", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Czekamy na uczestników",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        // Kod zaproszenia
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kod zaproszenia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = session.inviteCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "Podziel się kodem ze znajomymi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Uczestnicy (${session.participantIds.size}):",
            style = MaterialTheme.typography.titleSmall
        )
        session.participantNames.values.forEach { name ->
            Text("• $name", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStartVoting,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Rozpocznij głosowanie", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AllVotedContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Zagłosowałeś na wszystkie!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Czekamy aż reszta grupy skończy głosowanie...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun VotingContent(
    current: Proposal,
    next: Proposal?,
    votedCount: Int,
    total: Int,
    canVeto: Boolean,
    onVote: (Boolean) -> Unit,
    onVeto: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val swipeThreshold = screenWidth * SWIPE_THRESHOLD_FRACTION

    Column(modifier = Modifier.fillMaxSize()) {
        // Pasek postępu
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else votedCount.toFloat() / total },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${(votedCount + 1).coerceAtMost(total)} / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Stos kart (pokazujemy aktualną i następną dla efektu głębi)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Następna karta (pod spodem)
            next?.let { nextProposal ->
                ProposalCard(
                    proposal = nextProposal,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.95f
                        scaleY = 0.95f
                    },
                    offsetX = 0f,
                    onVote = {}
                )
            }

            // Aktualna karta (na wierzchu) – obsługa swipe.
            // key(current.id) wymusza świeżą instancję composable (i jego stan
            // offsetu) dla KAŻDEJ propozycji.
            key(current.id) {
                SwipeableProposalCard(
                    proposal = current,
                    swipeThreshold = swipeThreshold.value,
                    onVote = onVote
                )
            }
        }

        // Przycisk VETO – usuwa propozycję z puli dla wszystkich graczy (jedno na osobę)
        TextButton(
            onClick = onVeto,
            enabled = canVeto,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (canVeto) "Veto – usuń dla wszystkich" else "Veto wykorzystane")
        }

        // Przyciski głosowania
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Przycisk NIE
            FloatingActionButton(
                onClick = { onVote(false) },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Nie",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Przycisk TAK
            FloatingActionButton(
                onClick = { onVote(true) },
                containerColor = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Tak",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Karta propozycji z obsługą gestów swipe (drag).
 * Animacja: karta obraca się i przesuwa w zależności od kierunku przeciągania.
 * Po przekroczeniu progu – karta "odpada" i rejestrowany jest głos.
 */
@Composable
private fun SwipeableProposalCard(
    proposal: Proposal,
    swipeThreshold: Float,
    onVote: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Stan przesunięcia jest KLUCZOWANY na proposal.id – każda nowa karta
    // startuje od środka (0,0), zamiast dziedziczyć pozycję poprzedniej.
    val offsetX = remember(proposal.id) { Animatable(0f) }
    val offsetY = remember(proposal.id) { Animatable(0f) }
    // Blokada przed podwójnym głosem (np. drag + ponowny gest w trakcie animacji odlatywania).
    var voteCast by remember(proposal.id) { mutableStateOf(false) }

    // Rotacja oraz nakładki TAK/NIE proporcjonalne do bieżącego przesunięcia.
    val rotation = (offsetX.value / swipeThreshold) * 15f
    val yesAlpha = (offsetX.value / swipeThreshold).coerceIn(0f, 1f)
    val noAlpha = (-offsetX.value / swipeThreshold).coerceIn(0f, 1f)

    ProposalCard(
        proposal = proposal,
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .rotate(rotation)
            .pointerInput(proposal.id) {
                // Dystans, na jaki karta "odlatuje" poza ekran przy zatwierdzeniu głosu.
                val flingDistance = size.width * 1.5f
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (!voteCast) {
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                offsetY.snapTo(offsetY.value + dragAmount.y)
                            }
                        }
                    },
                    onDragEnd = {
                        if (!voteCast) {
                            when {
                                // Przekroczono próg w prawo -> TAK. Karta odlatuje, potem głos.
                                offsetX.value > swipeThreshold -> {
                                    voteCast = true
                                    scope.launch {
                                        offsetX.animateTo(flingDistance, tween(250))
                                        onVote(true)
                                    }
                                }
                                // Przekroczono próg w lewo -> NIE.
                                offsetX.value < -swipeThreshold -> {
                                    voteCast = true
                                    scope.launch {
                                        offsetX.animateTo(-flingDistance, tween(250))
                                        onVote(false)
                                    }
                                }
                                // Za mało – płynny powrót do środka.
                                else -> {
                                    scope.launch {
                                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                    scope.launch {
                                        offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                        }
                    }
                )
            },
        offsetX = offsetX.value,
        yesAlpha = yesAlpha,
        noAlpha = noAlpha,
        onVote = {}
    )
}

/**
 * Wizualna karta propozycji z obrazem, tytułem i oceną.
 */
@Composable
private fun ProposalCard(
    proposal: Proposal,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f,
    yesAlpha: Float = 0f,
    noAlpha: Float = 0f,
    onVote: (Boolean) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Obraz tła
            AsyncImage(
                model = proposal.imageUrl,
                contentDescription = proposal.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Nakładka gradientowa (ciemna, od dołu)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 300f
                        )
                    )
            )

            // Nakładka TAK (zielona, po prawej)
            if (yesAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF4CAF50).copy(alpha = yesAlpha * 0.3f))
                ) {
                    Text(
                        text = "TAK ✓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(24.dp)
                            .graphicsLayer { alpha = yesAlpha }
                    )
                }
            }

            // Nakładka NIE (czerwona, po lewej)
            if (noAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF44336).copy(alpha = noAlpha * 0.3f))
                ) {
                    Text(
                        text = "NIE ✗",
                        color = Color(0xFFF44336),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .graphicsLayer { alpha = noAlpha }
                    )
                }
            }

            // Informacje o propozycji (dół karty)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = proposal.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Ocena gwiazdkowa
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (index < proposal.rating.toInt()) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = " ${String.format("%.1f", proposal.rating)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Dodatkowe informacje (rok, gatunek itp.)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    proposal.extraInfo.entries.take(3).forEach { (key, value) ->
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = value,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = proposal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
