package com.groupswipe.data.repository

import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

/**
 * Awaryjny, wbudowany zestaw REALNYCH miejsc używany, gdy Overpass (OpenStreetMap)
 * nie odpowie lub zwróci pustkę. Dzięki temu kategorie "Jedzenie" i "Hotele"
 * NIGDY nie są puste i nie pokazują pojedynczych atrap.
 *
 * Lista jest tasowana i przycinana do 10 pozycji, więc kolejne gry różnią się
 * zestawem propozycji. Zdjęcia pochodzą ze stabilnego źródła (picsum), które
 * zawsze się ładuje.
 */
object CuratedPlaces {

    private data class Place(val name: String, val desc: String, val rating: Float)

    private val RESTAURANTS = listOf(
        Place("Zapiecek", "Tradycyjne polskie pierogi i kuchnia domowa w klimatycznym wnętrzu.", 4.3f),
        Place("Der Elefant", "Elegancka restauracja przy Placu Bankowym z kuchnią europejską.", 4.4f),
        Place("Youmiko Sushi", "Kameralne miejsce z autorskim, świeżym sushi.", 4.6f),
        Place("Bibenda", "Nowoczesna kuchnia polska i autorskie koktajle.", 4.5f),
        Place("Bez Gwiazdek", "Sezonowe menu degustacyjne z polskich regionów.", 4.6f),
        Place("Restauracja Polka", "Polskie klasyki w wykonaniu Magdy Gessler.", 4.2f),
        Place("Zoni", "Fine dining w dawnej Fabryce Norblina.", 4.4f),
        Place("Nolita", "Kuchnia europejska z gwiazdką, eleganckie dania.", 4.7f),
        Place("Epoka", "Menu inspirowane historycznymi polskimi przepisami.", 4.7f),
        Place("Kraken Rum Bar", "Owoce morza i ryby w marynarskim klimacie.", 4.4f),
        Place("Specjały Regionalne", "Regionalne polskie potrawy w sercu Starówki.", 4.1f),
        Place("Bar Mleczny Prasowy", "Kultowy bar mleczny – tanio i swojsko.", 4.3f)
    )

    private val HOTELS = listOf(
        Place("Hotel Bristol", "Luksusowy, zabytkowy hotel przy Krakowskim Przedmieściu.", 4.7f),
        Place("Raffles Europejski", "Pięciogwiazdkowy hotel z galerią sztuki.", 4.7f),
        Place("Polonia Palace Hotel", "Klasyczny hotel z 1913 r. naprzeciw Pałacu Kultury.", 4.5f),
        Place("Warsaw Marriott Hotel", "Wieżowiec z widokiem na centrum, blisko dworca.", 4.5f),
        Place("InterContinental Warszawa", "Nowoczesny hotel z basenem na 43. piętrze.", 4.6f),
        Place("Hotel Rialto", "Butikowy hotel w stylu art déco.", 4.5f),
        Place("PURO Warszawa Centrum", "Designerski hotel z nowoczesnymi pokojami.", 4.6f),
        Place("Sofitel Victoria", "Elegancki hotel przy Ogrodzie Saskim.", 4.4f),
        Place("Novotel Warszawa Centrum", "Wygodny hotel w samym centrum miasta.", 4.2f),
        Place("Mercure Warszawa Centrum", "Hotel blisko głównych atrakcji i komunikacji.", 4.2f),
        Place("H15 Boutique", "Apartamentowy butikowy hotel w eleganckiej kamienicy.", 4.5f),
        Place("Hampton by Hilton Warsaw City Centre", "Praktyczny hotel ze śniadaniem w cenie.", 4.4f)
    )

    fun restaurants(sessionId: String): List<Proposal> =
        RESTAURANTS.toProposals(sessionId, SessionCategory.RESTAURANTS, "restaurant,food")

    fun hotels(sessionId: String): List<Proposal> =
        HOTELS.toProposals(sessionId, SessionCategory.HOTELS, "hotel,room")

    private fun List<Place>.toProposals(sessionId: String, category: SessionCategory, keywords: String): List<Proposal> =
        this.shuffled().take(10).mapIndexed { i, p ->
            val lock = (p.name.hashCode() and 0xFFFF)
            Proposal(
                id = "${sessionId}_curated_${category.name}_$i",
                sessionId = sessionId,
                title = p.name,
                description = p.desc,
                imageUrl = "https://loremflickr.com/500/750/$keywords?lock=$lock",
                rating = p.rating,
                category = category.displayName,
                detailUrl = "https://www.google.com/search?q=${p.name.replace(" ", "+")}+Warszawa"
            )
        }
}
