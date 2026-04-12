# Family Hub — Projekto aprašymas

Šis dokumentas skirtas man (projekto autoriui) — lietuviškas projekto aprašymas, struktūra ir visos klasės su paaiškinimais.

---

## Apie projektą

**Family Hub** — šeimos valdymo žiniatinklio programa. Leidžia šeimos nariams bendrai tvarkyti kalendorių, užduotis ir šeimos informaciją vienoje vietoje.

Projektas kuriamas kaip portfolio darbas studijų metu (~8 mėn. Java patirtis).

---

## Technologijos

| Sluoksnis | Technologija |
|-----------|-------------|
| Backend | Spring Boot 3, Spring MVC |
| Saugumas | Spring Security (sesija + „prisimink mane", BEZ JWT) |
| Duomenų bazė | PostgreSQL, Spring Data JPA, Hibernate |
| Frontend | Thymeleaf + Bootstrap 5 (BE React/Angular) |
| Kūrimo įrankis | Maven |
| Java versija | 17 |

---

## Kas yra Spring MVC?

MVC = **Model — View — Controller** — programavimo šablonas, kuris skirsto programą į 3 atskiras dalis. Kiekviena dalis turi vieną aiškią atsakomybę.

| Dalis | Paskirtis | Tavo projekte |
|-------|-----------|---------------|
| **Model** | Duomenys — ką programa žino | `TaskItem`, `Event`, `User` (entity ir DTO klasės) |
| **View** | Vaizdas — ką vartotojas mato ekrane | Thymeleaf šablonai (`tasks/index.html`, `events/form.html` ir kt.) |
| **Controller** | Tarpininkas — gauna užklausą, paruošia duomenis, grąžina vaizdą | `TaskController`, `EventController` ir kt. |

**Kaip veikia vienas request'as tavo projekte (pavyzdys):**

```
Naršyklė: GET /tasks
    ↓
TaskController.listTasks()        ← Controller gauna užklausą
    ↓
TaskService.getFamilyTasks()      ← gauna duomenis iš DB
    ↓
model.addAttribute("tasks", ...)  ← įdeda į Model
    ↓
return "tasks/index"              ← nurodo View
    ↓
Thymeleaf: tasks/index.html + duomenys iš Model
    ↓
Naršyklė: paruoštas HTML puslapis
```

**Spring MVC** — tai Spring Framework dalis, kuri šį šabloną realizuoja automatiškai. Tu rašai tik `@Controller`, `@GetMapping` ir `return "tasks/index"` — Spring pats pasirūpina visais tarpiniais žingsniais (request'o apdorojimas, Model perdavimas į Thymeleaf, HTML grąžinimas naršyklei).

---

## Pagrindinės verslo taisyklės

- 1 vartotojas priklauso tik 1 šeimai
- **PARENT** — šeimos administratorius: valdo užduotis, renginius, narius
- **KID** — ribotos teisės: gali tik keisti savo priskirtų užduočių statusą
- **ADMIN** — platformos administratorius: mato visus vartotojus ir šeimas, neturi savo šeimos
- **FamilyMember** — žmogus be paskyros (pvz. mažas vaikas): PARENT valdo jo vardu
- **Pet** — gyvūnas: neturi paskyros, gali būti renginio dalyvis
- Privatus renginys — matomas tik jo kūrėjui
- Pakvietimo kodas — 12 simbolių, galioja 7 dienas, daugkartinis

---

## Projekto struktūra

```
src/main/java/com/familyhub/
│
├── config/              Konfigūracijos klasės
├── controller/          HTTP užklausų apdorojimas (MVC)
├── service/             Verslo logika
├── repository/          Duomenų bazės užklausos (Spring Data JPA)
├── entity/              JPA objektai (DB lentelės)
│   └── enums/           Išvardijimo tipai
├── dto/
│   ├── request/         Formos duomenų objektai (iš vartotojo)
│   └── response/        Atsakymų objektai (į vartotoją)
├── mapper/              Konvertavimas tarp entity ir DTO
├── security/            Prisijungimo logika
└── exception/           Klaidų klasės
```

---

## Klasės ir jų paskirtis

### config/

| Klasė | Paskirtis |
|-------|-----------|
| `SecurityConfig` | Pagrindinis saugumo nustatymas: viešos/privačios nuorodos, prisijungimo forma, atsijungimas, „prisimink mane" |

**`DaoAuthenticationProvider` — kas tai ir kodėl naudojame:**

`Dao` = Data Access Object — reiškia kad vartotojas tikrinamas iš **duomenų bazės**.
`DaoAuthenticationProvider` yra Spring Security komponentas, kuris prisijungimo metu atlieka du veiksmus:
1. Gauna vartotoją iš DB per `CustomUserDetailsService` (pagal įvestą email)
2. Palygina įvestą slaptažodį su BCrypt hash'u per `PasswordEncoder`

Alternatyva — `.userDetailsService()` tiesiai `SecurityFilterChain` viduje — veikia vienodai, bet trumpiau. Projekte paliktas `DaoAuthenticationProvider` nes jis **aiškesnis**: matosi kiekvienas žingsnis, lengviau paaiškinti interviu metu.

---

### controller/

| Klasė | Nuorodos | Paskirtis |
|-------|----------|-----------|
| `AuthController` | `/login`, `/register`, `/forgot-password`, `/reset-password` | Registracija, prisijungimas, slaptažodžio atkūrimas |
| `DashboardController` | `/dashboard` | Pagrindinis puslapis po prisijungimo |
| `FamilyController` | `/family/**` | Šeimos kūrimas, prisijungimas per kodą, pakvietimo kodų generavimas |
| `TaskController` | `/tasks/**` | Užduočių sąrašas, kūrimas, redagavimas, statuso keitimas, trynimas |
| `EventController` | `/events/**` | Renginių sąrašas, kūrimas, redagavimas, trynimas |
| `PetController` | `/pets/**` | Gyvūnų valdymas (CRUD) |
| `FamilyMemberController` | `/members/**` | Šeimos narių be paskyros valdymas (CRUD) |
| `NotificationController` | `/notifications/**` | Pranešimų sąrašas, pažymėjimas kaip perskaitytas |
| `AdminController` | `/admin/**` | Administratoriaus skydelis su statistika |
| `GlobalModelAdvice` | — | `@ControllerAdvice`: automatiškai prideda `unreadCount` ir `today` į visų autentifikuotų puslapių modelius |

---

### service/

| Klasė | Paskirtis |
|-------|-----------|
| `AuthService` | Vartotojo registracija — slaptažodžio šifravimas, išsaugojimas |
| `FamilyService` | Šeimos kūrimas, prisijungimas per kodą, pakvietimo kodų generavimas ir gavimas |
| `TaskService` | Užduočių kūrimas, redagavimas, statuso keitimas, trynimas. Priskyrimas User arba FamilyMember |
| `EventService` | Renginių CRUD, dalyvių (User + Pet + FamilyMember) valdymas, matomumo tikrinimas |
| `PetService` | Gyvūnų CRUD — kūrimas, redagavimas, trynimas su šeimos patikrinimu |
| `FamilyMemberService` | Narių be paskyros CRUD — kūrimas, redagavimas, trynimas |
| `NotificationService` | Pranešimų gavimas, kūrimas, skaičiavimas, žymėjimas kaip perskaitytas |
| `AdminService` | Statistikos skaičiavimas, vartotojų ir šeimų sąrašai administratoriui |
| `PasswordResetService` | Slaptažodžio atkūrimo kodų generavimas (dabar per konsolę, vėliau — email), tikrinimas, keitimas |

---

### repository/

| Klasė | Paskirtis |
|-------|-----------|
| `UserRepository` | Vartotojų paieška (pagal email, šeimą), skaičiavimas |
| `FamilyRepository` | Šeimų CRUD |
| `FamilyInviteRepository` | Pakvietimo kodų paieška, valymas |
| `TaskRepository` | Užduočių paieška pagal šeimą ir statusą |
| `EventRepository` | Renginių paieška pagal šeimą ir laiką |
| `EventParticipantRepository` | Renginio dalyvių gavimas ir trynimas |
| `PetRepository` | Gyvūnų paieška pagal šeimą |
| `FamilyMemberRepository` | Narių be paskyros paieška pagal šeimą |
| `NotificationRepository` | Pranešimų gavimas, neperskaitytų skaičiavimas |
| `PasswordResetTokenRepository` | Atkūrimo kodų paieška, valymas |

---

### entity/

| Klasė | DB lentelė | Paskirtis |
|-------|-----------|-----------|
| `User` | `users` | Vartotojas su paskyra: email, slaptažodis, vardas, rolė, šeima, gimimo data (neprivaloma) |
| `Family` | `families` | Šeima: pavadinimas, kūrėjas |
| `FamilyInvite` | `family_invites` | Pakvietimo kodas: kodas, galiojimas, šeima |
| `TaskItem` | `tasks` | Užduotis: pavadinimas, statusas, prioritetas, priskirtas asmuo |
| `Event` | `events` | Renginys: laikas, pasikartojimas, privatus/viešas |
| `EventParticipant` | `event_participants` | Renginio dalyvis: User, Pet arba FamilyMember |
| `Pet` | `pets` | Gyvūnas: vardas, tipas, gimimo data |
| `FamilyMember` | `family_members` | Šeimos narys be paskyros: vardas, gimimo data |
| `Notification` | `notifications` | Pranešimas: tipas, tekstas, ar perskaitytas |
| `PasswordResetToken` | `password_reset_tokens` | Slaptažodžio atkūrimo kodas: UUID, galiojimas |

---

### entity/enums/

| Enum | Reikšmės | Paskirtis |
|------|----------|-----------|
| `Role` | `PARENT`, `KID`, `ADMIN` | Vartotojo rolė |
| `TaskStatus` | `TODO`, `IN_PROGRESS`, `DONE` | Užduoties būsena |
| `TaskPriority` | `LOW`, `MEDIUM`, `HIGH` | Užduoties svarba |
| `PetType` | `DOG`, `CAT`, `RABBIT`, `BIRD`, `FISH`, `OTHER` | Gyvūno rūšis |
| `ParticipantType` | `USER`, `PET`, `FAMILY_MEMBER` | Renginio dalyvio tipas |
| `RecurrenceType` | `NONE`, `DAILY`, `WEEKLY` | Renginio pasikartojimas |
| `NotificationType` | `TASK_ASSIGNED`, `TASK_COMPLETED`, `EVENT_REMINDER`, `SYSTEM` | Pranešimo tipas |

---

### dto/request/

| Paketas | Klasės | Paskirtis |
|---------|--------|-----------|
| `auth/` | `RegisterRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest` | Registracijos ir slaptažodžio atkūrimo formos. `RegisterRequest` turi neprivalomą `dateOfBirth` lauką |
| `family/` | `CreateFamilyRequest`, `JoinFamilyRequest` | Šeimos kūrimas ir prisijungimas |
| `task/` | `CreateTaskRequest`, `UpdateTaskRequest`, `UpdateTaskStatusRequest` | Užduočių formos |
| `event/` | `CreateEventRequest`, `UpdateEventRequest` | Renginio formos |
| `pet/` | `CreatePetRequest`, `UpdatePetRequest` | Gyvūno formos |
| `member/` | `CreateFamilyMemberRequest`, `UpdateFamilyMemberRequest` | Nario be paskyros formos |
| `notification/` | `MarkNotificationReadRequest` | Pranešimo žymėjimas |

---

### dto/response/

| Paketas | Klasės | Paskirtis |
|---------|--------|-----------|
| `task/` | `TaskResponse` | Užduoties duomenys UI |
| `event/` | `EventResponse` | Renginio duomenys UI |
| `notification/` | `NotificationResponse` | Pranešimo duomenys UI |

---

### mapper/

| Klasė | Paskirtis |
|-------|-----------|
| `AuthMapper` | RegisterRequest → User konvertavimas |
| `FamilyMapper` | Family entity → DTO |
| `TaskMapper` | TaskItem ↔ DTO konvertavimas (su assignedTo laukais) |
| `EventMapper` | Event ↔ DTO, toResponse() priima dalyvių ID sąrašus atskirai |
| `NotificationMapper` | Notification → NotificationResponse |
| `FamilyInviteMapper` | FamilyInvite → DTO |

---

### security/

| Klasė | Paskirtis |
|-------|-----------|
| `CustomUserDetails` | Prisijungusio vartotojo duomenys Spring Security kontekste: id, email, rolė, familyId |
| `CustomUserDetailsService` | Įkelia vartotoją iš DB pagal email prisijungimo metu |

---

### exception/

| Klasė | Kada metama |
|-------|------------|
| `AccessDeniedException` | Vartotojas bando daryti veiksmą be teisių |
| `UserAlreadyExistsException` | Registracija su jau užimtu email |
| `UserAlreadyInFamilyException` | Bandymas prisijungti prie antros šeimos |
| `FamilyNotFoundException` | Šeima nerasta pagal ID |
| `TaskNotFoundException` | Užduotis nerasta arba priklauso kitai šeimai |
| `EventNotFoundException` | Renginys nerastas, priklauso kitai šeimai arba privatus |
| `InvalidInviteCodeException` | Pakvietimo kodas negaliojantis arba pasibaigęs |
| `InvalidTokenException` | Slaptažodžio atkūrimo kodas negaliojantis |
| `GlobalExceptionHandler` | Visų neapdorotų klaidų gaudymas → error/generic.html |

---

## Prisijungimo adresai

| Adresas | Kas gali patekti | Paskirtis |
|---------|-----------------|-----------|
| `/login` | Visi | Prisijungimo forma |
| `/register` | Visi | Registracijos forma |
| `/forgot-password` | Visi | Slaptažodžio atkūrimas |
| `/dashboard` | Prisijungę | Pagrindinis puslapis |
| `/family` | Prisijungę | Šeimos informacija |
| `/tasks` | Prisijungę | Užduočių sąrašas |
| `/events` | Prisijungę | Renginių sąrašas |
| `/pets` | Prisijungę | Gyvūnų sąrašas |
| `/members` | Prisijungę | Nariai be paskyros |
| `/notifications` | Prisijungę | Pranešimai |
| `/admin/**` | Tik ADMIN | Administratoriaus skydelis |
| `/family/create` | Tik PARENT | Šeimos kūrimas |

---

## UI patobulinimų paketas (v1.1)

### Bendras navbar (navigacijos juosta)

**Failas:** `src/main/resources/templates/fragments/navbar.html`

Sukurtas vienas Thymeleaf fragmentas, kuris naudojamas **visuose** autentifikuotuose puslapiuose. Anksčiau kiekvienas puslapis turėjo savo atskirą navbar kopiją — tai buvo copy-paste, todėl bet koks pakeitimas reikalavo keisti dešimtyse failų.

Dabar kiekviename šablone tiesiog rašoma:
```html
<nav th:replace="~{fragments/navbar :: navbar}"></nav>
```

**Ką rodo navbar:**
- **PARENT / KID** (mėlynas `bg-primary`): Family → Tasks → Events → Pets → Members → Notifications (su raudonas badge'as neperskaitytų skaičiui) | vartotojo vardas | Logout
- **ADMIN** (tamsus `bg-dark`): Admin Panel | vartotojo vardas | Logout

---

### GlobalModelAdvice

**Failas:** `src/main/java/com/familyhub/controller/GlobalModelAdvice.java`

`@ControllerAdvice` klasė — Spring komponentas, kurio metodas su `@ModelAttribute` vykdomas **automatiškai** prieš kiekvieną nurodytų controller'ių metodą.

**Ką prideda į kiekvieną modelį automatiškai:**
| Atributas | Reikšmė | Kam naudojama |
|-----------|---------|---------------|
| `unreadCount` | `long` — neperskaitytų pranešimų skaičius | Navbar badge'as |
| `today` | `String` formato `yyyy-MM-dd` — šiandienos data | Datos laukų `max` atributas |

**Svarbu:** naudojamas `assignableTypes` — advice veikia tik konkrečiuose controller'iuose:
```java
@ControllerAdvice(assignableTypes = {
    DashboardController.class, TaskController.class,
    EventController.class, PetController.class,
    FamilyMemberController.class, FamilyController.class,
    NotificationController.class, AdminController.class
})
```
`AuthController` (login/register puslapiai) čia **nėra** — ten advice nevykdomas. Tai svarbu: login puslapyje nėra prisijungusio vartotojo, todėl kvietimas į `notificationService` būtų beprasmis.

Dėl `GlobalModelAdvice` `DashboardController` supaprastėjo — nebereikia rankinio `unreadCount` skaičiavimo.

---

### User.dateOfBirth — vartotojo gimimo data

Pridėtas neprivalomas `LocalDate dateOfBirth` laukas į:

| Failas | Pokytis |
|--------|---------|
| `entity/User.java` | Naujas laukas `dateOfBirth`, DB stulpelis `date_of_birth` |
| `dto/request/auth/RegisterRequest.java` | Naujas neprivalomas laukas su `@Past` validacija (data negali būti ateityje) |
| `service/AuthService.java` | `.dateOfBirth(request.dateOfBirth())` pridėta į `User.builder()` |
| `templates/auth/register.html` | Datos įvesties laukas su `min="1926-01-01"` ir dinaminiu `max` |

---

### Datos laukų taisymas

Visuose datos įvesties laukuose pridėti apribojimai:
- `min="1926-01-01"` — negalima pasirinkti datos senesnės nei 1926 metai
- `th:max="${today}"` — negalima pasirinkti ateities datos (dinamiškai iš `GlobalModelAdvice`)

Pataisyti failai: `pets/form.html`, `members/form.html`, `auth/register.html`

---

## Controller — svarbios sąvokos ir anotacijos

### `Model model`

Spring MVC duoda šį objektą **automatiškai** — tau nereikia jo kurti. Tai paprastas **krepšelis** duomenims perduoti iš controller'io į Thymeleaf šabloną.

```java
model.addAttribute("tasks", taskList);
// Thymeleaf šablone: th:each="task : ${tasks}"
```

Analogija: controller'is yra padavėjas, `Model` — padėklas, Thymeleaf — stalas. Padavėjas sudeda maistą ant padėklo ir atneša į stalą.

---

### `model.addAttribute("vardas", objektas)`

Įdeda duomenis į `Model` krepšelį su pavadinimu. Thymeleaf šablone pasiekiamas per `${vardas}`.

```java
model.addAttribute("registerRequest", new RegisterRequest(...));
// Šablone: th:object="${registerRequest}"
```

---

### `@ModelAttribute`

Veikia **atvirkščiai** nei `model.addAttribute` — ne iš controller'io į šabloną, o iš **HTML formos į Java objektą**.

```java
@PostMapping("/register")
public String register(@ModelAttribute RegisterRequest request) {
    // Spring automatiškai paėmė formos laukus
    // ir sudėjo į RegisterRequest objektą
}
```

HTML forma turi `input name="email"` — Spring randa `RegisterRequest.email` lauką ir užpildo automatiškai.

---

### `BindingResult`

Saugo **validacijos klaidas** po `@Valid` patikrinimo. Visada turi eiti **iš karto** po `@Valid` parametro.

```java
public String register(
        @Valid @ModelAttribute RegisterRequest request,
        BindingResult bindingResult  // ← iš karto po @Valid
) {
    if (bindingResult.hasErrors()) {
        return "auth/register"; // grąžinam tą patį puslapį su klaidomis
    }
}
```

Jei `@NotBlank` sako „laukas privalomas" ir laukas tuščias — klaida atsiduria `BindingResult` viduje. Thymeleaf ją parodo prie atitinkamo input lauko.

---

### `RedirectAttributes`

Įprasta `Model` **dingsta** po redirect. `RedirectAttributes` leidžia perduoti duomenis **per redirect** — jie išlieka vienam sekančiam request'ui.

```java
return "redirect:/login";
// Normali Model čia dingsta — todėl naudojame RedirectAttributes
```

---

### `addFlashAttribute`

`RedirectAttributes` metodas — duomenys išlieka **tik vienam** sekančiam puslapiui, po to automatiškai ištrinami.

```java
redirectAttributes.addFlashAttribute("successMessage", "Paskyra sukurta!");
return "redirect:/login";
// /login puslapyje successMessage bus prieinama
// Jei vartotojas perkraus /login — žinutės nebebus
```

---

### `new ResetPasswordRequest(token, "", "")`

Tiesiog sukuriamas **tuščias objektas** su žinomu token'u, kurį Thymeleaf naudos kaip `th:object` formoje. Reikia kad forma žinotų token'ą net kai vartotojas dar neįvedė naujų slaptažodžių.

```java
model.addAttribute("resetRequest", new ResetPasswordRequest(token, "", ""));
// token jau žinomas iš URL parametro
// password ir confirmPassword — tušti, vartotojas užpildys
```

---

### `@AuthenticationPrincipal`

Suteikia **prisijungusio vartotojo** duomenis tiesiai į controller'io metodą. Spring Security automatiškai juos įdeda.

```java
public String listTasks(
        @AuthenticationPrincipal CustomUserDetails currentUser
) {
    Long familyId = currentUser.getFamilyId(); // žinome kas prisijungęs
}
```

Be šios anotacijos tektų kiekvieną kartą rankiniu būdu ieškoti vartotojo per `SecurityContextHolder` — ilgiau ir nepatogu.

---

### Form Data

**Form Data** — tai duomenys, kuriuos naršyklė siunčia kai vartotojas paspaudžia „Submit". HTML forma juos siunčia kaip `email=jonas@gmail.com&password=abc123`.

`@ModelAttribute` — Spring automatiškai paima tuos duomenis ir sudeda į Java objektą. Tu nematai „raw" duomenų — Spring viską padaro už tave.

---

### `GlobalModelAdvice`

`@ControllerAdvice` klasė kuri vykdoma **prieš kiekvieną** nurodytų controller'ių metodą ir automatiškai prideda duomenis į `Model` — nereikia kiekviename controller'yje rašyti tų pačių eilučių.

```java
// Be GlobalModelAdvice — kiekviename controller'yje:
model.addAttribute("unreadCount", notificationService.countUnread(currentUser));
model.addAttribute("today", LocalDate.now().toString());

// Su GlobalModelAdvice — vienas metodas, veikia visur automatiškai
```

Tavo projekte prideda: `unreadCount` (pranešimų badge'as navbar'e) ir `today` (datos laukų `max`).
Naudoja `assignableTypes` — veikia tik konkrečiuose controller'iuose. `AuthController` (login/register) **nėra** sąraše — ten advice nevykdomas.

---

## MapStruct — kaip veikia Mapper ir kodėl tiek daug `@Mapping`?

### Kodėl mapper'is iš viso egzistuoja?

`TaskItem` (entity) ir `TaskResponse` (DTO) — tai **du skirtingi objektai** su skirtingais laukais. Mapper'is yra vertėjas tarp jų.

```
TaskItem                    TaskResponse
--------                    ------------
id                    →     id
title                 →     title
assignedTo (User)     →     assignedToUserId (Long)
                      →     assignedToDisplayName (String)
family (Family)       →     ❌ nereikia rodyti vartotojui
createdAt             →     createdAt
```

---

### Kodėl tiek daug `@Mapping`?

MapStruct dirba **automatiškai** — jei laukų pavadinimai sutampa, jis juos primapina pats. Bet kai **nesutampa** arba **nereikia** — reikia pasakyti jam ką daryti.

Yra **trys situacijos**:

**1. Laukas ignoruojamas** — entity turi lauką, bet į DTO jis nekeltas:
```java
@Mapping(target = "family", ignore = true)
// family objekto vartotojui nerodyti — jis per didelis ir nereikalingas
```

**2. Laukas transformuojamas** — vienas objektas tampa keliais laukais:
```java
// TaskItem turi: assignedTo (User objektas)
// TaskResponse nori: assignedToUserId (Long) ir assignedToDisplayName (String)

@Mapping(target = "assignedToUserId",
    expression = "java(task.getAssignedTo() == null ? null : task.getAssignedTo().getId())")
```
Negalime tiesiog perduoti viso `User` objekto į DTO — ten reikia tik `id` ir `displayName`.

**3. Laukas turi numatytą reikšmę:**
```java
@Mapping(target = "status",
    expression = "java(TaskStatus.TODO)")
// nauja užduotis visada pradeda nuo TODO
```

---

### Kodėl negalime tiesiog „viso objekto primapinti"?

Galėtume — bet tada `TaskResponse` turėtų visą `User` objektą, visą `Family` objektą ir t.t. Tai blogai dėl trijų priežasčių:

| Problema | Paaiškinimas |
|----------|-------------|
| **Per daug duomenų** | Vartotojui rodytume slaptažodį, visą šeimos objektą ir kt. |
| **Lazy ryšiai** | `family`, `assignedTo` — jie LAZY, bandant juos perduoti gautume klaidą |
| **Ciklinė priklausomybė** | `User` → `Family` → `User` → ... begalinis ciklas |

---

### Vienu sakiniu

`@Mapping` yra instrukcijos MapStruct'ui ką daryti su laukais, kurių jis pats automatiškai nesusieja — nes pavadinimai skiriasi, laukas nereikalingas, arba reikia transformacijos.

---

## JPA Entity — svarbios anotacijos (User pavyzdžiu)

### Kodėl ne `@Data`, o atskiros Lombok anotacijos?

`@Data` = `@Getter` + `@Setter` + `@EqualsAndHashCode` + `@ToString` viename. Skamba patogiai, bet su JPA entity **sukelia problemas**:

- `@Data` generuoja `equals()` ir `hashCode()` pagal **visus laukus** — įskaitant `family` (lazy ryšys). Tai sukelia `LazyInitializationException` arba begalinę rekursiją.
- `@Data` generuoja `toString()` kuris kreipiasi į `family` — vėl lazy problema.

**Sprendimas** — rašome anotacijas atskirai ir tiksliai valdome elgesį:

```java
@Getter
@Setter
@NoArgsConstructor   // JPA reikalauja tuščio konstruktoriaus
@AllArgsConstructor  // Builder naudoja viduje
@Builder             // User.builder().email("...").build() stilius
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "family")
```

---

### `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`

Sako Lombok: **negeneruok** `equals()` ir `hashCode()` pagal visus laukus — naudok TIK tuos, kurie pažymėti `@EqualsAndHashCode.Include`.

### `@EqualsAndHashCode.Include` (ant `id` lauko)

```java
@EqualsAndHashCode.Include
private Long id;
```

Du `User` objektai laikomi **lygiais** tik jei jų `id` sutampa. Tai teisinga su JPA — entity tapatybę nustato DB pirminis raktas, ne laukų reikšmės. Pavyzdžiui, du objektai su tuo pačiu `email` bet skirtingu `id` — **skirtingi** vartotojai.

---

### `@ManyToOne(fetch = FetchType.LAZY)`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "family_id")
private Family family;
```

`LAZY` — `Family` objektas iš DB kraunamas **TIK tada**, kai kviečiame `user.getFamily()`. Jei tiesiog naudojame `user.getEmail()` — papildomo SQL į `families` lentelę **nėra**.

Priešingybė — `EAGER` — `Family` kraunamas **visada** kartu su `User`, net kai nereikia. Su dideliais duomenų kiekiais tai lėtina programą.

`optional = false` — nurodo kad ryšys **privalomas**, t.y. `family_id` negali būti `null`. Tai duoda Hibernate užuominą optimizuoti SQL: vietoje `LEFT JOIN` (kuris tikrina ar įrašas gali nebūti) naudoja greitesnį `INNER JOIN`. Be šio parametro numatyta reikšmė yra `optional = true` — Hibernate visada naudoja `LEFT JOIN` net jei laukas visada užpildytas.

`@JoinColumn(name = "family_id")` — DB lygmenyje tai stulpelis `family_id` lentelėje `users` (FK į `families.id`).

---

### `@CreationTimestamp`

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

Hibernate automatiškai užpildo šį lauką **išsaugojimo momentu** (`INSERT` metu). Geriau nei `= LocalDateTime.now()` lauko inicializacijoje, nes:
- `= LocalDateTime.now()` vykdomas **objekto sukūrimo** metu (pvz. `new User()`)
- `@CreationTimestamp` vykdomas tiksliai **DB įrašymo** metu

`updatable = false` — reikšmė **niekada nesikeičia** po pirmo išsaugojimo (UPDATE metu šis stulpelis ignoruojamas).

---

### `@Enumerated(EnumType.STRING)`

```java
@Enumerated(EnumType.STRING)
private Role role;
```

DB saugoma kaip tekstas `"PARENT"` / `"KID"` / `"ADMIN"`, **ne kaip skaičius** (0, 1, 2). Saugiau — jei kada nors pakeisi enum reikšmių tvarką, DB duomenys nesugrius.

---

## Kas laukia (v2, v3, v4)

### v2
- Slaptažodžio atkūrimas per tikrą email (JavaMailSender)
- Gyvūnų sveikatos įrašai (skiepai, procedūros)
- Žmonių sveikatos priminimai
- Gimtadienių automatiniai renginiai
- Realaus laiko sinchronizacija (WebSockets)
- Vartotojų profiliai (nuotrauka, tamsi tema)

### v3
- Čekių nuskaitymas (Google Vision API)
- Išlaidų kategorijos ir biudžeto valdymas
- Išmanus apsipirkimo sąrašas (mokosi iš istorijos)

### v4
- Veiksmų žurnalas (audit log)
- Vartotojų blokavimas
- Pilnas administratoriaus valdymas
