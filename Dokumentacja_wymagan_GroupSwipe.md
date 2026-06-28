# Dokumentacja wymagań

**Projekt:** GroupSwipe (aplikacja mobilna Android)
**Wersja dokumentu:** 1.0
**Data:** 18.06.2026

---

## 1. Wprowadzenie

### 1.1. Cel dokumentu
Dokument opisuje wymagania stawiane aplikacji mobilnej GroupSwipe. Stanowi punkt odniesienia dla zespołu projektowego, testerów oraz osób oceniających projekt. Wymagania odtworzono na podstawie analizy kodu źródłowego (warstwy domenowej, repozytoriów, modeli widoku oraz manifestu Androida).

### 1.2. Opis produktu
GroupSwipe to aplikacja na Androida wspierająca grupowe podejmowanie decyzji w stylu „przesuń, aby zagłosować” (swipe). Grupa znajomych tworzy wspólną sesję w wybranej kategorii (filmy, restauracje, hotele, wakacje, aktywności), a każdy uczestnik ocenia kolejne propozycje gestem TAK/NIE. Po zakończeniu głosowania aplikacja agreguje głosy i wyłania zwycięzcę; remis jest rozstrzygany losowo („koło fortuny”). Propozycje pochodzą z zewnętrznych, w większości darmowych API.

### 1.3. Zakres
Specyfikacja obejmuje uwierzytelnianie, zarządzanie znajomymi, tworzenie i prowadzenie sesji głosowania, prezentację wyników, historię oraz integracje z usługami zewnętrznymi. Poza zakresem pozostają płatności, czat tekstowy oraz panel administracyjny.

### 1.4. Definicje i skróty

| Termin | Znaczenie |
|---|---|
| Sesja | Wspólne głosowanie grupy w wybranej kategorii, synchronizowane w czasie rzeczywistym. |
| Propozycja | Pojedynczy element do oceny (film, lokal, miasto, wydarzenie). |
| Veto | Jednorazowe (na osobę) trwałe usunięcie propozycji z puli dla wszystkich uczestników. |
| Host | Użytkownik, który utworzył sesję. |
| Kod zaproszenia | 8-znakowy kod oraz deep link umożliwiające dołączenie do sesji. |
| Fallback | Dane zastępcze prezentowane, gdy zewnętrzne API nie odpowiada. |
| DTO | Obiekt transferu danych z zewnętrznego API. |

---

## 2. Opis ogólny systemu

### 2.1. Architektura i technologie
Aplikacja zbudowana jest zgodnie z podejściem warstwowym (prezentacja – domena – dane) z wykorzystaniem wzorca MVVM. Wykorzystane technologie:

- Język i UI: Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- Wstrzykiwanie zależności: Hilt (Dagger).
- Backend i synchronizacja: Firebase Authentication oraz Cloud Firestore (czas rzeczywisty).
- Pamięć lokalna: Room (historia sesji), DataStore (preferencje lokalizacji).
- Sieć: Retrofit + OkHttp; ładowanie obrazów: Coil.
- Lokalizacja: Google Play Services Location.

Minimalny wspierany system to Android 7.0 (API 24), docelowy API 35.

### 2.2. Klasy użytkowników

| Rola | Opis |
|---|---|
| Użytkownik niezalogowany | Może się zarejestrować lub zalogować. |
| Uczestnik | Zalogowany użytkownik: zarządza znajomymi, dołącza do sesji, głosuje, przegląda wyniki i historię. |
| Host sesji | Uczestnik z dodatkowym uprawnieniem rozpoczęcia głosowania w utworzonej przez siebie sesji. |

### 2.3. Założenia i ograniczenia
- Do działania kluczowych funkcji wymagane jest połączenie z internetem oraz aktywne usługi Firebase.
- Dostępność i jakość propozycji zależą od zewnętrznych API; przy ich niedostępności prezentowane są dane zastępcze.
- Pozyskanie lokalizacji wymaga zgody użytkownika; alternatywnie można ręcznie wybrać miasto.
- Aplikacja jest zlokalizowana w języku polskim.

---

## 3. Wymagania funkcjonalne

Każde wymaganie ma identyfikator (WF-xx), opis oraz priorytet. Priorytet wyznaczono metodą MoSCoW uproszczoną do trzech poziomów: **Wysoki**, **Średni**, **Niski**.

### 3.1. Uwierzytelnianie i konto

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-01 | Rejestracja | Założenie konta na podstawie e-maila, hasła i nazwy wyświetlanej; profil zapisywany w Firestore. | Wysoki |
| WF-02 | Logowanie | Logowanie istniejącego użytkownika e-mailem i hasłem. | Wysoki |
| WF-03 | Walidacja danych | Sprawdzenie e-maila, długości hasła (min. 6 znaków), nazwy (min. 2 znaki) i zgodności haseł. | Wysoki |
| WF-04 | Czytelne komunikaty błędów | Tłumaczenie błędów Firebase na przyjazne komunikaty po polsku. | Średni |
| WF-05 | Wylogowanie | Zakończenie sesji użytkownika. | Wysoki |

### 3.2. Znajomi

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-06 | Wyszukiwanie użytkowników | Wyszukiwanie po adresie e-mail (prefiks) z pominięciem własnego konta. | Średni |
| WF-07 | Wysyłanie zaproszeń | Wysłanie zaproszenia z zabezpieczeniem przed duplikatem. | Średni |
| WF-08 | Oczekujące zaproszenia | Podgląd w czasie rzeczywistym zaproszeń skierowanych do użytkownika. | Średni |
| WF-09 | Akceptacja/odrzucenie | Obsługa decyzji o zaproszeniu i aktualizacja listy znajomych. | Średni |

### 3.3. Sesje grupowe

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-10 | Tworzenie sesji | Utworzenie sesji z tytułem i jedną z pięciu kategorii; host dodawany automatycznie. | Wysoki |
| WF-11 | Zapraszanie znajomych | Wskazanie znajomych dodawanych do sesji przy tworzeniu. | Średni |
| WF-12 | Kod zaproszenia | Generowanie 8-znakowego kodu. | Wysoki |
| WF-13 | Dołączanie kodem | Dołączenie do istniejącej sesji po podaniu kodu. | Wysoki |
| WF-14 | Rozpoczęcie głosowania | Zmiana statusu sesji z „oczekiwanie” na „głosowanie” przez hosta. | Wysoki |
| WF-15 | Synchronizacja stanu | Aktualizacja stanu sesji u wszystkich uczestników w czasie rzeczywistym. | Wysoki |

### 3.4. Propozycje

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-16 | Pobieranie propozycji | Pozyskanie do 10 propozycji właściwych dla kategorii z zewnętrznego API. | Wysoki |
| WF-17 | Dane zastępcze | Lista zastępcza/kuratorska, gdy API nie odpowiada lub zwróci pustą odpowiedź. | Wysoki |
| WF-18 | Lokalizacja propozycji | Dla restauracji, hoteli i aktywności propozycje dobierane wg lokalizacji (GPS lub miasto). | Średni |
| WF-19 | Różnorodność | Losowanie strony/kolejności, aby kolejne sesje prezentowały różne propozycje. | Niski |

### 3.5. Głosowanie

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-20 | Głos TAK/NIE | Oddanie głosu gestem przesunięcia lub przyciskiem dla bieżącej propozycji. | Wysoki |
| WF-21 | Postęp głosowania | Prezentacja licznika ocenionych/pozostałych propozycji. | Średni |
| WF-22 | Veto | Jednorazowe (na osobę) trwałe usunięcie propozycji dla wszystkich uczestników. | Średni |
| WF-23 | Wykrycie końca | Automatyczne oznaczenie gotowości, gdy nic nie pozostało do oceny (także po wetach). | Wysoki |
| WF-24 | Zakończenie sesji | Zmiana statusu na „zakończona”, gdy wszyscy uczestnicy ukończą głosowanie. | Wysoki |

### 3.6. Wyniki, historia i ustawienia

| ID | Funkcja | Opis | Priorytet |
|---|---|---|---|
| WF-25 | Agregacja wyników | Zliczenie głosów TAK i obliczenie udziału procentowego dla każdej propozycji. | Wysoki |
| WF-26 | Wyłonienie zwycięzcy | Zwycięża propozycja z największą liczbą głosów TAK, o ile jest jedna taka i padł co najmniej jeden głos TAK. | Wysoki |
| WF-27 | Rozstrzygnięcie remisu | Przy remisie „koło fortuny” losuje zwycięzcę; wynik jednakowy dla wszystkich (transakcja). | Średni |
| WF-28 | Historia sesji | Lokalne archiwum zakończonych sesji z wyłonionym zwycięzcą. | Średni |
| WF-29 | Źródło lokalizacji | Wybór między lokalizacją GPS a ręcznie wskazanym miastem. | Średni |

---

## 4. Wymagania niefunkcjonalne

| ID | Kategoria | Wymaganie |
|---|---|---|
| WN-01 | Wydajność | Operacje sieciowe objęte limitami czasu (4–6 s na zapytanie, 30 s na utworzenie sesji); UI nie blokuje się podczas pobierania danych. |
| WN-02 | Niezawodność | Mechanizm fallback i serwery lustrzane Overpass zapewniają działanie nawet przy awarii pojedynczego API. |
| WN-03 | Użyteczność | Spójny interfejs Material 3, czytelne gesty swipe, komunikaty po polsku. |
| WN-04 | Bezpieczeństwo | Uwierzytelnianie Firebase; zalecane reguły bezpieczeństwa Firestore ograniczające dostęp do danych uczestnika. |
| WN-05 | Synchronizacja | Spójność współbieżna kluczowych operacji (głos, veto, remis) realizowana operacjami atomowymi/transakcjami Firestore. |
| WN-06 | Kompatybilność | Wsparcie Android 7.0+ (API 24), obsługa układów RTL i kopii zapasowej. |
| WN-07 | Utrzymywalność | Czytelny podział na warstwy (MVVM), wstrzykiwanie zależności, izolacja źródeł danych w repozytoriach. |
| WN-08 | Lokalizacja | Interfejs i treści po polsku; daty/strefy obsługiwane wg standardu UTC dla zapytań do API. |

---

## 5. Integracje zewnętrzne i uprawnienia

| Usługa | Zastosowanie | Klucz API |
|---|---|---|
| TMDB | Propozycje filmów (kategoria Filmy) | Wymagany |
| OpenStreetMap / Overpass | Restauracje i hotele wg lokalizacji | Nie |
| Wikipedia REST | Opisy miast (kategoria Wakacje) | Nie |
| Ticketmaster | Wydarzenia/aktywności wg lokalizacji | Wymagany |
| Firebase Auth + Firestore | Konta i synchronizacja sesji | Konfiguracja projektu |

**Uprawnienia Androida:**
- `INTERNET`, `ACCESS_NETWORK_STATE` – komunikacja z usługami zewnętrznymi.
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` – dobór propozycji wg lokalizacji.

---

## 6. Model danych (kluczowe encje)

| Encja | Najważniejsze pola |
|---|---|
| User | uid, email, displayName, friendIds |
| Session | id, host, title, category, status, participantIds, proposalIds, vetoedProposalIds, vetoUsedUids, tiebreakWinnerId, inviteCode |
| Proposal | id, sessionId, title, description, imageUrl, rating, category, externalId, detailUrl |
| Vote | uid, sessionId, proposalId, isYes, timestamp |
| ProposalResult | proposal, yesVotes, totalVotes, isWinner, percentage (pole pochodne) |
| SessionHistory (Room) | sessionId, title, category, winnerTitle, finishedAt, participantCount |

---

## 7. Podsumowanie
Specyfikacja obejmuje 29 wymagań funkcjonalnych w 6 obszarach oraz 8 wymagań niefunkcjonalnych. Wymagania o priorytecie wysokim tworzą ścieżkę krytyczną produktu: rejestracja/logowanie, utworzenie i prowadzenie sesji, głosowanie oraz prezentacja wyników. Wymagania średnie i niskie rozszerzają wartość produktu (znajomi, veto, historia, różnorodność propozycji). Sposób weryfikacji wymagań opisano w dokumencie „Testy aplikacji”, a ryzyka związane z ich realizacją – w dokumencie „Ocena ryzyka”.
