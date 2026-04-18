# Kodo peržiūros pamokos — Family Hub

Senior lygio kodo peržiūra. Ką radome, kodėl reikėjo keisti, ir ko išmokome.

---

## Kas buvo gerai (palikome nepakeistą)

- **Sluoksnių architektūra** — Controller → Service → Repository. Controller'iai nepasiekia Repository tiesiogiai.
- **DTO pattern** — atskiri `request/` ir `response/` objektai. Entitija (DB struktūra) nesimato tinkle.
- **Custom exceptions** — `EventNotFoundException`, `UserNotFoundException` ir kt. Kiekviena klaida turi savo tipą.
- **`@Transactional(readOnly = true)`** — ant visų read metodų. Hibernate praleidžia dirty checking — greitesnis skaitymas.
- **N+1 prevencija EventService** — `findAllByEventIdIn()` vietoje vieno query per eventą. Komentaras paaiškina kodėl.
- **Security check šeimos ribose** — `getEventBelongingToFamily()`, `getTaskBelongingToFamily()`. Apsauga nuo URL manipulation.
- **`GlobalModelAdvice`** — vietoje kartojamo `model.addAttribute(...)` kiekviename controller'yje.
- **`@EqualsAndHashCode(onlyExplicitlyIncluded = true)`** — teisingas JPA entity equals/hashCode. Vengia Hibernate proxy problemų.
- **Password reset security** — "If this email exists..." — neatskleidi ar emailas egzistuoja sistemoje (OWASP reikalavimas).

---

## Problema #1 — N+1 DB queries `DashboardController`

### Kas buvo blogai

```java
// 6 atskiros DB užklausos kiekvienam dashboard apsilankymui:
List<TaskItem> todoTasks     = taskService.getFamilyTasksByStatus(familyId, TODO);
List<TaskItem> inProgressTasks = taskService.getFamilyTasksByStatus(familyId, IN_PROGRESS);
List<TaskItem> doneTasks     = taskService.getFamilyTasksByStatus(familyId, DONE);
List<TaskItem> pendingTasks  = taskService.getFamilyTasks(familyId).stream()...

List<EventResponse> upcomingEvents = eventService.getVisibleFamilyEventsBetween(...tomorrow...);
long todayEventsCount = eventService.getVisibleFamilyEventsBetween(...today...).size();
```

### Kodėl tai blogai

DB query yra brangi operacija — tinklas, connection pool, disk I/O. Kiekvienas papildomas query lėtina puslapį. Čia dashboard'as darė **6 DB queries** kiekvieną kartą kai vartotojas atidarė puslapį.

### Kaip pataisėme

```java
// Tasks: 4 queries → 1 query + filtravimas atmintyje
List<TaskItem> allTasks = taskService.getFamilyTasks(familyId);
List<TaskItem> todoTasks      = allTasks.stream().filter(t -> t.getStatus() == TODO).toList();
List<TaskItem> doneTasks      = allTasks.stream().filter(t -> t.getStatus() == DONE).toList();
List<TaskItem> pendingTasks   = allTasks.stream().filter(...dueDate...).limit(4).toList();

// Events: 2 queries → 1 query + filtravimas atmintyje
List<EventResponse> sidebarEvents = eventService.getVisibleFamilyEventsBetween(...tomorrow...);
List<EventResponse> upcomingEvents = sidebarEvents.stream().limit(4).toList();
long todayEventsCount = sidebarEvents.stream().filter(e -> !e.startsAt().toLocalDate().isAfter(today)).count();
```

### Pamoka

> **Taisyklė:** Pirmiausia paklausk — *ar galiu gauti viską vienu query ir filtruoti Java kode?*
> Jei duomenų rinkinys ne milijonai įrašų — taip, galima ir taip reikia daryti.
> Filtravimas atmintyje (Java) yra daug pigesnis nei papildomas DB query.

---

## Problema #2 — `RuntimeException` vietoje domain exception

### Kas buvo blogai

```java
// NotificationService.java
Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
//                          ^^^^^^^^^^^^^^^^^ anoniminė klaida — nežinoma kas nutiko
```

Visa kita kodebase naudoja custom exceptions. Šitas vienas buvo `RuntimeException`.

### Kodėl tai blogai

- `RuntimeException` yra **anoniminė klaida** — neįmanoma atskirti "notification not found" nuo "server crashed"
- `GlobalExceptionHandler` negali skirtingai reaguoti — viskas rodo tą patį generinį puslapį
- Nenuoseklumas — kitos entitijos turi savo exceptions, notifikacijos — ne

### Kaip pataisėme

Sukūrėme `NotificationNotFoundException.java`:

```java
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
```

Ir pakeitėme kvietimą:

```java
.orElseThrow(() -> new NotificationNotFoundException(notificationId));
```

### Pamoka

> **Pattern'as** kurį naudoji visame projekte:
> ```java
> public class XxxNotFoundException extends RuntimeException {
>     public XxxNotFoundException(Long id) {
>         super("Xxx not found: " + id);
>     }
> }
> ```
> Kiekviena entitija turi savo exception. Tai leidžia ateityje `GlobalExceptionHandler` grąžinti 404 konkrečiam tipui.

---

## Problema #3 — Loginis bug su task statusų skaičiavimais

### Kas buvo blogai

```java
var tasks = (status != null)
        ? taskService.getFamilyTasksByStatus(familyId, status)  // ← tik filtruoti
        : taskService.getFamilyTasks(familyId);

// KLAIDA: skaičiuojama iš filtruoto sąrašo, ne pilno!
model.addAttribute("todoCount",       tasks.stream().filter(t -> t.getStatus() == TODO).count());
model.addAttribute("inProgressCount", tasks.stream().filter(t -> t.getStatus() == IN_PROGRESS).count());
model.addAttribute("doneCount",       tasks.stream().filter(t -> t.getStatus() == DONE).count());
```

### Kodėl tai blogai

Jei filtruoji pagal `?status=TODO`, tada `tasks` turi tik TODO įrašus.
Rezultatas ekrane: **"5 tasks, 5 to do, 0 in progress, 0 done"** — nors DB yra 3 in progress ir 8 done.
Vartotojas mato neteisingą statistiką.

### Kaip pataisėme

```java
// Visada krauname visus tasks (1 DB query)
List<TaskItem> allTasks = taskService.getFamilyTasks(familyId);
// Rodymui — filtruojame atmintyje
List<TaskItem> tasks = (status != null)
        ? allTasks.stream().filter(t -> t.getStatus() == status).toList()
        : allTasks;

// Statistika — visada iš pilno sąrašo
model.addAttribute("totalCount",      allTasks.size());
model.addAttribute("todoCount",       allTasks.stream().filter(t -> t.getStatus() == TODO).count());
model.addAttribute("inProgressCount", allTasks.stream().filter(t -> t.getStatus() == IN_PROGRESS).count());
model.addAttribute("doneCount",       allTasks.stream().filter(t -> t.getStatus() == DONE).count());
```

### Pamoka

> **Loginis bug** dažnai atsiranda kai tas pats kintamasis naudojamas dviem tikslams:
> rodymui (filtruotas) ir statistikai (visas).
> **Sprendimas:** atskirti — `allTasks` statistikai, `tasks` rodymui.

---

## Problema #4 — DRY pažeidimas: dubliuotas `applyBackNavigation()`

### Kas buvo blogai

Identiškas metodas egzistavo dviejuose controller'iuose:

```java
// EventController.java — eilutė 182
private void applyBackNavigation(Model model, String from, String defaultUrl, String defaultLabel) {
    if ("dashboard".equals(from)) { ... }
    else { ... }
}

// TaskController.java — eilutė 248
private void applyBackNavigation(Model model, String from, String defaultUrl, String defaultLabel) {
    if ("dashboard".equals(from)) { ... }  // ← identiškas kodas
    else { ... }
}
```

### Kodėl tai blogai

**DRY** — Don't Repeat Yourself. Kai tas pats kodas egzistuoja dviejose vietose:
- jei reikia keisti logiką, keisi **abiejose** vietose
- rizikuoji pamiršti vieną
- klaida vienoje vietoje nebus klaida kitoje — nenuoseklumas

### Kaip pataisėme

Sukūrėme `NavigationUtils.java` (package-private):

```java
class NavigationUtils {
    private NavigationUtils() {}

    static void applyBackNavigation(Model model, String from, String defaultUrl, String defaultLabel) {
        if ("dashboard".equals(from)) {
            model.addAttribute("backUrl", "/dashboard");
            model.addAttribute("backLabel", "Back to dashboard");
            model.addAttribute("fromDashboard", true);
        } else {
            model.addAttribute("backUrl", defaultUrl);
            model.addAttribute("backLabel", defaultLabel);
            model.addAttribute("fromDashboard", false);
        }
    }
}
```

Abu controller'iai dabar kviečia `NavigationUtils.applyBackNavigation(...)`.

### Pamoka

> **Kodėl `static` metodas, ne `@Component`?**
> Metodas neturi jokio state (nelaiko duomenų). Statinis metodas yra paprastesnis ir teisingas.
>
> **Kodėl `package-private` (`class`, ne `public class`)?**
> `NavigationUtils` skirtas tik `controller` paketui. `public` reikštų "gali naudoti bet kas iš bet kur".
> **Principle of Least Privilege** — minimalus reikalingas matomumas, ne daugiau.

---

## Problema #5 — N+1 queries per dalyvį/vykdytoją

### Kas buvo blogai

```java
// EventService.buildParticipants() ir TaskService.applyAssignees()
for (String participantId : participantIds) {
    if (participantId.startsWith("USER_")) {
        Long userId = Long.parseLong(participantId.substring(5));
        userRepository.findById(userId)  // ← 1 DB query PER dalyvį!
                .filter(...)
                .ifPresent(...);
    }
}
```

### Kodėl tai blogai

Event'as su 5 dalyviais → **5 atskiri DB queries**. Tai klasikinis **N+1 anti-pattern**:
`N` duomenų elementų → `N` DB queries vietoje 1.

### Kaip pataisėme

```java
// 1. Išrenkame visus ID pagal tipą
List<Long> userIds = new ArrayList<>();
List<Long> petIds  = new ArrayList<>();
List<Long> memberIds = new ArrayList<>();

for (String participantId : participantIds) {
    if (participantId.startsWith("USER_"))   userIds.add(Long.parseLong(participantId.substring(5)));
    if (participantId.startsWith("PET_"))    petIds.add(Long.parseLong(participantId.substring(4)));
    if (participantId.startsWith("MEMBER_")) memberIds.add(Long.parseLong(participantId.substring(7)));
}

// 2. Krauname visus vienu batch query (WHERE id IN (...))
userRepository.findAllById(userIds).stream().filter(...).forEach(...);
petRepository.findAllById(petIds).stream().filter(...).forEach(...);
familyMemberRepository.findAllById(memberIds).stream().filter(...).forEach(...);
```

Rezultatas: **maks. 3 DB queries** nepriklausomai nuo dalyvių skaičiaus.

### Pamoka

> **N+1 taisyklė:** jei matai `for` + `repository.findById()` — tai beveik visada N+1.
> **Sprendimas:** ištraukti visus ID iš ciklo, tada `findAllById()`.
> `findAllById()` yra `JpaRepository` metodas — nereikia rašyti custom query.
> Spring Data jį įgyvendina su `WHERE id IN (1, 2, 3, ...)` SQL.

---

## Problema #6 — Naming confusion: `getFamilyMembers()` grąžina skirtingus dalykus

### Kas buvo blogai

```java
familyService.getFamilyMembers(familyId)        // → List<User>         (registruoti vartotojai)
familyMemberService.getFamilyMembers(familyId)  // → List<FamilyMember> (nariai be paskyros)
```

Toks pat pavadinimas — visiškai skirtingi tipai ir semantika.

### Kodėl tai blogai

**Meluojantis pavadinimas.** Kitas developer'is (arba tu pati po 3 mėnesių) skaitys kodą ir galvos:
*"Ar tai registruoti vartotojai ar FamilyMember entitijos?"*
Turės tikrinti kiekvieną kartą. Tai švaisto laiką ir kelia klaidų riziką.

### Kaip pataisėme

Pervadinome `FamilyService.getFamilyMembers()` → `getFamilyUsers()`:

```java
// Prieš
public List<User> getFamilyMembers(Long familyId) { ... }

// Po
public List<User> getFamilyUsers(Long familyId) { ... }
```

Atnaujinti visi 3 kvietimai: `FamilyController`, `EventController`, `TaskController`.
**Templates nekito** — jie naudoja record laukų pavadinimus (`page.members`), ne service metodų pavadinimus.

### Pamoka

> **Metodas turi grąžinti tai, ką žada pavadinimas.**
> `getFamilyUsers()` aiškiai sako — grąžina `User` objektus.
> `getFamilyMembers()` + `List<User>` = prieštaravimas.
>
> **Svarbu suprasti:** pervadinus service metodą, templates nekinta —
> Thymeleaf naudoja DTO/record laukų pavadinimus, ne service metodų pavadinimus.
> Šie du lygiai yra atskirti.

---

## Problema #7 — `catch (Exception e)` ir `catch (IllegalArgumentException e)` `AuthController`

### Kas buvo blogai

```java
// AuthController.resetPassword()
try {
    passwordResetService.resetPassword(request);
    return "redirect:/login";
} catch (IllegalArgumentException e) {       // ← generinis Java tipas
    bindingResult.rejectValue("confirmPassword", "error.confirm", e.getMessage());
    return "auth/reset-password";
} catch (Exception e) {                      // ← sugaudo VISKĄ
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    return "redirect:/forgot-password";
}
```

Service meta `InvalidTokenException` — tai žinome tiksliai. Bet `catch (Exception e)` sugaudo viską: `NullPointerException`, DB ryšio klaidas ir kt. — visus siunčia į `/forgot-password`. Jei DB sugriūtų, vartotojas matytų `/forgot-password` vietoje klaidos puslapio.

`catch (IllegalArgumentException e)` — irgi per platu. Generinis Java tipas. Jei kažkas kitas service viduje metų `IllegalArgumentException`, controller'is jį sugautų ir rodytų "Passwords do not match".

### Kodėl try-catch controller'yje yra TEISINGAS (bendra taisyklė)

Try-catch controller'yje yra **tinkamas** kai reikia skirtingų routing sprendimų:

| Situacija | Controller'io sprendimas |
|-----------|--------------------------|
| Viskas gerai | `redirect:/login` |
| Slaptažodžiai nesutampa | re-renderinti formą su klaida lauke |
| Tokenas negaliojantis | `redirect:/forgot-password` su pranešimu |

Tai yra **routing sprendimas**, ne verslo logika. Service meta exceptions, controller nusprendžia kur siųsti vartotoją. MVC Thymeleaf aplikacijoje tai yra teisinga atsakomybių skirtis.

### Kaip pataisėme

Slaptažodžių tikrinimą perkėlėme į **controller'į prieš service kvietimą** — service nebemetą `IllegalArgumentException`:

```java
// Controller — tikrina prieš service kvietimą
if (!request.newPassword().equals(request.confirmPassword())) {
    bindingResult.rejectValue("confirmPassword", "error.confirm", "Passwords do not match.");
    return "auth/reset-password";
}

try {
    passwordResetService.resetPassword(request);
    return "redirect:/login";
} catch (InvalidTokenException e) {          // ← konkretus domain tipas
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    return "redirect:/forgot-password";
}
```

```java
// Service — pašalintas tikrinimas, tik verslo logika
public void resetPassword(ResetPasswordRequest request) {
    PasswordResetToken resetToken = tokenRepository.findByToken(request.token())
            .orElseThrow(InvalidTokenException::new);
    // ...
}
```

### Pamoka

> **Validacija vs verslo logika:**
> - Slaptažodžių sutapimo tikrinimas = **vartotojo įvesties validacija** → controller'is arba DTO
> - Tokeno tikrinimas = **verslo logika** → service
> - Service turi tikėtis gauti jau validuotus duomenis
>
> **Taisyklė:** jei catch bloke turite `Exception` arba generinį Java tipą
> (`IllegalArgumentException`, `RuntimeException`) — tai signalas kad arba naudojate
> per platų exception, arba trūksta konkretaus domain exception.
>
> **catch (Exception e) controller'yje = visada blogai.**

---

## Galutinė suvestinė

| # | Problema | Kategorija | Failas |
|---|----------|-----------|--------|
| 1 | 6 DB queries → 2 (N+1 dashboard) | Performance | `DashboardController` |
| 2 | `RuntimeException` → `NotificationNotFoundException` | Kodo nuoseklumas | `NotificationService` |
| 3 | Statistika skaičiuojama iš filtruoto sąrašo | Loginis bug | `TaskController` |
| 4 | Dubliuotas `applyBackNavigation()` | DRY | `EventController`, `TaskController` |
| 5 | N+1 per participant/assignee | Performance | `EventService`, `TaskService` |
| 6 | `getFamilyMembers()` grąžina `List<User>` | Naming | `FamilyService` |
| 7 | `catch (Exception e)` + `catch (IllegalArgumentException e)` | Error handling | `AuthController` |

---

## Dizaino apsaugos taisyklė

Prieš kiekvieną refactoringą reikia patikrinti:

1. **Ar keičiu model attribute pavadinimą?** → reikia keisti ir Thymeleaf template'ą tame pačiame keitime
2. **Ar keičiu DTO lauko pavadinimą?** → reikia keisti ir visus template'us kurie jį naudoja
3. **Ar šalinu duomenis kuriuos template naudoja?** → template'as tyliai suges (`null` arba `LazyInitializationException`)
4. **Ar keičiu lazy loading strategiją?** → Thymeleaf gali gauti `LazyInitializationException` po transakcijos

Šeimos duomenų kontraktas (negalima pažeisti):
- `page.members` — registruoti vartotojai
- `page.familyMembers` — nariai be paskyros
- `page.pets` — augintiniai
- `page.parentInviteCode`, `page.kidInviteCode` — kvietimo kodai

---

*Peržiūra atlikta: 2026-04-18*
