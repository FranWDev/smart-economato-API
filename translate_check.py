#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pattern-based professional translation engine for ValidationMessages.
Reads ES base, translates every value by pattern matching, writes all languages.
"""
import os, re

VDIR = "src/main/resources/i18n/validation/"

# Read Spanish base
with open(os.path.join(VDIR, "ValidationMessages_es.properties"), "r", encoding="utf-8") as f:
    es_lines = [l.strip() for l in f if "=" in l and not l.startswith("#")]

es_pairs = []
for line in es_lines:
    k, v = line.split("=", 1)
    es_pairs.append((k.strip(), v.strip()))

print(f"Read {len(es_pairs)} keys from ES base")

# ── Translation dictionaries per language ──
# Each lang has: phrases dict that maps Spanish phrases/patterns to target lang

LANG_META = {
    "en": "English", "de": "Deutsch", "fr": "Français", "it": "Italiano",
    "pt": "Português", "ca": "Català", "eu": "Euskara", "gl": "Galego",
    "nl": "Nederlands", "sv": "Svenska", "no": "Norsk", "da": "Dansk",
    "fi": "Suomi", "pl": "Polski", "cs": "Čeština", "sk": "Slovenčina",
    "hu": "Magyar", "ro": "Română", "hr": "Hrvatski", "sl": "Slovenščina",
    "bg": "Български", "is": "Íslenska",
    "et": "Eesti", "lv": "Latviešu", "lt": "Lietuvių",
    "el": "Ελληνικά", "mt": "Malti", "ga": "Gaeilge",
}

# Common phrase fragments in Spanish and their translations
# We'll do exact-match first, then pattern match
def build_translations():
    """Returns dict: lang -> { spanish_value: translated_value }"""
    result = {}

    # For each language, provide direct key->value translations
    # We'll handle this by building a comprehensive mapping

    # Common Spanish fragments and their translations per language
    # Structure: fragment_es -> { lang: fragment_translated }
    REQUIRED_M = {  # masculine "es obligatorio"
        "en": "is required", "de": "ist erforderlich", "fr": "est obligatoire",
        "it": "è obbligatorio", "pt": "é obrigatório", "ca": "és obligatori",
        "eu": "beharrezkoa da", "gl": "é obrigatorio",
        "nl": "is verplicht", "sv": "krävs", "no": "er påkrevd", "da": "er påkrævet",
        "fi": "on pakollinen", "pl": "jest wymagany", "cs": "je povinný", "sk": "je povinný",
        "hu": "kötelező", "ro": "este obligatoriu", "hr": "je obavezan", "sl": "je obvezen",
        "bg": "е задължително", "is": "er nauðsynlegt",
        "et": "on kohustuslik", "lv": "ir obligāts", "lt": "yra privalomas",
        "el": "είναι υποχρεωτικό", "mt": "huwa obbligatorju", "ga": "tá riachtanach",
    }
    REQUIRED_F = {  # feminine "es obligatoria"
        "en": "is required", "de": "ist erforderlich", "fr": "est obligatoire",
        "it": "è obbligatoria", "pt": "é obrigatória", "ca": "és obligatòria",
        "eu": "beharrezkoa da", "gl": "é obrigatoria",
        "nl": "is verplicht", "sv": "krävs", "no": "er påkrevd", "da": "er påkrævet",
        "fi": "on pakollinen", "pl": "jest wymagana", "cs": "je povinná", "sk": "je povinná",
        "hu": "kötelező", "ro": "este obligatorie", "hr": "je obavezna", "sl": "je obvezna",
        "bg": "е задължителна", "is": "er nauðsynlegt",
        "et": "on kohustuslik", "lv": "ir obligāta", "lt": "yra privaloma",
        "el": "είναι υποχρεωτικό", "mt": "hija obbligatorja", "ga": "tá riachtanach",
    }

    # Subject translations (Spanish -> per-lang)
    SUBJECTS = {
        "El nombre del alérgeno": {
            "en": "The allergen name", "de": "Der Allergenname", "fr": "Le nom de l'allergène",
            "it": "Il nome dell'allergene", "pt": "O nome do alergénio", "ca": "El nom de l'al·lergen",
            "eu": "Alergenoaren izena", "gl": "O nome do alérxeno",
            "nl": "De allergeennaam", "sv": "Allergennamnet", "no": "Allergennavnet", "da": "Allergennavnet",
            "fi": "Allergeenin nimi", "pl": "Nazwa alergenu", "cs": "Název alergenu", "sk": "Názov alergénu",
            "hu": "Az allergén neve", "ro": "Numele alergenului", "hr": "Naziv alergena", "sl": "Ime alergena",
            "bg": "Името на алергена", "is": "Nafn ofnæmisvaldans",
            "et": "Allergeeni nimi", "lv": "Alergēna nosaukums", "lt": "Alergeno pavadinimas",
            "el": "Το όνομα του αλλεργιογόνου", "mt": "L-isem tal-allerġen", "ga": "Ainm an ailléirgín",
        },
        "La nueva contraseña": {
            "en": "The new password", "de": "Das neue Passwort", "fr": "Le nouveau mot de passe",
            "it": "La nuova password", "pt": "A nova palavra-passe", "ca": "La nova contrasenya",
            "eu": "Pasahitz berria", "gl": "O novo contrasinal",
            "nl": "Het nieuwe wachtwoord", "sv": "Det nya lösenordet", "no": "Det nye passordet", "da": "Den nye adgangskode",
            "fi": "Uusi salasana", "pl": "Nowe hasło", "cs": "Nové heslo", "sk": "Nové heslo",
            "hu": "Az új jelszó", "ro": "Noua parolă", "hr": "Nova lozinka", "sl": "Novo geslo",
            "bg": "Новата парола", "is": "Nýja lykilorðið",
            "et": "Uus parool", "lv": "Jaunā parole", "lt": "Naujas slaptažodis",
            "el": "Ο νέος κωδικός", "mt": "Il-password l-ġdida", "ga": "An pasfhocal nua",
        },
        "El nombre de usuario": {
            "en": "Username", "de": "Der Benutzername", "fr": "Le nom d'utilisateur",
            "it": "Il nome utente", "pt": "O nome de utilizador", "ca": "El nom d'usuari",
            "eu": "Erabiltzaile izena", "gl": "O nome de usuario",
            "nl": "De gebruikersnaam", "sv": "Användarnamnet", "no": "Brukernavnet", "da": "Brugernavnet",
            "fi": "Käyttäjänimi", "pl": "Nazwa użytkownika", "cs": "Uživatelské jméno", "sk": "Používateľské meno",
            "hu": "A felhasználónév", "ro": "Numele de utilizator", "hr": "Korisničko ime", "sl": "Uporabniško ime",
            "bg": "Потребителското име", "is": "Notendanafnið",
            "et": "Kasutajanimi", "lv": "Lietotājvārds", "lt": "Vartotojo vardas",
            "el": "Το όνομα χρήστη", "mt": "L-isem tal-utent", "ga": "An t-ainm úsáideora",
        },
        "La contraseña": {
            "en": "The password", "de": "Das Passwort", "fr": "Le mot de passe",
            "it": "La password", "pt": "A palavra-passe", "ca": "La contrasenya",
            "eu": "Pasahitza", "gl": "O contrasinal",
            "nl": "Het wachtwoord", "sv": "Lösenordet", "no": "Passordet", "da": "Adgangskoden",
            "fi": "Salasana", "pl": "Hasło", "cs": "Heslo", "sk": "Heslo",
            "hu": "A jelszó", "ro": "Parola", "hr": "Lozinka", "sl": "Geslo",
            "bg": "Паролата", "is": "Lykilorðið",
            "et": "Parool", "lv": "Parole", "lt": "Slaptažodis",
            "el": "Ο κωδικός πρόσβασης", "mt": "Il-password", "ga": "An pasfhocal",
        },
        "El producto": {
            "en": "The product", "de": "Das Produkt", "fr": "Le produit",
            "it": "Il prodotto", "pt": "O produto", "ca": "El producte",
            "eu": "Produktua", "gl": "O produto",
            "nl": "Het product", "sv": "Produkten", "no": "Produktet", "da": "Produktet",
            "fi": "Tuote", "pl": "Produkt", "cs": "Produkt", "sk": "Produkt",
            "hu": "A termék", "ro": "Produsul", "hr": "Proizvod", "sl": "Izdelek",
            "bg": "Продуктът", "is": "Varan",
            "et": "Toode", "lv": "Produkts", "lt": "Produktas",
            "el": "Το προϊόν", "mt": "Il-prodott", "ga": "An táirge",
        },
        "La cantidad": {
            "en": "The quantity", "de": "Die Menge", "fr": "La quantité",
            "it": "La quantità", "pt": "A quantidade", "ca": "La quantitat",
            "eu": "Kopurua", "gl": "A cantidade",
            "nl": "De hoeveelheid", "sv": "Kvantiteten", "no": "Mengden", "da": "Mængden",
            "fi": "Määrä", "pl": "Ilość", "cs": "Množství", "sk": "Množstvo",
            "hu": "A mennyiség", "ro": "Cantitatea", "hr": "Količina", "sl": "Količina",
            "bg": "Количеството", "is": "Magnið",
            "et": "Kogus", "lv": "Daudzums", "lt": "Kiekis",
            "el": "Η ποσότητα", "mt": "Il-kwantità", "ga": "An chainníocht",
        },
        "El tipo de movimiento": {
            "en": "The movement type", "de": "Die Bewegungsart", "fr": "Le type de mouvement",
            "it": "Il tipo di movimento", "pt": "O tipo de movimento", "ca": "El tipus de moviment",
            "eu": "Mugimendu mota", "gl": "O tipo de movemento",
            "nl": "Het type beweging", "sv": "Rörelsetypen", "no": "Bevegelsestypen", "da": "Bevægelsestypen",
            "fi": "Liiketyyppi", "pl": "Typ ruchu", "cs": "Typ pohybu", "sk": "Typ pohybu",
            "hu": "A mozgástípus", "ro": "Tipul de mișcare", "hr": "Vrsta kretanja", "sl": "Vrsta gibanja",
            "bg": "Типът на движение", "is": "Tegund hreyfingar",
            "et": "Liikumise tüüp", "lv": "Kustības tips", "lt": "Judėjimo tipas",
            "el": "Ο τύπος κίνησης", "mt": "It-tip ta' moviment", "ga": "Cineál na gluaiseachta",
        },
        "El ID del producto": {
            "en": "The product ID", "de": "Die Produkt-ID", "fr": "L'identifiant du produit",
            "it": "L'ID del prodotto", "pt": "O ID do produto", "ca": "L'ID del producte",
            "eu": "Produktuaren IDa", "gl": "O ID do produto",
            "nl": "Het product-ID", "sv": "Produkt-ID", "no": "Produkt-ID", "da": "Produkt-ID",
            "fi": "Tuotteen tunnus", "pl": "ID produktu", "cs": "ID produktu", "sk": "ID produktu",
            "hu": "A termék azonosítója", "ro": "ID-ul produsului", "hr": "ID proizvoda", "sl": "ID izdelka",
            "bg": "ID на продукта", "is": "Auðkenni vöru",
            "et": "Toote ID", "lv": "Produkta ID", "lt": "Produkto ID",
            "el": "Το ID του προϊόντος", "mt": "L-ID tal-prodott", "ga": "ID an táirge",
        },
        "El ID del usuario": {
            "en": "The user ID", "de": "Die Benutzer-ID", "fr": "L'identifiant de l'utilisateur",
            "it": "L'ID dell'utente", "pt": "O ID do utilizador", "ca": "L'ID de l'usuari",
            "eu": "Erabiltzailearen IDa", "gl": "O ID do usuario",
            "nl": "Het gebruikers-ID", "sv": "Användar-ID", "no": "Bruker-ID", "da": "Bruger-ID",
            "fi": "Käyttäjän tunnus", "pl": "ID użytkownika", "cs": "ID uživatele", "sk": "ID používateľa",
            "hu": "A felhasználó azonosítója", "ro": "ID-ul utilizatorului", "hr": "ID korisnika", "sl": "ID uporabnika",
            "bg": "ID на потребителя", "is": "Auðkenni notanda",
            "et": "Kasutaja ID", "lv": "Lietotāja ID", "lt": "Vartotojo ID",
            "el": "Το ID του χρήστη", "mt": "L-ID tal-utent", "ga": "ID an úsáideora",
        },
        "El ID de la receta": {
            "en": "The recipe ID", "de": "Die Rezept-ID", "fr": "L'identifiant de la recette",
            "it": "L'ID della ricetta", "pt": "O ID da receita", "ca": "L'ID de la recepta",
            "eu": "Errezetaren IDa", "gl": "O ID da receita",
            "nl": "Het recept-ID", "sv": "Recept-ID", "no": "Oppskrift-ID", "da": "Opskrift-ID",
            "fi": "Reseptin tunnus", "pl": "ID przepisu", "cs": "ID receptu", "sk": "ID receptu",
            "hu": "A recept azonosítója", "ro": "ID-ul rețetei", "hr": "ID recepta", "sl": "ID recepta",
            "bg": "ID на рецептата", "is": "Auðkenni uppskriftar",
            "et": "Retsepti ID", "lv": "Receptes ID", "lt": "Recepto ID",
            "el": "Το ID της συνταγής", "mt": "L-ID tar-riċetta", "ga": "ID an oidis",
        },
        "El ID de la orden": {
            "en": "The order ID", "de": "Die Bestell-ID", "fr": "L'identifiant de la commande",
            "it": "L'ID dell'ordine", "pt": "O ID da encomenda", "ca": "L'ID de la comanda",
            "eu": "Eskaeraren IDa", "gl": "O ID do pedido",
            "nl": "Het bestel-ID", "sv": "Order-ID", "no": "Ordre-ID", "da": "Ordre-ID",
            "fi": "Tilauksen tunnus", "pl": "ID zamówienia", "cs": "ID objednávky", "sk": "ID objednávky",
            "hu": "A rendelés azonosítója", "ro": "ID-ul comenzii", "hr": "ID narudžbe", "sl": "ID naročila",
            "bg": "ID на поръчката", "is": "Auðkenni pöntunar",
            "et": "Tellimuse ID", "lv": "Pasūtījuma ID", "lt": "Užsakymo ID",
            "el": "Το ID της παραγγελίας", "mt": "L-ID tal-ordni", "ga": "ID an ordaithe",
        },
        "El nombre del producto": {
            "en": "The product name", "de": "Der Produktname", "fr": "Le nom du produit",
            "it": "Il nome del prodotto", "pt": "O nome do produto", "ca": "El nom del producte",
            "eu": "Produktuaren izena", "gl": "O nome do produto",
            "nl": "De productnaam", "sv": "Produktnamnet", "no": "Produktnavnet", "da": "Produktnavnet",
            "fi": "Tuotteen nimi", "pl": "Nazwa produktu", "cs": "Název produktu", "sk": "Názov produktu",
            "hu": "A termék neve", "ro": "Numele produsului", "hr": "Naziv proizvoda", "sl": "Ime izdelka",
            "bg": "Името на продукта", "is": "Nafn vöru",
            "et": "Toote nimi", "lv": "Produkta nosaukums", "lt": "Produkto pavadinimas",
            "el": "Το όνομα του προϊόντος", "mt": "L-isem tal-prodott", "ga": "Ainm an táirge",
        },
        "El nombre de la receta": {
            "en": "The recipe name", "de": "Der Rezeptname", "fr": "Le nom de la recette",
            "it": "Il nome della ricetta", "pt": "O nome da receita", "ca": "El nom de la recepta",
            "eu": "Errezetaren izena", "gl": "O nome da receita",
            "nl": "De receptnaam", "sv": "Receptnamnet", "no": "Oppskriftsnavnet", "da": "Opskriftsnavnet",
            "fi": "Reseptin nimi", "pl": "Nazwa przepisu", "cs": "Název receptu", "sk": "Názov receptu",
            "hu": "A recept neve", "ro": "Numele rețetei", "hr": "Naziv recepta", "sl": "Ime recepta",
            "bg": "Името на рецептата", "is": "Nafn uppskriftar",
            "et": "Retsepti nimi", "lv": "Receptes nosaukums", "lt": "Recepto pavadinimas",
            "el": "Το όνομα της συνταγής", "mt": "L-isem tar-riċetta", "ga": "Ainm an oidis",
        },
        "El nombre del proveedor": {
            "en": "The supplier name", "de": "Der Lieferantenname", "fr": "Le nom du fournisseur",
            "it": "Il nome del fornitore", "pt": "O nome do fornecedor", "ca": "El nom del proveïdor",
            "eu": "Hornitzailearen izena", "gl": "O nome do provedor",
            "nl": "De leveranciersnaam", "sv": "Leverantörsnamnet", "no": "Leverandørnavnet", "da": "Leverandørnavnet",
            "fi": "Toimittajan nimi", "pl": "Nazwa dostawcy", "cs": "Název dodavatele", "sk": "Názov dodávateľa",
            "hu": "A szállító neve", "ro": "Numele furnizorului", "hr": "Naziv dobavljača", "sl": "Ime dobavitelja",
            "bg": "Името на доставчика", "is": "Nafn birgis",
            "et": "Tarnija nimi", "lv": "Piegādātāja nosaukums", "lt": "Tiekėjo pavadinimas",
            "el": "Το όνομα του προμηθευτή", "mt": "L-isem tal-fornitur", "ga": "Ainm an tsoláthraí",
        },
        "El nombre": {
            "en": "The name", "de": "Der Name", "fr": "Le nom",
            "it": "Il nome", "pt": "O nome", "ca": "El nom",
            "eu": "Izena", "gl": "O nome",
            "nl": "De naam", "sv": "Namnet", "no": "Navnet", "da": "Navnet",
            "fi": "Nimi", "pl": "Nazwa", "cs": "Název", "sk": "Názov",
            "hu": "A név", "ro": "Numele", "hr": "Naziv", "sl": "Ime",
            "bg": "Името", "is": "Nafnið",
            "et": "Nimi", "lv": "Nosaukums", "lt": "Pavadinimas",
            "el": "Το όνομα", "mt": "L-isem", "ga": "An t-ainm",
        },
        "El usuario": {
            "en": "The username", "de": "Der Benutzername", "fr": "Le nom d'utilisateur",
            "it": "Il nome utente", "pt": "O utilizador", "ca": "L'usuari",
            "eu": "Erabiltzailea", "gl": "O usuario",
            "nl": "De gebruiker", "sv": "Användaren", "no": "Brukeren", "da": "Brugeren",
            "fi": "Käyttäjä", "pl": "Użytkownik", "cs": "Uživatel", "sk": "Používateľ",
            "hu": "A felhasználó", "ro": "Utilizatorul", "hr": "Korisnik", "sl": "Uporabnik",
            "bg": "Потребителят", "is": "Notandinn",
            "et": "Kasutaja", "lv": "Lietotājs", "lt": "Vartotojas",
            "el": "Ο χρήστης", "mt": "L-utent", "ga": "An t-úsáideoir",
        },
        "El código del producto": {
            "en": "The product code", "de": "Der Produktcode", "fr": "Le code produit",
            "it": "Il codice prodotto", "pt": "O código do produto", "ca": "El codi del producte",
            "eu": "Produktuaren kodea", "gl": "O código do produto",
            "nl": "De productcode", "sv": "Produktkoden", "no": "Produktkoden", "da": "Produktkoden",
            "fi": "Tuotekoodi", "pl": "Kod produktu", "cs": "Kód produktu", "sk": "Kód produktu",
            "hu": "A termékkód", "ro": "Codul produsului", "hr": "Šifra proizvoda", "sl": "Šifra izdelka",
            "bg": "Кодът на продукта", "is": "Vörukóði",
            "et": "Toote kood", "lv": "Produkta kods", "lt": "Produkto kodas",
            "el": "Ο κωδικός προϊόντος", "mt": "Il-kodiċi tal-prodott", "ga": "Cód an táirge",
        },
        "La unidad de medida": {
            "en": "The unit of measure", "de": "Die Maßeinheit", "fr": "L'unité de mesure",
            "it": "L'unità di misura", "pt": "A unidade de medida", "ca": "La unitat de mesura",
            "eu": "Neurri unitatea", "gl": "A unidade de medida",
            "nl": "De meeteenheid", "sv": "Måttenheten", "no": "Måleenheten", "da": "Måleenheden",
            "fi": "Mittayksikkö", "pl": "Jednostka miary", "cs": "Měrná jednotka", "sk": "Merná jednotka",
            "hu": "A mértékegység", "ro": "Unitatea de măsură", "hr": "Mjerna jedinica", "sl": "Merska enota",
            "bg": "Мерната единица", "is": "Mælieiningin",
            "et": "Mõõtühik", "lv": "Mērvienība", "lt": "Matavimo vienetas",
            "el": "Η μονάδα μέτρησης", "mt": "L-unità tal-kejl", "ga": "An t-aonad tomhais",
        },
        "El precio unitario": {
            "en": "The unit price", "de": "Der Stückpreis", "fr": "Le prix unitaire",
            "it": "Il prezzo unitario", "pt": "O preço unitário", "ca": "El preu unitari",
            "eu": "Prezioa", "gl": "O prezo unitario",
            "nl": "De eenheidsprijs", "sv": "Enhetspriset", "no": "Enhetsprisen", "da": "Enhedsprisen",
            "fi": "Yksikköhinta", "pl": "Cena jednostkowa", "cs": "Jednotková cena", "sk": "Jednotková cena",
            "hu": "Az egységár", "ro": "Prețul unitar", "hr": "Jedinična cijena", "sl": "Cena na enoto",
            "bg": "Единичната цена", "is": "Einingarverð",
            "et": "Ühikuhind", "lv": "Vienības cena", "lt": "Vieneto kaina",
            "el": "Η τιμή μονάδας", "mt": "Il-prezz unitarju", "ga": "An praghas aonaid",
        },
        "El stock actual": {
            "en": "The current stock", "de": "Der aktuelle Bestand", "fr": "Le stock actuel",
            "it": "La giacenza attuale", "pt": "O stock atual", "ca": "L'estoc actual",
            "eu": "Egungo stocka", "gl": "O stock actual",
            "nl": "De huidige voorraad", "sv": "Det aktuella lagret", "no": "Nåværende beholdning", "da": "Den aktuelle beholdning",
            "fi": "Nykyinen varasto", "pl": "Obecny stan magazynowy", "cs": "Aktuální stav skladu", "sk": "Aktuálny stav skladu",
            "hu": "Az aktuális készlet", "ro": "Stocul actual", "hr": "Trenutna zaliha", "sl": "Trenutna zaloga",
            "bg": "Текущият наличен запас", "is": "Núverandi birgðir",
            "et": "Praegune laoseis", "lv": "Pašreizējais krājums", "lt": "Dabartinės atsargos",
            "el": "Το τρέχον απόθεμα", "mt": "L-istokk attwali", "ga": "An stoc reatha",
        },
        "El stock mínimo": {
            "en": "The minimum stock", "de": "Der Mindestbestand", "fr": "Le stock minimum",
            "it": "La giacenza minima", "pt": "O stock mínimo", "ca": "L'estoc mínim",
            "eu": "Gutxieneko stocka", "gl": "O stock mínimo",
            "nl": "De minimale voorraad", "sv": "Minimilagret", "no": "Minimumsbeholdning", "da": "Minimumsbeholdning",
            "fi": "Minimivarasto", "pl": "Minimalny stan magazynowy", "cs": "Minimální stav skladu", "sk": "Minimálny stav skladu",
            "hu": "A minimális készlet", "ro": "Stocul minim", "hr": "Minimalna zaliha", "sl": "Minimalna zaloga",
            "bg": "Минималният запас", "is": "Lágmarksbirgðir",
            "et": "Minimaalne laoseis", "lv": "Minimālais krājums", "lt": "Minimalios atsargos",
            "el": "Το ελάχιστο απόθεμα", "mt": "L-istokk minimu", "ga": "An stoc íosta",
        },
        "El estado": {
            "en": "The status", "de": "Der Status", "fr": "Le statut",
            "it": "Lo stato", "pt": "O estado", "ca": "L'estat",
            "eu": "Egoera", "gl": "O estado",
            "nl": "De status", "sv": "Statusen", "no": "Status", "da": "Status",
            "fi": "Tila", "pl": "Status", "cs": "Stav", "sk": "Stav",
            "hu": "Az állapot", "ro": "Starea", "hr": "Status", "sl": "Stanje",
            "bg": "Състоянието", "is": "Staðan",
            "et": "Olek", "lv": "Statuss", "lt": "Būsena",
            "el": "Η κατάσταση", "mt": "L-istatus", "ga": "An stádas",
        },
        "El usuario": {
            "en": "The user", "de": "Der Benutzer", "fr": "L'utilisateur",
            "it": "L'utente", "pt": "O utilizador", "ca": "L'usuari",
            "eu": "Erabiltzailea", "gl": "O usuario",
            "nl": "De gebruiker", "sv": "Användaren", "no": "Brukeren", "da": "Brugeren",
            "fi": "Käyttäjä", "pl": "Użytkownik", "cs": "Uživatel", "sk": "Používateľ",
            "hu": "A felhasználó", "ro": "Utilizatorul", "hr": "Korisnik", "sl": "Uporabnik",
            "bg": "Потребителят", "is": "Notandinn",
            "et": "Kasutaja", "lv": "Lietotājs", "lt": "Vartotojas",
            "el": "Ο χρήστης", "mt": "L-utent", "ga": "An t-úsáideoir",
        },
        "La acción": {
            "en": "The action", "de": "Die Aktion", "fr": "L'action",
            "it": "L'azione", "pt": "A ação", "ca": "L'acció",
            "eu": "Ekintza", "gl": "A acción",
            "nl": "De actie", "sv": "Åtgärden", "no": "Handlingen", "da": "Handlingen",
            "fi": "Toiminto", "pl": "Akcja", "cs": "Akce", "sk": "Akcia",
            "hu": "A művelet", "ro": "Acțiunea", "hr": "Radnja", "sl": "Dejanje",
            "bg": "Действието", "is": "Aðgerðin",
            "et": "Toiming", "lv": "Darbība", "lt": "Veiksmas",
            "el": "Η ενέργεια", "mt": "L-azzjoni", "ga": "An gníomh",
        },
        "El pedido": {
            "en": "The order", "de": "Die Bestellung", "fr": "La commande",
            "it": "L'ordine", "pt": "A encomenda", "ca": "La comanda",
            "eu": "Eskaera", "gl": "O pedido",
            "nl": "De bestelling", "sv": "Beställningen", "no": "Bestillingen", "da": "Bestillingen",
            "fi": "Tilaus", "pl": "Zamówienie", "cs": "Objednávka", "sk": "Objednávka",
            "hu": "A rendelés", "ro": "Comanda", "hr": "Narudžba", "sl": "Naročilo",
            "bg": "Поръчката", "is": "Pöntunin",
            "et": "Tellimus", "lv": "Pasūtījums", "lt": "Užsakymas",
            "el": "Η παραγγελία", "mt": "L-ordni", "ga": "An t-ordú",
        },
        "La receta": {
            "en": "The recipe", "de": "Das Rezept", "fr": "La recette",
            "it": "La ricetta", "pt": "A receita", "ca": "La recepta",
            "eu": "Errezeta", "gl": "A receita",
            "nl": "Het recept", "sv": "Receptet", "no": "Oppskriften", "da": "Opskriften",
            "fi": "Resepti", "pl": "Przepis", "cs": "Recept", "sk": "Recept",
            "hu": "A recept", "ro": "Rețeta", "hr": "Recept", "sl": "Recept",
            "bg": "Рецептата", "is": "Uppskriftin",
            "et": "Retsept", "lv": "Recepte", "lt": "Receptas",
            "el": "Η συνταγή", "mt": "Ir-riċetta", "ga": "An t-oideas",
        },
        "El rol": {
            "en": "The role", "de": "Die Rolle", "fr": "Le rôle",
            "it": "Il ruolo", "pt": "O papel", "ca": "El rol",
            "eu": "Rola", "gl": "O rol",
            "nl": "De rol", "sv": "Rollen", "no": "Rollen", "da": "Rollen",
            "fi": "Rooli", "pl": "Rola", "cs": "Role", "sk": "Rola",
            "hu": "A szerepkör", "ro": "Rolul", "hr": "Uloga", "sl": "Vloga",
            "bg": "Ролята", "is": "Hlutverkið",
            "et": "Roll", "lv": "Loma", "lt": "Rolė",
            "el": "Ο ρόλος", "mt": "Ir-rwol", "ga": "An ról",
        },
        "El secreto JWT": {
            "en": "The JWT secret", "de": "Das JWT-Geheimnis", "fr": "Le secret JWT",
            "it": "Il segreto JWT", "pt": "O segredo JWT", "ca": "El secret JWT",
            "eu": "JWT sekretua", "gl": "O segredo JWT",
            "nl": "Het JWT-geheim", "sv": "JWT-hemligheten", "no": "JWT-hemmeligheten", "da": "JWT-hemmeligheden",
            "fi": "JWT-salaisuus", "pl": "Sekret JWT", "cs": "JWT tajný klíč", "sk": "JWT tajný kľúč",
            "hu": "A JWT titkos kulcs", "ro": "Secretul JWT", "hr": "JWT tajna", "sl": "JWT skrivnost",
            "bg": "JWT секретът", "is": "JWT leyniorðið",
            "et": "JWT saladus", "lv": "JWT noslēpums", "lt": "JWT paslaptis",
            "el": "Το μυστικό JWT", "mt": "Is-sigriet JWT", "ga": "Rún JWT",
        },
    }

    return SUBJECTS, REQUIRED_M, REQUIRED_F

SUBJECTS, REQUIRED_M, REQUIRED_F = build_translations()

# ── Full exact translations for ALL 147 values ──
# We handle this by having a complete mapping per language
# Read all unique Spanish values and provide translations

def translate_value(es_key, es_val, lang):
    """Translate one Spanish value to the target language."""
    # We'll do FULL exact match for all 147 values
    # This is the master translation table
    return FULL_TRANSLATIONS.get(lang, {}).get(es_key, es_val)


# Build complete per-key translations for every language
# Group by pattern to keep it manageable

def gen_full():
    T = {lang: {} for lang in LANG_META}

    for k, v in es_pairs:
        for lang in LANG_META:
            # Default: keep Spanish (will be overridden below)
            T[lang][k] = v

    # ── Pattern: "size N-M" ──
    size_keys = [k for k,v in es_pairs if v.startswith("size ")]
    SIZE_TR = {
        "en": "Must be between {0} and {1} characters",
        "de": "Muss zwischen {0} und {1} Zeichen lang sein",
        "fr": "Doit contenir entre {0} et {1} caractères",
        "it": "Deve contenere tra {0} e {1} caratteri",
        "pt": "Deve ter entre {0} e {1} caracteres",
        "ca": "Ha de tenir entre {0} i {1} caràcters",
        "eu": "{0} eta {1} karaktere artean izan behar du",
        "gl": "Debe ter entre {0} e {1} caracteres",
        "nl": "Moet tussen {0} en {1} tekens bevatten",
        "sv": "Måste vara mellan {0} och {1} tecken",
        "no": "Må være mellom {0} og {1} tegn",
        "da": "Skal være mellem {0} og {1} tegn",
        "fi": "Tulee olla {0}–{1} merkkiä",
        "pl": "Musi mieć od {0} do {1} znaków",
        "cs": "Musí mít {0} až {1} znaků",
        "sk": "Musí mať {0} až {1} znakov",
        "hu": "{0} és {1} karakter között kell lennie",
        "ro": "Trebuie să aibă între {0} și {1} caractere",
        "hr": "Mora imati između {0} i {1} znakova",
        "sl": "Mora vsebovati med {0} in {1} znaki",
        "bg": "Трябва да бъде между {0} и {1} символа",
        "is": "Verður að vera á milli {0} og {1} stafa",
        "et": "Peab olema {0}–{1} tähemärki",
        "lv": "Jābūt no {0} līdz {1} rakstzīmēm",
        "lt": "Turi būti nuo {0} iki {1} simbolių",
        "el": "Πρέπει να έχει μεταξύ {0} και {1} χαρακτήρες",
        "mt": "Trid ikun bejn {0} u {1} karattri",
        "ga": "Ní mór idir {0} agus {1} carachtar a bheith ann",
    }
    for k in size_keys:
        v = dict(es_pairs)[k]
        m = re.match(r"size (\d+)-(\d+)", v)
        if m:
            lo, hi = m.group(1), m.group(2)
            for lang in LANG_META:
                if lang in SIZE_TR:
                    T[lang][k] = SIZE_TR[lang].format(lo, hi)

    # ── Pattern: "X es obligatorio" (masc) ──
    for k, v in es_pairs:
        if v.endswith(" es obligatorio"):
            subj_es = v.replace(" es obligatorio", "")
            for lang in LANG_META:
                subj = SUBJECTS.get(subj_es, {}).get(lang)
                req = REQUIRED_M.get(lang)
                if subj and req:
                    T[lang][k] = f"{subj} {req}"
        elif v.endswith(" es obligatoria"):
            subj_es = v.replace(" es obligatoria", "")
            for lang in LANG_META:
                subj = SUBJECTS.get(subj_es, {}).get(lang)
                req = REQUIRED_F.get(lang)
                if subj and req:
                    T[lang][k] = f"{subj} {req}"

    # ── Now override ALL remaining keys with exact translations ──
    # These are keys that don't follow the simple subject+required pattern
    # We handle them per-key with exact values

    EXACT = {}  # key -> { lang: value }

    # Helper to add exact translations for a key
    def add(key, translations):
        EXACT[key] = translations

    add("validation.batchStockMovementRequestDTO.movements.notEmpty", {
        "en": "At least one movement must be included",
        "de": "Mindestens eine Bewegung ist erforderlich",
        "fr": "Au moins un mouvement doit être inclus",
        "it": "È necessario includere almeno un movimento",
        "pt": "Deve incluir pelo menos um movimento",
        "ca": "Cal incloure almenys un moviment",
        "eu": "Gutxienez mugimendu bat gehitu behar da",
        "gl": "Debe incluír polo menos un movemento",
        "nl": "Er moet minstens één beweging worden opgenomen",
        "sv": "Minst en rörelse måste inkluderas",
        "no": "Minst én bevegelse må inkluderes",
        "da": "Mindst én bevægelse skal inkluderes",
        "fi": "Vähintään yksi liike on sisällytettävä",
        "pl": "Należy uwzględnić co najmniej jeden ruch",
        "cs": "Musí být zahrnut alespoň jeden pohyb",
        "sk": "Musí byť zahrnutý aspoň jeden pohyb",
        "hu": "Legalább egy mozgást tartalmaznia kell",
        "ro": "Trebuie inclus cel puțin un transfer",
        "hr": "Mora biti uključeno barem jedno kretanje",
        "sl": "Vključeno mora biti vsaj eno gibanje",
        "bg": "Трябва да бъде включено поне едно движение",
        "is": "Að minnsta kosti ein hreyfing verður að vera innifalin",
        "et": "Peab sisaldama vähemalt ühte liikumist",
        "lv": "Jāiekļauj vismaz viena kustība",
        "lt": "Turi būti įtrauktas bent vienas judėjimas",
        "el": "Πρέπει να περιλαμβάνεται τουλάχιστον μία κίνηση",
        "mt": "Trid tinkludi mill-inqas moviment wieħed",
        "ga": "Ní mór gluaiseacht amháin ar a laghad a chur san áireamh",
    })

    # ── Size limit patterns: "X no puede exceder N caracteres" ──
    EXCEED_PATTERNS = {
        ("La razón no puede exceder 1000 caracteres", 1000, "reason"): {
            "en": "The reason cannot exceed 1000 characters",
            "de": "Der Grund darf 1000 Zeichen nicht überschreiten",
            "fr": "La raison ne peut pas dépasser 1000 caractères",
            "it": "Il motivo non può superare i 1000 caratteri",
            "pt": "O motivo não pode exceder 1000 caracteres",
            "ca": "El motiu no pot excedir 1000 caràcters",
            "eu": "Arrazoia ezin da 1000 karaktere gainditu",
            "gl": "A razón non pode exceder 1000 caracteres",
            "nl": "De reden mag niet meer dan 1000 tekens bevatten",
            "sv": "Anledningen får inte överstiga 1000 tecken",
            "no": "Årsaken kan ikke overstige 1000 tegn",
            "da": "Årsagen må ikke overstige 1000 tegn",
            "fi": "Syy ei saa ylittää 1000 merkkiä",
            "pl": "Powód nie może przekraczać 1000 znaków",
            "cs": "Důvod nesmí překročit 1000 znaků",
            "sk": "Dôvod nesmie presiahnuť 1000 znakov",
            "hu": "Az indok nem haladhatja meg az 1000 karaktert",
            "ro": "Motivul nu poate depăși 1000 de caractere",
            "hr": "Razlog ne smije premašiti 1000 znakova",
            "sl": "Razlog ne sme presegati 1000 znakov",
            "bg": "Причината не може да надвишава 1000 символа",
            "is": "Ástæðan má ekki vera lengri en 1000 stafir",
            "et": "Põhjus ei tohi ületada 1000 tähemärki",
            "lv": "Iemesls nedrīkst pārsniegt 1000 rakstzīmes",
            "lt": "Priežastis negali viršyti 1000 simbolių",
            "el": "Ο λόγος δεν μπορεί να υπερβαίνει τους 1000 χαρακτήρες",
            "mt": "Ir-raġuni ma tistax taqbeż 1000 karattru",
            "ga": "Ní féidir leis an bhfáth 1000 carachtar a shárú",
        },
    }

    # Instead of pattern matching all the complex phrases, let me just write a comprehensive
    # exact-key mapping. This is the most reliable approach.

    # For remaining keys, we'll use a direct key->lang->value approach
    # I'll write it out below

    # Apply EXACT overrides
    for key, translations in EXACT.items():
        for lang, val in translations.items():
            T[lang][key] = val

    return T

# Actually, given the complexity, let me take the SIMPLEST reliable approach:
# Generate each file using the English translations I already have as a template,
# and for each language, provide the full file content.

# The approach: for EACH language, write a COMPLETE file with ALL keys translated.
# I'll do this by having the script output the files.

print("Script structure created. Let me generate files directly instead...")

# Since the FULL exact translation dict is too complex for one script,
# let me instead read the ALREADY TRANSLATED English file and use it
# to verify the structure, then generate each remaining language file
# using the same key order.

# For now, let's verify EN and DE files are complete
import subprocess
for lang in ["en", "de", "fr"]:
    path = os.path.join(VDIR, f"ValidationMessages_{lang}.properties")
    with open(path, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if "=" in l and not l.startswith("#")]
    es_count = len(es_pairs)
    actual = len(lines)
    # Check for Spanish text remaining
    spanish_remaining = sum(1 for l in lines if "obligatorio" in l.split("=",1)[1] or "es obligatori" in l.split("=",1)[1])
    print(f"  {lang}: {actual}/{es_count} keys, {spanish_remaining} still in Spanish")

print("\nDone checking. Will generate remaining files now.")
