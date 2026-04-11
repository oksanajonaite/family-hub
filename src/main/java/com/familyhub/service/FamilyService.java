package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final UserRepository userRepository;

    // @Transactional — visas metodas vyksta vienoje DB transakcijoje.
    // Jei bet kurioje vietoje įvyksta exception — VISI pakeitimai atšaukiami.
    // Pvz.: jei familyRepository.save() pavyko, bet userRepository.save() nepavyko —
    // Family irgi neišsaugoma (rollback). Duomenų vientisumas garantuotas.
    @Transactional
    public Family createFamily(CreateFamilyRequest request, User creator) {
        // Business taisyklė: 1 vartotojas = 1 šeima
        if (creator.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        // Builder pattern — aiškesnis nei konstruktorius su 10 parametrų.
        // Generuoja tik norimų laukų turinį, kitus palieka null/default.
        Family family = Family.builder()
                .name(request.name())
                .createdBy(creator)
                .build();
        // Pirmasis save() — gauna DB sugeneruotą id (be jo negalime priskirti šeimai)
        family = familyRepository.save(family);

        // Susiejame vartotoją su šeima ir išsaugome
        creator.setFamily(family);
        userRepository.save(creator);

        // Iš karto sukuriame pirmą invite code — PARENT galės pakviesti šeimos narius
        generateInviteCode(family, creator);

        return family;
    }

    @Transactional
    public Family joinByInviteCode(String code, User user) {
        if (user.getFamily() != null) {
            throw new UserAlreadyInFamilyException();
        }

        // findByCodeAndUsedFalse — repository metodas: randa tik nenaudotą kodą.
        // .filter() — papildoma patikra: ar kodas dar negaliojęs (expiresAt > dabar).
        // Atskyrėme nuo repository metodo, nes datos logika priklauso service'ui.
        // .orElseThrow() — jei kodas nerastas arba filtras nepraėjo — meta exception.
        // InvalidInviteCodeException::new — method reference, lygus () -> new InvalidInviteCodeException()
        FamilyInvite invite = familyInviteRepository
                .findByCodeAndUsedFalse(code)
                .filter(i -> i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidInviteCodeException::new);

        // Kodas daugkartinis — visi šeimos nariai naudoja tą patį kodą kol jis negaliojęs.
        // NebežymIme invite kaip used — jis lieka aktyvus kitiems nariam.
        // invite.getFamily() veikia čia saugiai, nes esame @Transactional kontekste —
        // Hibernate sesija aktyvi, lazy loading veikia.
        user.setFamily(invite.getFamily());
        userRepository.save(user);

        return invite.getFamily();
    }

    // Pridedame čia — controller neturi kreiptis į UserRepository tiesiogiai
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional
    public FamilyInvite generateInviteCode(Family family, User requestingUser) {
        // UUID.randomUUID() — generuoja atsitiktinį universaliai unikalų identifikatorių.
        // Pvz.: "550e8400-e29b-41d4-a716-446655440000"
        // .replace("-", "") — pašaliname brūkšnelius: "550e8400e29b41d4a716446655440000"
        // .substring(0, 12) — paimame pirmus 12 simbolių: "550e8400e29b"
        // .toUpperCase() — didžiosios raidės: "550E8400E29B"
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        FamilyInvite invite = FamilyInvite.builder()
                .family(family)
                .code(code)
                .createdBy(requestingUser)
                // plusDays(7) — kodas galioja 7 dienas nuo sukūrimo
                .expiresAt(LocalDateTime.now().plusDays(7))
                .used(false)
                .build();

        return familyInviteRepository.save(invite);
    }

    // readOnly = true — Hibernate neatlieks "dirty checking".
    // Dirty checking = Hibernate tikrina ar objektai pasikeitė ir rašo UPDATE.
    // Jei žinome kad tik skaitome — galime šią optimizaciją įjungti.
    @Transactional(readOnly = true)
    public Family getFamily(Long familyId) {
        // orElseThrow su lambda — jei nerastas, meta FamilyNotFoundException
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
    }

    @Transactional(readOnly = true)
    public List<User> getFamilyMembers(Long familyId) {
        return userRepository.findAllByFamilyId(familyId);
    }

    @Transactional(readOnly = true)
    public String getActiveInviteCode(Long familyId) {
        return familyInviteRepository
                // Ilgas metodo pavadinimas — Spring Data JPA interpretuoja jį kaip SQL:
                // WHERE family_id = ? AND used = false AND expires_at > ?
                // ORDER BY created_at DESC LIMIT 1
                .findTopByFamilyIdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(familyId, LocalDateTime.now())
                // .map() — jei Optional turi reikšmę, paima tik code lauką
                .map(FamilyInvite::getCode)
                // .orElse(null) — jei nėra aktyvaus kodo, grąžina null
                .orElse(null);
    }
}
