# Ocena ryzyka

**Projekt:** GroupSwipe (aplikacja mobilna Android)
**Wersja dokumentu:** 1.0
**Data:** 18.06.2026

---

## 1. Wprowadzenie

### 1.1. Cel i zakres
Dokument identyfikuje ryzyka związane z budową, wdrożeniem i eksploatacją aplikacji GroupSwipe oraz proponuje działania ograniczające. Ryzyka pogrupowano w kategorie: techniczne, bezpieczeństwo i prywatność, funkcjonalne, prawne oraz projektowe.

### 1.2. Metoda oceny
Każde ryzyko oceniono w dwóch wymiarach – prawdopodobieństwa (P) wystąpienia oraz wpływu (W) na projekt – w skali 1–3 (1 – niskie, 2 – średnie, 3 – wysokie). Poziom ryzyka wyznaczono jako iloczyn P × W:

| Iloczyn P × W | Poziom | Reakcja |
|:---:|:---:|---|
| 1 – 2 | NISKI | Monitorować, akceptować |
| 3 – 4 | ŚREDNI | Zaplanować ograniczenie |
| 6 – 9 | WYSOKI | Działanie priorytetowe |

---

## 2. Rejestr ryzyk

### 2.1. Ryzyka techniczne

| ID | Ryzyko | P | W | Poziom | Działania ograniczające |
|---|---|:---:|:---:|:---:|---|
| R-01 | Niedostępność zewnętrznego API (np. Overpass HTTP 406, wycofane Teleport) powoduje brak propozycji | 3 | 2 | ŚREDNI | Serwery lustrzane Overpass, limity czasu i wielopoziomowy fallback na dane kuratorskie/zastępcze; monitoring odpowiedzi API. |
| R-02 | Zmiana lub wycofanie wersji API (np. nowe API Foursquare) psuje integrację | 2 | 2 | ŚREDNI | Izolacja w warstwie DTO/repozytoriów; testy kontraktowe; przypięcie wersji API; szybka podmiana źródła. |
| R-03 | Przekroczenie limitów/kosztów zewnętrznych API | 2 | 2 | ŚREDNI | Buforowanie wyników, ograniczenie liczby zapytań na sesję, monitoring zużycia kluczy. |
| R-04 | Brak konfiguracji ProGuard/R8 (`minifyEnabled=false`) – większy pakiet, brak zaciemnienia | 2 | 1 | NISKI | Włączyć minifikację i reguły R8 dla wydania; przegląd zależności. |
| R-05 | Awaria usług Firebase blokuje logowanie i sesje | 1 | 3 | ŚREDNI | Komunikaty o stanie sieci, ponowne próby, plan reakcji na incydent; rozważyć degradację funkcji offline. |

### 2.2. Bezpieczeństwo i prywatność

| ID | Ryzyko | P | W | Poziom | Działania ograniczające |
|---|---|:---:|:---:|:---:|---|
| R-06 | Klucze API umieszczone w repozytorium/`buildConfig` mogą zostać przejęte | 3 | 3 | WYSOKI | Przenieść klucze poza repozytorium (zmienne środowiskowe/secret store), rotować ujawnione klucze, ograniczyć je domeną/uprawnieniami; serwerowy proxy do API. |
| R-07 | Zbyt liberalne reguły bezpieczeństwa Firestore – dostęp do cudzych sesji/głosów | 3 | 3 | WYSOKI | Wdrożyć reguły ograniczające odczyt/zapis do uczestników sesji i właściciela danych; testy reguł (emulator Firebase). |
| R-08 | Przetwarzanie lokalizacji i danych osobowych bez pełnej zgody/informacji (RODO) | 2 | 3 | WYSOKI | Polityka prywatności, zgody na lokalizację, minimalizacja danych, możliwość usunięcia konta i danych. |
| R-09 | Deep link do dołączania (`https://groupswipe.app/join`) może być nadużyty | 2 | 2 | ŚREDNI | Weryfikacja kodu po stronie reguł, ograniczenie ważności kodu, App Links z weryfikacją domeny. |

### 2.3. Ryzyka funkcjonalne

| ID | Ryzyko | P | W | Poziom | Działania ograniczające |
|---|---|:---:|:---:|:---:|---|
| R-10 | Warunki wyścigu przy jednoczesnym głosowaniu/wetowaniu wielu osób | 2 | 2 | ŚREDNI | Operacje atomowe i transakcje Firestore (zastosowane dla remisu i veta); testy współbieżności i obciążeniowe. |
| R-11 | Błędne wyłonienie zwycięzcy przy remisie | 1 | 3 | ŚREDNI | Transakcja utrwala tylko pierwsze losowanie – jednakowy wynik dla wszystkich; pokrycie testem TC-05. |
| R-12 | Utrata postępu po awarii/zamknięciu aplikacji w trakcie głosowania | 2 | 2 | ŚREDNI | Stan głosów utrwalany w Firestore na bieżąco; lokalny cache (Room) historii i głosów. |

### 2.4. Ryzyka prawne i projektowe

| ID | Ryzyko | P | W | Poziom | Działania ograniczające |
|---|---|:---:|:---:|:---:|---|
| R-13 | Naruszenie regulaminów/licencji dostawców treści (TMDB, Ticketmaster, OSM) | 2 | 2 | ŚREDNI | Przestrzeganie warunków użycia i atrybucji, prezentacja źródeł, brak redystrybucji niezgodnej z licencją. |
| R-14 | Uzależnienie od jednego dostawcy (Firebase) – trudna migracja | 2 | 2 | ŚREDNI | Abstrakcja dostępu do danych w repozytoriach umożliwiająca podmianę backendu; eksport danych. |
| R-15 | Brak automatycznych testów w repozytorium na starcie projektu | 2 | 2 | ŚREDNI | Dodanie testów jednostkowych (zrealizowane), włączenie ich do CI; rozszerzanie pokrycia. |

---

## 3. Macierz ryzyka (P × W)

Rozmieszczenie ryzyk względem prawdopodobieństwa (wiersze) i wpływu (kolumny).

| P \ W | Wpływ = 1 | Wpływ = 2 | Wpływ = 3 |
|:---:|:---:|:---:|:---:|
| **Prawdop. = 3** | – | R-01 *(śr.)* | R-06, R-07 *(wys.)* |
| **Prawdop. = 2** | R-04 *(niski)* | R-02, R-03, R-09, R-10, R-12, R-13, R-14, R-15 *(śr.)* | R-08 *(wys.)* |
| **Prawdop. = 1** | – | – | R-05, R-11 *(śr.)* |

---

## 4. Podsumowanie i rekomendacje
Zidentyfikowano 15 ryzyk. Trzy z nich osiągają poziom wysoki i wymagają działania priorytetowego, wszystkie w obszarze bezpieczeństwa i prywatności:

- **R-06** – usunięcie kluczy API z repozytorium i ich rotacja oraz wprowadzenie serwerowego pośrednika do wywołań płatnych API.
- **R-07** – wdrożenie i przetestowanie restrykcyjnych reguł bezpieczeństwa Firestore.
- **R-08** – zapewnienie zgodności z RODO: polityka prywatności, zgody, minimalizacja danych i możliwość ich usunięcia.

Ryzyka techniczne związane z zewnętrznymi API (R-01–R-03) są w znacznym stopniu ograniczone przez zastosowane mechanizmy fallback i limity czasu, lecz wymagają monitoringu. Ryzyka funkcjonalne (R-10–R-12) są adresowane operacjami atomowymi i transakcjami oraz pokryte testami. Rekomenduje się włączenie testów jednostkowych do procesu CI oraz wykonanie testów współbieżności i obciążeniowych przed wdrożeniem produkcyjnym.
