package com.iset.projet_integration.Service;

import com.iset.projet_integration.Entities.Post;
import com.iset.projet_integration.Repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Map<String, Object> toggleLike(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if(post.getLikedByUserIds() == null) {
            post.setLikedByUserIds(new ArrayList<>());
        }

        boolean liked;
        if(post.getLikedByUserIds().contains(userId)) {
            post.getLikedByUserIds().remove(userId);
            liked = false;
        } else {
            post.getLikedByUserIds().add(userId);
            liked = true;
        }

        post.setLikesCount(post.getLikedByUserIds().size());
        postRepository.save(post);

        // Retourner aussi "hasLiked" pour que le frontend sache directement l’état
        return Map.of(
                "liked", liked,
                "likesCount", post.getLikesCount(),
                "hasLiked", liked, // <- ajouté
                "likedByUserIds", post.getLikedByUserIds()
        );
    }


    public boolean hasUserLiked(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        List<String> likedUsers = post.getLikedByUserIds();
        return likedUsers != null && likedUsers.contains(userId);
    }

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }
}
