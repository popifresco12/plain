package com.plain.app.data

import com.plain.app.model.City
import com.plain.app.model.Plan
import com.plain.app.model.PlanType

object PlansData {

    val barcelonaPlans = listOf(
        Plan(1, "Subir al Tibidabo al atardecer", "Bus hasta el Parque de Atracciones y subida a pie. Vistas 360° de toda la ciudad al atardecer.", "Tibidabo", "0€", PlanType.AMBOS, "2h", "Naturaleza", City.BARCELONA, "🌅"),
        Plan(2, "Tapeo por El Born", "De bar en bar: calles medievales, vinos y tapas. Imprescindible: La Vinya del Senyor.", "El Born", "10-15€", PlanType.PAREJA, "3h", "Gastronomía", City.BARCELONA, "🥘"),
        Plan(3, "Mercat de la Boqueria", "Degustación de jugos, tapas y frutas exóticas. Ideal para ir solo y perderse entre puestos.", "La Rambla", "5-10€", PlanType.SOLO, "1.5h", "Gastronomía", City.BARCELONA, "🍤"),
        Plan(4, "Bunkers del Carmel", "Las mejores vistas de Barcelona gratis. Lleva cerveza y ponte al atardecer.", "Turó de la Rovira", "0€", PlanType.AMBOS, "1.5h", "Naturaleza", City.BARCELONA, "📸"),
        Plan(5, "Ruta graffiti por el Raval", "Arte urbano, murales enormes y galerías callejeras. Recorrido autoguiado.", "El Raval", "0€", PlanType.SOLO, "2h", "Cultura", City.BARCELONA, "🎨"),
        Plan(6, "Picnic en la Ciutadella", "El parque más bonito de la ciudad. Ideal para llevar queso, vino y manta.", "Parc de la Ciutadella", "5€", PlanType.PAREJA, "2h", "Naturaleza", City.BARCELONA, "🧺"),
        Plan(7, "Museo Picasso (domingo gratis)", "Entrada gratuita desde las 15h los domingos. Una de las mejores colecciones del mundo.", "El Born", "0€", PlanType.SOLO, "2h", "Cultura", City.BARCELONA, "🖼️"),
        Plan(8, "Baño en la Barceloneta + vermut", "Día de playa urbana con baño y luego vermut en un chiringuito.", "Barceloneta", "0€", PlanType.AMBOS, "3h", "Naturaleza", City.BARCELONA, "🏖️"),
        Plan(9, "Ruta modernista por el Eixample", "Recorrido gratuito autoguiado: Casa Batlló, La Pedrera, Sagrada Família desde fuera.", "Eixample", "0€", PlanType.SOLO, "2.5h", "Cultura", City.BARCELONA, "🏛️"),
        Plan(10, "Mercat dels Encants", "Mercadillo de domingo con gangas, antigüedades y objetos únicos.", "Glòries", "0€", PlanType.AMBOS, "2h", "Compras", City.BARCELONA, "🛍️"),
        Plan(11, "Pasear por el Laberinto de Horta", "El jardín laberíntico más antiguo de Barcelona. Entrada 3€.", "Horta", "3€", PlanType.PAREJA, "1.5h", "Naturaleza", City.BARCELONA, "🌳"),
        Plan(12, "Ruta gótica + calles escondidas", "Descubre el Barri Gòtic: el Puente del Obispo, la Catedral y plazas secretas.", "Barri Gòtic", "0€", PlanType.SOLO, "2h", "Cultura", City.BARCELONA, "📷"),
        Plan(13, "Montjuïc: jardins + castillo", "Subida a pie o en teleférico, jardines botánicos y vistas al puerto. El castillo es gratis.", "Montjuïc", "0€", PlanType.SOLO, "3h", "Naturaleza", City.BARCELONA, "🏰"),
        Plan(14, "Sónar de día (jazz + electrónica)", "Entrada de día al Sónar. Música, arte digital y ambiente único.", "Fira Gran Via", "12€", PlanType.AMBOS, "4h", "Música", City.BARCELONA, "🎧")
    )

    val villenaPlans = listOf(
        Plan(101, "Castillo de la Atalaya", "Impresionante castillo medieval con vistas a todo el Valle. Visita guiada 3€.", "Castillo", "3€", PlanType.AMBOS, "1.5h", "Cultura", City.VILLENA, "🏰"),
        Plan(102, "Ruta senderismo Sierra de la Villa", "Ruta circular de 6km por la sierra con vistas al castillo y al valle.", "Sierra de la Villa", "0€", PlanType.SOLO, "3h", "Naturaleza", City.VILLENA, "🥾"),
        Plan(103, "Paseo casco antiguo + tapas", "Calles empedradas, plazas con encanto y tapeo de calidad a precios de pueblo.", "Casco antiguo", "10€", PlanType.PAREJA, "2h", "Gastronomía", City.VILLENA, "🥘"),
        Plan(104, "Street Food Market", "Comida internacional, música en directo y artesanía. Entrada gratuita.", "Recinto Ferial", "0€", PlanType.AMBOS, "3h", "Gastronomía", City.VILLENA, "🍔"),
        Plan(105, "Ruta en bici por Las Virtudes", "Ruta fácil en bici hasta el Santuario de Las Virtudes, rodeado de naturaleza.", "Las Virtudes", "0€", PlanType.SOLO, "2h", "Deporte", City.VILLENA, "🚴"),
        Plan(106, "Mercado de diseño y artesanía", "Puestos de cerámica, ilustración y diseño local. Coincide con el Street Food Market.", "Recinto Ferial", "0€", PlanType.PAREJA, "1h", "Compras", City.VILLENA, "🎨"),
        Plan(107, "Día de piscina natural / Pantano", "Baño en el Pantano de Villena. Lleva nevera y sombrilla.", "Pantano de Villena", "0€", PlanType.SOLO, "Todo el día", "Naturaleza", City.VILLENA, "🏊"),
        Plan(108, "Teatro Chapí", "Obra de teatro o cine de cartelera en el teatro histórico de la ciudad.", "Teatro Chapí", "5-8€", PlanType.PAREJA, "2h", "Cultura", City.VILLENA, "🎭"),
        Plan(109, "Fiestas del Medievo", "Mercado medieval, justas, música y animación callejera. Gratis en la calle.", "Centro histórico", "0€", PlanType.AMBOS, "4h", "Cultura", City.VILLENA, "⚔️"),
        Plan(110, "Cata de vinos en bodega local", "Degustación de vinos de la DOP Alicante en pequeñas bodegas familiares.", "Bodega local", "5-10€", PlanType.PAREJA, "1.5h", "Gastronomía", City.VILLENA, "🍷")
    )

    fun getPlansFor(city: City): List<Plan> = when (city) {
        City.BARCELONA -> barcelonaPlans
        City.VILLENA -> villenaPlans
    }
}
