package pl.szelag.ai_knowledge_base.dto;

/**
 * Data structure containing basic profile information for the currently
 * authenticated user.
 */
public record UserProfileResponse(
        String name,
        String email,
        String picture,
        String givenName,
        String familyName) {
}