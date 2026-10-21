package am.veolia.bot.parser;

import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.OutageAnnouncement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutageAnnouncementParserTest {

    private final OutageAnnouncementParser parser = new OutageAnnouncementParser();

    @Test
    void parsesTheCanonicalShengavitExample() {
        String text = """
                Վթարային ջրանջատում Երևանի Շենգավիթ վարչական շրջանում օգոստոսի 16-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է իր հաճախորդներին և սպառողներին, որ վթարային
                աշխատանքներով պայմանավորված ս.թ օգոստոսի 16-ին ժամը 14:00-16:00-ն կդադարեցվի
                Մանթաշյան 6-12 զույգ համարի շենքերի ջրամատակարարումը:
                Ընկերությունը հայցում է սպառողների ներողամտությունը պատճառված անհանգստության
                և կանխավ շնորհակալություն հայտնում ըմբռնման համար:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1001", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        OutageAnnouncement announcement = result.get();
        assertThat(announcement.postId()).isEqualTo("VeoliaJur/1001");
        assertThat(announcement.district()).isEqualTo("Երևանի Շենգավիթ վարչական շրջանում");
        assertThat(announcement.date()).isEqualTo("օգոստոսի 16");
        assertThat(announcement.startTime()).isEqualTo("14:00");
        assertThat(announcement.endTime()).isEqualTo("16:00");
        assertThat(announcement.streets()).containsExactly("Մանթաշյան 6-12 զույգ համարի շենքերի");
        assertThat(announcement.rawText()).isEqualTo(text);
    }

    @Test
    void parsesMultipleCommaSeparatedStreets() {
        String text = """
                Վթարային ջրանջատում Երևանի Կենտրոն վարչական շրջանում սեպտեմբերի 3-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ սեպտեմբերի 3-ին ժամը 10:00-13:00-ն կդադարեցվի Թումանյան 10, Սայաթ-Նովա 5
                և Աբովյան 22 ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1002", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().streets()).containsExactly("Թումանյան 10", "Սայաթ-Նովա 5", "Աբովյան 22");
    }

    @Test
    void parsesAnOtherCityAnnouncement() {
        String text = """
                Վթարային ջրանջատում Աբովյան քաղաքում հունվարի 5-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ հունվարի 5-ին ժամը 09:00-12:00-ն կդադարեցվի Երևանյան փողոցի ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1003", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().district()).isEqualTo("Աբովյան քաղաքում");
        assertThat(result.get().streets()).containsExactly("Երևանյան փողոցի");
    }

    @Test
    void parsesAnOpenEndedTimeWithNoEndTime() {
        String text = """
                Վթարային ջրանջատում Երևանի Արաբկիր վարչական շրջանում մարտի 2-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ մարտի 2-ին ժամը 08:30-ից կդադարեցվի Կոմիտասի պողոտա 40 ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1004", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().startTime()).isEqualTo("08:30");
        assertThat(result.get().endTime()).isNull();
        assertThat(result.get().streets()).containsExactly("Կոմիտասի պողոտա 40");
    }

    /** The real multi-area, two-day post from 2026-10-21 that the old parser skipped. */
    static final String MULTI_AREA_POST = """
            Վթարային ջրանջատում Երևանում, Կոտայքի և Արագածոտնի մարզերում հոկտեմբերի 21/22-ին

            «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է իր հաճախորդներին և սպառողներին, որ վթարի վերացման աշխատանքներով պայմանավորված, ս.թ. հոկտեմբերի 21-ին ժամը 11:00-ից մինչև հոկտեմբերի 22-ը ժամը 11:00-ն կդադարեցվի հետևյալ տարածքների ջրամատակարարումը.
            Դավթաշեն վարչական շրջանը՝ ամբողջությամբ,
            Աջափնյակ վարչական շրջանի՝ Նազարբեկյան, Նորաշեն, Գ-1 թաղամասերի, Ալիխանյան Եղբայրների,Մարգարյան, Ֆուչիկի, Բաշինջաղյան, Ջանիբեկյան, Շիրազի, Մազմանյան փողոցները իրենց նրբանցքներով, Հալաբյան 22-38 համարները իրենց կոտորակներով,
            Կոտայքի մարզի՝ Զովունի գյուղի 21-37 փողոցների, Պռոշյան, Քասախ գյուղերի, Եղվարդ քաղաքի,
            Արագածոտնի մարզի՝ Նոր Երզնկա, Սաղմոսավան, Ուշի գյուղերի:

            Ընկերությունը հայցում է սպառողների ներողամտությունը պատճառված անհանգստության և կանխավ շնորհակալություն հայտնում ըմբռնման համար:""";

    @Test
    void parsesAMultiAreaTwoDayPostIntoOneAreaPerLine() {
        Optional<OutageAnnouncement> result = parser.parse(new ChannelPost("VeoliaJur/2000", MULTI_AREA_POST));

        assertThat(result).isPresent();
        OutageAnnouncement announcement = result.get();
        assertThat(announcement.date()).isEqualTo("հոկտեմբերի 21/22");
        assertThat(announcement.startTime()).isEqualTo("11:00");
        assertThat(announcement.endTime()).isEqualTo("11:00");
        assertThat(announcement.areas()).extracting(OutageAnnouncement.Area::scope).containsExactly(
                "Դավթաշեն վարչական շրջանը",
                "Աջափնյակ վարչական շրջանի",
                "Կոտայքի մարզի",
                "Արագածոտնի մարզի");
        assertThat(announcement.areas()).extracting(OutageAnnouncement.Area::whole)
                .containsExactly(true, false, false, false);
        assertThat(announcement.areas().get(1).streets())
                .contains("Նազարբեկյան", "Ալիխանյան Եղբայրների", "Մարգարյան", "Հալաբյան 22-38 համարները իրենց կոտորակներով");
        assertThat(announcement.areas().get(2).streets())
                .containsExactly("Զովունի գյուղի 21-37 փողոցների", "Պռոշյան", "Քասախ գյուղերի", "Եղվարդ քաղաքի");
        assertThat(announcement.areas().get(3).streets()).containsExactly("Նոր Երզնկա", "Սաղմոսավան", "Ուշի գյուղերի");
        assertThat(announcement.streets()).doesNotContain("Ընկերությունը հայցում է սպառողների ներողամտությունը");
    }

    @Test
    void anAreaLineWithoutARegionNameKeepsTheTitleInItsScope() {
        String text = """
                Վթարային ջրանջատում Կոտայքի մարզում մայիսի 4-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ ս.թ. մայիսի 4-ին ժամը 10:00-18:00-ն կդադարեցվի
                հետևյալ տարածքների ջրամատակարարումը.
                Զովունի գյուղի՝ 1-5 փողոցներ,
                Եղվարդ քաղաքի՝ Երևանյան փողոց:""";

        OutageAnnouncement announcement = parser.parse(new ChannelPost("VeoliaJur/2001", text)).orElseThrow();

        assertThat(announcement.areas()).extracting(OutageAnnouncement.Area::scope).containsExactly(
                "Զովունի գյուղի Կոտայքի մարզում",
                "Եղվարդ քաղաքի Կոտայքի մարզում");
        assertThat(announcement.endTime()).isEqualTo("18:00");
    }

    @Test
    void skipsUnrelatedPostsRatherThanThrowing() {
        ChannelPost post = new ChannelPost("VeoliaJur/1005", "Շնորհավոր Նոր տարի բոլորիդ:");

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsPostsMissingTheTimeStreetsClause() {
        ChannelPost post = new ChannelPost("VeoliaJur/1006",
                "Վթարային ջրանջատում Երևանի Նոր Նորք վարչական շրջանում մայիսի 1-ին\n\n"
                        + "Աշխատանքները կավարտվեն մոտակա օրերին:");

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isEmpty();
    }
}
