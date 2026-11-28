# Versiuni Bolt Calc

Numărul apare în aplicație, lângă titlu ("Bolt Calc v2"), și în fișierul de diagnostic.
Pentru o versiune nouă se schimbă `versionCode` și `versionName` în `app/build.gradle.kts`.

- **v6**: diagnostic mai bun pentru oferte ratate. „Rata de acceptare” din meniul Bolt nu mai e luată drept buton de acceptare (umplea jurnalul și captura). Fișierul de diagnostic păstrează ultimele 3 ecrane care arată a ofertă dar n-au fost citite, ultimele 3 oferte live citite și ultimele 3 programate (ecran brut), iar jurnalul notează când un ecran cu buton de acceptare e tratat ca listă de curse programate. Jurnal: 60 de linii, fără repetări la rând. Ofertele cu opriri intermediare (mai mult de 2 distanțe pe card) se salvează separat în diagnostic. Parsarea ofertelor și calculele rămân neschimbate.
- **v5**: prețul final de pe ecranul „Confirmă tariful” (plata prin aplicație) se folosește în toate calculele după cursă: profit real și lei/km real în Istoric, iar rezumatul de sus (profit, medie lei/km) folosește profitul real unde există. Fără GPS, profitul cu preț final se calculează pe km anunțați. În listă, prețul mare e cel final. Restul ca v4.
- **v4**: card separat „Activări” cu starea serviciului de accesibilitate și a locației (ACTIV / INACTIV / PARȚIAL); butonul de salvare arată SALVAT până la următoarea modificare. Restul ca v3.
- **v3**: iconiță proprie pentru aplicație (indicator roșu/galben/verde pe fond albastru-închis). Restul ca v2.
- **v2**: butoanele din ecranul principal cresc după text (nu se mai taie), numărul versiunii afișat.
  Include: curse programate (listă + detaliu, fără istoric), preț final, ecran Preferate, culori pe adrese evitate,
  acceptare automată dedusă, comutator PORNIT/OPRIT, poziție banner, resetare selectivă, km reali și km în gol, diagnostic în fișier.
- **v1**: toate versiunile de până acum, fără număr afișat.
