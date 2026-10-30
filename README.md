# Brulion

Nieoficjalna aplikacja na Androida do dziennika elektronicznego **Librus**,
dla rodziców i uczniów: oceny, plan lekcji, frekwencja, wiadomości, terminarz,
codzienne podsumowanie dnia i widżet z planem lekcji.

Brulion powstał na podstawie [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android)
i tak jak on jest udostępniany na licencji [GNU GPL v3](LICENSE). Szczegóły
pochodzenia kodu: [NOTICE.md](NOTICE.md).

## Instalacja i aktualizacje

Aplikacji nie ma w Google Play. Plik APK jest w zakładce
[Releases](https://github.com/depablo/brulion/releases/latest). Zainstalowana
aplikacja sama sprawdza nowe wersje (Ustawienia → Aktualizacje) i instaluje je
po potwierdzeniu.

## Budowanie

Wymagane JDK 17+ i Android SDK (platforma 34):

```
./gradlew assembleDebug
```

Wersja debug ma identyfikator `com.bonsaponsi.brulion.debug` i instaluje się obok wydania.
Wydania: [RELEASING.md](RELEASING.md).

## Zastrzeżenia

Brulion nie jest powiązany z firmą Librus. Aplikacja korzysta z tych samych
usług co oficjalna aplikacja i strona Librusa, logując się danymi użytkownika;
działanie może się zmienić bez zapowiedzi, gdy Librus coś zmieni. Program jest
dostarczany bez żadnej gwarancji.
