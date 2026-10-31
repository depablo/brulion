package pl.brulion.data.feed

import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import kotlin.random.Random

/**
 * A short line under a new grade in its notification, for the student or for the parent.
 * The texts don't address the reader ("Ty", "Twój") and avoid gendered forms, so they fit
 * anyone; low grades encourage instead of blaming.
 */
object GradeMotivation {
    /** The text for [entry], or null when it isn't a grade from 1 to 6 (points, "np", "+"). */
    fun forEntry(entry: FeedEntryEntity, parent: Boolean): String? {
        if (entry.kind != FeedKind.GRADE) return null
        val grade = gradeOf(entry.title) ?: return null
        val texts = (if (parent) Parent else Student).getValue(grade)
        // Seeded by the grade, so the same grade keeps its text if notified again.
        return texts[Random(entry.refKey.hashCode()).nextInt(texts.size)]
    }

    /** 1 to 6 from a title ending in " – 5+" (see [toFeed]); modifiers don't change the band. */
    internal fun gradeOf(title: String): Int? {
        val value = title.substringAfterLast(" – ", "").trim()
        return GradeValue.matchEntire(value)?.groupValues?.get(1)?.toInt()
    }

    private val GradeValue = Regex("([1-6])[+-]?")

    internal val Student: Map<Int, List<String>> = mapOf(
        6 to listOf(
            "Celująco! Wielkie gratulacje.",
            "Najwyższa półka – brawo!",
            "Mistrzowski wynik!",
            "Szóstka! Ta praca naprawdę się opłaciła.",
            "Wyjątkowe osiągnięcie, gratulacje!",
            "Lepiej się nie da – brawo!",
            "To jest poziom mistrzowski.",
            "Pełen sukces. Tak trzymać!",
            "Wiedza na medal!",
            "Imponujący wynik, gratulacje!",
        ),
        5 to listOf(
            "Świetna robota!",
            "Bardzo dobry wynik – tak trzymać!",
            "Brawo, widać efekty nauki.",
            "Piątka! Gratulacje.",
            "Świetnie poszło!",
            "Solidna wiedza – tak dalej!",
            "Kawał dobrej roboty.",
            "Bardzo dobrze – praca popłaca.",
            "Gratulacje, piękny wynik!",
            "Tak trzymać, jest świetnie!",
        ),
        4 to listOf(
            "Dobra robota!",
            "Dobry wynik – tak dalej!",
            "Solidnie! Jeszcze krok do piątki.",
            "Czwórka to mocny wynik.",
            "Dobrze poszło, gratulacje!",
            "Widać, że materiał jest opanowany.",
            "Niezły wynik – brawo!",
            "Dobra ocena, a stąd już blisko do piątki.",
            "Tak trzymać!",
            "Pewny krok naprzód.",
        ),
        3 to listOf(
            "Jest dobrze, a może być jeszcze lepiej.",
            "Niezła podstawa – warto jeszcze powtórzyć materiał.",
            "Zaliczone, następnym razem może być więcej.",
            "Krok po kroku do lepszych wyników.",
            "Warto dopracować kilka tematów.",
            "Solidna podstawa do dalszej pracy.",
            "Kilka powtórek i będzie jeszcze lepiej.",
            "Dobry punkt wyjścia.",
            "Zaliczone! Teraz pora na więcej.",
            "Niewiele brakuje do lepszej oceny.",
        ),
        2 to listOf(
            "Warto jeszcze przyswoić materiał.",
            "Zaliczone – teraz czas na powtórkę.",
            "Kolejna próba może pójść znacznie lepiej.",
            "Dobra okazja, żeby wrócić do materiału.",
            "Powtórka materiału na pewno się opłaci.",
            "Gorsze dni się zdarzają – następnym razem będzie lepiej.",
            "Warto dopytać nauczyciela o trudniejsze tematy.",
            "Małymi krokami do lepszego wyniku.",
            "Odrobina powtórki i będzie lepiej.",
            "Jest od czego się odbić.",
        ),
        1 to listOf(
            "Gorsze dni się zdarzają – warto spróbować jeszcze raz.",
            "To dobry moment na powtórkę materiału.",
            "Warto zapytać nauczyciela o możliwość poprawy.",
            "Nie warto się poddawać – poprawa jest w zasięgu.",
            "Każda wpadka to lekcja na przyszłość.",
            "Z pomocą nauczyciela łatwiej nadrobić zaległości.",
            "Spokojnie – materiał da się nadrobić.",
            "Najważniejsze to nie odpuszczać.",
            "Warto poprosić o pomoc kogoś z klasy albo nauczyciela.",
            "Następna próba może pójść dużo lepiej.",
        ),
    )

    internal val Parent: Map<Int, List<String>> = mapOf(
        6 to listOf(
            "Wybitny wynik – gratulacje!",
            "Celująca ocena, ogromny sukces.",
            "Zdolne dziecko!",
            "Najwyższa ocena – jest z czego być dumnym.",
            "Wyjątkowe osiągnięcie, warto je docenić.",
            "Szóstka! Warto to razem uczcić.",
            "Mistrzowski wynik.",
            "Powód do dumy!",
            "Wspaniałe osiągnięcie – warto pochwalić.",
            "Ciężka praca przyniosła piękny efekt.",
        ),
        5 to listOf(
            "Zdolne dziecko!",
            "Bardzo dobry wynik – warto pochwalić.",
            "Świetna ocena, jest się z czego cieszyć.",
            "Piątka! Nauka przynosi efekty.",
            "Bardzo dobrze – powód do dumy.",
            "Solidna wiedza, gratulacje!",
            "Dobra passa – warto ją docenić.",
            "Świetny wynik, warto o nim wspomnieć przy kolacji.",
            "Widać efekty pracy – brawo!",
            "Piękny wynik, gratulacje!",
        ),
        4 to listOf(
            "Dobry wynik – warto go docenić.",
            "Czwórka to solidna ocena.",
            "Dobrze idzie!",
            "Nauka przynosi efekty.",
            "Dobra ocena, krok od piątki.",
            "Warto pochwalić za dobry wynik.",
            "Solidna praca, dobry wynik.",
            "Materiał jest dobrze opanowany.",
            "Dobry kierunek – tak dalej.",
            "Powód do zadowolenia.",
        ),
        3 to listOf(
            "Dostateczna ocena – jest na czym budować.",
            "Dobra podstawa, a może być jeszcze lepiej.",
            "Może warto razem przejrzeć materiał?",
            "Kilka powtórek może podnieść kolejną ocenę.",
            "Warto zapytać, który temat sprawił trudność.",
            "Solidna podstawa do dalszej nauki.",
            "Warto docenić wysiłek i zachęcić do więcej.",
            "Niewiele brakuje do lepszego wyniku.",
            "Dobry moment, żeby porozmawiać o nauce.",
            "Zachęta może zdziałać cuda.",
        ),
        2 to listOf(
            "Warto porozmawiać o materiale z dzieckiem.",
            "Może przyda się wspólna powtórka?",
            "Dobra okazja, żeby zapytać, co sprawiło trudność.",
            "Wsparcie w nauce może tu wiele zmienić.",
            "Gorsze dni się zdarzają – warto dodać otuchy.",
            "Może warto zapytać nauczyciela o możliwość poprawy?",
            "Spokojna rozmowa o nauce może pomóc.",
            "Warto wspólnie zaplanować powtórkę.",
            "Słowo otuchy na pewno się przyda.",
            "Kolejna ocena może być lepsza – warto w to wierzyć.",
        ),
        1 to listOf(
            "Warto spokojnie porozmawiać o tym, co poszło nie tak.",
            "Może przyda się pomoc w nadrobieniu materiału?",
            "Warto zapytać nauczyciela o możliwość poprawy.",
            "Teraz najbardziej przyda się wsparcie.",
            "Gorsze dni się zdarzają – słowo otuchy pomoże.",
            "Wspólny plan powtórki może wiele zmienić.",
            "Warto sprawdzić, czy materiał nie sprawia większych trudności.",
            "Rozmowa bez presji może pomóc najbardziej.",
            "Poprawa jest w zasięgu – warto zachęcić.",
            "Dobry moment, żeby zaproponować pomoc.",
        ),
    )
}
