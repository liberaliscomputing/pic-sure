package edu.harvard.dbmi.avillach.service;

import edu.harvard.dbmi.avillach.data.entity.Resource;
import edu.harvard.dbmi.avillach.data.repository.ResourceRepository;
import edu.harvard.dbmi.avillach.util.response.PICSUREResponse;
import edu.harvard.dbmi.avillach.utils.PicsureWarNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service handling business logic for CRUD on resources
 */
@Path("/resource/")
public class PicsureResourceService {

    Logger logger = LoggerFactory.getLogger(PicsureResourceService.class);

    @Inject
    ResourceRepository resourceRepo;

    @GET
    @RolesAllowed(PicsureWarNaming.RoleNaming.ROLE_SYSTEM)
    @Path("get{resourceId:(/[^/]+?)?}")
    public Response getResourceByIdOrAll(
            @PathParam("resourceId") String resourceId) {
        logger.info("Looing for resource by ID: " + resourceId);
        if (resourceId == null || resourceId.length() <= 1)
            resourceId = "all";
        else
            resourceId = resourceId.substring(1);

        List<Resource> resources;
        if (("all").equalsIgnoreCase(resourceId)){
            resources = resourceRepo.list();
        } else {
            Resource resource = resourceRepo.getById(UUID.fromString(resourceId));
            if (resource == null)
                return PICSUREResponse.protocolError("Resource is not found by given resource ID: " + resourceId);
            else
                return PICSUREResponse.success(resource);
        }

        return PICSUREResponse.success(resources);
    }

    @POST
    @RolesAllowed(PicsureWarNaming.RoleNaming.ROLE_SYSTEM)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("add")
    public Response addResource(List<Resource> resources){
        if (resources == null || resources.isEmpty())
            return PICSUREResponse.protocolError("No resource to be added.");

        List<Resource> addedResources = addOrUpdate(resources, true);

        if (addedResources.size() < resources.size())
            return PICSUREResponse.applicationError(Integer.toString(resources.size()-addedResources.size())
                    + " resources are NOT operated." +
                    " Added resources are as follow: ", addedResources);

        return PICSUREResponse.success("All resources are added.", addedResources);
    }

    @POST
    @RolesAllowed(PicsureWarNaming.RoleNaming.ROLE_SYSTEM)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("update")
    public Response updateResource(List<Resource> resources){
        if (resources == null || resources.isEmpty())
            return PICSUREResponse.protocolError("No resource to be updated.");

        List<Resource> addedResources = addOrUpdate(resources, false);

        if (addedResources.size() < resources.size())
            return PICSUREResponse.applicationError(Integer.toString(resources.size()-addedResources.size())
                    + " resources are NOT operated." +
                    " Updated resources are as follow: ", addedResources);

        return PICSUREResponse.success("All resources are updated.", addedResources);
    }

    /**
     *
     * @param resources
     * @param forAdd true for adding, false for merging
     * @return
     */
    private List<Resource> addOrUpdate(@NotNull List<Resource> resources, boolean forAdd){
        List<Resource> operatedResources = new ArrayList<>();
        for (Resource resource : resources){
            if (forAdd)
                resourceRepo.persist(resource);
            else
                resourceRepo.merge(resource);
            if (resourceRepo.getById(resource.getUuid()) == null){
                continue;
            }
            operatedResources.add(resource);
        }
        return operatedResources;
    }



    @Transactional
    @GET
    @RolesAllowed(PicsureWarNaming.RoleNaming.ROLE_SYSTEM)
    @Path("remove/{resourceId}")
    public Response removeById(@PathParam("resourceId") final String resourceId) {
        UUID uuid = UUID.fromString(resourceId);
        Resource resource = resourceRepo.getById(uuid);
        if (resource == null)
            return PICSUREResponse.protocolError("Resource is not found by resource ID");

        resourceRepo.remove(resource);

        resource = resourceRepo.getById(uuid);
        if (resource != null){
            return PICSUREResponse.applicationError("Cannot delete the resource by id: " + resourceId);
        }

        return PICSUREResponse.success("Successfully deleted resource by id: " + resourceId, null, MediaType.APPLICATION_JSON_TYPE);

    }

}
