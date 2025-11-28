package com.iset.projet_integration.Controller;

import com.iset.projet_integration.dto.UserDto;
import com.iset.projet_integration.Entities.User;
import com.iset.projet_integration.Repository.UserRepository;
import com.iset.projet_integration.Service.KeycloakAdminService;
import org.keycloak.admin.client.Keycloak;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/keycloak")
public class KeycloakController {

    private final Keycloak keycloak;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    public KeycloakController(Keycloak keycloak, UserRepository userRepository, KeycloakAdminService keycloakAdminService) {
        this.keycloak = keycloak;
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
        System.out.println("👉 KEYCLOAK INJECTED = " + keycloak);
    }

    // -------------------------------
    // REGISTER USER
    // -------------------------------
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDto dto) {
        // 1️⃣ Créer l'utilisateur dans Keycloak
        ResponseEntity<User> keycloakResponse = keycloakAdminService.createUser(dto);
        if (!keycloakResponse.getStatusCode().is2xxSuccessful()) {
            // Retourner l’erreur Keycloak (conflit, erreur, etc.)
            return ResponseEntity.status(keycloakResponse.getStatusCode())
                    .body(keycloakResponse.getBody());
        }

        // 2️⃣ Si succès, enregistrer dans MongoDB
        User user = new User();
        user.setIdentifiant(dto.getIdentifiant());
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPassword(dto.getPassword());
        user.setRole(User.Role.valueOf(dto.getRole().name()));


        User savedUser = userRepository.save(user);

        return ResponseEntity.ok(savedUser);
    }

    // -------------------------------
    // LOGIN USER
    // -------------------------------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=password"
                    + "&client_id=angular-client"
                    + "&username=" + username
                    + "&password=" + password;

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://localhost:8080/realms/projet-integration/protocol/openid-connect/token",
                    request,
                    Map.class
            );

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    // -------------------------------
    // TEST KEYCLOAK CONNECTION
    // -------------------------------
    @GetMapping("/test-keycloak")
    public ResponseEntity<String> testKeycloak() {
        try {
            System.out.println("🧪 Testing Keycloak connection...");

            // Test 1: Lister les realms
            var realms = keycloak.realms().findAll();
            System.out.println("✅ Realms count: " + realms.size());

            // Test 2: Accéder au realm projet-integration
            var realmResource = keycloak.realm("projet-integration");
            var realmInfo = realmResource.toRepresentation();
            System.out.println("✅ Realm found: " + realmInfo.getRealm());

            // Test 3: Lister les rôles
            var roles = realmResource.roles().list();
            System.out.println("✅ Roles available: " + roles.stream().map(r -> r.getName()).collect(Collectors.toList()));

            // Test 4: Vérifier les clients
            var clients = realmResource.clients().findAll();
            System.out.println("✅ Clients count: " + clients.size());

            return ResponseEntity.ok("Keycloak connection OK! Realm: " + realmInfo.getRealm());

        } catch (Exception e) {
            System.err.println("❌ Keycloak test failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Keycloak error: " + e.getMessage());
        }
    }

}
