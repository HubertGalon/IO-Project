# Testy aplikacji

**Projekt:** GroupSwipe (aplikacja mobilna Android)
**Wersja dokumentu:** 1.0
**Data:** 18.06.2026

---

## 1. Wprowadzenie

### 1.1. Cel
Dokument przedstawia plan testów aplikacji GroupSwipe, zestaw scenariuszy testowych oraz wyniki ich faktycznego przeprowadzenia. Testy weryfikują kluczową logikę biznesową odpowiedzialną za poprawność głosowania, wyłaniania zwycięzcy, mechanizmu veta, walidacji danych oraz generowania kodów zaproszeń.

### 1.2. Zakres testów
Testowaniu poddano logikę domenową i logikę modeli widoku, które są deterministyczne i niezależne od warstwy sieci. Dodatkowo zaprojektowano manualne scenariusze end-to-end dla ścieżek wymagających działającego backendu Firebase. Poza zakresem automatyzacji w tym etapie pozostają: integracja z zewnętrznymi API (testowana ręcznie/eksploracyjnie) oraz testy wydajnościowe.

### 1.3. Podejście i narzędzia

| Poziom | Narzędzie / metoda | Co weryfikuje |
|---|---|---|
| Testy jednostkowe | JUnit 4 (`app/src/test`, JVM) | Czysta logika: wyniki, postęp, veto, walidacja |
| Testy instrumentalne | Espresso / Compose UI Test | Interakcje w interfejsie (zaprojektowane) |
| Testy manualne / E2E | Scenariusze ręczne na 2 kontach | Ścieżki z synchronizacją Firestore |


---

## 2. Scenariusze testowe

Każdy scenariusz zawiera cel, warunki wstępne, kroki, dane wejściowe oraz oczekiwany wynik. Identyfikatory TC-01…TC-07 odpowiadają testom automatycznym, MT-01…MT-04 – testom manualnym.

### TC-01. Obliczanie procentu głosów (`ProposalResult.percentage`)

| Element | Treść |
|---|---|
| Cel | Weryfikacja poprawnego obliczania udziału głosów TAK, w tym zabezpieczenia przed dzieleniem przez zero. |
| Powiązane wymaganie | WF-25 |
| Dane wejściowe | Pary (głosy TAK / wszystkie głosy): 3/4, 0/5, 5/5, 0/0, 1/3. |
| Kroki | Utworzyć `ProposalResult` i odczytać pole `percentage` dla każdej pary. |
| Oczekiwany wynik | 75, 0, 100, 0 (bez wyjątku), 33 (zaokrąglenie w dół). |

### TC-02. Filtrowanie propozycji i postęp głosowania (`VotingUiState`)

| Element | Treść |
|---|---|
| Cel | Sprawdzenie wskazywania bieżącej propozycji, licznika postępu i wykrycia końca puli. |
| Powiązane wymaganie | WF-20, WF-21, WF-23 |
| Dane wejściowe | Lista 4 propozycji; kolejne oddane głosy. |
| Kroki | Odczytać bieżącą propozycję i liczniki po 0, 2 i 4 głosach. |
| Oczekiwany wynik | Bieżąca: p1 → p3 → brak; licznik 0 → 2 → 4; po komplecie `hasMore=false`. |

### TC-03. Mechanizm veta

| Element | Treść |
|---|---|
| Cel | Weryfikacja reguły „jedno veto na osobę” i usuwania propozycji dla wszystkich. |
| Powiązane wymaganie | WF-22 |
| Dane wejściowe | 3 propozycje; użytkownik u1 wetuje p2; następnie u2. |
| Kroki | Sprawdzić `canVeto` przed i po vecie u1; sprawdzić pulę aktywnych; sprawdzić `canVeto` dla u2. |
| Oczekiwany wynik | p2 znika z aktywnych; u1 nie może wetować ponownie; u2 wciąż może. |

### TC-04. Wyłanianie zwycięzcy bez remisu

| Element | Treść |
|---|---|
| Cel | Sprawdzenie wskazania zwycięzcy oraz przypadku braku głosów TAK. |
| Powiązane wymaganie | WF-26 |
| Dane wejściowe | Wyniki: p1=4, p2=2, p3=1; oraz p1=0, p2=0. |
| Kroki | Wyznaczyć zwycięzcę i sprawdzić wykrycie remisu. |
| Oczekiwany wynik | Zwycięża p1; przy samych zerach brak zwycięzcy. |

### TC-05. Wykrywanie remisu i rozstrzygnięcie kołem fortuny

| Element | Treść |
|---|---|
| Cel | Weryfikacja wykrycia remisu i jednoznacznego wyniku po losowaniu. |
| Powiązane wymaganie | WF-27 |
| Dane wejściowe | Wyniki: p1=3, p2=3, p3=1; losowanie wskazuje p2. |
| Kroki | Wykryć remis; sprawdzić brak zwycięzcy przed losowaniem; ustawić tiebreak=p2. |
| Oczekiwany wynik | Remis wykryty; po losowaniu dokładnie jeden zwycięzca = p2. |

### TC-06. Walidacja danych logowania i rejestracji

| Element | Treść |
|---|---|
| Cel | Sprawdzenie reguł walidacji formularzy. |
| Powiązane wymaganie | WF-03 |
| Dane wejściowe | Pusty e-mail, błędny format, hasło < 6 znaków, nazwa < 2 znaków, niezgodne hasła, dane poprawne. |
| Kroki | Wywołać walidację logowania i rejestracji dla każdego przypadku. |
| Oczekiwany wynik | Właściwy komunikat błędu dla danych niepoprawnych; brak błędu dla poprawnych. |

### TC-07. Generowanie kodu zaproszenia

| Element | Treść |
|---|---|
| Cel | Weryfikacja długości, wielkości liter i praktycznej unikalności kodu. |
| Powiązane wymaganie | WF-12 |
| Dane wejściowe | 1000 wygenerowanych kodów. |
| Kroki | Wygenerować kody i sprawdzić właściwości. |
| Oczekiwany wynik | Każdy kod: 8 znaków, wielkie litery; liczba unikalnych ≥ 999/1000. |

### 2.1. Manualne scenariusze end-to-end

| ID | Scenariusz | Oczekiwany wynik |
|---|---|---|
| MT-01 | Rejestracja i logowanie nowego użytkownika | Konto utworzone, profil zapisany, użytkownik zalogowany na ekranie głównym. |
| MT-02 | Utworzenie sesji i zaproszenie znajomego | Sesja widoczna u obu uczestników; wygenerowany kod/link działa. |
| MT-03 | Dołączenie do sesji kodem | Użytkownik dodany do listy uczestników po wpisaniu kodu. |
| MT-04 | Pełne głosowanie dwóch osób i wynik | Po ukończeniu przez obie osoby status = zakończona; zwycięzca wyłoniony i identyczny u obu. |

---

## 3. Przeprowadzenie testów jednostkowych

==================================================================
 GroupSwipe - przeprowadzenie testow jednostkowych logiki biznesowej
==================================================================

TC-01: Obliczanie procentu glosow TAK (ProposalResult.percentage)
  [PASS] 3/4 glosow = 75%
  [PASS] 0/5 glosow = 0%
  [PASS] 5/5 glosow = 100%
  [PASS] zabezpieczenie dzielenia przez zero (0 glosow) = 0%
  [PASS] zaokraglanie w dol 1/3 = 33%

TC-02: Filtrowanie propozycji i postep glosowania (VotingUiState)
  [PASS] na start 4 aktywne propozycje
  [PASS] biezaca propozycja to p1
  [PASS] zaglosowano 0
  [PASS] sa kolejne propozycje
  [PASS] po 2 glosach biezaca to p3
  [PASS] zaglosowano 2
  [PASS] pozostaly 2 do oceny
  [PASS] po ocenie wszystkich brak kolejnych
  [PASS] brak biezacej propozycji (null)

TC-03: Mechanizm veta (jedno veto/osobe, propozycja znika dla wszystkich)
  [PASS] uzytkownik moze zawetowac (jeszcze nie uzyl)
  [PASS] zawetowana p2 zniknela -> 2 aktywne
  [PASS] p2 nie wystepuje wsrod aktywnych
  [PASS] uzytkownik nie moze zawetowac drugi raz
  [PASS] inny uzytkownik (u2) wciaz moze zawetowac

TC-04: Wylanianie zwyciezcy bez remisu
  [PASS] brak remisu
  [PASS] p1 (najwiecej glosow) jest zwyciezca
  [PASS] p2 nie jest zwyciezca
  [PASS] gdy 0 glosow TAK -> brak zwyciezcy

TC-05: Wykrywanie remisu i rozstrzygniecie kolem fortuny
  [PASS] remis na 1. miejscu wykryty
  [PASS] przy nierozstrzygnietym remisie brak jednoznacznego zwyciezcy
  [PASS] po losowaniu zwyciezca to wylosowane p2
  [PASS] p1 nie jest juz zwyciezca
  [PASS] dokladnie jeden zwyciezca po losowaniu

TC-06: Walidacja danych logowania i rejestracji
  [PASS] pusty email
  [PASS] zly format email
  [PASS] za krotkie haslo
  [PASS] poprawne logowanie -> brak bledu
  [PASS] za krotka nazwa
  [PASS] rozne hasla
  [PASS] poprawna rejestracja -> brak bledu

TC-07: Generowanie kodu zaproszenia do sesji
  [PASS] kazdy kod ma dlugosc 8 znakow
  [PASS] kazdy kod jest zapisany wielkimi literami
  [PASS] kody sa praktycznie unikalne (>= 999/1000)

==================================================================
 PODSUMOWANIE: 38 zaliczonych, 0 niezaliczonych, 38 asercji lacznie
 WYNIK: WSZYSTKIE TESTY ZALICZONE
==================================================================
```

---

## 4. Zbiorcze wyniki

### 4.1. Testy automatyczne

| ID | Scenariusz | Asercje | Status |
|---|---|:---:|:---:|
| TC-01 | Procent głosów | 5 | ZALICZONY |
| TC-02 | Postęp głosowania | 9 | ZALICZONY |
| TC-03 | Mechanizm veta | 5 | ZALICZONY |
| TC-04 | Zwycięzca bez remisu | 4 | ZALICZONY |
| TC-05 | Remis i koło fortuny | 5 | ZALICZONY |
| TC-06 | Walidacja formularzy | 7 | ZALICZONY |
| TC-07 | Kod zaproszenia | 3 | ZALICZONY |
| **Razem** | **7 scenariuszy** | **38** | **38/38** |

---

## 5. Wnioski i uwagi z testów
Wszystkie 38 asercji testów jednostkowych zakończyło się powodzeniem. Logika wyłaniania zwycięzcy, postępu głosowania, mechanizmu veta oraz walidacji działa zgodnie z wymaganiami. W trakcie analizy odnotowano obserwacje do dalszej pracy:

- **Współbieżność:** kolejność rejestrowania zakończenia głosowania opiera się na operacjach atomowych Firestore – zaleca się dodatkowe testy obciążeniowe wielu jednoczesnych głosów (ryzyko R-10 w Ocenie ryzyka).
- **Zależność od API:** ścieżki z danymi zastępczymi należy testować przez wymuszenie błędu sieci, aby potwierdzić poprawny fallback (WF-17).
- **Walidacja e-mail** w aplikacji korzysta z mechanizmu Androida (`Patterns.EMAIL_ADDRESS`); w teście jednostkowym zastosowano równoważne sprawdzenie formatu, a pełną weryfikację przewidziano w teście instrumentalnym.
