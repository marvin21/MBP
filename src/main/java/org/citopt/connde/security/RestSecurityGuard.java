package org.citopt.connde.security;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.citopt.connde.domain.user_entity.UserEntity;
import org.citopt.connde.repository.UserEntityRepository;
import org.citopt.connde.service.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides methods for checking user permissions and retrieving permissible user entities for incoming REST requests.
 */
@Component
public class RestSecurityGuard {

    @Autowired
    private UserEntityService userEntityService;

    /**
     * Checks whether the current user has a certain permissions for a given user entity.
     *
     * @param userEntity The user entity to check
     * @param permission The permission to check
     * @return True, if the user has the permission; false otherwise
     */
    public boolean checkPermission(UserEntity userEntity, String permission) {
        //Sanity check
        if (userEntity == null) {
            throw new IllegalArgumentException("User entity must not be null.");
        } else if ((permission == null) || (permission.isEmpty())) {
            throw new IllegalArgumentException("Permission must not be null or empty.");
        }

        //Perform check for permission
        return userEntityService.isUserPermitted(userEntity, permission);
    }

    /**
     * This method takes a page with user entities and replaces its content consistently with a certain
     * set of user entities of the current user from an user entity repository. This is helpful for pageable GET
     * requests that are supposed to retrieve all user entities from a certain repository for the current user, because
     * the @PostFilter annotation that would normally take care of discarding all entities of other users in
     * the repository interface definition does does not support pages. By calling this method, the @PostAuthorization
     * annotation can now be used for this purpose instead.
     * <p>
     * Example within a repository interface:
     * <pre>
     * &#64;Query("{_id: null}") //Fail fast
     * &#64;PostAuthorize("&#64;RestSecurityGuard.retrieveUserEntities(returnObject, #pageable, &#64;deviceRepository)")
     * Page&#60;Device&#62; findAll(Pageable pageable);
     * </pre>
     *
     * @param page       The page originally generated by processing of the GET request. Its content will be discarded
     *                   within this method.
     * @param pageable   The pageable that was provided within the GET requests and describes which set of user entities
     *                   of the current user is supposed to be retrieved from the repository
     * @param repository The repository from which the user entities of the current user are supposed to be retrieved
     * @return False, in case the page could not be modified using reflections; otherwise always true
     */
    @SuppressWarnings("unchecked")
    public boolean retrieveUserEntities(Page<UserEntity> page, Pageable pageable, UserEntityRepository repository) {
        //Get all device user entities the current user has access to
        List<UserEntity> userEntities = userEntityService.getUserEntitiesFromRepository(repository);

        //Extract the content of the passed page using reflection
        List content = null;
        try {
            content = (List) FieldUtils.readField(page, "content", true);
        } catch (IllegalAccessException ignored) {
        }

        //Sanity check
        if (content == null) {
            return false;
        }

        //Calculate start and end of page from pageable
        int start = pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), userEntities.size());

        //Replace elements of content list with the new page elements
        content.clear();
        content.addAll(userEntities.subList(start, end));

        //Update total counter using reflection
        try {
            FieldUtils.writeField(page, "total", content.size(), true);
        } catch (IllegalAccessException ignored) {
        }

        return true;
    }
}
