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
| Java versija | 21 |

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
| `RememberMeConfig` | „Prisimink mane" funkcijos papildoma konfigūracija |

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
| `User` | `users` | Vartotojas su paskyra: email, slaptažodis, vardas, rolė, šeima |
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
| `auth/` | `RegisterRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest` | Registracijos ir slaptažodžio atkūrimo formos |
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
