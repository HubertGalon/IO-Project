# GroupSwipe 🤝

Aplikacja mobilna na Androida umożliwiająca grupom znajomych wspólne podejmowanie decyzji przez głosowanie swipe left/right.

---

## 🌐 Integracje z zewnętrznymi API

| Kategoria | API | Plan | Klucz |
|-----------|-----|------|-------|
| 🎬 Filmy | [TMDB](https://www.themoviedb.org/settings/api) | Darmowy, bez limitu | Wymagany |
| 🍽️ Restauracje | [Foursquare Places v3](https://foursquare.com/developers) | Darmowy, 1000 req/dzień | Wymagany |
| 🏨 Hotele | [OpenTripMap](https://dev.opentripmap.org/) | Darmowy, 1000 req/dzień | Wymagany |
| ✈️ Wakacje | [Teleport Public API](https://developers.teleport.org/api/) | **Darmowy, bez klucza** | Nie wymagany |
| 🎯 Aktywności | [Ticketmaster Discovery](https://developer.ticketmaster.com/) | Darmowy, 5000 req/dzień | Wymagany |

---

## 🚀 Instrukcja uruchomienia krok po kroku

### Wymagania
- Android Studio Hedgehog (2023.1.1) lub nowszy
- JDK 17 · Android SDK 34 · Konto Google

---

### Krok 1 – Firebase

1. Przejdź na [console.firebase.google.com](https://console.firebase.google.com/)
2. **Utwórz projekt** → `GroupSwipe`
3. Kliknij ikonę Androida → wpisz pakiet `com.groupswipe`
4. **Pobierz `google-services.json`** → skopiuj do `app/`
5. Włącz **Authentication → Email/Hasło**
6. Utwórz **Firestore Database** (tryb testowy, region `europe-west1`)
7. Wgraj reguły z pliku `firestore.rules`

---

### Krok 2 – Klucze API

Utwórz plik `local.properties` (kopiując `local.properties.template`) i wypełnij:

#### TMDB (filmy)
1. Zarejestruj się: [themoviedb.org](https://www.themoviedb.org/)
2. Potwierdź email → Ustawienia → [API](https://www.themoviedb.org/settings/api) → Utwórz klucz (Developer)
3. Wstaw **API Key (v3 auth)** jako `TMDB_API_KEY`

#### Foursquare (restauracje + aktywności fallback)
1. Zarejestruj się: [foursquare.com/developers/signup](https://foursquare.com/developers/signup)
2. Utwórz projekt → skopiuj **API Key**
3. Wstaw **BEZ prefixu "fsq3"** jako `FOURSQUARE_API_KEY`
   > Aplikacja automatycznie dodaje `fsq3` przed wysłaniem żądania

#### OpenTripMap (hotele)
1. Zarejestruj się: [dev.opentripmap.org](https://dev.opentripmap.org/) (bez karty kredytowej)
2. Skopiuj klucz z dashboardu → wstaw jako `OPENTRIPMAP_API_KEY`

#### Ticketmaster (aktywności / wydarzenia)
1. Zarejestruj się: [developer.ticketmaster.com](https://developer.ticketmaster.com/)
2. Utwórz aplikację → skopiuj **Consumer Key** → wstaw jako `TICKETMASTER_API_KEY`

#### Teleport (wakacje)
✅ **Nie wymaga klucza** – w pełni darmowe publiczne API.

---

### Krok 3 – Uruchomienie

```bash
# Otwórz projekt w Android Studio
File → Open → wybierz folder GroupSwipe

# Poczekaj na synchronizację Gradle (~2-5 min przy pierwszym uruchomieniu)
# Podłącz urządzenie (API 24+) lub uruchom emulator
# Kliknij ▶ Run (Shift+F10)
```

---

## 🏗️ Architektura projektu

```
app/src/main/java/com/groupswipe/
├── data/
│   ├── local/           # Room (historia, cache)
│   ├── remote/
│   │   ├── api/         # Retrofit interfaces (TMDB, Foursquare, OpenTripMap, Teleport, Ticketmaster)
│   │   └── dto/         # Data Transfer Objects + mapowanie → model domenowy
│   └── repository/
│       ├── AuthRepository.kt
│       ├── FriendsRepository.kt
│       ├── LocationProvider.kt   # FusedLocationProviderClient (z fallback na Warszawę)
│       ├── ProposalRepository.kt # Logika pobierania z 5 API, parallel coroutines
│       └── SessionRepository.kt
├── di/AppModule.kt       # Hilt: 5 oddzielnych instancji Retrofit
├── domain/model/         # Modele domenowe
└── presentation/         # Compose UI (MVVM)
```

---

## 🔍 Jak działa pobieranie danych

### Restauracje (Foursquare)
- Pobiera lokalizację użytkownika (lub Warszawa jeśli brak uprawnień)
- Wyszukuje popularnych miejsca spożywcze (kategoria 13065=Food) w promieniu 5 km
- Zwraca: nazwa, kategoria, ocena (0-10→0-5★), zdjęcie, adres, status (otwarte/zamknięte), strona www

### Hotele (OpenTripMap)
- Wyszukuje obiekty noclegowe (`kinds=accomodations`) w promieniu 10 km, ocena ≥ 2/3
- Pobiera listę XID-ów, następnie **równolegle** pobiera szczegóły (coroutines `async/awaitAll`)
- Filtruje tylko te ze zdjęciem lub opisem Wikipedii
- Zwraca: nazwa, opis z Wikipedii, zdjęcie, lokalizacja, link do artykułu

### Wakacje (Teleport)
- Losuje 14 z 20 predefiniowanych destynacji (Barcelona, Tokio, Paryż...)
- Dla każdej **równolegle** pobiera wyniki jakości życia (`/scores`) i zdjęcia (`/images`)
- Ekstrakt HTML z opisu → czysty tekst
- Zwraca: Teleport Score (0-100→0-5★), koszty życia, bezpieczeństwo, zdjęcie miasta, opis

### Aktywności (Ticketmaster)
- Wyszukuje nadchodzące wydarzenia (music, arts, sports) w promieniu 50 km
- Filtruje tylko eventy ze zdjęciem i datą
- Fallback: promień 200 km i więcej kategorii jeśli brak wyników
- Zwraca: nazwa, data (sformatowana PL), miejsce, cena, kategoria, link do zakupu biletów

---

## ✅ Lista funkcjonalności

| Funkcja | Status |
|---------|--------|
| Auth (Firebase Email/Password) | ✅ |
| Profil + wyszukiwanie userów | ✅ |
| System znajomych | ✅ |
| Tworzenie sesji grupowej | ✅ |
| Dołączanie kodem | ✅ |
| Real-time sync (Firestore) | ✅ |
| Swipe głosowanie z animacją | ✅ |
| **TMDB – prawdziwe filmy** | ✅ |
| **Foursquare – restauracje z lokalizacji** | ✅ |
| **OpenTripMap – hotele z lokalizacji** | ✅ |
| **Teleport – 20 destynacji podróżniczych** | ✅ |
| **Ticketmaster – nadchodzące wydarzenia** | ✅ |
| Prośba o uprawnienia lokalizacji | ✅ |
| Fallback (Warszawa) bez uprawnień | ✅ |
| Parallel API calls (coroutines) | ✅ |
| Wyniki i ranking | ✅ |
| Historia sesji (Room) | ✅ |
| Dark mode | ✅ |
