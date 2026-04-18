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

## Problema #8 — `TaskController` priklausė nuo 3 service'ų + verslo logika controller'yje

### Kas buvo blogai

```java
// TaskController turėjo 3 priklausomybes:
private final TaskService taskService;
private final FamilyService familyService;
private final FamilyMemberService familyMemberService;

// buildFormData() — verslo logika controller'yje:
private TaskFormData buildFormData(Long taskId, List<String> assigneeIds, CustomUserDetails currentUser) {
    return new TaskFormData(
            familyService.getFamilyUsers(currentUser.getFamilyId()),
            familyMemberService.getFamilyMembers(currentUser.getFamilyId()),
            ...
    );
}

// toEditRequest() logika — transformacija controller'yje:
List<String> assigneeIds = new ArrayList<>();
task.getAssignedUsers().forEach(u -> assigneeIds.add("USER_" + u.getId()));
task.getAssignedMembers().forEach(m -> assigneeIds.add("MEMBER_" + m.getId()));
UpdateTaskRequest request = new UpdateTaskRequest(task.getTitle(), ...);

// catch(Exception e) — per platus:
try {
    task = taskService.getTaskByIdForFamily(id, currentUser.getFamilyId());
} catch (Exception e) {
    // sugauna VISKĄ — net NullPointerException
}
```

### Kodėl tai blogai

1. **SRP pažeidimas** — controller'is žino apie `FamilyService` ir `FamilyMemberService`. Jis neturėtų rūpintis kaip gauti šeimos narius — tai `TaskService` atsakomybė.
2. **Verslo logika controller'yje** — `buildFormData()` ir entitijos→DTO transformacija priklauso service'ui.
3. **`catch(Exception e)`** — sulaiko bet kokią klaidą, net programavimo klaidas (`NullPointerException`, `ClassCastException`). Klaidos tampa nematomos.

### Kaip pataisėme

```java
// TaskService — du nauji metodai:
public UpdateTaskRequest toEditRequest(TaskItem task) {
    List<String> assigneeIds = new ArrayList<>();
    task.getAssignedUsers().forEach(u -> assigneeIds.add("USER_" + u.getId()));
    task.getAssignedMembers().forEach(m -> assigneeIds.add("MEMBER_" + m.getId()));
    return new UpdateTaskRequest(task.getTitle(), task.getDescription(),
            task.getPriority(), assigneeIds, task.getDueDate());
}

@Transactional(readOnly = true)
public TaskFormData buildTaskFormData(Long taskId, List<String> assigneeIds, Long familyId) {
    return new TaskFormData(
            userRepository.findAllByFamilyId(familyId),
            familyMemberRepository.findAllByFamilyId(familyId),
            List.of(TaskPriority.values()),
            taskId,
            assigneeIds
    );
}

// TaskController — tik viena priklausomybė:
private final TaskService taskService;

// editForm() — konkretus exception tipas:
try {
    TaskItem task = taskService.getTaskByIdForFamily(id, currentUser.getFamilyId());
    UpdateTaskRequest request = taskService.toEditRequest(task);
    model.addAttribute("formData", taskService.buildTaskFormData(id, request.assigneeIds(), currentUser.getFamilyId()));
    ...
} catch (TaskNotFoundException | AccessDeniedException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Task not found.");
    return "redirect:/tasks";
}
```

### Pamoka

> **Controller'is turi tik:**
> - Gauti HTTP užklausą
> - Perduoti duomenis į service'ą
> - Grąžinti view pavadinimą arba redirect
>
> **Controller'is negali:**
> - Turėti kelis service'us kurie nesusiję su jo pagrindiniu darbu
> - Daryti entitijos→DTO transformacijas
> - Kurti form helper duomenis (`buildFormData()`)
>
> **`catch(Exception e)` taisyklė:**
> - Naudok tik konkretus domain exception tipus: `catch(TaskNotFoundException | AccessDeniedException e)`
> - Jei nežinai ko tikėtis — tai ženklas kad service'as neturi pakankamai konkrečių exception tipų

---

## Problema #9 — `FamilyController` priklausė nuo 4 service'ų + entity grafas controller'yje

### Kas buvo blogai

```java
// FamilyController turėjo 4 priklausomybes:
private final FamilyService familyService;
private final FamilyMemberService familyMemberService;
private final PetService petService;
private final UserDetailsService userDetailsService;

// familyPage() — 6 atskiri service kviečiami iš 3 service'ų:
model.addAttribute("page", new FamilyPageData(
        familyService.getFamily(familyId),
        familyService.getFamilyUsers(familyId),
        familyMemberService.getFamilyMembers(familyId),  // ← kitas service
        petService.getFamilyPets(familyId),              // ← dar kitas service
        familyService.getActiveInviteCode(familyId, Role.PARENT),
        familyService.getActiveInviteCode(familyId, Role.KID),
        currentUser.getId()
));

// generateInviteCode() — controller naviguoja entity grafą:
User user = familyService.getUserById(currentUser.getId());
familyService.generateInviteCode(user.getFamily(), user, role); // ← user.getFamily() controller'yje!

// Kartojamas pattern 4 metoduose:
User user = familyService.getUserById(currentUser.getId()); // createFamily
User user = familyService.getUserById(currentUser.getId()); // joinFamily
User user = familyService.getUserById(currentUser.getId()); // generateInviteCode
User parent = familyService.getUserById(currentUser.getId()); // removeMember
```

### Kodėl tai blogai

1. **SRP pažeidimas** — `FamilyMemberService` ir `PetService` controller'yje tik dėl `familyPage()`. Controller'is neturėtų žinoti iš kokių šaltinių surenkamas puslapis.
2. **Entity grafas controller'yje** — `user.getFamily()` yra Hibernate entity navigacija. Controller'is neturėtų dereferencuoti entitys — tai service atsakomybė.
3. **DRY pažeidimas** — `getUserById(currentUser.getId())` kartojamas 4 kartus. Jei keičiasi ID tipas ar logika — keisti 4 vietose.
4. **Nereikalingas viešas metodas** — `getUserById()` buvo `public` tik dėl controller'io. Po refactoringo jis pašalintas.

### Kaip pataisėme

```java
// FamilyService — naujas metodas agregavimui:
@Transactional(readOnly = true)
public FamilyPageData buildFamilyPageData(Long familyId, Long currentUserId) {
    return new FamilyPageData(
            familyRepository.findById(familyId).orElseThrow(...),
            userRepository.findAllByFamilyId(familyId),
            familyMemberRepository.findAllByFamilyId(familyId),
            petRepository.findAllByFamilyId(familyId),
            getActiveInviteCode(familyId, Role.PARENT),
            getActiveInviteCode(familyId, Role.KID),
            currentUserId
    );
}

// Service metodai priima ID, ne entity:
public Family createFamily(CreateFamilyRequest request, Long creatorId)  // ne User creator
public Family joinByInviteCode(String code, Long userId)                  // ne User user
public void generateInviteCode(Long familyId, Long requestingUserId, Role role)
public void removeMember(Long memberId, Long requestingParentId, Long familyId)

// generateInviteCode vidinė logika atskirta į private metodą:
private void createInviteCode(Family family, User requestingUser, Role role) { ... }

// FamilyController — tik 2 priklausomybės:
private final FamilyService familyService;
private final UserDetailsService userDetailsService;

// Controller'is perduoda tik ID:
familyService.createFamily(request, currentUser.getId());
familyService.joinByInviteCode(request.inviteCode(), currentUser.getId());
familyService.generateInviteCode(currentUser.getFamilyId(), currentUser.getId(), role);
familyService.removeMember(id, currentUser.getId(), currentUser.getFamilyId());
```

### Pamoka

> **Entity grafas controller'yje = blogas ženklas:**
> `user.getFamily()`, `event.getCreator().getId()`, `task.getAssignedUsers()` controller'yje
> reiškia kad controller'is žino per daug apie domeną.
> Service turi priimti primityvius tipus (Long, String) ir pats surinkti reikalingus entity.
>
> **Service metodo parametrų taisyklė:**
> Jei controller'is turi kviesti `getUserById()` tik tam, kad perduotų `User` į kitą metodą —
> tas metodas turi priimti `Long userId` ir pats fetčinti.
>
> **Agregavimas priklauso service'ui:**
> Jei puslapis reikalauja duomenų iš N skirtingų šaltinių — service turi turėti
> vieną metodą `buildPageData()` kuris tai padaro viduje.

---

## Problema #10 — `FamilyMemberController`: saugumo spraga + `RuntimeException` + transformacija controller'yje

### Kas buvo blogai

```java
// FamilyMemberService — generiniai exceptions:
.orElseThrow(() -> new RuntimeException("Family member not found: " + memberId));  // ← ne domain exception
.orElseThrow(() -> new IllegalStateException("Family not found"));                 // ← ne domain exception

// FamilyMemberService — priima security objektą:
public FamilyMember createMember(CreateFamilyMemberRequest request, CustomUserDetails currentUser)

// FamilyMemberService — klaidinantys komentarai:
// Only PARENT can add an account-less family member — enforced in the controller
// (bet controller'yje rolės tikrinimo nebuvo!)

// FamilyMemberController — entity→DTO transformacija:
FamilyMember member = familyMemberService.getMemberById(id, currentUser.getFamilyId());
UpdateFamilyMemberRequest request = new UpdateFamilyMemberRequest(
        member.getName(), member.getDateOfBirth()  // ← priklauso service'ui
);

// FamilyMemberController — nėra exception handling:
FamilyMember member = familyMemberService.getMemberById(id, currentUser.getFamilyId());
// jei narys nerastas — vartotojas gauna 500 klaidos puslapį

// FamilyMemberController — NĖRA rolės tikrinimo (saugumo spraga):
// KID vartotojas galėjo POST'inti į /members/create ir sukurti narį!
```

### Kodėl tai blogai

1. **`RuntimeException` / `IllegalStateException`** — neinformatyvūs, negali jų gaudyti konkrečiai
2. **`CustomUserDetails` service'e** — sluoksnių pažeidimas: service'as neturėtų žinoti apie Spring Security objektus
3. **Klaidinantys komentarai** — "enforced in the controller", bet tikrinimo nebuvo. Tokios pastabos kode yra pavojingos — jose pasitikima, bet realybė kitokia
4. **Saugumo spraga** — autorizacijos tikrinimo nebuvimas leidžia KID vartotojui kviesti PARENT operacijas

### Kaip pataisėme

```java
// Naujas exception:
public class FamilyMemberNotFoundException extends RuntimeException {
    public FamilyMemberNotFoundException(Long id) {
        super("Family member not found: " + id);
    }
}

// FamilyMemberService — tvarkingi exceptions:
.orElseThrow(() -> new FamilyMemberNotFoundException(memberId));
.orElseThrow(() -> new FamilyNotFoundException(familyId));

// FamilyMemberService — priima Long, ne CustomUserDetails:
public FamilyMember createMember(CreateFamilyMemberRequest request, Long familyId)

// FamilyMemberService — naujas toEditRequest():
public UpdateFamilyMemberRequest toEditRequest(Long memberId, Long familyId) {
    FamilyMember member = getMemberById(memberId, familyId);
    return new UpdateFamilyMemberRequest(member.getName(), member.getDateOfBirth());
}

// FamilyMemberController — rolės tikrinimas VISUOSE veiksmuose:
if (currentUser.getRole() != Role.PARENT) {
    redirectAttributes.addFlashAttribute("errorMessage", "Only parents can add family members.");
    return "redirect:/family";
}

// FamilyMemberController — exception handling:
try {
    UpdateFamilyMemberRequest request = familyMemberService.toEditRequest(id, currentUser.getFamilyId());
    ...
} catch (FamilyMemberNotFoundException | AccessDeniedException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Member not found.");
    return "redirect:/family";
}
```

### Pamoka

> **"Enforced in the controller" komentaras = raudonas vėliavėlis:**
> Tai reiškia kad kažkas turėtų tikrinti, bet tikrinimas yra kažkur kitur.
> Tokios pastabos dažnai "pamirštamos" — kodas vystosi, tikrinimas dingsta, komentaras lieka.
> Geriau: arba tikrinti service'e su `AccessDeniedException`, arba tikrinti controller'yje su konkrečiu kodu.
>
> **Service metodai neturi priimti Spring Security objektų:**
> `CustomUserDetails`, `Authentication`, `Principal` — tai Spring Security abstrakcijos.
> Service'as turėtų dirbti su primityviais tipais: `Long familyId`, `Long userId`, `Role role`.
>
> **`RuntimeException` / `IllegalStateException` service'e = visada blogai:**
> Kiekvienas domeninis "nerastas" atvejis turi turėti savo exception klasę.
> Tai leidžia gaudyti konkrečiai ir rodyti tinkamą klaidos pranešimą.

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
| 8 | 3 service priklausomybės + verslo logika + `catch(Exception e)` | SRP + Error handling | `TaskController` |
| 9 | 4 service priklausomybės + entity grafas + `getUserById()` kartojimas | SRP + DRY | `FamilyController` |
| 10 | `RuntimeException` + `CustomUserDetails` service'e + saugumo spraga | Exception handling + Security | `FamilyMemberController` |

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
