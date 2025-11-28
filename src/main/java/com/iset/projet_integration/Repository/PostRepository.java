package com.iset.projet_integration.Repository;

import com.iset.projet_integration.Entities.Demande;
import com.iset.projet_integration.Entities.Post;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PostRepository extends MongoRepository<Post, String> {
    List<Post> findAllByOrderByDateCreationDesc();
    List<Post> findByTypeDemande(Demande.TypeDemande typeDemande);
}
