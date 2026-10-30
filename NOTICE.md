# Uwagi o licencji i pochodzeniu kodu

Brulion © 2026 depablo (https://github.com/depablo)

Ten program jest wolnym oprogramowaniem: możesz go rozprowadzać i modyfikować
na warunkach Powszechnej Licencji Publicznej GNU w wersji 3 (GNU GPL v3),
opublikowanej przez Free Software Foundation. Pełny tekst licencji znajduje się
w pliku [LICENSE](LICENSE).

Program rozpowszechniany jest **bez jakiejkolwiek gwarancji**, nawet domyślnej
gwarancji przydatności handlowej albo przydatności do określonego celu.

## Szkolny.eu

Brulion powstał z pomocą projektu **Szkolny.eu**
(https://github.com/szkolny-eu/szkolny-android), © Kuba Szczodrzyński
i współtwórcy, udostępnianego na licencji GNU GPL v3. Kod Brulionu napisano od
nowa, ale obsługę Librusa oparto na wiedzy ze Szkolnego.eu (rewizja `2d6d823a`):

- adresy i identyfikatory klienta API Librusa oraz przebieg logowania przez
  Portal Librus i Synergię, w tym `AutoLoginToken` do sesji webowej
  (`app/src/main/java/pl/brulion/librus/`),
- znaczenie pól w odpowiedziach API przy odczycie ocen, planu lekcji,
  frekwencji, wydarzeń, ogłoszeń i uwag (`app/src/main/java/pl/brulion/data/sync/`).

Wiadomości korzystają z nowszego API `wiadomosci.librus.pl/api`, którego
Szkolny.eu nie używa.

Interfejs, baza danych, podsumowania, widżety i aktualizacje nie mają
odpowiednika w kodzie Szkolnego.eu. Brulion nie jest wersją Szkolnego.eu, nie jest z nim
powiązany ani przez jego autorów wspierany.

Zgodnie z życzeniem autorów Szkolnego.eu Brulion nie jest rozpowszechniany przez
Google Play.

## Znaki towarowe

Librus i Synergia są znakami towarowymi Librus Sp. z o.o. Brulion jest
nieoficjalną aplikacją, niezwiązaną z firmą Librus; nazwy te są używane
wyłącznie w celach informacyjnych.
