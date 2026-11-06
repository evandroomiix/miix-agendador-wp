package com.miix.agendadorwp.service

import java.util.Calendar

data class NationalHoliday(
    val name: String,
    val day: Int,
    val month: Int, // 1-12
    val description: String,
    val suggestedMessage: String
)

object HolidayCalendarHelper {

    val BRAZIL_HOLIDAYS_2026_2027 = listOf(
        NationalHoliday("Ano Novo / Confraternização Universal", 1, 1, "Feriado Nacional", "Feliz Ano Novo! Que este novo ano seja repleto de conquistas, saúde e muito sucesso! ✨🥂"),
        NationalHoliday("Carnaval", 17, 2, "Data Comemorativa", "Aproveite os dias de folia e descanso com muita alegria e segurança! 🎉🎭"),
        NationalHoliday("Dia Internacional da Mulher", 8, 3, "Comemorativo", "Parabéns a todas as mulheres pela força, dedicação e inspiração diária! 🌸💐"),
        NationalHoliday("Páscoa", 5, 4, "Feriado Religioso", "Uma abençoada Páscoa para você e toda a sua família, repleta de união, amor e paz! 🍫🕊️"),
        NationalHoliday("Tiradentes", 21, 4, "Feriado Nacional", "Lembramos hoje a luta pela liberdade e independência da nossa nação. Bom feriado! 🇧🇷"),
        NationalHoliday("Dia do Trabalho", 1, 5, "Feriado Nacional", "Parabéns a todos os trabalhadores que com dedicação constroem um futuro melhor todos os dias! 💼🛠️"),
        NationalHoliday("Dia das Mães", 10, 5, "Comemorativo (2º Domingo)", "Feliz Dia das Mães! Uma homenagem especial àquela que dedica seu amor incondicional! ❤️🌷"),
        NationalHoliday("Dia dos Namorados", 12, 6, "Comemorativo", "Feliz Dia dos Namorados! Que o amor continue sendo o melhor motivo para comemorar! 💖🌹"),
        NationalHoliday("Corpus Christi", 4, 6, "Feriado Religioso", "Desejamos um feriado abençoado de muita reflexão, fé e renovação espiritual. 🙏✨"),
        NationalHoliday("Dia dos Pais", 9, 8, "Comemorativo (2º Domingo)", "Feliz Dia dos Pais! Nosso carinho e gratidão a todos os pais pelo exemplo e dedicação! 👔💙"),
        NationalHoliday("Independência do Brasil", 7, 9, "Feriado Nacional", "7 de Setembro: Orgulho da nossa pátria e da nossa história. Bom feriado a todos! 🇧🇷✨"),
        NationalHoliday("Nossa Senhora Aparecida / Dia das Crianças", 12, 10, "Feriado Nacional", "Dia de fé, bênçãos e da alegria pura das nossas crianças! Viva Nossa Senhora e os pequenos! 🎈👧👦"),
        NationalHoliday("Finados", 2, 11, "Feriado Nacional", "Um dia de respeito, oração e saudades daqueles que sempre viverão em nossas memórias. 🕊️🕯️"),
        NationalHoliday("Proclamação da República", 15, 11, "Feriado Nacional", "Celebramos hoje a força e os valores da nossa República. Bom descanso! 🇧🇷"),
        NationalHoliday("Dia da Consciência Negra", 20, 11, "Feriado Nacional", "Dia de reflexão, igualdade, respeito e valorização da cultura e história afro-brasileira. ✊🏿✊🏾"),
        NationalHoliday("Black Friday", 27, 11, "Campanha Comercial", "Chegou a nossa Black Friday! Ofertas imperdíveis com descontos exclusivos hoje! Não perca! 🏷️🔥"),
        NationalHoliday("Natal", 25, 12, "Feriado Nacional", "Feliz Natal! Que a paz, a harmonia e o amor de Cristo iluminem o seu lar e de sua família! 🎄🎁✨"),
        NationalHoliday("Véspera de Ano Novo", 31, 12, "Fim de Ano", "Gratidão pelo ano que passou e muita esperança para o ano que está chegando! Boas Festas! 🎆🎉")
    )

    fun getHolidayTimestamp(holiday: NationalHoliday, year: Int = Calendar.getInstance().get(Calendar.YEAR)): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, holiday.month - 1)
            set(Calendar.DAY_OF_MONTH, holiday.day)
            set(Calendar.HOUR_OF_DAY, 9) // 9:00 da manhã
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
