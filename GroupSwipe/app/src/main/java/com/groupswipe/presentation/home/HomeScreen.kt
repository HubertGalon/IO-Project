package com.groupswipe.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.groupswipe.domain.model.Session
import com.groupswipe.domain.model.SessionStatus
import com.groupswipe.presentation.session.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onFriends: () -> Unit,
    onHistory: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val homeState by viewModel.homeState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadCurrentUser() }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinError = null },
            title = { Text("Dołącz do sesji") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wpisz kod zaproszenia od organizatora sesji:")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase(); joinError = null },
                        label = { Text("Kod sesji (8 znaków)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    joinError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (joinCode.length < 6) {
                        joinError = "Kod jest za krótki"
                        return@Button
                    }
                    viewModel.joinByCode(joinCode,
                        onSuccess = { sessionId ->
                            showJoinDialog = false
                            joinCode = ""
                            onOpenSession(sessionId)
                        },
                        onError = { error -> joinError = error }
                    )
                }) { Text("Dołącz") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GroupSwipe 🤝",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        homeState.currentUser?.displayName?.let { name ->
                            Text(
                                text = "Cześć, $name!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Ikona znajomych z badge oczekujących zaproszeń
                    BadgedBox(
                        badge = {
                            if (homeState.pendingRequests.isNotEmpty()) {
                                Badge { Text(homeState.pendingRequests.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = onFriends) {
                            Icon(Icons.Default.People, contentDescription = "Znajomi")
                        }
                    }
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = "Historia")
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Wyloguj")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Dołącz do sesji")
                }
                FloatingActionButton(onClick = onCreateSession) {
                    Icon(Icons.Default.Add, contentDescription = "Nowa sesja")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (homeState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (homeState.activeSessions.isEmpty()) {
                // Stan pusty
                EmptyHomeContent(
                    onCreateSession = onCreateSession,
                    onJoinSession = { showJoinDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Aktywne sesje",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(homeState.activeSessions) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onOpenSession(session.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeContent(
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🤷", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Brak aktywnych sesji",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Utwórz nową sesję grupową lub dołącz do istniejącej kodem zaproszenia",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(onClick = onCreateSession, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Utwórz sesję")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onJoinSession, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Dołącz kodem")
        }
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit
) {
    val statusColor = when (session.status) {
        SessionStatus.WAITING -> MaterialTheme.colorScheme.tertiary
        SessionStatus.VOTING -> MaterialTheme.colorScheme.primary
        SessionStatus.FINISHED -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (session.status) {
        SessionStatus.WAITING -> "⏳ Oczekiwanie"
        SessionStatus.VOTING -> "🗳️ Głosowanie"
        SessionStatus.FINISHED -> "✅ Zakończona"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.category.emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Host: ${session.hostName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Awatary uczestników
                Row {
                    session.participantIds.take(4).forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .offset(x = (-index * 8).dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.error
                                    )[index % 4]
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (session.participantNames.values.toList()
                                    .getOrNull(index)?.take(1) ?: "?"),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (session.status == SessionStatus.VOTING) {
                    Text(
                        text = "${session.finishedVotingUids.size}/${session.participantIds.size} zagłosowało",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
