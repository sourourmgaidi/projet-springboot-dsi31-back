package com.iset.projet_integration.Service;

import com.iset.projet_integration.Entities.Demande;
import com.iset.projet_integration.Entities.Notification;
import com.iset.projet_integration.Entities.Post;
import com.iset.projet_integration.Entities.User;
import com.iset.projet_integration.Repository.DemandeRepository;
import com.iset.projet_integration.Repository.NotificationRepository;
import com.iset.projet_integration.Repository.PostRepository;
import com.iset.projet_integration.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DemandeService {

    private final DemandeRepository demandeRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public DemandeService(DemandeRepository demandeRepository,
                          NotificationRepository notificationRepository,
                          UserRepository userRepository,
                          PostRepository postRepository) {
        this.demandeRepository = demandeRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    // 🔹 Ajouter une demande simple
    public Demande creerDemande(Demande demande) {
        demande.setEtat(Demande.EtatDemande.EN_ATTENTE);
        Demande saved = demandeRepository.save(demande);

        // Notification vers l'admin
        List<User> admins = userRepository.findByRole(User.Role.ADMIN);
        if (!admins.isEmpty()) {
            Notification notif = new Notification();
            notif.setMessage("Nouvelle demande reçue de " + demande.getUser().getIdentifiant());
            notif.setType(Notification.TypeNotification.valueOf(demande.getTypeDemande().name()));
            notif.setStatut(Notification.StatutNotification.EN_ATTENTE);
            notif.setExpediteur(demande.getUser());
            notif.setDestinataire(admins.get(0));
            notif.setDemande(saved);
            notificationRepository.save(notif);
        }
        return saved;
    }

    // 🔹 CORRIGÉ : Ajouter une demande avec images/vidéos
    public Demande creerDemandeAvecFichiers(Demande demande,
                                            List<MultipartFile> images,
                                            List<MultipartFile> videos,
                                            String userIdKeycloak) { // Renommer le paramètre

        // CORRECTION : Récupérer l'utilisateur par ID au lieu de identifiant
        User user = userRepository.findById(userIdKeycloak)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + userIdKeycloak));

        demande.setUser(user); // 🔥 Assigner l'utilisateur

        // Convertir les fichiers en chemins (ou URL)
        if (images != null) {
            List<String> imagePaths = images.stream()
                    .map(this::saveFile)
                    .collect(Collectors.toList());
            demande.setImageUrls(imagePaths);
        }

        if (videos != null) {
            List<String> videoPaths = videos.stream()
                    .map(this::saveFile)
                    .collect(Collectors.toList());
            demande.setVideoUrls(videoPaths);
        }

        return creerDemande(demande);
    }

    // Méthode fictive pour stocker un fichier et retourner son chemin
    private String saveFile(MultipartFile file) {
        return file.getOriginalFilename();
    }

    public List<Demande> listerDemandes() {
        return demandeRepository.findAll();
    }

    public Demande getDemandeById(String id) {
        return demandeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée avec l'ID: " + id));
    }

    public List<Demande> getDemandesByEtat(Demande.EtatDemande etat) {
        return demandeRepository.findByEtat(etat);
    }

    public Demande updateDemande(String id, Demande demandeDetails) {
        Demande demande = getDemandeById(id);
        demande.setContenu(demandeDetails.getContenu());
        demande.setTypeDemande(demandeDetails.getTypeDemande());
        demande.setImageUrls(demandeDetails.getImageUrls());
        demande.setVideoUrls(demandeDetails.getVideoUrls());
        return demandeRepository.save(demande);
    }

    public void deleteDemande(String id) {
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(notif -> notif.getDemande() != null && notif.getDemande().getId().equals(id))
                .collect(Collectors.toList());
        notificationRepository.deleteAll(notifications);
        demandeRepository.deleteById(id);
    }

    public Demande updateDemandeNeedy(String id, User user, Demande demandeDetails) {
        Demande demande = getDemandeById(id);
        if (!demande.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Vous n'êtes pas autorisé à modifier cette demande.");
        if (demande.getEtat() != Demande.EtatDemande.EN_ATTENTE)
            throw new RuntimeException("Seules les demandes EN_ATTENTE peuvent être modifiées.");

        demande.setContenu(demandeDetails.getContenu());
        demande.setTypeDemande(demandeDetails.getTypeDemande());
        demande.setImageUrls(demandeDetails.getImageUrls());
        demande.setVideoUrls(demandeDetails.getVideoUrls());

        return demandeRepository.save(demande);
    }

    public void deleteDemandeNeedy(String id, User user) {
        Demande demande = getDemandeById(id);
        if (!demande.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Vous n'êtes pas autorisé à supprimer cette demande.");
        if (demande.getEtat() != Demande.EtatDemande.EN_ATTENTE)
            throw new RuntimeException("Seules les demandes EN_ATTENTE peuvent être supprimées.");

        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(notif -> notif.getDemande() != null && notif.getDemande().getId().equals(id))
                .collect(Collectors.toList());
        notificationRepository.deleteAll(notifications);

        demandeRepository.delete(demande);
    }

    public List<Notification> traiterDemande(String demandeId, String action) {
        Demande demande = getDemandeById(demandeId);
        User needy = demande.getUser();

        // Récupérer un admin (avec vérification)
        List<User> admins = userRepository.findByRole(User.Role.ADMIN);
        if (admins.isEmpty()) {
            throw new RuntimeException("Aucun administrateur trouvé");
        }
        User admin = admins.get(0);

        List<Notification> notificationsCrees = new ArrayList<>();

        if (action.equalsIgnoreCase("accepter")) {
            demande.setEtat(Demande.EtatDemande.ACCEPTEE);
            demandeRepository.save(demande);

            // Créer Post avec images et vidéos
            Post post = new Post();
            post.setContenu(demande.getContenu());

            // TEMPORAIREMENT COMMENTÉ - À CORRIGER APRÈS AVOIR VU LES ENTITÉS
            // post.setTypePost(demande.getTypeDemande()); // ERREUR - MÉTHODE INEXISTANTE

            post.setUser(needy);
            post.setImageUrls(demande.getImageUrls());
            post.setVideoUrls(demande.getVideoUrls());
            postRepository.save(post);

            // Notification pour le needy
            Notification notifNeedy = new Notification();
            notifNeedy.setMessage("Votre demande a été acceptée ✅");
            notifNeedy.setType(Notification.TypeNotification.valueOf(demande.getTypeDemande().name()));
            notifNeedy.setStatut(Notification.StatutNotification.ACCEPTEE);
            notifNeedy.setExpediteur(admin);
            notifNeedy.setDestinataire(needy);
            notifNeedy.setDemande(demande);
            notificationRepository.save(notifNeedy);
            notificationsCrees.add(notifNeedy);

            // Notifications pour Donor et Association
            List<User> recepteurs = new ArrayList<>();
            recepteurs.addAll(userRepository.findByRole(User.Role.DONNATEUR));
            recepteurs.addAll(userRepository.findByRole(User.Role.ASSOCIATION));

            for (User user : recepteurs) {
                Notification notifDon = new Notification();
                notifDon.setMessage("Nouvelle demande publiée : " + demande.getContenu());
                notifDon.setType(Notification.TypeNotification.valueOf(demande.getTypeDemande().name()));
                notifDon.setStatut(Notification.StatutNotification.ACCEPTEE);
                notifDon.setExpediteur(needy);
                notifDon.setDestinataire(user);

                // TEMPORAIREMENT COMMENTÉ - À CORRIGER APRÈS AVOIR VU LES ENTITÉS
                // notifDon.setPost(post); // ERREUR - MÉTHODE INEXISTANTE

                notificationRepository.save(notifDon);
                notificationsCrees.add(notifDon);
            }

            // Supprimer la demande initiale
            demandeRepository.delete(demande);

        } else if (action.equalsIgnoreCase("refuser")) {
            demande.setEtat(Demande.EtatDemande.REFUSEE);
            demandeRepository.save(demande);

            // Notification pour le needy
            Notification notifRefus = new Notification();
            notifRefus.setMessage("Votre demande '" + demande.getContenu() + "' a été refusée ❌");
            notifRefus.setType(Notification.TypeNotification.valueOf(demande.getTypeDemande().name()));
            notifRefus.setStatut(Notification.StatutNotification.REFUSEE);
            notifRefus.setExpediteur(admin);
            notifRefus.setDestinataire(needy);
            notifRefus.setDemande(demande);
            notificationRepository.save(notifRefus);
            notificationsCrees.add(notifRefus);

            // Supprimer la demande
            demandeRepository.delete(demande);
        }

        return notificationsCrees;
    }
}