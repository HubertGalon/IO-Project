package com.groupswipe.presentation.session.create

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.groupswipe.domain.model.SessionCategory
import com.groupswipe.domain.model.User
import com.groupswipe.presentation.session.SessionViewModel

/** Kategorie wymagające dostępu do lokalizacji (żeby znaleźć miejsca w pobliżu) */
private val LOCATION_REQUIRED_CATEGORIES = setOf(
    SessionCategory.RESTAURANTS,
    SessionCategory.HOTELS,
    SessionCategory.ACTIVITIES
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    onSessionCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.createState.collectAsState()
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SessionCategory.MOVIES) }
    var locationGranted by remember { mutableStateOf(false) }
    var showLocationRationale by remember { mutableStateOf(false) }

    // Launcher do prośby o uprawnienia lokalizacji
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) { viewModel.loadFriendsForSession() }

    LaunchedEffect(state.createdSessionId) {
        state.createdSessionId?.let { id ->
            viewModel.resetCreateState()
            onSessionCreated(id)
        }
    }

    // Dialog wyjaśniający potrzebę lokalizacji
    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            title = { Text("Potrzebujemy lokalizacji") },
            text = {
                Text(
                    "Aby znaleźć restauracje, hotele i wydarzenia w Twojej okolicy, " +
                    "aplikacja potrzebuje dostępu do Twojej lokalizacji.\n\n" +
                    "Bez uprawnień użyta zostanie domyślna lokalizacja (Warszawa centrum).",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    showLocationRationale = false
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) { Text("Udziel dostępu") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationRationale = false }) {
                    Text("Pomiń (użyj Warszawy)")
                }
            }
        )
    }

    // Zapytaj o lokalizację przy wyborze kategorii jej wymagającej (chyba że wybrano miasto)
    LaunchedEffect(selectedCategory, state.useCity) {
        if (selectedCategory in LOCATION_REQUIRED_CATEGORIES && !locationGranted && !state.useCity) {
            showLocationRationale = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nowa sesja grupowa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Tytuł sesji ----
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Tytuł sesji",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("np. Piątkowy wieczór, Wakacje 2025") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.isLoading
                        )
                    }
                }
            }

            // ---- Wybór kategorii ----
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Kategoria",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "Określa skąd pobierane są propozycje do głosowania",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SessionCategory.entries.forEach { category ->
                            CategoryOption(
                                category = category,
                                isSelected = selectedCategory == category,
                                needsLocation = category in LOCATION_REQUIRED_CATEGORIES,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }

            // Baner lokalizacji – pokazuj gdy kategoria wymaga lokalizacji
            if (selectedCategory in LOCATION_REQUIRED_CATEGORIES) {
                item {
                    LocationSourceCard(
                        useCity = state.useCity,
                        selectedCity = state.selectedCity,
                        cities = state.availableCities,
                        onUseGps = { viewModel.selectLocationMode(false) },
                        onChooseCity = { viewModel.selectLocationMode(true) },
                        onSelectCity = { viewModel.selectCity(it) }
                    )
                }
                if (!state.useCity) {
                    item {
                        LocationBanner(
                            locationGranted = locationGranted,
                            onRequestLocation = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // ---- Zaproś znajomych ----
            if (state.availableFriends.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Zaproś znajomych",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Pozostałych możesz zaprosić kodem po utworzeniu sesji",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
                items(state.availableFriends) { friend ->
                    FriendSelectionItem(
                        friend = friend,
                        isSelected = friend.uid in state.selectedFriendIds,
                        onClick = { viewModel.toggleFriendSelection(friend.uid) }
                    )
                }
            }

            // ---- Błąd ----
            if (state.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ---- Przycisk tworzenia ----
            item {
                Button(
                    onClick = { viewModel.createSession(title, selectedCategory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !state.isLoading && title.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pobieranie propozycji z API...")
                    } else {
                        Icon(Icons.Default.Rocket, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Utwórz sesję", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LocationSourceCard(
    useCity: Boolean,
    selectedCity: String?,
    cities: List<String>,
    onUseGps: () -> Unit,
    onChooseCity: () -> Unit,
    onSelectCity: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Źródło lokalizacji",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Skąd szukać propozycji w pobliżu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Opcja 1: GPS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseGps() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = !useCity, onClick = onUseGps)
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Moja lokalizacja (GPS)")
            }

            // Opcja 2: Wybrane miasto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChooseCity(); expanded = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = useCity, onClick = { onChooseCity(); expanded = true })
                Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Wybierz miasto")
            }

            // Lista miast – zawsze dostępna (rozwijana po kliknięciu)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onChooseCity(); expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedCity ?: "Wybierz miasto…",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                onSelectCity(city)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationBanner(locationGranted: Boolean, onRequestLocation: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (locationGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (locationGranted) Icons.Default.LocationOn else Icons.Default.LocationOff,
                contentDescription = null,
                tint = if (locationGranted)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (locationGranted) "Lokalizacja aktywna" else "Lokalizacja nieaktywna",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (locationGranted)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = if (locationGranted)
                        "Propozycje zostaną dopasowane do Twojej okolicy"
                    else
                        "Używana zostanie lokalizacja domyślna (Warszawa)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (locationGranted)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            if (!locationGranted) {
                TextButton(onClick = onRequestLocation) {
                    Text("Zezwól", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CategoryOption(
    category: SessionCategory,
    isSelected: Boolean,
    needsLocation: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                // Pokaż źródło danych
                val sourceLabel = when (category) {
                    SessionCategory.MOVIES      -> "TMDB API"
                    SessionCategory.RESTAURANTS -> "OpenStreetMap • lokalizacja/miasto"
                    SessionCategory.HOTELS      -> "OpenStreetMap • lokalizacja/miasto"
                    SessionCategory.TRAVEL      -> "Wikipedia • popularne miasta świata"
                    SessionCategory.ACTIVITIES  -> "Ticketmaster • lokalizacja/miasto"
                }
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (needsLocation) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Wymaga lokalizacji",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FriendSelectionItem(
    friend: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(friend.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(friend.email, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = friend.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
