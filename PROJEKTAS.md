# Family Hub - projekto aprasas

Sis failas yra trumpas, aktualus lietuviskas projekto aprasas paciam projekto autoriui.

Jei reikia:
- dizaino taisykliu -> ziurek [Design.md](C:\Users\Lenovo\Project hub\family-hub\Design.md)
- UI techniniu kontraktu -> ziurek [UI_CONTRACTS.md](C:\Users\Lenovo\Project hub\family-hub\UI_CONTRACTS.md)
- template atsakomybiu zemelapio -> ziurek [src/main/resources/templates/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\templates\README.md)
- css atsakomybiu zemelapio -> ziurek [src/main/resources/static/css/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\resources\static\css\README.md)
- controller logikos zemelapio -> ziurek [src/main/java/com/familyhub/controller/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\java\com\familyhub\controller\README.md)
- service logikos zemelapio -> ziurek [src/main/java/com/familyhub/service/README.md](C:\Users\Lenovo\Project hub\family-hub\src\main\java\com\familyhub\service\README.md)

## Apie projekta

**Family Hub** - seimos valdymo ziniatinklio programa.

Ji skirta vienoje vietoje tvarkyti:
- seimos kalendoriu
- uzduotis
- seimos narius
- gyvunus
- pranesimus

Projektas kuriamas kaip portfolio darbas su Spring Boot ir Thymeleaf.

## Technologijos

- Java 17
- Spring Boot 3
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Thymeleaf
- Bootstrap 5
- Maven

## Pagrindine verslo logika

- 1 vartotojas priklauso tik 1 seimai
- rolės:
  - `PARENT` - seimos administratorius
  - `KID` - ribotu teisiu vartotojas
  - `ADMIN` - platformos administratorius
- `FamilyMember` - seimos narys be paskyros
- `Pet` - gyvunas be paskyros
- `FamilyMember` ir `Pet` gali buti priskiriami prie renginiu ir uzduociu
- privatūs renginiai matomi tik kurejui
- seimai galioja du invite kodai:
  - `PARENT`
  - `KID`

## Dabartine produkto kryptis

Produktas turi jaustis:
- ramus
- siltas
- lengvas
- lengvai skanuojamas
- ne admin tipo

Svarbiausios UI taisykles:
- sidebar minimalus
- notifications pasiekiami per varpeli, ne per sidebar
- pagrindinis create entry point yra topbar `Add`
- dashboard yra pagrindinis darbo ekranas
- forms turi buti kompaktiskos ir negasdinti

## Pagrindiniai puslapiai

- `Dashboard` - kalendorius + trumpa siandienos informacija + desineje upcoming / due soon
- `Family` - registered members, members without account, pets, family access
- `Tasks` - seimos workflow sarasas
- `Events` - seimos schedule sarasas
- `Notifications` - inbox tipo pranesimu ekranas
- `Admin` - tik ADMIN rolei

## Kodo struktura

Pagrindiniai paketai:
- `config`
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `mapper`
- `security`
- `exception`

Trumpai:
- `controller` - route'ai, modeliai, redirect'ai
- `service` - verslo logika
- `repository` - DB uzklausos
- `entity` - JPA lenteliu modeliai
- `dto` - request/response objektai

## Svarbu refaktorinant

Refaktorinant reikia saugoti:
- `displayName` naudojima vietoje email UI
- contextual back navigation
- dashboard summary ir right panel logika
- family page struktura
- kompaktiskas task ir event formas
- topbar bell -> notifications flow
- unread notification count tiekima i shell

Jei kyla klausimas ar kazka keisti:
- pirma tikrink `Design.md`
- po to `UI_CONTRACTS.md`

## Tikslas

Tikslas yra ne tik kad projektas veiktu, bet ir kad jis atrodytu kaip vientisas, ramus, product-level portfolio darbas, kurio UI ir backend logika nesugriuva po refaktoriu.
